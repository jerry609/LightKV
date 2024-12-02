package com.kv.server.storage;

import com.kv.server.consensus.LogEntry;
import java.util.List;

public interface LogStore {
    void append(LogEntry entry);
    void append(List<LogEntry> entries); // 新增方法
    LogEntry getEntry(long index);
    List<LogEntry> getEntries(long fromIndex);
    long getLastIndex();
    long getLastTerm();
    long getTermForIndex(long index);
    boolean containsEntry(long index, long term);
    void close();
}
