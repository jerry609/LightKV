package com.kv.server.consensus;

import java.io.Serializable;

public class LogEntry implements Serializable {
    private final long term;
    private final long index;
    private final byte[] command;
    private final long timestamp;

    public LogEntry(long term, long index, byte[] command) {
        this.term = term;
        this.index = index;
        this.command = command;
        this.timestamp = System.currentTimeMillis();
    }

    public long getTerm() {
        return term;
    }

    public long getIndex() {
        return index;
    }

    public byte[] getCommand() {
        return command;
    }

    public long getTimestamp() {
        return timestamp;
    }
}