package com.kv.server.storage;

import com.kv.common.model.KeyValue;
import java.io.Closeable;

public interface KVStorage extends Closeable {
    void put(byte[] key, byte[] value) throws Exception;
    byte[] get(byte[] key) throws Exception;
    void delete(byte[] key) throws Exception;
    void flush() throws Exception;
}