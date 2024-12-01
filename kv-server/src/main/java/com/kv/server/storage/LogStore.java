package com.kv.server.storage;

import com.kv.server.consensus.LogEntry;
import java.util.List;

public interface LogStore {
    void append(LogEntry entry);
    LogEntry getEntry(long index);
    List<LogEntry> getEntries(long fromIndex);
    long getLastIndex();
    long getLastTerm();
    long getTermForIndex(long index);
    long getCommitIndex();
    void setCommitIndex(long commitIndex);

    void close();
}