package com.kv.server.consensus;

public class RequestVoteResponse {
    private final long term;
    private final boolean voteGranted;

    public RequestVoteResponse(long term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public long getTerm() { return term; }
    public boolean isVoteGranted() { return voteGranted; }
}
