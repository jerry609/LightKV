namespace java com.kv.thrift

// 节点信息
struct NodeInfo {
    1: string nodeId;
    2: string host;
    3: i32 port;
    4: string status;
}

// 异常定义
exception KVServiceException {
    1: i32 code;
    2: string message;
}

// 键值存储服务
service KVService {
    // 基本操作
    bool put(1: string key, 2: binary value) throws (1: KVServiceException e);
    binary get(1: string key) throws (1: KVServiceException e);
    bool del(1: string key) throws (1: KVServiceException e);
    bool exists(1: string key) throws (1: KVServiceException e);
    // 批量操作
    map<string, binary> batchGet(1: list<string> keys) throws (1: KVServiceException e);
    bool batchPut(1: map<string, binary> kvMap) throws (1: KVServiceException e);

    // 节点管理
    NodeInfo getNodeInfo() throws (1: KVServiceException e);
}

// 元数据服务
service MetadataService {
    // 节点注册与发现
    bool registerNode(1: NodeInfo nodeInfo) throws (1: KVServiceException e);
    list<NodeInfo> getAllNodes() throws (1: KVServiceException e);
    NodeInfo getRoute(1: string key) throws (1: KVServiceException e);
}