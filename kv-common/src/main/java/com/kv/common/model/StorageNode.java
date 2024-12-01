package com.kv.common.model;

public class StorageNode {
    public enum NodeStatus {
        ONLINE,
        OFFLINE,
        SYNCING
    }

    private String nodeId;
    private String host;
    private int port;
    private NodeStatus status;
    private long lastHeartbeat;

    public StorageNode(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.status = NodeStatus.OFFLINE;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }
}