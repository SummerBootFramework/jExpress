#!/bin/bash
# shellcheck shell=bash

# Check if Java was actually found
JAVA_PATH=$(find /usr/lib/jvm -name 'java-21-openjdk*' -type d | head -1)

if [ -z "$JAVA_PATH" ]; then
    echo "Error: Java 21 OpenJDK not found in /usr/lib/jvm"
    exit 1
fi

echo "Starting with Java: ${JAVA_PATH}"

"${JAVA_PATH}/bin/java" \
 -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005 \
 -Dio.netty.native.workdir=./ \
 -Dio.grpc.netty.shaded.io.netty.native.workdir=./ \
 -Djava.awt.headless=true \
 -Xms2G -Xmx2G \
 -XX:+UseZGC -XX:ZUncommitDelay=300 -XX:+ZGenerational -XX:+AlwaysPreTouch \
 -XX:+PerfDisableSharedMem \
 -XX:+ZUncommit \
 -XX:+DisableExplicitGC \
 -XX:MaxDirectMemorySize=1g \
 -XX:+HeapDumpOnOutOfMemoryError \
 -XX:HeapDumpPath="standalone_$1/log/heapdump.hprof" \
 -XX:+ExitOnOutOfMemoryError \
 -Xlog:gc*:file="standalone_$1/log/gc.log":time,level,tags:filecount=5,filesize=10M \
 -Dfile.encoding=UTF-8 \
 -Duser.timezone=America/Toronto \
 -Djava.security.egd=file:/dev/./urandom \
 -Dio.netty.handler.ssl.openssl.engine.enable=true \
 -Dio.netty.leakDetectionLevel=SIMPLE \
 -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
 -jar jExpressApp.jar -domain ""$1"" -debug
