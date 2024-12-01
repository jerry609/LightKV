package com.kv.server.meta;

import com.kv.common.model.StorageNode;
import com.kv.thrift.NodeInfo;
import org.apache.http.annotation.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.stream.Collectors;
@ThreadSafe
public class RouterManager {
    private final MetadataManager metadataManager;
    private final int replicaCount;
    private final Map<String, List<StorageNode>> routingTable;
    private final ConsistentHash<StorageNode> consistentHash;

    public RouterManager(@NotNull MetadataManager metadataManager, int replicaCount) {
        this.metadataManager = metadataManager;
        this.replicaCount = replicaCount;
        this.routingTable = new HashMap<>();
        this.consistentHash = new ConsistentHash<>(100, Collections.emptyList()); // 100个虚拟节点

        // 反向设置RouterManager到MetadataManager
        metadataManager.setRouterManager(this);

        // 注册领导者变更监听器
        metadataManager.addLeaderChangeListener(this::onLeaderChange);
    }

    private void onLeaderChange(String newLeaderId) {
        recalculateRouting(metadataManager.getNodes());
    }

    public void recalculateRouting(Collection<StorageNode> nodes) {
        // 更新一致性哈希环
        List<StorageNode> activeNodes = nodes.stream()
            .filter(node -> node.getStatus() == StorageNode.NodeStatus.ONLINE)
            .collect(Collectors.toList());

        consistentHash.updateNodes(activeNodes);

        // 清除旧的路由表
        routingTable.clear();
    }
      /**
     * Gets information about the local node.
     *
     * @return NodeInfo containing details about the local node
     */
public NodeInfo getLocalNodeInfo() {
        StorageNode localNode = metadataManager.getLocalNode();
        return new NodeInfo(
            localNode.getNodeId(),
            localNode.getHost(),
            localNode.getPort(),
            localNode.getStatus().name()
        );
    }
    public StorageNode getNodeForKey(String key) {
        return consistentHash.getNode(key);
    }

    public List<StorageNode> getReplicaNodesForKey(String key) {
        return consistentHash.getNodes(key, replicaCount);
    }
}