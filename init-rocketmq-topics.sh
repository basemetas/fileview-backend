#!/bin/bash

# Initialize RocketMQ topics required by FileView services.

set -euo pipefail

if [ "${MQ_ENGINE:-}" != "rocketmq" ]; then
    echo "[Fileview] MQ_ENGINE is not rocketmq, skip RocketMQ topic initialization."
    exit 0
fi

NAMESRV_ADDR="${ROCKETMQ_NAMESRV_ADDR:-127.0.0.1:9876}"
CLUSTER_NAME="${ROCKETMQ_CLUSTER:-DefaultCluster}"
ROCKETMQ_HOME="${ROCKETMQ_HOME:-/usr/local/rocketmq}"
MQADMIN="${ROCKETMQ_HOME}/bin/mqadmin"

echo "[Fileview] Initializing RocketMQ topics..."
echo "[Fileview] NameServer: ${NAMESRV_ADDR}"
echo "[Fileview] Cluster: ${CLUSTER_NAME}"

if [ ! -x "$MQADMIN" ]; then
    echo "[Fileview] ERROR: mqadmin not found or not executable: ${MQADMIN}"
    exit 1
fi

if ! { netstat -tunlp 2>/dev/null || ss -tunlp 2>/dev/null; } | grep -q ":9876"; then
    echo "[Fileview] ERROR: RocketMQ NameServer is not listening on port 9876."
    exit 1
fi

if ! "$MQADMIN" clusterList -n "$NAMESRV_ADDR" >/tmp/fileview-rocketmq-clusters.txt 2>&1; then
    echo "[Fileview] ERROR: RocketMQ Broker is not registered in NameServer."
    cat /tmp/fileview-rocketmq-clusters.txt
    exit 1
fi

if ! grep -q "$CLUSTER_NAME" /tmp/fileview-rocketmq-clusters.txt; then
    echo "[Fileview] ERROR: RocketMQ cluster '${CLUSTER_NAME}' was not found."
    cat /tmp/fileview-rocketmq-clusters.txt
    exit 1
fi

declare -a TOPICS=(
    "download-tasks|4|network file download tasks"
    "preview-events|4|conversion result events for preview service"
    "file-events|4|file conversion request events"
    "convert-events|4|internal conversion result events"
)

for topic_config in "${TOPICS[@]}"; do
    IFS='|' read -r topic_name queue_num description <<< "$topic_config"
    echo "[Fileview] Creating/updating topic: ${topic_name} (${description})"

    "$MQADMIN" updateTopic \
        -n "$NAMESRV_ADDR" \
        -c "$CLUSTER_NAME" \
        -t "$topic_name" \
        -r "$queue_num" \
        -w "$queue_num"

    sleep 0.5
done

echo "[Fileview] RocketMQ topics initialized."
