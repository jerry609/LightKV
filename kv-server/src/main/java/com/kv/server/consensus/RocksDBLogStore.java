package com.kv.server.consensus;

import com.kv.server.storage.LogStore;
import org.rocksdb.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class RocksDBLogStore implements LogStore {
    private final RocksDB db;
    private final AtomicLong lastIndex = new AtomicLong(0);
    private final AtomicLong commitIndex = new AtomicLong(0);
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
            db = RocksDB.open(options, dbPath);

            // 初始化 lastIndex
            initializeLastIndex();
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to initialize RocksDB at path: " + dbPath, e);
        }
    }

    private void initializeLastIndex() {
        try (RocksIterator iter = db.newIterator()) {
            iter.seekToLast();
            if (iter.isValid()) {
                lastIndex.set(bytesToLong(iter.key()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize last index", e);
        }
    }

    @Override
    public synchronized void append(LogEntry entry) {
        try {
            byte[] key = longToBytes(entry.getIndex());
            byte[] value = serialize(entry);
            db.put(key, value);
            lastIndex.set(entry.getIndex());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to append log entry", e);
        }
    }

    @Override
    public synchronized void append(List<LogEntry> entries) {
        try (WriteBatch writeBatch = new WriteBatch()) {
            for (LogEntry entry : entries) {
                byte[] key = longToBytes(entry.getIndex());
                byte[] value = serialize(entry);
                writeBatch.put(key, value);
                lastIndex.set(entry.getIndex());
            }
            db.write(new WriteOptions(), writeBatch);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to append log entries", e);
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
        return lastIndex.get();
    }

    @Override
    public long getLastTerm() {
        if (lastIndex.get() == 0) {
            return 0;
        }
        LogEntry lastEntry = getEntry(lastIndex.get());
        return lastEntry != null ? lastEntry.getTerm() : 0;
    }

    @Override
    public long getTermForIndex(long index) {
        LogEntry entry = getEntry(index);
        return entry != null ? entry.getTerm() : 0;
    }

    @Override
    public boolean containsEntry(long index, long term) {
        LogEntry entry = getEntry(index);
        return entry != null && entry.getTerm() == term;
    }

    @Override
    public void close() {
        if (db != null) {
            db.close();
        }
    }

    private byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private long bytesToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    private LogEntry deserialize(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (LogEntry) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize LogEntry", e);
        }
    }

    private byte[] serialize(LogEntry entry) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(entry);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize LogEntry", e);
        }
    }
}
