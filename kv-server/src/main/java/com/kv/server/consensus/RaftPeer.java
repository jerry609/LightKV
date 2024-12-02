package com.kv.server.consensus;

import com.kv.thrift.AppendEntriesRequest;
import com.kv.thrift.AppendEntriesResponse;
import com.kv.thrift.RaftService;
import com.kv.thrift.RequestVoteRequest;
import com.kv.thrift.RequestVoteResponse;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RaftPeer {
    private static final Logger log = LoggerFactory.getLogger(RaftPeer.class);

    // Constants
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 2000;
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000;
    private static final int MAX_MESSAGE_LENGTH = 100 * 1024 * 1024;
    private static final int SOCKET_TIMEOUT_MS = 10000;
    private static final int MAX_CONNECTION_RETRIES = 5;
    private static final long CIRCUIT_BREAKER_RESET_TIMEOUT_MS = 120000;
    private static final long CLEANUP_INTERVAL_MS = 60000;
    private static final int CONNECTION_BACKOFF_MS = 1000;

    // Instance fields
    private final String id;
    private final String host;
    private final int port;
    private volatile long nextIndex;
    private volatile long matchIndex;

    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastSuccessfulCommunication = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastHealthCheck = new AtomicLong(System.currentTimeMillis());
    private final CircuitBreaker circuitBreaker;
    private final ConnectionPool connectionPool;
    private final AtomicLong lastConnectionAttempt = new AtomicLong(0);

    private volatile long lastCleanupTime = System.currentTimeMillis();
    private final Object connectionLock = new Object();
    private final AtomicInteger consecutiveTimeouts = new AtomicInteger(0);
    private final AtomicLong lastValidRequestTime = new AtomicLong(0);

    private void recordTimeout() {
        int timeouts = consecutiveTimeouts.incrementAndGet();
        if (timeouts >= 3) { // 连续3次超时
            log.warn("Peer {} has timed out {} times consecutively", id, timeouts);
            resetConnections(); // 重置所有连接
        }
    }

    private void recordSuccessfulRequest() {
        consecutiveTimeouts.set(0);
        lastValidRequestTime.set(System.currentTimeMillis());
    }
    // Constructor
    public RaftPeer(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.nextIndex = 1;
        this.matchIndex = 0;
        this.circuitBreaker = new CircuitBreaker(MAX_CONSECUTIVE_FAILURES, CIRCUIT_BREAKER_RESET_TIMEOUT_MS);
        this.connectionPool = new ConnectionPool();
    }
    // Getter methods
    public String getId() {
        return id;
    }

    public long getNextIndex() {
        return nextIndex;
    }

    public long getMatchIndex() {
        return matchIndex;
    }

    // Setter methods
    public void setNextIndex(long index) {
        this.nextIndex = index;
    }

    public void setMatchIndex(long index) {
        this.matchIndex = index;
    }

    // RequestVote method
    public RequestVoteResponse requestVote(RequestVoteRequest request) throws Exception {
        if (!circuitBreaker.allowRequest()) {
            log.warn("Circuit breaker is open for peer {}", id);
            throw new PeerCommunicationException("Circuit breaker is open", null);
        }

        ensureConnectionBackoff();
        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_CONNECTION_RETRIES) {
            RaftService.Client client = null;
            try {
                client = connectionPool.acquire();
                RequestVoteResponse response = client.requestVote(request);
                recordSuccess();
                circuitBreaker.recordSuccess();
                return response;
            } catch (TTransportException e) {
                log.warn("Transport error on vote request attempt {} to peer {}: {}",
                        retries + 1, id, e.getMessage());
                if (client != null) {
                    connectionPool.invalidate(client);
                }
                lastException = e;
                recordFailure();
                circuitBreaker.recordFailure();
            } catch (Exception e) {
                log.warn("General error on vote request attempt {} to peer {}: {}",
                        retries + 1, id, e.getMessage());
                lastException = e;
                recordFailure();
                circuitBreaker.recordFailure();
            } finally {
                if (client != null) {
                    connectionPool.release(client);
                }
            }

            retries++;
            if (retries < MAX_CONNECTION_RETRIES) {
                Thread.sleep(RETRY_DELAY_MS);
            }
        }

        throw new PeerCommunicationException(
                String.format("Failed to request vote from peer %s after %d attempts", id, retries),
                lastException
        );
    }
    // Public methods
    public AppendEntriesResponse appendEntries(AppendEntriesRequest request) throws Exception {
        if (!circuitBreaker.allowRequest()) {
            log.warn("Circuit breaker is open for peer {}", id);
            throw new PeerCommunicationException("Circuit breaker is open", null);
        }

        ensureConnectionBackoff();
        cleanupStaleConnections();
        int retries = 0;
        Exception lastException = null;
        long startTime = System.currentTimeMillis();

        while (retries < MAX_CONNECTION_RETRIES) {
            RaftService.Client client = null;
            try {
                client = connectionPool.acquire();
                AppendEntriesResponse response = client.appendEntries(request);
                recordSuccess();
                circuitBreaker.recordSuccess();

                long duration = System.currentTimeMillis() - startTime;
                if (duration > CONNECTION_TIMEOUT_MS / 2) {
                    log.warn("Slow appendEntries operation to peer {}: {}ms", id, duration);
                }

                return response;
            } catch (TTransportException e) {
                log.warn("Transport error on attempt {} to peer {}: {}",
                        retries + 1, id, e.getMessage());
                if (client != null) {
                    connectionPool.invalidate(client);
                }
                lastException = e;
                recordFailure();
                circuitBreaker.recordFailure();
            } catch (Exception e) {
                log.warn("General error on attempt {} to peer {}: {}",
                        retries + 1, id, e.getMessage());
                lastException = e;
                recordFailure();
                circuitBreaker.recordFailure();
            } finally {
                if (client != null) {
                    connectionPool.release(client);
                }
            }

            retries++;
            if (retries < MAX_CONNECTION_RETRIES) {
                long delay = RETRY_DELAY_MS * (1L << Math.min(retries, 4));
                Thread.sleep(Math.min(delay, 10000));
            }
        }

        throw new PeerCommunicationException(
                String.format("Failed to append entries to peer %s after %d attempts", id, retries),
                lastException
        );
    }
    /**
     * 尝试重新建立与对等节点的连接
     * @return 重连是否成功
     */
    public boolean reconnect() {
        synchronized (connectionLock) {
            log.info("Attempting to reconnect to peer {} at {}:{}", id, host, port);

            // 清理现有连接
            connectionPool.cleanup();

            // 重置断路器
            circuitBreaker.reset();

            // 尝试建立新连接
            RaftService.Client testClient = null;
            try {
                testClient = connectionPool.acquire();
                // 进行一个简单的心跳测试
                AppendEntriesRequest request = new AppendEntriesRequest(0, "", 0, 0, Collections.emptyList(), 0);
                testClient.appendEntries(request);

                // 重置失败计数
                failureCount.set(0);
                available.set(true);
                lastSuccessfulCommunication.set(System.currentTimeMillis());

                log.info("Successfully reconnected to peer {}", id);
                return true;
            } catch (Exception e) {
                log.warn("Failed to reconnect to peer {}: {}", id, e.getMessage());
                if (testClient != null) {
                    connectionPool.invalidate(testClient);
                }
                return false;
            } finally {
                if (testClient != null) {
                    connectionPool.release(testClient);
                }
            }
        }
    }

    /**
     * 检查连接是否可用
     * @return 连接是否可用
     */
    public boolean isAvailable() {
        return available.get() &&
                System.currentTimeMillis() - lastSuccessfulCommunication.get() < HEALTH_CHECK_INTERVAL_MS;
    }

    /**
     * 强制清理和重置所有连接
     */
    public void resetConnections() {
        synchronized (connectionLock) {
            connectionPool.cleanup();
            circuitBreaker.reset();
            failureCount.set(0);
            lastConnectionAttempt.set(0);
            available.set(true);
        }
    }

    // Private methods
    private void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (failureCount.get() >= MAX_CONSECUTIVE_FAILURES) {
            available.set(false);
        }
    }

    private void recordSuccess() {
        failureCount.set(0);
        lastSuccessfulCommunication.set(System.currentTimeMillis());
        available.set(true);
    }

    private void cleanupStaleConnections() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            synchronized (connectionLock) {
                try {
                    connectionPool.cleanup();
                    lastCleanupTime = now;
                } catch (Exception e) {
                    log.warn("Error during connection cleanup for peer {}: {}", id, e.getMessage());
                }
            }
        }
    }

    private void ensureConnectionBackoff() throws InterruptedException {
        long lastAttempt = lastConnectionAttempt.get();
        long now = System.currentTimeMillis();
        if (now - lastAttempt < CONNECTION_BACKOFF_MS) {
            Thread.sleep(CONNECTION_BACKOFF_MS - (now - lastAttempt));
        }
        lastConnectionAttempt.set(now);
    }

    // Inner Classes
    private class ConnectionPool {
        private final Queue<RaftService.Client> idleConnections = new ConcurrentLinkedQueue<>();
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private static final int MAX_POOL_SIZE = 10;
        private static final int MIN_IDLE = 2;

        public RaftService.Client acquire() throws Exception {
            RaftService.Client client = idleConnections.poll();
            if (client != null && validateConnection(client)) {
                return client;
            }

            if (activeConnections.get() < MAX_POOL_SIZE) {
                client = createNewConnection();
                activeConnections.incrementAndGet();
                return client;
            }

            for (int i = 0; i < MAX_RETRY_ATTEMPTS; i++) {
                client = idleConnections.poll();
                if (client != null && validateConnection(client)) {
                    return client;
                }
                Thread.sleep(100);
            }

            throw new PeerCommunicationException("Connection pool exhausted", null);
        }

        public void release(RaftService.Client client) {
            if (validateConnection(client)) {
                if (idleConnections.size() < MIN_IDLE) {
                    idleConnections.offer(client);
                } else {
                    closeClient(client);
                    activeConnections.decrementAndGet();
                }
            } else {
                closeClient(client);
                activeConnections.decrementAndGet();
            }
        }

        public void invalidate(RaftService.Client client) {
            closeClient(client);
            activeConnections.decrementAndGet();
        }

        public void cleanup() {
            Queue<RaftService.Client> newIdleConnections = new ConcurrentLinkedQueue<>();

            while (!idleConnections.isEmpty()) {
                RaftService.Client client = idleConnections.poll();
                if (client != null && validateConnection(client)) {
                    newIdleConnections.offer(client);
                } else {
                    closeClient(client);
                    activeConnections.decrementAndGet();
                }
            }

            idleConnections.addAll(newIdleConnections);
        }

        private RaftService.Client createNewConnection() throws Exception {
            TSocket socket = new TSocket(host, port, CONNECTION_TIMEOUT_MS);
            socket.getSocket().setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.getSocket().setKeepAlive(true);
            socket.getSocket().setTcpNoDelay(true);

            // 添加新的配置
            socket.getSocket().setSoLinger(true, 0);  // 强制关闭时立即释放
            socket.getSocket().setReuseAddress(true); // 允许重用地址
            socket.getSocket().setSendBufferSize(16 * 1024); // 设置更小的缓冲区
            socket.getSocket().setReceiveBufferSize(16 * 1024);

            try {
                socket.open();
                TFramedTransport transport = new TFramedTransport(socket, MAX_MESSAGE_LENGTH);
                TBinaryProtocol protocol = new TBinaryProtocol(transport);
                return new RaftService.Client(protocol);
            } catch (Exception e) {
                try {
                    socket.close();
                } catch (Exception ce) {
                    log.warn("Error closing socket after creation failure", ce);
                }
                throw new PeerCommunicationException(
                        String.format("Failed to create connection to peer %s (%s:%d)",
                                id, host, port),
                        e
                );
            }
        }

        // 改进连接验证逻辑
        private boolean validateConnection(RaftService.Client client) {
            if (client == null) {
                return false;
            }

            try {
                TTransport transport = client.getInputProtocol().getTransport();
                // 同时检查 input 和 output transport
                TTransport outputTransport = client.getOutputProtocol().getTransport();

                return transport != null && transport.isOpen() &&
                        outputTransport != null && outputTransport.isOpen();
            } catch (Exception e) {
                log.debug("Connection validation failed for peer {}: {}", id, e.getMessage());
                return false;
            }
        }


        private void closeClient(RaftService.Client client) {
            try {
                if (client != null) {
                    // 同时关闭 input 和 output transport
                    TTransport inputTransport = client.getInputProtocol().getTransport();
                    TTransport outputTransport = client.getOutputProtocol().getTransport();

                    try {
                        if (inputTransport != null && inputTransport.isOpen()) {
                            inputTransport.close();
                        }
                    } finally {
                        if (outputTransport != null && outputTransport.isOpen()) {
                            outputTransport.close();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error closing client connection: {}", e.getMessage());
            }
        }
    }

    private static class CircuitBreaker {
        private enum State {
            CLOSED,
            OPEN,
            HALF_OPEN
        }

        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicLong lastStateChange = new AtomicLong(System.currentTimeMillis());
        private final int failureThreshold;
        private final long resetTimeoutMs;

        public CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }

        public boolean allowRequest() {
            State currentState = state.get();
            if (currentState == State.OPEN) {
                if (System.currentTimeMillis() - lastStateChange.get() > resetTimeoutMs) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        lastStateChange.set(System.currentTimeMillis());
                    }
                    return true;
                }
                return false;
            }
            return true;
        }

        public void recordSuccess() {
            failureCount.set(0);
            if (state.get() == State.HALF_OPEN) {
                state.set(State.CLOSED);
                lastStateChange.set(System.currentTimeMillis());
            }
        }

        public void recordFailure() {
            if (failureCount.incrementAndGet() >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    lastStateChange.set(System.currentTimeMillis());
                }
            }
        }

        public void reset() {
            state.set(State.CLOSED);
            failureCount.set(0);
            lastStateChange.set(System.currentTimeMillis());
        }
    }
}