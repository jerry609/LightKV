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
    private final int port;
    public KVServer(String configPath, int port, int replicaCount) throws Exception {
        this.port = port;

        // 加载配置文件
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
        }

        // 初始化节点ID
        this.nodeId = config.getProperty("node.id");

        // 创建元数据管理器
        this.metadataManager = new MetadataManager("id","127.0.0.1",8080);

        // 初始化Raft peers
        List<RaftPeer> peers = loadPeersFromConfig(config);

        // 创建Raft节点
        this.raftNode = new RaftNode(nodeId, peers);

        // 创建路由管理器
        this.routerManager = new RouterManager(metadataManager, replicaCount);

        // 注册Raft状态变更监听器
        raftNode.setLeaderChangeListener(newLeaderId ->
            metadataManager.updateLeader(newLeaderId));
    }

    private List<RaftPeer> loadPeersFromConfig(Properties config) {
        List<RaftPeer> peers = new ArrayList<>();
        String[] peerConfigs = config.getProperty("cluster.peers").split(",");

        for (String peerConfig : peerConfigs) {
            String[] parts = peerConfig.split(":");
            peers.add(new RaftPeer(parts[0], parts[1], Integer.parseInt(parts[2])));
        }

        return peers;
    }

    public void start() throws Exception {
        // 启动Raft节点
        raftNode.start();

        // 创建Thrift服务处理器
        KVServiceImpl handler = new KVServiceImpl(routerManager, raftNode);
        KVService.Processor<KVServiceImpl> processor = new KVService.Processor<>(handler);

        // 启动Thrift服务器
        TServerTransport serverTransport = new TServerSocket(port);
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport)
            .processor(processor)
            .minWorkerThreads(4)
            .maxWorkerThreads(32);

        TThreadPoolServer server = new TThreadPoolServer(args);
        System.out.println("KV Server started on port " + port + " with nodeId: " + nodeId);
        server.serve();
    }

    public void stop() {
        if (raftNode != null) {
            raftNode.stop();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: KVServer <config_path> <port> <replica_count>");
            System.exit(1);
        }

        String configPath = args[0];
        int port = Integer.parseInt(args[1]);
        int replicaCount = Integer.parseInt(args[2]);

        try {
            KVServer server = new KVServer(configPath, port, replicaCount);

            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
            }));

            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start KVServer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}