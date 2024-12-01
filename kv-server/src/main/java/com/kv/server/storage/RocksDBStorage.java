package com.kv.server.storage;

import com.kv.common.storage.BloomFilterService;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.rocksdb.*;
import com.kv.common.model.KeyValue;
import com.kv.common.storage.KVStorage;
import com.kv.common.utils.SerializationUtil;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public class RocksDBStorage implements KVStorage {
    private final RocksDB db;
    private final BloomFilterImpl bloomFilter;
    private final Cache<byte[], byte[]> cache;
    private static final int DEFAULT_EXPECTED_INSERTIONS = 1_000_000;
    private static final double DEFAULT_FPP = 0.01;
    private static final int DEFAULT_MAXIMUM_CACHE_SIZE = 10_000;
    private static final int DEFAULT_TIME_LIMIT = 10;

    public RocksDBStorage() {
        Options options = new Options()
                .setCreateIfMissing(true)
                .setWriteBufferSize(64 * 1024 * 1024)
                .setMaxWriteBufferNumber(3)
                .setMaxBackgroundCompactions(10);

        try {
            RocksDB.loadLibrary();
            db = RocksDB.open(options, "rocksdb-data");
            bloomFilter = new BloomFilterImpl(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP);
            this.cache = CacheBuilder.newBuilder()
                    .maximumSize(DEFAULT_MAXIMUM_CACHE_SIZE)
                    .expireAfterAccess(DEFAULT_TIME_LIMIT, TimeUnit.MINUTES)
                    .build();
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to initialize RocksDB", e);
        }
    }

    @Override
    public void put(byte[] key, byte[] value) {
        try {
            db.put(key, value);
            cache.put(key, value);
            bloomFilter.add(key);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to put key-value", e);
        }
    }

    @Override
    public byte[] get(byte[] key) {
        if (!bloomFilter.mightContain(key)) {
            return null;
        }
        byte[] value = cache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        try {
            value = db.get(key);
            if (value != null) {
                cache.put(key, value);
            }
            return value;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to get value", e);
        }
    }

    @Override
    public void delete(byte[] key) throws Exception {
        try {
            db.delete(key);
            // Note: We don't remove from bloom filter as it doesn't support removal
            cache.invalidate(key);
        } catch (RocksDBException e) {
            throw new Exception("Failed to delete key", e);
        }
    }

    @Override
    public void flush() throws Exception {
        try {
            FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true);
            db.flush(flushOptions);
        } catch (RocksDBException e) {
            throw new Exception("Failed to flush database", e);
        }
    }

    public byte[] createSnapshot() throws IOException {
        try (Snapshot snapshot = db.getSnapshot();
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            // Get iterator with snapshot
            ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot);
            RocksIterator iterator = db.newIterator(readOptions);

            // Write all key-value pairs
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                oos.writeInt(iterator.key().length);
                oos.write(iterator.key());
                oos.writeInt(iterator.value().length);
                oos.write(iterator.value());
            }

            return baos.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to create snapshot", e);
        }
    }

    public void restoreFromSnapshot(byte[] snapshotData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(snapshotData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {

            WriteOptions writeOptions = new WriteOptions();
            WriteBatch batch = new WriteBatch();

            while (bais.available() > 0) {
                int keyLength = ois.readInt();
                byte[] key = new byte[keyLength];
                ois.readFully(key);

                int valueLength = ois.readInt();
                byte[] value = new byte[valueLength];
                ois.readFully(value);

                batch.put(key, value);
                bloomFilter.add(key);
            }

            db.write(writeOptions, batch);
        } catch (Exception e) {
            throw new IOException("Failed to restore from snapshot", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (db != null) {
            db.close();
        }
    }
}