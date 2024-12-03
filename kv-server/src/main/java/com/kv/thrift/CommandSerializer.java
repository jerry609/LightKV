package com.kv.thrift;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CommandSerializer {
    private static final byte CMD_PUT = 1;
    private static final byte CMD_DELETE = 2;
    private static final byte CMD_BATCH_PUT = 3;

    public static byte[] serializePutCommand(String key, ByteBuffer value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = new byte[value.remaining()];
        value.get(valueBytes);
        value.rewind(); // Reset position for future reads

        // Format: [CMD_PUT][keyLength][key][valueLength][value]
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + keyBytes.length + 4 + valueBytes.length);
        buffer.put(CMD_PUT);                 // Command type (1 byte)
        buffer.putInt(keyBytes.length);      // Key length (4 bytes)
        buffer.put(keyBytes);                // Key bytes
        buffer.putInt(valueBytes.length);    // Value length (4 bytes)
        buffer.put(valueBytes);              // Value bytes

        return buffer.array();
    }

    public static byte[] serializeDeleteCommand(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        // Format: [CMD_DELETE][keyLength][key]
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + keyBytes.length);
        buffer.put(CMD_DELETE);             // Command type (1 byte)
        buffer.putInt(keyBytes.length);     // Key length (4 bytes)
        buffer.put(keyBytes);               // Key bytes

        return buffer.array();
    }

    public static byte[] serializeBatchPutCommand(Map<String, ByteBuffer> kvMap) {
        // Calculate total size needed
        int size = 1 + 4; // CMD byte + number of entries

        // Calculate size for all entries
        for (Map.Entry<String, ByteBuffer> entry : kvMap.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            size += 4 + keyBytes.length; // Key length and key
            size += 4 + entry.getValue().remaining(); // Value length and value
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(CMD_BATCH_PUT);          // Command type (1 byte)
        buffer.putInt(kvMap.size());        // Number of entries (4 bytes)

        for (Map.Entry<String, ByteBuffer> entry : kvMap.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            ByteBuffer value = entry.getValue();
            byte[] valueBytes = new byte[value.remaining()];
            value.get(valueBytes);
            value.rewind(); // Reset position for future reads

            buffer.putInt(keyBytes.length);  // Key length
            buffer.put(keyBytes);            // Key bytes
            buffer.putInt(valueBytes.length); // Value length
            buffer.put(valueBytes);          // Value bytes
        }

        return buffer.array();
    }

    // Deserialization methods for state machine
    public static CommandType getCommandType(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid command data");
        }

        switch (data[0]) {
            case CMD_PUT:
                return CommandType.PUT;
            case CMD_DELETE:
                return CommandType.DELETE;
            case CMD_BATCH_PUT:
                return CommandType.BATCH_PUT;
            default:
                throw new IllegalArgumentException("Unknown command type: " + data[0]);
        }
    }

    public static PutCommand deserializePutCommand(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.get(); // Skip command type

        int keyLength = buffer.getInt();
        byte[] keyBytes = new byte[keyLength];
        buffer.get(keyBytes);
        String key = new String(keyBytes, StandardCharsets.UTF_8);

        int valueLength = buffer.getInt();
        byte[] valueBytes = new byte[valueLength];
        buffer.get(valueBytes);

        return new PutCommand(key, valueBytes);
    }

    public static DeleteCommand deserializeDeleteCommand(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.get(); // Skip command type

        int keyLength = buffer.getInt();
        byte[] keyBytes = new byte[keyLength];
        buffer.get(keyBytes);
        String key = new String(keyBytes, StandardCharsets.UTF_8);

        return new DeleteCommand(key);
    }

    public static BatchPutCommand deserializeBatchPutCommand(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.get(); // Skip command type

        int numEntries = buffer.getInt();
        Map<String, byte[]> entries = new HashMap<>();

        for (int i = 0; i < numEntries; i++) {
            int keyLength = buffer.getInt();
            byte[] keyBytes = new byte[keyLength];
            buffer.get(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);

            int valueLength = buffer.getInt();
            byte[] valueBytes = new byte[valueLength];
            buffer.get(valueBytes);

            entries.put(key, valueBytes);
        }

        return new BatchPutCommand(entries);
    }

    public enum CommandType {
        PUT,
        DELETE,
        BATCH_PUT
    }

    // Command classes for state machine
    public static class PutCommand {
        private final String key;
        private final byte[] value;

        public PutCommand(String key, byte[] value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }
        public byte[] getValue() { return value; }
    }

    public static class DeleteCommand {
        private final String key;

        public DeleteCommand(String key) {
            this.key = key;
        }

        public String getKey() { return key; }
    }

    public static class BatchPutCommand {
        private final Map<String, byte[]> entries;

        public BatchPutCommand(Map<String, byte[]> entries) {
            this.entries = entries;
        }

        public Map<String, byte[]> getEntries() { return entries; }
    }
}