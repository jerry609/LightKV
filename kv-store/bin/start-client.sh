#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <host> <port>"
    exit 1
fi

HOST=$1
PORT=$2

# 设置CLASSPATH
CLASSPATH="lib/*"

# 启动客户端
java -cp $CLASSPATH com.kv.client.KVCommandClient $HOST $PORT