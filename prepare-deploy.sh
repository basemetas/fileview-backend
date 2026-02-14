#!/bin/bash
# 文件名: prepare-deploy.sh
# 准备部署目录结构

# 检测是否为 root 用户
if [ "$EUID" -eq 0 ]; then
    # root 用户不需要 sudo
    SUDO=""
else
    # 非 root 用户使用 sudo
    SUDO="sudo"
fi

echo "📁 创建 /opt/fileview 标准目录结构..."

# 创建主目录和一级子目录
$SUDO mkdir -p /opt/fileview/{bin,config,lib,logs,data,resources}

$SUDO mkdir -p /opt/fileview/bin/cad2x

# 创建配置目录
$SUDO mkdir -p /opt/fileview/config/{preview,convert}

# 创建 lib 目录
$SUDO mkdir -p /opt/fileview/lib/{preview,convert}

# 创建日志目录
$SUDO mkdir -p /opt/fileview/logs/{preview,convert}

# 创建数据目录
$SUDO mkdir -p /opt/fileview/data/{preview,convert,temp,target,uploads,downloads,uncompress}
$SUDO mkdir -p /opt/fileview/data/{libreoffice,x2t,cad2x}

# 创建资源目录
$SUDO mkdir -p /opt/fileview/resources/fonts

# 设置权限
if [ -n "$SUDO" ]; then
    # 非 root 用户，设置当前用户为所有者
    $SUDO chown -R $USER:$USER /opt/fileview
fi
$SUDO chmod -R 755 /opt/fileview

echo "✅ 目录结构创建完成！"
echo ""

# 显示目录结构（如果有 tree 命令）
if command -v tree &> /dev/null; then
    tree /opt/fileview -L 2
else
    echo "目录结构："
    ls -la /opt/fileview/
    echo ""
    echo "配置目录："
    ls -la /opt/fileview/config/
    echo ""
    echo "库目录："
    ls -la /opt/fileview/lib/
    echo ""
    echo "日志目录："
    ls -la /opt/fileview/logs/
    echo ""
    echo "数据目录："
    ls -la /opt/fileview/data/
fi
