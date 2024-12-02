package com.kv.server.consensus;


import com.kv.server.storage.LogStore;
import org.rocksdb.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.nio.ByteBuffer;

public class RocksDBLogStore implements LogStore {
    private final RocksDB db;
    private volatile long lastIndex;
    private volatile long commitIndex;
    private final String dbPath;
    public RocksDBLogStore(String dbPath) {
        this.dbPath = dbPath;
        try {
            // 确保目录存在
            File directory = new File(dbPath);
            if (directory.exists() && !directory.isDirectory()) {
                // 如果存在但不是目录，先删除
                if (!directory.delete()) {
                    throw new RuntimeException("Failed to delete existing file: " + dbPath);
                }
            }
            // 创建目录及其父目录
            if (!directory.exists() && !directory.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + dbPath);
            }

            // 配置 RocksDB
            Options options = new Options()
                    .setCreateIfMissing(true)
                    .setWriteBufferSize(64 * 1024 * 1024)
                    .setMaxWriteBufferNumber(3)
                    .setMaxBackgroundCompactions(10);

            // 初始化 RocksDB
            RocksDB.loadLibrary();
            db = RocksDB.open(options, dbPath);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to initialize RocksDB at path: " + dbPath, e);
        }
    }

    private void initializeLastIndex() {
        try {
            RocksIterator iter = db.newIterator();
            iter.seekToLast();
            if (iter.isValid()) {
                lastIndex = ByteBuffer.wrap(iter.key()).getLong();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize last index", e);
        }
    }

    @Override
    public void append(LogEntry entry) {
        try {
            byte[] key = longToBytes(entry.getIndex());
            byte[] value = serialize(entry);
            db.put(key, value);
            lastIndex = entry.getIndex();
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to append log entry", e);
        }
    }

    @Override
    public LogEntry getEntry(long index) {
        try {
            byte[] key = longToBytes(index);
            byte[] value = db.get(key);
            if (value == null) {
                return null;
            }
            return deserialize(value);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to get log entry", e);
        }
    }

    @Override
    public List<LogEntry> getEntries(long fromIndex) {
        List<LogEntry> entries = new ArrayList<>();
        try (RocksIterator iter = db.newIterator()) {
            iter.seek(longToBytes(fromIndex));
            while (iter.isValid()) {
                entries.add(deserialize(iter.value()));
                iter.next();
            }
        }
        return entries;
    }

    @Override
    public long getLastIndex() {
        return lastIndex;
    }

    @Override
    public long getLastTerm() {
        LogEntry lastEntry = getEntry(lastIndex);
        return lastEntry != null ? lastEntry.getTerm() : 0;
    }

    @Override
    public long getTermForIndex(long index) {
        LogEntry entry = getEntry(index);
        return entry != null ? entry.getTerm() : 0;
    }

    @Override
    public long getCommitIndex() {
        return commitIndex;
    }

    @Override
    public void setCommitIndex(long commitIndex) {
        this.commitIndex = commitIndex;
    }

    @Override
    public void close() {
        if (db != null) {
            // Make sure to close any open iterators before closing the database
            try (RocksIterator iter = db.newIterator()) {
                // Close iterator explicitly
                iter.close();
            } catch (Exception e) {
                // Log the error but continue with closing db
                e.printStackTrace();
            }

            // Close the database
            db.close();
        }
    }


    private byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    private LogEntry deserialize(byte[] data) {
        // 实现反序列化逻辑
        return null;
    }

    private byte[] serialize(LogEntry entry) {
        // 实现序列化逻辑
        return null;
    }
}
