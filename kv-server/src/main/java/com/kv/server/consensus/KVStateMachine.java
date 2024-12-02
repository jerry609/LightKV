package com.kv.server.consensus;

import com.kv.common.model.Operation;
import com.kv.server.storage.RocksDBStorage;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVStateMachine implements StateMachine {
    private final RocksDBStorage storage;
    private final ReentrantReadWriteLock lock;
    private volatile long lastApplied;

    public KVStateMachine(String dbPath) {
        this.storage = new RocksDBStorage(dbPath);
        this.lock = new ReentrantReadWriteLock();
        this.lastApplied = 0;
    }

    @Override
    public void apply(LogEntry entry) throws Exception {
        if (entry.getIndex() <= lastApplied) {
            return;
        }

        lock.writeLock().lock();
        try {
            Operation op = Operation.deserialize(entry.getCommand());
            switch (op.getType()) {
                case PUT:
                    storage.put(op.getKey().getBytes(), op.getValue().getBytes());
                    break;
                case DELETE:
                    storage.delete(op.getKey().getBytes());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation type");
            }
            lastApplied = entry.getIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String get(String key) {
        lock.readLock().lock();
        try {
            byte[] value = storage.get(key.getBytes());
            return value != null ? new String(value) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public byte[] takeSnapshot() {
        lock.readLock().lock();
        try {
            return storage.createSnapshot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void restoreFromSnapshot(byte[] snapshot) {
        lock.writeLock().lock();
        try {
            storage.restoreFromSnapshot(snapshot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            storage.close();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
