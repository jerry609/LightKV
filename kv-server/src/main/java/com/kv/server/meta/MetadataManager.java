package com.kv.server.meta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.kv.common.model.StorageNode;

public class MetadataManager {
    private final Map<String, StorageNode> clusterNodes;
    private volatile String currentLeader;
    private LeaderChangeListener leaderChangeListener;
    private RouterManager routerManager;
    private StorageNode localNode;

    public MetadataManager(String nodeId, String host, int port) {
        this.clusterNodes = new ConcurrentHashMap<>();
        // 初始化本地节点为 ONLINE 状态
        this.localNode = new StorageNode(nodeId, host, port);
        this.localNode.setStatus(StorageNode.NodeStatus.ONLINE);
        this.localNode.updateHeartbeat();
        this.clusterNodes.put(nodeId, localNode);
    }

    /**
     * 获取本地节点信息
     */
    public StorageNode getLocalNode() {
        // 确保心跳是最新的
        localNode.updateHeartbeat();
        return localNode;
    }

    public void updateLeader(String newLeaderId) {
        this.currentLeader = newLeaderId;
        if (leaderChangeListener != null) {
            leaderChangeListener.onLeaderChange(newLeaderId);
        }
    }

    public void setLeaderChangeListener(LeaderChangeListener listener) {
        this.leaderChangeListener = listener;
    }

    public void addLeaderChangeListener(LeaderChangeListener listener) {
        this.leaderChangeListener = listener;
    }

    public void setRouterManager(RouterManager routerManager) {
        this.routerManager = routerManager;
    }

    public Collection<StorageNode> getNodes() {
        return Collections.unmodifiableCollection(clusterNodes.values());
    }

    public StorageNode getLeaderNode() {
        return clusterNodes.get(currentLeader);
    }

    public void updateNodeStatus(String nodeId, StorageNode.NodeStatus status) {
        StorageNode node = clusterNodes.get(nodeId);
        if (node != null) {
            node.setStatus(status);
            if (status == StorageNode.NodeStatus.ONLINE) {
                node.updateHeartbeat();
            }
            if (routerManager != null) {
                routerManager.recalculateRouting(getNodes());
            }
        }
    }

    public void addNode(StorageNode node) {
        if (node != null) {
            node.updateHeartbeat(); // 确保新节点有最新的心跳时间
            clusterNodes.put(node.getNodeId(), node);
            if (routerManager != null) {
                routerManager.recalculateRouting(getNodes());
            }
        }
    }

    public void removeNode(String nodeId) {
        if (!nodeId.equals(localNode.getNodeId())) {  // 防止删除本地节点
            clusterNodes.remove(nodeId);
            if (routerManager != null) {
                routerManager.recalculateRouting(getNodes());
            }
        }
    }

    public interface LeaderChangeListener {
        void onLeaderChange(String newLeaderId);
    }
}