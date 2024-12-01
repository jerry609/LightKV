package com.kv.server.consensus;


import com.kv.server.storage.LogStore;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

public class RaftNode {
    // 导入声明部分的修改
    private static final int ELECTION_TIMEOUT_MIN = 150;
    private static final int ELECTION_TIMEOUT_MAX = 300;

    private final String nodeId;
    private final List<RaftPeer> peers;
    private final LogStore logStore;
    /**
     * -- GETTER --
     *  Gets the state machine instance.
     *
     * @return The KV state machine
     */
    @Getter
    private final KVStateMachine stateMachine;
    private final ScheduledExecutorService scheduler;
    private volatile NodeState state;
    private volatile String currentLeader;
    private final AtomicLong currentTerm;
    private volatile String votedFor;
    private final ConcurrentMap<Long, CompletableFuture<Boolean>> pendingProposals;
    private ScheduledFuture<?> electionTimer;
    private final Random random;
    private volatile long commitIndex;
    private volatile long lastApplied;
    private LeaderChangeListener leaderChangeListener;

    public RaftNode(String nodeId, List<RaftPeer> peers) {
        this.nodeId = nodeId;
        this.peers = peers;
        this.logStore = new RocksDBLogStore();
        this.stateMachine = new KVStateMachine();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.state = NodeState.FOLLOWER;
        this.currentTerm = new AtomicLong(0);
        this.pendingProposals = new ConcurrentHashMap<>();
        this.random = new Random();
        this.commitIndex = 0;
        this.lastApplied = 0;
    }

    public void setLeaderChangeListener(LeaderChangeListener listener) {
        this.leaderChangeListener = listener;
    }

    private void checkHeartbeat() {
        if (state != NodeState.LEADER) {
            return;
        }

        // 发送心跳
        sendHeartbeat();
    }

    private void resetElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel(false);
        }

        int timeout = ELECTION_TIMEOUT_MIN +
                random.nextInt(ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN);

        electionTimer = scheduler.schedule(
                this::startElection,
                timeout,
                TimeUnit.MILLISECONDS
        );
    }

    private void startElection() {
        state = NodeState.CANDIDATE;
        currentTerm.incrementAndGet();
        votedFor = nodeId;
        AtomicInteger votesReceived = new AtomicInteger(1);  // 投给自己的一票

        // 请求其他节点投票
        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                continue;
            }

            scheduler.execute(() -> {
                try {
                    RequestVoteRequest request = new RequestVoteRequest(
                            currentTerm.get(),
                            nodeId,
                            logStore.getLastIndex(),
                            logStore.getLastTerm()
                    );

                    RequestVoteResponse response = peer.requestVote(request);
                    if (response.isVoteGranted()) {
                        synchronized (this) {
                            votesReceived.getAndIncrement();
                            if (state == NodeState.CANDIDATE &&
                                    votesReceived.get() > peers.size() / 2) {
                                becomeLeader();
                            }
                        }
                    }
                } catch (Exception e) {
                    // 处理投票请求失败
                }
            });
        }
    }

    private void becomeLeader() {
        state = NodeState.LEADER;
        currentLeader = nodeId;

        // 初始化所有节点的nextIndex
        for (RaftPeer peer : peers) {
            peer.setNextIndex(logStore.getLastIndex() + 1);
        }

        // 通知领导者变更
        if (leaderChangeListener != null) {
            leaderChangeListener.onLeaderChange(nodeId);
        }

        // 开始发送心跳
        sendHeartbeat();
    }

    private void sendHeartbeat() {
        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                continue;
            }

            AppendEntriesRequest request = new AppendEntriesRequest(
                    currentTerm.get(),
                    nodeId,
                    logStore.getLastIndex(),
                    logStore.getLastTerm(),
                    null,  // 心跳不包含日志条目
                    commitIndex
            );

            scheduler.execute(() -> {
                try {
                    peer.appendEntries(request);
                } catch (Exception e) {
                    // 处理心跳发送失败
                }
            });
        }
    }

    private void handleAppendEntriesResponse(
            RaftPeer peer,
            AppendEntriesResponse response,
            List<LogEntry> entries) throws Exception {

        if (response.isSuccess()) {
            // 更新peer的复制进度
            peer.setNextIndex(peer.getNextIndex() + entries.size());
            peer.setMatchIndex(peer.getNextIndex() - 1);

            // 检查是否可以提交新的日志
            updateCommitIndex();
        } else {
            // 如果失败，减少nextIndex重试
            peer.setNextIndex(peer.getNextIndex() - 1);
        }
    }

    private void updateCommitIndex() throws Exception {
        for (long n = commitIndex + 1; n <= logStore.getLastIndex(); n++) {
            if (logStore.getEntry(n).getTerm() == currentTerm.get()) {
                int matchCount = 1;  // 包括自己
                for (RaftPeer peer : peers) {
                    if (peer.getMatchIndex() >= n) {
                        matchCount++;
                    }
                }

                if (matchCount > peers.size() / 2) {
                    commitIndex = n;
                    applyLogEntries();
                }
            }
        }
    }

    private void applyLogEntries() throws Exception {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = logStore.getEntry(lastApplied);
            stateMachine.apply(entry);

            // 完成等待的提议
            CompletableFuture<Boolean> future =
                    pendingProposals.remove(entry.getIndex());
            if (future != null) {
                future.complete(true);
            }
        }
    }

    public void stop() {
        try {
            // 取消选举定时器
            if (electionTimer != null) {
                electionTimer.cancel(true);
            }

            // 关闭调度器
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 关闭状态机
            if (stateMachine != null) {
                stateMachine.close();
            }

            // 关闭日志存储
            if (logStore != null) {
                logStore.close();
            }

            // 完成所有待处理的提议
            for (CompletableFuture<Boolean> future : pendingProposals.values()) {
                future.completeExceptionally(new IllegalStateException("Node is shutting down"));
            }
            pendingProposals.clear();

        } catch (Exception e) {
            throw new RuntimeException("Failed to stop RaftNode", e);
        }
    }

    public void start() {
        // 启动心跳检测
        scheduler.scheduleWithFixedDelay(
                this::checkHeartbeat,
                0,
                100,
                TimeUnit.MILLISECONDS
        );

        // 启动选举超时检测
        resetElectionTimer();
    }

    /**
     * Proposes a new command to the Raft cluster.
     *
     * @param command The command to be replicated
     * @return A future that completes when the command is committed
     * @throws Exception if the proposal fails
     */
    public CompletableFuture<Boolean> propose(byte[] command) throws Exception {
        if (state != NodeState.LEADER) {
            throw new IllegalStateException("Not the leader");
        }

        // Create log entry
        LogEntry entry = new LogEntry(
                logStore.getLastIndex() + 1,
                currentTerm.get(),
                command
        );

        // Store locally
        logStore.append(entry);

        // Create future for tracking completion
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingProposals.put(entry.getIndex(), future);

        // Replicate to peers
        for (RaftPeer peer : peers) {
            if (peer.getId().equals(nodeId)) {
                continue;
            }

            replicateLog(peer);
        }

        return future;
    }

    private void replicateLog(RaftPeer peer) {
        long nextIndex = peer.getNextIndex();
        List<LogEntry> entries = new ArrayList<>();

        try {
            for (long i = nextIndex; i <= logStore.getLastIndex(); i++) {
                entries.add(logStore.getEntry(i));
            }

            if (!entries.isEmpty()) {
                AppendEntriesRequest request = new AppendEntriesRequest(
                        currentTerm.get(),
                        nodeId,
                        nextIndex - 1,
                        logStore.getEntry(nextIndex - 1).getTerm(),
                        entries,
                        commitIndex
                );

                scheduler.execute(() -> {
                    try {
                        AppendEntriesResponse response = peer.appendEntries(request);
                        handleAppendEntriesResponse(peer, response, entries);
                    } catch (Exception e) {
                        // Handle replication failure
                    }
                });
            }
        } catch (Exception e) {
            // Handle log access failure
        }
    }

    public interface LeaderChangeListener {
        void onLeaderChange(String newLeaderId);
    }
}