package com.kv.server.consensus;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class LogEntry implements Serializable {
    private final long index;
    private final long term;
    private final byte[] command;
    private final long timestamp;

    public LogEntry(long index, long term, byte[] command) {
        this.index = index;
        this.term = term;
        this.command = command;
        this.timestamp = System.currentTimeMillis();
    }

    // Getter 方法
    public long getIndex() {
        return index;
    }

    public long getTerm() {
        return term;
    }

    public byte[] getCommand() {
        return command;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // 将自定义的 LogEntry 转换为 Thrift 的 LogEntry
    public com.kv.thrift.LogEntry toThrift() {
        return new com.kv.thrift.LogEntry(
                index,
                term,
                ByteBuffer.wrap(command)
        );
    }

    // 从 Thrift 的 LogEntry 创建自定义的 LogEntry
    public static LogEntry fromThrift(com.kv.thrift.LogEntry thriftEntry) {
        return new LogEntry(
                thriftEntry.index,
                thriftEntry.term,
                thriftEntry.command.array()
        );
    }
}
