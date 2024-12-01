package com.kv.server.consensus;


import java.io.IOException;

public interface StateMachine {
    /**
     * 应用日志条目到状态机
     */
    void apply(LogEntry entry) throws Exception;

    /**
     * 创建状态机快照
     */
    byte[] takeSnapshot();

    /**
     * 从快照恢复状态机
     */
    void restoreFromSnapshot(byte[] snapshot);

    void close() throws IOException;
}
