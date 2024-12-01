package com.kv.server.consensus;

public class AppendEntriesResponse {
    private final long term;
    private final boolean success;

    public AppendEntriesResponse(long term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public long getTerm() { return term; }
    public boolean isSuccess() { return success; }
}