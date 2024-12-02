package com.kv.server.consensus;

import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TSocket;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomTServerSocket extends TServerTransport {
    private static final Logger log = LoggerFactory.getLogger(CustomTServerSocket.class);
    private final ServerSocket serverSocket;
    private final int clientTimeout;
    private final int maxMessageSize;

    public CustomTServerSocket(ServerSocket serverSocket, int clientTimeout, int maxMessageSize) {
        this.serverSocket = serverSocket;
        this.clientTimeout = clientTimeout;
        this.maxMessageSize = maxMessageSize;
    }

    @Override
    public void listen() throws TTransportException {
        // ServerSocket 已经在外部绑定和配置，无需在此处执行任何操作
    }

    @Override
    public TTransport accept() throws TTransportException {
        try {
            Socket clientSocket = serverSocket.accept();
            log.info("Accepted connection from {}", clientSocket.getRemoteSocketAddress());
            // 设置 Socket 选项
            clientSocket.setKeepAlive(true);
            clientSocket.setTcpNoDelay(true);
            clientSocket.setSoTimeout(clientTimeout);
            // 返回 Thrift 的 TSocket 封装，并设置最大消息大小
            return new TSocket(clientSocket);
        } catch (IOException e) {
            log.error("Error accepting client connection", e);
            throw new TTransportException("Error accepting client connection", e);
        }
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.warn("Error closing server socket", e);
            // 忽略关闭异常
        }
    }
}
