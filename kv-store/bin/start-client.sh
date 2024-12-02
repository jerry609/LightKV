#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <host> <port>"
    exit 1
fi

HOST=$1
PORT=$2

# 获取脚本所在目录的父目录（项目根目录）
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 设置CLASSPATH，包含所有必要的jar包
CLASSPATH="$PROJECT_DIR/lib/*:$PROJECT_DIR/target/*"

# 启动客户端
java -cp "$CLASSPATH" com.kv.client.KVCommandClient "$HOST" "$PORT"