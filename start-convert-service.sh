#!/bin/bash
# 转换服务启动脚本

APP_NAME="fileview-convert"
JAR_FILE="/opt/fileview/lib/convert/${APP_NAME}.jar"
CONFIG_DIR="/opt/fileview/config/convert"
LOG_DIR="/opt/fileview/logs/convert"
DATA_DIR="/opt/fileview/data/convert"
PID_DIR="/opt/fileview/bin/convert"
PID_FILE="${PID_DIR}/${APP_NAME}.pid"

# 环境配置（优先级：命令行参数 > 环境变量 > 默认值）
PROFILE="${1:-${SPRING_PROFILES_ACTIVE:-prod}}"

# MQ 引擎配置（优先级：环境变量 > 默认值）
MQ_ENGINE="${MQ_ENGINE:-redis}"
echo "[Fileview] $APP_NAME 使用 MQ 引擎: ${MQ_ENGINE}"

# JVM参数（转换服务需要更多内存）
# 2C4G环境默认配置，支持通过环境变量外部配置（优先级：环境变量 > 默认值）

# 堆内存配置
JAVA_HEAP_MIN="${JAVA_HEAP_MIN:-512m}"
JAVA_HEAP_MAX="${JAVA_HEAP_MAX:-1536m}"

# 元空间配置
JAVA_METASPACE_MIN="${JAVA_METASPACE_MIN:-128m}"
JAVA_METASPACE_MAX="${JAVA_METASPACE_MAX:-256m}"

# GC配置
JAVA_GC_TYPE="${JAVA_GC_TYPE:-G1GC}"
JAVA_GC_PAUSE_TIME="${JAVA_GC_PAUSE_TIME:-200}"

# 组装 JVM 参数
JVM_OPTS="-Xms${JAVA_HEAP_MIN} -Xmx${JAVA_HEAP_MAX}"
JVM_OPTS="$JVM_OPTS -XX:MetaspaceSize=${JAVA_METASPACE_MIN} -XX:MaxMetaspaceSize=${JAVA_METASPACE_MAX}"
JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -Dsun.jnu.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -XX:+Use${JAVA_GC_TYPE}"
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=${JAVA_GC_PAUSE_TIME}"
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"
JVM_OPTS="$JVM_OPTS -XX:+UseCompressedOops"
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=$LOG_DIR/heap_dump.hprof"
# 显式传递 MQ_ENGINE 给 Spring Boot
JVM_OPTS="$JVM_OPTS -DMQ_ENGINE=${MQ_ENGINE}"

# 环境变量（使用 C.UTF-8 更通用）
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export LOG_PATH="$LOG_DIR"  # 传递给 Logback

# 清理旧进程(先通过进程名检查,更可靠)
if pgrep -f "$APP_NAME" > /dev/null 2>&1; then
    echo "[Fileview] 检测到 $APP_NAME 进程,正在停止..."
    pkill -9 -f "$APP_NAME" 2>/dev/null || true
    sleep 2
fi

# 清理PID文件残留
rm -f "$PID_FILE" 2>/dev/null || true

# 确保目录存在
mkdir -p "$LOG_DIR" "$DATA_DIR" "$PID_DIR"

# 启动应用
echo "[Fileview] 启动 $APP_NAME (环境: $PROFILE)..."
nohup java $JVM_OPTS \
    -Dlogging.config=file:${CONFIG_DIR}/logback-spring.xml \
    -Dspring.config.additional-location=file:${CONFIG_DIR}/ \
    -jar "$JAR_FILE" \
    --spring.profiles.active=$PROFILE \
    > "$LOG_DIR/stdout.log" 2>&1 &

# 保存PID
echo $! > "$PID_FILE"
echo "[Fileview] $APP_NAME 已启动"
