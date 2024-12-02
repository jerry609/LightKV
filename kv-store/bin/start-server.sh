#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <node-id> <config-path>"
    exit 1
fi

NODE_ID=$1
CONFIG_PATH=$2

# 设置CLASSPATH
CLASSPATH="lib/*"

# 设置JVM参数
JVM_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 启动服务器
java $JVM_OPTS -cp $CLASSPATH \
    -Dlogback.configurationFile=$CONFIG_PATH/logback.xml \
    com.kv.server.KVServer \
    $CONFIG_PATH/server.properties