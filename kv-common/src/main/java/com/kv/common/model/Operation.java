package com.kv.common.model;


import java.io.IOException;
import java.io.Serializable;
import com.kv.common.utils.SerializationUtil;

public class Operation implements Serializable {
    public enum Type {
        PUT,
        DELETE,
        GET
    }

    private final Type type;
    private final String key;
    private final String value;

    public Operation(Type type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public byte[] serialize() throws IOException {
        return SerializationUtil.serialize(this);
    }

    public static Operation deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        return (Operation) SerializationUtil.deserialize(bytes);
    }
}
