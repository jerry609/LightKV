package com.kv.thrift;

import com.kv.server.meta.RouterManager;
import com.kv.server.consensus.RaftNode;
import com.kv.thrift.NodeInfo;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class KVServiceImpl implements KVService.Iface {
    private static final long OPERATION_TIMEOUT = 5000;//5 seconds
    private final RouterManager routerManager;
    private final RaftNode raftNode;

    public KVServiceImpl(RouterManager routerManager, RaftNode raftNode) {
        this.routerManager = routerManager;
        this.raftNode = raftNode;
    }

    @Override
    public boolean put(String key, ByteBuffer value) throws KVServiceException, TException {
        try {
            byte[] command = CommandSerializer.serializePutCommand(key, value);
            CompletableFuture<Boolean> future = raftNode.propose(command);
            return future.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new KVServiceException(1, "Failed to put value: " + e.getMessage());
        }
    }

    @Override
    public ByteBuffer get(String key) throws KVServiceException, TException {
        try {
            return ByteBuffer.wrap(raftNode.getStateMachine().get(key).getBytes());
        } catch (Exception e) {
            throw new KVServiceException(2, "Failed to get value: " + e.getMessage());
        }
    }

    @Override
    public boolean dele(String key) throws KVServiceException, TException {
        try {
            byte[] command = CommandSerializer.serializeDeleteCommand(key);
            CompletableFuture<Boolean> future = raftNode.propose(command);
            return future.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new KVServiceException(3, "Failed to delete value: " + e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) throws KVServiceException, TException {
        try {
            byte[] value = raftNode.getStateMachine().get(key).getBytes();
            return value != null;
        } catch (Exception e) {
            throw new KVServiceException(4, "Failed to check key existence: " + e.getMessage());
        }
    }

    @Override
    public Map<String, ByteBuffer> batchGet(List<String> keys) throws KVServiceException, TException {
        try {
            Map<String, ByteBuffer> result = new HashMap<>();
            for (String key : keys) {
                ByteBuffer value = ByteBuffer.wrap(raftNode.getStateMachine().get(key).getBytes());
                if (value != null) {
                    result.put(key, value);
                }
            }
            return result;
        } catch (Exception e) {
            throw new KVServiceException(5, "Failed to batch get values: " + e.getMessage());
        }
    }

    @Override
    public boolean batchPut(Map<String, ByteBuffer> kvMap) throws KVServiceException, TException {
        try {
            byte[] command = CommandSerializer.serializeBatchPutCommand(kvMap);
            CompletableFuture<Boolean> future = raftNode.propose(command);
            return future.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new KVServiceException(6, "Failed to batch put values: " + e.getMessage());
        }
    }

    public NodeInfo getNodeInfo() throws KVServiceException, TException {
        try {
            return routerManager.getLocalNodeInfo();
        } catch (Exception e) {
            throw new KVServiceException(7, "Failed to get node info: " + e.getMessage());
        }
    }

    private static class CommandSerializer {
        private static final byte CMD_PUT = 1;
        private static final byte CMD_DELETE = 2;
        private static final byte CMD_BATCH_PUT = 3;

        public static byte[] serializePutCommand(String key, ByteBuffer value) {
            // TODO: Implement proper serialization
            // This is a placeholder implementation
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[1 + 4 + keyBytes.length + 4 + value.capacity()];
            result[0] = CMD_PUT;
            // Add key length and key bytes
            // Add value length and value bytes
            return result;
        }

        public static byte[] serializeDeleteCommand(String key) {
            // TODO: Implement proper serialization
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[1 + 4 + keyBytes.length];
            result[0] = CMD_DELETE;
            // Add key length and key bytes
            return result;
        }

        public static byte[] serializeBatchPutCommand(Map<String, ByteBuffer> kvMap) {
            // TODO: Implement proper serialization
            // This is a placeholder implementation
            return new byte[]{CMD_BATCH_PUT};
        }
    }
}