#!/bin/bash

# RocketMQ Topic 初始化脚本
# 用于创建项目所需的所有消息主题

set -e

# 检查 MQ 引擎类型
if [ "${MQ_ENGINE}" != "rocketmq" ]; then
    echo "[Fileview] MQ_ENGINE 不是 rocketmq,跳过 RocketMQ Topics 初始化"
    exit 0
fi

# 配置参数
NAMESRV_ADDR="${ROCKETMQ_NAMESRV_ADDR:-127.0.0.1:9876}"
CLUSTER_NAME="${ROCKETMQ_CLUSTER:-DefaultCluster}"
BROKER_ADDR="${ROCKETMQ_BROKER_ADDR:-127.0.0.1:10911}"
ROCKETMQ_HOME="${ROCKETMQ_HOME:-/usr/local/rocketmq}"

echo "[Fileview] 初始化 RocketMQ Topics..."

# 检查 RocketMQ 是否运行
if ! netstat -tunlp 2>/dev/null | grep -q 9876 && ! ss -tunlp 2>/dev/null | grep -q 9876; then
    echo "[Fileview] 错误: RocketMQ NameServer 未运行"
    exit 1
fi

if ! netstat -tunlp 2>/dev/null | grep -q 10911 && ! ss -tunlp 2>/dev/null | grep -q 10911; then
    echo "[Fileview] 错误: RocketMQ Broker 未运行"
    exit 1
fi

# Topic 配置
# 格式: "Topic名称|队列数量|描述"
declare -a TOPICS=(
    "download-tasks|4|文件下载任务队列"
    "preview-events|4|文件预览事件队列"
    "file-events|4|文件转换事件队列"
)

# 创建 Topic
for topic_config in "${TOPICS[@]}"; do
    IFS='|' read -r topic_name queue_num description <<< "$topic_config"
    
    # 创建或更新 Topic (确保在前台执行并等待完成)
    sh "$ROCKETMQ_HOME/bin/mqadmin" updateTopic \
        -n "$NAMESRV_ADDR" \
        -c "$CLUSTER_NAME" \
        -t "$topic_name" \
        -r "$queue_num" \
        -w "$queue_num" \
        -o true >/dev/null 2>&1 || true
    
    # 等待一下，确保 mqadmin 完全退出
    sleep 0.5
done

echo "[Fileview] RocketMQ Topics 初始化完成"

# 确保脚本正常退出
exit 0
