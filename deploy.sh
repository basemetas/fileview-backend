#!/bin/bash

# 编译部署脚本

# 检测是否为 root 用户
if [ "$EUID" -eq 0 ]; then
    # root 用户不需要 sudo
    SUDO=""
else
    # 非 root 用户使用 sudo
    SUDO="sudo"
fi

WORKSPACE=$(pwd)
DEPLOY_ROOT=$WORKSPACE/.release/opt/fileview
echo "发布目录: $DEPLOY_ROOT"

$SUDO mkdir -p $DEPLOY_ROOT

# 编译阶段
chmod +x ./mvnw

# 支持流水线传入版本号（如果未设置则使用默认值 local-dev）
RELEASE_VERSION=${RELEASE_VERSION:-local-dev}
echo "发布版本号: $RELEASE_VERSION"

./mvnw clean package -DskipTests -DreleaseVersion=$RELEASE_VERSION

# 检查预览服务 JAR
ls -lh fileview-preview/target/lib/fileview-preview.jar

# 检查转换服务 JAR
ls -lh fileview-convert/target/lib/fileview-convert.jar

# 检查外部化配置文件
ls -la fileview-preview/target/config/
ls -la fileview-convert/target/config/

# 创建目录

echo "📁 检查并创建标准目录结构..."

# 检查主目录是否存在
if [ -d "$DEPLOY_ROOT" ]; then
    echo "   ℹ️  发布目录已存在: $DEPLOY_ROOT"
else
    echo "   ✨ 创建发布目录: $DEPLOY_ROOT"
fi

# 创建主目录和一级子目录（幂等操作）
$SUDO mkdir -p $DEPLOY_ROOT/{bin,config,lib,logs,data,resources}
# 创建配置目录
$SUDO mkdir -p $DEPLOY_ROOT/config/{preview,convert}
# 创建 lib 目录
$SUDO mkdir -p $DEPLOY_ROOT/lib/{preview,convert}

echo "✅ 目录结构就绪！"
echo ""

# COPY 阶段

# 复制预览服务
echo "复制预览服务..."
cp $WORKSPACE/fileview-preview/target/lib/fileview-preview.jar \
   $DEPLOY_ROOT/lib/preview/

cp $WORKSPACE/fileview-preview/target/config/* \
   $DEPLOY_ROOT/config/preview/

echo "复制转换服务..."
cp $WORKSPACE/fileview-convert/target/lib/fileview-convert.jar \
   $DEPLOY_ROOT/lib/convert/

cp $WORKSPACE/fileview-convert/target/config/* \
   $DEPLOY_ROOT/config/convert/

echo "复制启停脚本..."
cp $WORKSPACE/start-convert-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/start-preview-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/stop-preview-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/stop-convert-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/init-rocketmq-topics.sh $DEPLOY_ROOT/bin/


# 设置权限
chmod 644 $DEPLOY_ROOT/lib/*/*.jar
chmod 644 $DEPLOY_ROOT/config/*/*.yml
chmod 644 $DEPLOY_ROOT/config/*/*.xml 2>/dev/null || true

# 设置可执行文件权限
chmod +x $DEPLOY_ROOT/bin/*.sh

echo "✅ 文件拷贝完成！"
echo ""

# TODO: 打包成zip