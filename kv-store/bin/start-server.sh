#!/bin/bash

if [ $# -ne 1 ]; then
    echo "Usage: $0 <config-path>"
    exit 1
fi

CONFIG_PATH=$1

# 设置 CLASSPATH
CLASSPATH="/Users/jerry/lib/kv-server-1.0-SNAPSHOT.jar"

# 设置 JVM 参数
JVM_OPTS="-Xms512m -Xmx1024m -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC"

# 启动服务器
java $JVM_OPTS -cp $CLASSPATH \
    -Dlogback.configurationFile=$CONFIG_PATH/logback.xml \
    com.kv.server.KVServer \
    $CONFIG_PATH/server.properties
