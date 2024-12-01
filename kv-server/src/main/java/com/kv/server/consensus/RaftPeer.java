package com.kv.server.consensus;

public class RaftPeer {
    private final String id;
    private final String host;
    private final int port;
    private volatile long nextIndex;
    private volatile long matchIndex;
    private volatile boolean failed;

    public RaftPeer(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.nextIndex = 0;
        this.matchIndex = 0;
        this.failed = false;
    }

    public String getId() {
        return id;
    }

    public long getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(long nextIndex) {
        this.nextIndex = nextIndex;
    }

    public long getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(long matchIndex) {
        this.matchIndex = matchIndex;
    }

    public AppendEntriesResponse appendEntries(AppendEntriesRequest request) throws Exception {
        // 实际实现需要通过RPC调用对端节点
        return null;
    }

    public RequestVoteResponse requestVote(RequestVoteRequest request) throws Exception {
        // 实际实现需要通过RPC调用对端节点
        return null;
    }

    public void markFailed() {
        this.failed = true;
    }
}
