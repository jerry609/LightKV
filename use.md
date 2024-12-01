# 分布式KV存储系统文件准备说明

1. 多机分布式数据存储与RPC：
```
kv-thrift/
├── KVService.thrift    # Thrift RPC接口定义
└── kv.thrift          # 基础数据类型定义
```

2. 数据的增加、修改和删除：
```
kv-server/src/main/java/com/kv/server/storage/
├── KVStorage.java     # 存储接口定义
└── RocksDBStorage.java # 具体存储实现，包含增删改操作
```

3. 多副本存储：
```
kv-server/src/main/java/com/kv/server/consensus/
├── RaftNode.java      # Raft协议实现，处理副本同步
└── KVStateMachine.java # 状态机实现，确保多副本一致性
```

4. 读写接口/检索界面：
```
kv-client/src/main/java/com/kv/client/
├── KVClient.java      # 客户端API
└── KVCommandClient.java # 命令行交互界面
```

5. 数据写入日志和一致性协议：
```
kv-server/src/main/java/com/kv/server/
├── consensus/RocksDBLogStore.java # 写入日志存储
└── consensus/RaftNode.java       # Raft一致性协议实现
```

6. 元数据管理：
```
kv-server/src/main/java/com/kv/server/meta/
├── MetadataManager.java # 元数据管理器
└── RouterManager.java   # 路由管理
```

7. Bloomfilter索引：
```
kv-server/src/main/java/com/kv/server/storage/
└── BloomFilterImpl.java # 布隆过滤器实现
```

8. 数据分片：
```
kv-server/src/main/java/com/kv/server/meta/
└── ConsistentHash.java # 一致性哈希实现，用于数据分片
```

9. 其他特性：
- 异常处理：
```
kv-common/src/main/java/com/kv/common/exception/
└── KVException.java
```
- 工具类：
```
kv-common/src/main/java/com/kv/common/utils/
├── NetworkUtil.java
├── SerializationUtil.java
└── SerializeUtil.java
```

基础模型定义：
```
kv-common/src/main/java/com/kv/common/model/
├── KeyValue.java      # KV数据模型
├── Operation.java     # 操作模型
└── StorageNode.java   # 存储节点模型
```

## 1. 项目结构
项目由四个主要模块组成：

```
kv-store/
├── kv-common/        # 公共模块
├── kv-client/        # 客户端模块
├── kv-server/        # 服务端模块
└── kv-thrift/        # Thrift接口定义模块
```

## 2. 模块说明

### 2.1 kv-common
公共模块，包含：
- 常量定义
- 异常处理
- 数据模型
- 存储接口
- 工具类

主要类：
- KVConstants: 常量定义
- KVException: 异常类
- KeyValue: 键值对模型
- Operation: 操作模型
- StorageNode: 存储节点模型
- BloomFilterService: 布隆过滤器服务
- KVStorage: 存储接口

### 2.2 kv-client
客户端模块，包含：
- KVClient: 客户端核心类
- KVCommandClient: 命令行客户端

### 2.3 kv-server
服务端模块，包含：
- consensus/: Raft共识实现
  - RaftNode: Raft节点实现
  - KVStateMachine: KV状态机
  - RocksDBLogStore: RocksDB日志存储
- meta/: 元数据管理
  - MetadataManager: 元数据管理器
  - RouterManager: 路由管理
  - ConsistentHash: 一致性哈希实现
- storage/: 存储实现
  - RocksDBStorage: RocksDB存储实现
  - BloomFilterImpl: 布隆过滤器实现
- KVServer: 服务器主类

### 2.4 kv-thrift
Thrift接口定义，包含：
- KVService.thrift: KV服务接口定义
- kv.thrift: 基础类型定义

## 3. 运行环境准备

### 3.1 依赖安装
1. Java环境
```bash
# 检查Java版本，需要JDK 8或以上
java -version
```

2. Maven
```bash
# 检查Maven版本
mvn -version
```

3. RocksDB
```bash
# Ubuntu/Debian
sudo apt-get install librocksdb-dev

# CentOS/RHEL
sudo yum install rocksdb-devel
```

### 3.2 编译项目
```bash
# 在项目根目录执行
mvn clean package
```

## 4. 配置文件准备

### 4.1 创建配置目录
```bash
mkdir -p config/node{1,2,3}
```

### 4.2 节点配置文件
为每个节点创建配置文件 config/node[1-3]/server.properties：

```properties
# 节点ID
node.id=node1

# 集群节点配置
cluster.peers=node1:localhost:8001,node2:localhost:8002,node3:localhost:8003

# 存储配置
data.dir=data/node1
log.dir=logs/node1

# RocksDB配置
rocksdb.path=data/node1/rocksdb

# 线程池配置
thread.pool.min=4
thread.pool.max=32

# 网络配置
server.port=8001
server.host=localhost
```

### 4.3 创建数据和日志目录
```bash
# 创建数据目录
mkdir -p data/node{1,2,3}/rocksdb

# 创建日志目录
mkdir -p logs/node{1,2,3}
```

## 5. 启动脚本准备

### 5.1 服务端启动脚本
创建 bin/start-server.sh：
```bash
#!/bin/bash

if [ $# -ne 3 ]; then
    echo "Usage: $0 <configPath> <port> <replicaCount>"
    exit 1
fi

CONFIG_PATH=$1
PORT=$2
REPLICA_COUNT=$3

java -cp "lib/*" com.kv.server.KVServer $CONFIG_PATH $PORT $REPLICA_COUNT
```

### 5.2 客户端启动脚本
创建 bin/start-client.sh：
```bash
#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <host> <port>"
    exit 1
fi

HOST=$1
PORT=$2

java -cp "lib/*" com.kv.client.KVCommandClient $HOST $PORT
```

## 6. 启动前检查清单

### 6.1 编译检查
- [ ] mvn clean package 执行成功
- [ ] 所有模块的target目录下有编译后的jar包

### 6.2 配置检查
- [ ] 所有节点的配置文件已创建
- [ ] 配置文件中的端口未被占用
- [ ] 数据目录和日志目录已创建
- [ ] 目录具有正确的读写权限

### 6.3 依赖检查
- [ ] Java环境正确安装（JDK 8+）
- [ ] RocksDB正确安装
- [ ] 启动脚本具有执行权限

### 6.4 网络检查
- [ ] 配置的端口未被占用
- [ ] 节点之间网络连通
- [ ] 主机名解析正确