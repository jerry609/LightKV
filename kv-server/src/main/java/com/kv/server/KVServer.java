package com.kv.server;

import com.kv.server.meta.MetadataManager;
import com.kv.server.meta.RouterManager;
import com.kv.server.consensus.RaftNode;
import com.kv.server.consensus.RaftPeer;
import com.kv.thrift.KVService;
import com.kv.thrift.KVServiceImpl;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.io.FileInputStream;

public class KVServer {
    private final String nodeId;
    private final MetadataManager metadataManager;
    private final RouterManager routerManager;
    private final RaftNode raftNode;
    private final int kvPort;        // KV service port
    private final int raftPort;      // Raft communication port
    private final Properties config; // 保留配置为类成员

    public KVServer(String configPath) throws Exception {
        // Load configuration file
        config = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
        }

        // Initialize node ID
        this.nodeId = config.getProperty("node.id");
        if (this.nodeId == null || this.nodeId.isEmpty()) {
            throw new IllegalArgumentException("node.id is not specified in the configuration file.");
        }

        // Initialize KV service port
        this.kvPort = Integer.parseInt(config.getProperty("server.port"));
        if (this.kvPort <= 0) {
            throw new IllegalArgumentException("Invalid server.port in the configuration file.");
        }

        // Initialize Raft communication port
        this.raftPort = Integer.parseInt(config.getProperty("raft.server.port"));
        if (this.raftPort <= 0) {
            throw new IllegalArgumentException("Invalid raft.server.port in the configuration file.");
        }

        // Initialize replica count
        int replicaCount = Integer.parseInt(config.getProperty("cluster.replication-factor", "3"));

        // Create metadata manager
        String metadataHost = config.getProperty("metadata.host", "127.0.0.1");
        int metadataPort = Integer.parseInt(config.getProperty("metadata.port", "8080"));
        this.metadataManager = new MetadataManager(this.nodeId, metadataHost, metadataPort);

        // Initialize Raft peers
        List<RaftPeer> peers = loadPeersFromConfig(config);

        // Get RocksDB path and Raft log path
        String rocksdbPath = config.getProperty("rocksdb.path", "/data/" + nodeId + "/rocksdb-data");
        String raftLogPath = config.getProperty("raft.log-path", "/data/" + nodeId + "/raft-log");

        // Create RaftNode with separate raftPort
        this.raftNode = new RaftNode(nodeId, peers, rocksdbPath, raftLogPath, raftPort);

        // Create router manager
        this.routerManager = new RouterManager(metadataManager, replicaCount);

        // Register Raft state change listener
        raftNode.setLeaderChangeListener(newLeaderId ->
                metadataManager.updateLeader(newLeaderId));
    }

    private List<RaftPeer> loadPeersFromConfig(Properties config) throws Exception {
        List<RaftPeer> peers = new ArrayList<>();
        String peersConfig = config.getProperty("cluster.peers");
        if (peersConfig == null || peersConfig.isEmpty()) {
            throw new IllegalArgumentException("cluster.peers is not specified in the configuration file.");
        }

        String[] peerConfigs = peersConfig.split(",");
        for (String peerConfig : peerConfigs) {
            String[] parts = peerConfig.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid peer configuration: " + peerConfig);
            }
            String peerId = parts[0];
            String peerHost = parts[1];
            int peerPort = Integer.parseInt(parts[2]);
            peers.add(new RaftPeer(peerId, peerHost, peerPort));
        }

        return peers;
    }

    public void start() throws Exception {
        // Start Raft node
        raftNode.start();

        // Create Thrift service handler
        KVServiceImpl handler = new KVServiceImpl(routerManager, raftNode);
        KVService.Processor<KVServiceImpl> processor = new KVService.Processor<>(handler);

        // Start Thrift server for KV service
        TServerTransport serverTransport = new TServerSocket(kvPort);

        // 从配置文件中读取线程池参数
        int minThreads = Integer.parseInt(config.getProperty("thread.pool.min", "4"));
        int maxThreads = Integer.parseInt(config.getProperty("thread.pool.max", "32"));

        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport)
                .processor(processor)
                .minWorkerThreads(minThreads)
                .maxWorkerThreads(maxThreads);

        TThreadPoolServer server = new TThreadPoolServer(args);
        System.out.println("KV Server started on port " + kvPort + " with nodeId: " + nodeId);
        System.out.println("Raft communication port: " + raftPort);
        server.serve();
    }

    public void stop() {
        if (raftNode != null) {
            raftNode.stop();
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.out.println("Usage: KVServer <config_path>");
                System.exit(1);
            }

            String configPath = args[0];

            // Create and start server
            KVServer server = new KVServer(configPath);
            server.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        } catch (Exception e) {
            System.err.println("Failed to start KVServer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
