package com.kv.server.consensus;

public class RequestVoteRequest {
    private final long term;
    private final String candidateId;
    private final long lastLogIndex;
    private final long lastLogTerm;

    public RequestVoteRequest(
            long term,
            String candidateId,
            long lastLogIndex,
            long lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    // Getters...
    public long getTerm() { return term; }
    public String getCandidateId() { return candidateId; }
    public long getLastLogIndex() { return lastLogIndex; }
    public long getLastLogTerm() { return lastLogTerm; }
}
