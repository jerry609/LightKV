package com.kv.server.consensus;

import com.kv.common.model.StorageNode;
import com.kv.server.meta.RouterManager;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataManager {
    private Map<String, StorageNode> clusterNodes;
    private RouterManager routerManager;
    private volatile String leaderId;
    private List<LeaderChangeListener> listeners;

    public MetadataManager() {
        this.clusterNodes = new ConcurrentHashMap<>();
    }

    // 设置RouterManager，避免循环依赖
    public void setRouterManager(RouterManager routerManager) {
        this.routerManager = routerManager;
    }

    public void updateLeader(String newLeaderId) {
        this.leaderId = newLeaderId;
        if (listeners != null) {
            for (LeaderChangeListener listener : listeners) {
                listener.onLeaderChange(newLeaderId);
            }
        }
    }

    public StorageNode getLeaderNode() {
        return clusterNodes.get(leaderId);
    }

    public void updateNodeStatus(String nodeId, StorageNode.NodeStatus status) {
        StorageNode node = clusterNodes.get(nodeId);
        if (node != null) {
            node.setStatus(status);
            if (routerManager != null) {
                routerManager.recalculateRouting(getNodes());
            }
        }
    }

    public Collection<StorageNode> getNodes() {
        return clusterNodes.values();
    }

    public void addLeaderChangeListener(LeaderChangeListener listener) {
        listeners.add(listener);
    }

    public interface LeaderChangeListener {
        void onLeaderChange(String newLeaderId);
    }
}