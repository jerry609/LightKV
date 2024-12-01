package com.kv.client;

import com.kv.thrift.KVService;
import com.kv.thrift.KVServiceException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class KVClient implements AutoCloseable {
    private final TTransport transport;
    private final KVService.Client client;
    private volatile boolean isClosed = false;

    /**
     * Creates a new KVClient instance.
     *
     * @param host The host address of the KV service
     * @param port The port number of the KV service
     * @throws TTransportException if connection cannot be established
     */
    public KVClient(String host, int port) throws TTransportException {
        this.transport = new TSocket(host, port);
        try {
            this.transport.open();
            TProtocol protocol = new TBinaryProtocol(transport);
            this.client = new KVService.Client(protocol);
        } catch (TTransportException e) {
            transport.close();
            throw e;
        }
    }

    /**
     * Retrieves the value associated with the given key.
     *
     * @param key The key to look up
     * @return The value associated with the key, or null if not found
     * @throws KVServiceException if there's an error in the KV service
     * @throws TException if there's a communication error
     * @throws IllegalStateException if the client is closed
     */
    public ByteBuffer get(byte[] key) throws KVServiceException, TException {
        checkNotClosed();
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        return client.get(Arrays.toString(key));
    }

    /**
     * Stores a key-value pair in the service.
     *
     * @param key The key to store
     * @param value The value to associate with the key
     * @throws KVServiceException if there's an error in the KV service
     * @throws TException if there's a communication error
     * @throws IllegalStateException if the client is closed
     */
    public void put(String key, ByteBuffer value) throws KVServiceException, TException {
        checkNotClosed();
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        client.put(key, value);
    }

    /**
     * Deletes a key-value pair from the service.
     *
     * @param key The key to delete
     * @throws KVServiceException if there's an error in the KV service
     * @throws TException if there's a communication error
     * @throws IllegalStateException if the client is closed
     */
    public void delete(String key) throws KVServiceException, TException {
        checkNotClosed();
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        client.dele(key);
    }

    /**
     * Checks if the client is currently connected.
     *
     * @return true if the transport is open, false otherwise
     */
    public boolean isConnected() {
        return transport.isOpen() && !isClosed;
    }

    private void checkNotClosed() {
        if (isClosed) {
            throw new IllegalStateException("Client is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (!isClosed) {
            isClosed = true;
            transport.close();
        }
    }
}