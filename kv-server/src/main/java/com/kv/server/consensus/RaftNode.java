package com.kv.server.consensus;

import com.kv.server.storage.LogStore;
import com.kv.thrift.AppendEntriesRequest;
import com.kv.thrift.AppendEntriesResponse;
import com.kv.thrift.LogEntry;
import com.kv.thrift.RaftService;
import com.kv.thrift.RequestVoteRequest;
import com.kv.thrift.RequestVoteResponse;
import lombok.Getter;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class RaftNode implements RaftService.Iface {
    private static final Logger log = LoggerFactory.getLogger(RaftNode.class);
    private static final long HEARTBEAT_INTERVAL = 500;    // 降低到 500ms
    private static final long HEARTBEAT_TIMEOUT_MS = 2000; // 降低到 2s
    private static final int ELECTION_TIMEOUT_MIN = 3000;  // 降低到 3s
    private static final int ELECTION_TIMEOUT_MAX = 4500;  // 降低到 4.5s
    private static final int ELECTION_TIMER_RESET_MIN_INTERVAL = 1000; // 新增：最小重置间隔

    private volatile long lastElectionTimerReset = 0; // 新增：记录上次重置时间
    // Constants
    private static final int SOCKET_TIMEOUT_MS = 10000;       // Socket超时设为10s
    private static final int RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long CONNECT_RETRY_DELAY_MS = 1000;

    // Core components
    private final String nodeId;
    private final List<RaftPeer> peers;
    private final LogStore logStore;
    @Getter
    private final KVStateMachine stateMachine;
    private final int port;

    // Thread pools
    private final ScheduledExecutorService scheduler;
    private final ExecutorService rpcExecutor;
    private TServer raftServer;

    // Node state
    private volatile NodeState state;
    private volatile String currentLeader;
    private final AtomicLong currentTerm;
    private volatile String votedFor;

    // Progress tracking
    private volatile long commitIndex;
    private volatile long lastApplied;
    private final ConcurrentMap<Long, CompletableFuture<Boolean>> pendingProposals;
    private final ConcurrentMap<String, Long> lastHeartbeatTime;
    private ScheduledFuture<?> electionTimer;
    private final Random random;

    private LeaderChangeListener leaderChangeListener;

    public RaftNode(String nodeId, List<RaftPeer> peers, String rocksdbPath,
                    String raftLogPath, int port) {
        this.nodeId = nodeId;
        this.peers = peers;
        this.port = port;

        // Initialize directories
        String logPath = raftLogPath + File.separator + "log";
        String statePath = raftLogPath + File.separator + "state";
        createDirectories(raftLogPath);

        // Initialize core components
        this.logStore = new RocksDBLogStore(logPath);
        this.stateMachine = new KVStateMachine(rocksdbPath);

        // Initialize thread pools
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.rpcExecutor = Executors.newFixedThreadPool(4);

        // Initialize state
        this.state = NodeState.FOLLOWER;
        this.currentTerm = new AtomicLong(0);
        this.votedFor = null;
        this.commitIndex = 0;
        this.lastApplied = 0;

        // Initialize tracking maps
        this.pendingProposals = new ConcurrentHashMap<>();
        this.lastHeartbeatTime = new ConcurrentHashMap<>();
        this.random = new Random();

        log.info("RaftNode initialized with ID: {}, port: {}", nodeId, port);
    }

    private void createDirectories(String raftLogPath) {
        File baseDir = new File(raftLogPath);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new RuntimeException("Failed to create base directory: " + raftLogPath);
        }
    }
    public boolean isLeader() {
        return state == NodeState.LEADER;
    }
    private boolean preVote() throws InterruptedException {
        AtomicInteger votesReceived = new AtomicInteger(1);
        CountDownLatch votingComplete = new CountDownLatch(peers.size());

        // 增加超时时间
        long timeout = ELECTION_TIMEOUT_MIN;

        // 为每个peer添加重试机制
        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                votingComplete.countDown();
                continue;
            }

            rpcExecutor.execute(() -> {
                for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
                    try {
                        RequestVoteRequest request = new RequestVoteRequest(
                                currentTerm.get() + 1,
                                nodeId,
                                logStore.getLastIndex(),
                                logStore.getLastTerm()
                        );
                        RequestVoteResponse response = peer.requestVote(request);
                        if (response.voteGranted) {
                            votesReceived.incrementAndGet();
                        }
                        break;
                    } catch (Exception e) {
                        if (attempt == RETRY_ATTEMPTS - 1) {
                            log.warn("Failed to get pre-vote from peer {}: {}",
                                    peer.getId(), e.getMessage());
                        }
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                votingComplete.countDown();
            });
        }

        return votingComplete.await(timeout, TimeUnit.MILLISECONDS) &&
                votesReceived.get() > peers.size() / 2;
    }
    private void checkPeerHealth() {
        long now = System.currentTimeMillis();
        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                continue;
            }

            Long lastHeartbeat = lastHeartbeatTime.get(peer.getId());
            if (lastHeartbeat == null || now - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                log.warn("Peer {} might be unavailable, attempting reconnection", peer.getId());

                // 使用同步锁避免并发重连
                synchronized(peer) {
                    // 再次检查，避免在获取锁期间状态已改变
                    lastHeartbeat = lastHeartbeatTime.get(peer.getId());
                    if (lastHeartbeat == null || now - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        // 重置连接前先关闭现有连接
                        peer.resetConnections();

                        // 使用指数退避进行重连
                        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
                            if (peer.reconnect()) {
                                log.info("Successfully reconnected to peer {} after {} attempts",
                                        peer.getId(), attempt + 1);
                                lastHeartbeatTime.put(peer.getId(), now);
                                break;
                            }
                            // 指数退避等待
                            try {
                                Thread.sleep(RETRY_DELAY_MS * (1L << Math.min(attempt, 4)));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public void startRaftServer() throws Exception {
        // 创建 ServerSocket 实例
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.setReceiveBufferSize(64 * 1024);  // 64KB receive buffer

        // 绑定地址和端口
        InetSocketAddress bindAddr = new InetSocketAddress(port);
        serverSocket.bind(bindAddr);

        // 创建自定义的 TServerTransport
        int clientTimeout = 5000; // 5秒客户端超时
        int maxMessageSize = 64 * 1024 * 1024; // 64MB
        CustomTServerSocket customServerSocket = new CustomTServerSocket(serverSocket, clientTimeout, maxMessageSize);

        // 创建 Thrift 处理器
        RaftService.Processor<RaftNode> processor = new RaftService.Processor<>(this);

        // 设置 Thrift 传输工厂
        TFramedTransport.Factory transportFactory = new TFramedTransport.Factory(maxMessageSize);

        // 配置 Thrift 服务器参数
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(customServerSocket)
                .processor(processor)
                .protocolFactory(new TBinaryProtocol.Factory())
                .transportFactory(transportFactory)
                .minWorkerThreads(4)
                .maxWorkerThreads(8);

        // 创建 Thrift 服务器实例
        raftServer = new TThreadPoolServer(args);

        // 使用守护线程启动服务器
        Thread serverThread = new Thread(() -> {
            try {
                log.info("Raft Thrift Server starting on port {} with nodeId: {}", port, nodeId);
                raftServer.serve();
            } catch (Exception e) {
                log.error("Raft server error", e);
            }
        }, "raft-server-thread");

        serverThread.setDaemon(true);
        serverThread.start();

        // 等待服务器完全启动
        Thread.sleep(1000);

        // 验证服务器是否正常启动
        if (!serverSocket.isBound() || serverSocket.isClosed()) {
            throw new IllegalStateException("Failed to start Raft server on port " + port);
        }

        log.info("Raft Thrift Server successfully started on port {} with nodeId: {}", port, nodeId);
    }

    // 添加相关常量
    private static final int MAX_MESSAGE_SIZE = 16 * 1024 * 1024;  // 16MB 最大消息大小
    private synchronized void startElection() throws InterruptedException {
        if (state == NodeState.LEADER) {
            return;
        }

        if (!preVote()) {
            log.info("Pre-vote failed, not starting election");
            resetElectionTimer();
            return;
        }

        try {
            state = NodeState.CANDIDATE;
            long newTerm = currentTerm.incrementAndGet();
            votedFor = nodeId;
            currentLeader = null;

            log.info("Starting election for term {} on node {}", newTerm, nodeId);

            final int requiredVotes = (peers.size() / 2) + 1;
            final AtomicInteger votesReceived = new AtomicInteger(1);
            CountDownLatch votingComplete = new CountDownLatch(peers.size());

            requestVotesFromPeers(newTerm, votesReceived, requiredVotes, votingComplete);

            if (!votingComplete.await(ELECTION_TIMEOUT_MAX, TimeUnit.MILLISECONDS)) {
                log.warn("Election for term {} timed out", newTerm);
                stepDown(newTerm);
                return;
            }

            if (state == NodeState.CANDIDATE && votesReceived.get() >= requiredVotes) {
                becomeLeader();
            }

        } catch (Exception e) {
            log.error("Error during election: {}", e.getMessage());
            stepDown(currentTerm.get());
        } finally {
            resetElectionTimer();
        }
    }

    private void requestVotesFromPeers(
            long term,
            AtomicInteger votesReceived,
            int requiredVotes,
            CountDownLatch votingComplete) {

        RequestVoteRequest request = new RequestVoteRequest(
                term,
                nodeId,
                logStore.getLastIndex(),
                logStore.getLastTerm()
        );

        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                votingComplete.countDown();
                continue;
            }

            rpcExecutor.execute(() -> requestVoteFromPeer(
                    peer, request, votesReceived, requiredVotes, votingComplete));
        }
    }

    private void requestVoteFromPeer(
            RaftPeer peer,
            RequestVoteRequest request,
            AtomicInteger votesReceived,
            int requiredVotes,
            CountDownLatch votingComplete) {

        for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
            try {
                RequestVoteResponse response = peer.requestVote(request);
                handleVoteResponse(response, votesReceived, requiredVotes);
                break;
            } catch (Exception e) {
                if (attempt == RETRY_ATTEMPTS - 1) {
                    log.warn("Failed to get vote from peer {}: {}",
                            peer.getId(), e.getMessage());
                } else {
                    try {
                        Thread.sleep(CONNECT_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (attempt == RETRY_ATTEMPTS - 1) {
                    votingComplete.countDown();
                }
            }
        }
    }

    private synchronized void handleVoteResponse(
            RequestVoteResponse response,
            AtomicInteger votesReceived,
            int majority) {

        if (state != NodeState.CANDIDATE) {
            return;
        }

        if (response.term > currentTerm.get()) {
            stepDown(response.term);
            return;
        }

        if (response.voteGranted && votesReceived.incrementAndGet() >= majority) {
            becomeLeader();
        }
    }

    // Leader Methods
    private synchronized void becomeLeader() {
        if (state == NodeState.LEADER) {
            return;
        }

        log.info("Node {} becoming leader for term {}", nodeId, currentTerm.get());
        state = NodeState.LEADER;
        currentLeader = nodeId;
        stopElectionTimer();

        long lastIndex = logStore.getLastIndex();
        for (RaftPeer peer : peers) {
            peer.setNextIndex(lastIndex + 1);
            peer.setMatchIndex(0);
        }

        sendHeartbeat();

        if (leaderChangeListener != null) {
            leaderChangeListener.onLeaderChange(nodeId);
        }

        scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                0,
                HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS
        );
    }

    private void sendHeartbeat() {
        if (state != NodeState.LEADER) {
            return;
        }

        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                continue;
            }

            rpcExecutor.execute(() -> sendHeartbeatToPeer(peer));
        }
    }

    private void sendHeartbeatToPeer(RaftPeer peer) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                AppendEntriesRequest request = createHeartbeatRequest(peer);
                AppendEntriesResponse response = peer.appendEntries(request);
                handleHeartbeatResponse(peer, response);
                return;
            } catch (Exception e) {
                log.warn("Heartbeat to {} failed, attempt {}/{}",
                        peer.getId(), attempt + 1, MAX_RETRY_ATTEMPTS);
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private synchronized void handleHeartbeatResponse(RaftPeer peer, AppendEntriesResponse response) {
        if (state != NodeState.LEADER) {
            return;
        }

        try {
            if (response.term > currentTerm.get()) {
                stepDown(response.term);
                return;
            }

            lastHeartbeatTime.put(peer.getId(), System.currentTimeMillis());

            if (response.success) {
                long lastIndex = logStore.getLastIndex();
                peer.setNextIndex(lastIndex + 1);
                peer.setMatchIndex(lastIndex);
                log.debug("Heartbeat successful to peer {}", peer.getId());
            } else {
                decrementNextIndex(peer);
                replicateLog(peer);
            }
        } catch (Exception e) {
            log.error("Error handling heartbeat response from peer {}: {}",
                    peer.getId(), e.getMessage());
        }
    }

    // Log Replication Methods
    private CompletableFuture<Void> replicateLog(RaftPeer peer) {
        long nextIndex = peer.getNextIndex();

        // 创建一个 CompletableFuture 来处理该 peer 的复制任务
        CompletableFuture<Void> replicationFuture = new CompletableFuture<>();

        // 异步执行日志复制任务
        CompletableFuture.runAsync(() -> {
            for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
                try {
                    // 获取需要复制的日志条目
                    List<com.kv.server.consensus.LogEntry> entries =
                            fetchLogEntries(nextIndex, logStore.getLastIndex());

                    if (entries.isEmpty()) {
                        replicationFuture.complete(null); // 无日志需要复制，标记为完成
                        return;
                    }

                    // 创建附加条目的请求
                    AppendEntriesRequest request = createAppendEntriesRequest(nextIndex - 1, entries);

                    // 发送异步请求
                    AppendEntriesResponse response = peer.appendEntries(request);

                    // 处理响应
                    handleAppendEntriesResponse(peer, response, entries);

                    // 复制成功，标记完成
                    replicationFuture.complete(null);
                    return;

                } catch (Exception e) {
                    log.warn("Failed to replicate log to peer {}, attempt {}/{}: {}",
                            peer.getId(), attempt + 1, RETRY_ATTEMPTS, e.getMessage());

                    if (attempt == RETRY_ATTEMPTS - 1) {
                        // 如果所有尝试失败，标记为异常
                        handleReplicationFailure(peer, e);
                        replicationFuture.completeExceptionally(e);
                    } else {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            replicationFuture.completeExceptionally(ie);
                            break;
                        }
                    }
                }
            }
        }, rpcExecutor);

        return replicationFuture;
    }
    private List<com.kv.server.consensus.LogEntry> fetchLogEntries(long fromIndex, long toIndex)
            throws Exception {
        List<com.kv.server.consensus.LogEntry> entries = new ArrayList<>();
        try {
            for (long i = fromIndex; i <= toIndex; i++) {
                com.kv.server.consensus.LogEntry entry = logStore.getEntry(i);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to fetch log entries from {} to {}: {}",
                    fromIndex, toIndex, e.getMessage());
            throw e;
        }
    }

    private AppendEntriesRequest createAppendEntriesRequest(
            long prevLogIndex,
            List<com.kv.server.consensus.LogEntry> entries) {
        List<LogEntry> thriftEntries = entries.stream()
                .map(entry -> new LogEntry(
                        entry.getIndex(),
                        entry.getTerm(),
                        ByteBuffer.wrap(entry.getCommand())))
                .collect(Collectors.toList());

        return new AppendEntriesRequest(
                currentTerm.get(),
                nodeId,
                prevLogIndex,
                logStore.getTermForIndex(prevLogIndex),
                thriftEntries,
                commitIndex
        );
    }

    private AppendEntriesRequest createHeartbeatRequest(RaftPeer peer) {
        return new AppendEntriesRequest(
                currentTerm.get(),
                nodeId,
                peer.getNextIndex() - 1,
                logStore.getTermForIndex(peer.getNextIndex() - 1),
                Collections.emptyList(),
                commitIndex
        );
    }

    private synchronized void handleAppendEntriesResponse(
            RaftPeer peer,
            AppendEntriesResponse response,
            List<com.kv.server.consensus.LogEntry> entries) throws Exception {

        if (response.term > currentTerm.get()) {
            stepDown(response.term);
            return;
        }

        if (state != NodeState.LEADER) {
            return;
        }

        if (response.success) {
            updatePeerProgress(peer, entries);
            updateCommitIndex();
        } else {
            decrementNextIndex(peer);
            replicateLog(peer);
        }
    }

    private void updatePeerProgress(RaftPeer peer, List<com.kv.server.consensus.LogEntry> entries) {
        if (!entries.isEmpty()) {
            long lastIndex = entries.get(entries.size() - 1).getIndex();
            peer.setMatchIndex(lastIndex);
            peer.setNextIndex(lastIndex + 1);
        }
    }

    private void decrementNextIndex(RaftPeer peer) {
        long nextIndex = peer.getNextIndex();
        peer.setNextIndex(Math.max(1, nextIndex - 1));
    }

    private void handleReplicationFailure(RaftPeer peer, Exception e) {
        log.error("Log replication failed for peer {}: {}", peer.getId(), e.getMessage());
        decrementNextIndex(peer);
        tryReconnectPeer(peer);
    }

    private synchronized void updateCommitIndex() throws Exception {
        long newCommitIndex = commitIndex;
        for (long n = commitIndex + 1; n <= logStore.getLastIndex(); n++) {
            if (isEntryCommitable(n)) {
                newCommitIndex = n;
            } else {
                break;
            }
        }

        if (newCommitIndex > commitIndex) {
            commitIndex = newCommitIndex;
            applyLogEntries();
        }
    }

    private boolean isEntryCommitable(long index) throws Exception {
        int matchCount = 1; // Include self
        for (RaftPeer peer : peers) {
            if (peer.getMatchIndex() >= index) {
                matchCount++;
            }
        }
        return matchCount > peers.size() / 2 &&
                logStore.getTermForIndex(index) == currentTerm.get();
    }

    private void applyLogEntries() throws Exception {
        while (lastApplied < commitIndex) {
            lastApplied++;
            com.kv.server.consensus.LogEntry entry = logStore.getEntry(lastApplied);
            if (entry != null) {
                try {
                    stateMachine.apply(entry);
                    completePendingProposal(entry);
                } catch (Exception e) {
                    log.error("Failed to apply log entry {}: {}", entry.getIndex(), e.getMessage());
                    throw e;
                }
            }
        }
    }

    private void completePendingProposal(com.kv.server.consensus.LogEntry entry) {
        CompletableFuture<Boolean> future = pendingProposals.remove(entry.getIndex());
        if (future != null) {
            future.complete(true);
        }
    }

    // Timer Management Methods
    private final Object electionTimerLock = new Object();

    private void resetElectionTimer() {
        synchronized(electionTimerLock) {
            long now = System.currentTimeMillis();
            // 避免频繁重置
            if (now - lastElectionTimerReset < ELECTION_TIMER_RESET_MIN_INTERVAL) {
                log.debug("Skipping election timer reset - too soon since last reset");
                return;
            }

            stopElectionTimer();

            // 使用更小的随机范围
            int randomTimeout = ELECTION_TIMEOUT_MIN +
                    random.nextInt(ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN);

            electionTimer = scheduler.schedule(
                    () -> {
                        try {
                            if (state != NodeState.LEADER) {  // 添加状态检查
                                startElection();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Election timer interrupted", e);
                        }
                    },
                    randomTimeout,
                    TimeUnit.MILLISECONDS
            );

            lastElectionTimerReset = now;
            log.debug("Election timer reset with timeout: {}ms", randomTimeout);
        }
    }

    private boolean isElectionTimerActive() {
        return electionTimer != null && !electionTimer.isDone() && !electionTimer.isCancelled();
    }


    private void stopElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel(true);
            electionTimer = null;
        }
    }

    // Health Check Methods
    private void checkHeartbeat() {
        if (state != NodeState.LEADER) {
            return;
        }
        checkPeerHealth();
    }



    private void tryReconnectPeer(RaftPeer peer) {
        rpcExecutor.execute(() -> {
            for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    AppendEntriesRequest request = createHeartbeatRequest(peer);
                    AppendEntriesResponse response = peer.appendEntries(request);
                    if (response != null) {
                        log.info("Successfully reconnected to peer {}", peer.getId());
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Failed to reconnect to peer {}, attempt {}/{}",
                            peer.getId(), attempt + 1, MAX_RETRY_ATTEMPTS);
                    if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                        try {
                            Thread.sleep(CONNECT_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        });
    }
    // RPC Interface Methods
    @Override
    public synchronized RequestVoteResponse requestVote(RequestVoteRequest request)
            throws TException {
        log.debug("Received vote request from {} for term {}",
                request.candidateId, request.term);

        // 先检查任期
        if (request.term < currentTerm.get()) {
            return new RequestVoteResponse(currentTerm.get(), false);
        }

        // 如果请求的任期更大，立即转为 follower
        if (request.term > currentTerm.get()) {
            stepDown(request.term);
        }

        // 检查是否已经投票给其他节点
        if (votedFor != null && !votedFor.equals(request.candidateId)
                && request.term == currentTerm.get()) {
            return new RequestVoteResponse(currentTerm.get(), false);
        }

        // 检查日志是否最新
        if (!isLogUpToDate(request.lastLogIndex, request.lastLogTerm)) {
            return new RequestVoteResponse(currentTerm.get(), false);
        }

        // 授予投票
        votedFor = request.candidateId;
        resetElectionTimer();  // 重置选举定时器

        log.info("Granted vote to {} for term {}", request.candidateId, request.term);
        return new RequestVoteResponse(currentTerm.get(), true);
    }

    private boolean isLogUpToDate(long lastLogIndex, long lastLogTerm) {
        long myLastLogTerm = logStore.getLastTerm();
        long myLastLogIndex = logStore.getLastIndex();

        if (lastLogTerm != myLastLogTerm) {
            return lastLogTerm > myLastLogTerm;
        }
        return lastLogIndex >= myLastLogIndex;
    }

    @Override
    public synchronized AppendEntriesResponse appendEntries(AppendEntriesRequest request)
            throws TException {
        try {
            if (request.term < currentTerm.get()) {
                return new AppendEntriesResponse(currentTerm.get(), false);
            }

            resetElectionTimer();

            if (request.term > currentTerm.get()) {
                stepDown(request.term);
            }

            currentLeader = request.leaderId;

            if (!logStore.containsEntry(request.prevLogIndex, request.prevLogTerm)) {
                return new AppendEntriesResponse(currentTerm.get(), false);
            }

            processLogEntries(request);
            updateCommitIndexFromLeader(request.leaderCommit);

            return new AppendEntriesResponse(currentTerm.get(), true);
        } catch (Exception e) {
            log.error("Error processing AppendEntries request: {}", e.getMessage());
            return new AppendEntriesResponse(currentTerm.get(), false);
        }
    }

    // Log Processing Methods
    private void processLogEntries(AppendEntriesRequest request) throws Exception {
        if (request.entries != null && !request.entries.isEmpty()) {
            List<com.kv.server.consensus.LogEntry> entries =
                    convertThriftEntries(request.entries);
            logStore.append(entries);
        }
    }

    private List<com.kv.server.consensus.LogEntry> convertThriftEntries(
            List<LogEntry> thriftEntries) {
        return thriftEntries.stream()
                .map(entry -> new com.kv.server.consensus.LogEntry(
                        entry.index,
                        entry.term,
                        entry.command.array()))
                .collect(Collectors.toList());
    }

    private void updateCommitIndexFromLeader(long leaderCommit) throws Exception {
        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, logStore.getLastIndex());
            applyLogEntries();
        }
    }

    // Command Proposal
    public CompletableFuture<Boolean> propose(byte[] command) throws Exception {
        if (state != NodeState.LEADER) {
            throw new IllegalStateException("Not the leader");
        }

        // 创建一个新的日志条目
        com.kv.server.consensus.LogEntry entry = new com.kv.server.consensus.LogEntry(
                logStore.getLastIndex() + 1,
                currentTerm.get(),
                command
        );

        try {
            // 将日志条目追加到日志存储中
            logStore.append(Collections.singletonList(entry));

            // 创建一个新的 CompletableFuture 来等待日志复制的结果
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            // 将该条目的索引与 future 关联起来，存储在 pendingProposals 中
            pendingProposals.put(entry.getIndex(), future);

            // 生成所有的复制操作任务
            List<CompletableFuture<Void>> replicationFutures = new ArrayList<>();
            for (RaftPeer peer : peers) {
                if (!peer.getId().equals(nodeId)) {
                    // 向每个 peer 节点发送日志复制请求，并返回一个 CompletableFuture
                    CompletableFuture<Void> replicateFuture = replicateLog(peer);
                    replicationFutures.add(replicateFuture);
                }
            }

            // 使用 allOf 等待所有复制操作完成
            CompletableFuture<Void> allReplications = CompletableFuture.allOf(replicationFutures.toArray(new CompletableFuture[0]));

            // 当所有复制操作完成时，标记 future 为完成
            allReplications.thenRun(() -> {
                future.complete(true); // 所有复制完成，标记为成功
            }).exceptionally(ex -> {
                // 如果有任何复制失败，标记 future 为异常
                future.completeExceptionally(ex);
                return null;
            });

            // 返回待完成的 future
            return future;
        } catch (Exception e) {
            log.error("Failed to propose command: {}", e.getMessage());
            throw e;
        }
    }

    // Lifecycle Methods
    public void start() {
        try {
            startRaftServer();
            startHeartbeatAndElection();
            log.info("RaftNode started successfully");
        } catch (Exception e) {
            log.error("Failed to start RaftNode", e);
            throw new RuntimeException("Failed to start Raft server", e);
        }
    }

    private void startHeartbeatAndElection() {
        scheduler.scheduleWithFixedDelay(
                this::checkHeartbeat,
                HEARTBEAT_INTERVAL,
                HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS
        );
        resetElectionTimer();
    }

    public void stop() {
        try {
            stopServices();
            closeResources();
            handlePendingProposals();
            log.info("RaftNode stopped successfully");
        } catch (Exception e) {
            log.error("Error stopping RaftNode", e);
            throw new RuntimeException("Failed to stop RaftNode", e);
        }
    }

    private void stopServices() {
        if (raftServer != null) {
            raftServer.stop();
        }

        stopElectionTimer();
        shutdownExecutor(scheduler, "Scheduler");
        shutdownExecutor(rpcExecutor, "RPC Executor");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("{} shutdown interrupted", name);
            }
        }
    }

    private void closeResources() throws IOException {
        if (stateMachine != null) {
            stateMachine.close();
        }

        if (logStore != null) {
            logStore.close();
        }
    }

    private void handlePendingProposals() {
        for (CompletableFuture<Boolean> future : pendingProposals.values()) {
            future.completeExceptionally(
                    new IllegalStateException("Node is shutting down")
            );
        }
        pendingProposals.clear();
    }

    // Utility Methods
    private synchronized void stepDown(long newTerm) {
        if (currentTerm.get() >= newTerm) {
            return;  // 如果当前term已经大于等于新term，不需要stepDown
        }

        log.info("Node {} stepping down in term {}", nodeId, newTerm);
        stopElectionTimer();
        state = NodeState.FOLLOWER;
        currentTerm.set(newTerm);
        votedFor = null;
        currentLeader = null;
        resetElectionTimer();  // 只调用一次
    }

        public void setLeaderChangeListener(LeaderChangeListener listener) {
            this.leaderChangeListener = listener;
        }

        public interface LeaderChangeListener {
            void onLeaderChange(String newLeaderId);
        }

        private enum NodeState {
            FOLLOWER,
            CANDIDATE,
            LEADER
        }
    }