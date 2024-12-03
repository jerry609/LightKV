package com.kv.thrift;


import com.kv.server.consensus.NodeState;
import com.kv.server.meta.RouterManager;
import com.kv.server.consensus.RaftNode;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class KVServiceImpl implements KVService.Iface {
    private static final Logger logger = LoggerFactory.getLogger(KVServiceImpl.class);
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000;

    private final KVServiceConfig config;
    private final RouterManager routerManager;
    private final RaftNode raftNode;
    private final ExecutorService executorService;
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong timeoutOperations = new AtomicLong(0);

    public KVServiceImpl(RouterManager routerManager, RaftNode raftNode, KVServiceConfig config) {
        this.routerManager = routerManager;
        this.raftNode = raftNode;
        this.config = config;
        this.executorService = new ThreadPoolExecutor(
                config.getMinThreads(),
                config.getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueSize()),
                new ThreadFactoryBuilder()
                        .setNameFormat("kv-service-pool-%d")
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public boolean put(String key, ByteBuffer value) throws KVServiceException, TException {
        validateKey(key);
        validateValue(value);

        totalOperations.incrementAndGet();
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        int retries = 0;

        while (retries < MAX_RETRIES) {
            try {
                // 检查节点状态
                NodeState status = checkNodeStatus();
                if (status != NodeState.LEADER) {
                    handleNonLeaderOperation(status);
                    retries++;
                    continue;
                }

                // 执行Put操作
                byte[] command = CommandSerializer.serializePutCommand(key, value);
                System.out.println(command);
                CompletableFuture<Boolean> future = raftNode.propose(command);
                System.out.println("future:"+future.join());
                boolean result = future.get(config.getOperationTimeout(), TimeUnit.MILLISECONDS);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Put operation for key {} completed in {}ms on attempt {}",
                        key, duration, retries + 1);
                return result;

            } catch (Exception e) {
                lastException = handleOperationException(e, key, retries, startTime);
                if (shouldAbortOperation(e)) {
                    break;
                }
                retries++;
                if (retries < MAX_RETRIES) {
                    waitBeforeRetry();
                }
            }
        }

        return handleOperationFailure(lastException, retries);
    }

    @Override
    public ByteBuffer get(String key) throws KVServiceException, TException {
        validateKey(key);
        totalOperations.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            String value = raftNode.getStateMachine().get(key);
            long duration = System.currentTimeMillis() - startTime;

            if (value == null) {
                logger.debug("Get operation for key {} returned null in {}ms", key, duration);
                return null;
            }

            logger.debug("Get operation for key {} completed in {}ms", key, duration);
            return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed to get value for key: {} after {}ms", key, duration, e);
            throw KVServiceHelper.wrapException(
                    ErrorCode.GET_ERROR,
                    "Failed to get value",
                    e
            );
        }
    }

    @Override
    public boolean dele(String key) throws KVServiceException, TException {
        validateKey(key);
        totalOperations.incrementAndGet();
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        int retries = 0;

        while (retries < MAX_RETRIES) {
            try {
                if (!raftNode.isLeader()) {
                    handleNonLeaderOperation(checkNodeStatus());
                    retries++;
                    continue;
                }

                byte[] command = CommandSerializer.serializeDeleteCommand(key);
                CompletableFuture<Boolean> future = raftNode.propose(command);
                boolean result = future.get(config.getOperationTimeout(), TimeUnit.MILLISECONDS);

                long duration = System.currentTimeMillis() - startTime;
                logger.info("Delete operation for key {} completed in {}ms", key, duration);
                return result;

            } catch (Exception e) {
                lastException = handleOperationException(e, key, retries, startTime);
                if (shouldAbortOperation(e)) {
                    break;
                }
                retries++;
                if (retries < MAX_RETRIES) {
                    waitBeforeRetry();
                }
            }
        }

        return handleOperationFailure(lastException, retries);
    }

    @Override
    public boolean exists(String key) throws KVServiceException, TException {
        validateKey(key);
        totalOperations.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            boolean exists = raftNode.getStateMachine().get(key) != null;
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Exists check for key {} completed in {}ms, result: {}",
                    key, duration, exists);
            return exists;

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed to check existence for key: {} after {}ms", key, duration, e);
            throw KVServiceHelper.wrapException(
                    ErrorCode.EXISTS_ERROR,
                    "Failed to check key existence",
                    e
            );
        }
    }

    @Override
    public Map<String, ByteBuffer> batchGet(List<String> keys) throws KVServiceException, TException {
        validateBatchKeys(keys);
        totalOperations.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            Map<String, ByteBuffer> result = new HashMap<>();
            List<CompletableFuture<BatchGetResult>> futures = new ArrayList<>();

            // 异步提交所有get操作
            for (String key : keys) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String value = raftNode.getStateMachine().get(key);
                        return new BatchGetResult(key, value != null ?
                                ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)) : null);
                    } catch (Exception e) {
                        logger.error("Error getting value for key: {}", key, e);
                        return new BatchGetResult(key, null);
                    }
                }, executorService));
            }

            // 等待所有操作完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(config.getOperationTimeout(), TimeUnit.MILLISECONDS);

            // 收集结果
            for (CompletableFuture<BatchGetResult> future : futures) {
                BatchGetResult batchResult = future.get();
                if (batchResult.value != null) {
                    result.put(batchResult.key, batchResult.value);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Batch get operation completed for {} keys in {}ms", keys.size(), duration);
            return result;

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed batch get for {} keys after {}ms", keys.size(), duration, e);
            throw KVServiceHelper.wrapException(
                    ErrorCode.BATCH_GET_ERROR,
                    "Failed to batch get values",
                    e
            );
        }
    }

    @Override
    public boolean batchPut(Map<String, ByteBuffer> kvMap) throws KVServiceException, TException {
        validateBatchPut(kvMap);
        totalOperations.incrementAndGet();
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        int retries = 0;

        while (retries < MAX_RETRIES) {
            try {
                if (!raftNode.isLeader()) {
                    handleNonLeaderOperation(checkNodeStatus());
                    retries++;
                    continue;
                }

                byte[] command = CommandSerializer.serializeBatchPutCommand(kvMap);
                CompletableFuture<Boolean> future = raftNode.propose(command);
                boolean result = future.get(config.getOperationTimeout(), TimeUnit.MILLISECONDS);

                long duration = System.currentTimeMillis() - startTime;
                logger.info("Batch put operation completed for {} keys in {}ms",
                        kvMap.size(), duration);
                return result;

            } catch (Exception e) {
                lastException = handleOperationException(e, "batch", retries, startTime);
                if (shouldAbortOperation(e)) {
                    break;
                }
                retries++;
                if (retries < MAX_RETRIES) {
                    waitBeforeRetry();
                }
            }
        }

        return handleOperationFailure(lastException, retries);
    }

    @Override
    public NodeInfo getNodeInfo() throws KVServiceException, TException {
        long startTime = System.currentTimeMillis();
        try {
            NodeInfo localNodeInfo = routerManager.getLocalNodeInfo();
            if (localNodeInfo == null) {
                throw KVServiceHelper.wrapException(
                        ErrorCode.NODE_INFO_ERROR,
                        "Failed to get local node info"
                );
            }

            // 使用正确的构造函数返回NodeInfo
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Get node info completed in {}ms", duration);
            return localNodeInfo;  // 直接返回本地节点信息

        } catch (Exception e) {
            logger.error("Failed to get node info after {}ms",
                    System.currentTimeMillis() - startTime, e);
            throw KVServiceHelper.wrapException(
                    ErrorCode.NODE_INFO_ERROR,
                    "Failed to get node info",
                    e
            );
        }
    }
    private NodeState checkNodeStatus() {
        if (!raftNode.isLeader()) {
            return NodeState.FOLLOWER;  // 直接返回FOLLOWER状态
        }
        return NodeState.LEADER;
    }

    private void handleNonLeaderOperation(NodeState status) throws KVServiceException {
        NodeInfo leaderInfo = routerManager.getLeaderNode();
        if (leaderInfo != null) {
            String message = String.format("Current node is not leader. Leader is: %s at %s:%d",
                    leaderInfo.getNodeId(), leaderInfo.getHost(), leaderInfo.getPort());
            throw KVServiceHelper.wrapException(
                    ErrorCode.NOT_LEADER,
                    message
            );
        }

        logger.warn("No leader available, waiting for election...");
    }


    private Exception handleOperationException(Exception e, String key, int retryCount, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        if (e instanceof TimeoutException) {
            timeoutOperations.incrementAndGet();
            logger.warn("Operation timed out for key: {} after {}ms on attempt {}",
                    key, duration, retryCount + 1);
        } else if (e instanceof InterruptedException) {
            logger.warn("Operation interrupted for key: {} after {}ms on attempt {}",
                    key, duration, retryCount + 1);
            Thread.currentThread().interrupt();
        } else {
            logger.error("Failed operation for key: {} after {}ms on attempt {}",
                    key, duration, retryCount + 1, e);
        }

        return e;
    }

    private boolean shouldAbortOperation(Exception e) {
        return e instanceof InterruptedException ||
                e instanceof IllegalStateException ||
                e instanceof SecurityException;
    }

    private void waitBeforeRetry() throws KVServiceException {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw KVServiceHelper.wrapException(
                    ErrorCode.INTERRUPTED,
                    "Operation interrupted during retry",
                    ie
            );
        }
    }

    private boolean handleOperationFailure(Exception lastException, int retries) throws KVServiceException {
        failedOperations.incrementAndGet();
        String errorMessage = String.format("Operation failed after %d retries", retries);
        logger.error(errorMessage);

        throw KVServiceHelper.wrapException(
                ErrorCode.EXECUTION_ERROR,
                errorMessage,
                lastException
        );
    }

    private void validateKey(String key) throws KVServiceException {
        if (key == null || key.isEmpty()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Key cannot be null or empty"
            );
        }
        if (key.length() > config.getMaxKeyLength()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Key length exceeds maximum allowed length of " + config.getMaxKeyLength()
            );
        }
    }

    private void validateValue(ByteBuffer value) throws KVServiceException {
        if (value == null) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Value cannot be null"
            );
        }
        if (value.remaining() > config.getMaxValueSize()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Value size exceeds maximum allowed size of " + config.getMaxValueSize()
            );
        }
    }

    private void validateBatchKeys(List<String> keys) throws KVServiceException {
        if (keys == null || keys.isEmpty()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Keys list cannot be null or empty"
            );
        }

        if (keys.size() > config.getBatchSize()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Batch size " + keys.size() + " exceeds maximum allowed size of " + config.getBatchSize()
            );
        }

        for (String key : keys) {
            validateKey(key);
        }
    }

    private void validateBatchPut(Map<String, ByteBuffer> kvMap) throws KVServiceException {
        if (kvMap == null || kvMap.isEmpty()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "KV map cannot be null or empty"
            );
        }

        if (kvMap.size() > config.getBatchSize()) {
            throw KVServiceHelper.wrapException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Batch size " + kvMap.size() + " exceeds maximum allowed size of " + config.getBatchSize()
            );
        }

        for (Map.Entry<String, ByteBuffer> entry : kvMap.entrySet()) {
            validateKey(entry.getKey());
            validateValue(entry.getValue());
        }
    }

    // 用于批量get操作的结果封装类
    private static class BatchGetResult {
        final String key;
        final ByteBuffer value;

        BatchGetResult(String key, ByteBuffer value) {
            this.key = key;
            this.value = value;
        }
    }

    public void shutdown() {
        logger.info("Shutting down KV service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Shutdown interrupted, forcing immediate shutdown");
            executorService.shutdownNow();
        }
        logger.info("KV service shutdown completed");
    }

    // Metrics methods
    public long getTotalOperations() {
        return totalOperations.get();
    }

    public long getFailedOperations() {
        return failedOperations.get();
    }

    public long getTimeoutOperations() {
        return timeoutOperations.get();
    }
}

