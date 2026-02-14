#!/bin/bash
# 预览服务停止脚本

APP_NAME="fileview-preview"
PID_FILE="/opt/fileview/bin/preview/${APP_NAME}.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "$APP_NAME is not running"
    exit 1
fi

PID=$(cat "$PID_FILE")
if ! ps -p $PID > /dev/null 2>&1; then
    echo "$APP_NAME is not running"
    rm -f "$PID_FILE"
    exit 1
fi

echo "Stopping $APP_NAME (PID: $PID)..."
kill $PID

# 等待最多30秒
for i in {1..30}; do
    if ! ps -p $PID > /dev/null 2>&1; then
        echo "$APP_NAME stopped"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
done

# 强制停止
echo "Force stopping $APP_NAME..."
kill -9 $PID
rm -f "$PID_FILE"
echo "$APP_NAME force stopped"
