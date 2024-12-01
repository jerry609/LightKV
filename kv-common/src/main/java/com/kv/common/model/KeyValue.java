package com.kv.common.model;

public class KeyValue {
    private byte[] key;
    private byte[] value;
    private long timestamp;

    public KeyValue(byte[] key, byte[] value) {
        this.key = key;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public byte[] getKey() { return key; }
    public byte[] getValue() { return value; }
    public long getTimestamp() { return timestamp; }
}
