package com.kv.common.storage;

import java.io.Closeable;

public interface KVStorage extends Closeable {
    void put(byte[] key, byte[] value) throws Exception;
    byte[] get(byte[] key) throws Exception;
    void delete(byte[] key) throws Exception;
    void flush() throws Exception;
}