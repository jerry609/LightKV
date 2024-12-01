package com.kv.server.consensus;

import lombok.Data;

@Data
public class RaftMessage {
    private long term;
    private String leaderId;
    private LogEntry[] entries;
    private int prevLogIndex;
    private long prevLogTerm;
    private int leaderCommit;

    // Getters and setters
}