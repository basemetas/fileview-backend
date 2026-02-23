#!/bin/bash

# Build and deployment script

# Check if running as root user
if [ "$EUID" -eq 0 ]; then
    # root user doesn't need sudo
    SUDO=""
else
    # non-root user uses sudo
    SUDO="sudo"
fi

WORKSPACE=$(pwd)
DEPLOY_ROOT=$WORKSPACE/.release/opt/fileview
echo "Deployment directory: $DEPLOY_ROOT"

# Clear directory if exists, otherwise create deployment directory
if [ -d "$DEPLOY_ROOT" ]; then
    echo "   🗑️  Cleaning existing deployment directory: $DEPLOY_ROOT"
    $SUDO rm -rf $DEPLOY_ROOT/*
else
    echo "   ✨ Creating deployment directory: $DEPLOY_ROOT"
    $SUDO mkdir -p $DEPLOY_ROOT
fi

# Compilation stage
chmod +x ./mvnw

# Support pipeline provided version number (if not set, use version.txt)
RELEASE_VERSION=${RELEASE_VERSION:-$(cat version.txt)}
echo "Release version: $RELEASE_VERSION"

./mvnw clean package -DskipTests -DreleaseVersion=$RELEASE_VERSION

# Check preview service JAR
ls -lh fileview-preview/target/lib/fileview-preview.jar

# Check convert service JAR
ls -lh fileview-convert/target/lib/fileview-convert.jar

# Check externalized config files
ls -la fileview-preview/target/config/
ls -la fileview-convert/target/config/

# Create directories

echo "📁 Checking and creating standard directory structure..."

# Check if main directory exists
if [ -d "$DEPLOY_ROOT" ]; then
    echo "   ℹ️  Deployment directory already exists: $DEPLOY_ROOT"
else
    echo "   ✨ Creating deployment directory: $DEPLOY_ROOT"
fi

# Create main directory and subdirectories (idempotent operation)
$SUDO mkdir -p $DEPLOY_ROOT/{bin,config,lib,logs,data,resources}
# Create config directory
$SUDO mkdir -p $DEPLOY_ROOT/config/{preview,convert}
# Create lib directory
$SUDO mkdir -p $DEPLOY_ROOT/lib/{preview,convert}

echo "✅ Directory structure ready!"
echo ""

# COPY Stage

# Copy preview service
echo "Copying preview service..."
cp $WORKSPACE/fileview-preview/target/lib/fileview-preview.jar \
   $DEPLOY_ROOT/lib/preview/

cp $WORKSPACE/fileview-preview/target/config/* \
   $DEPLOY_ROOT/config/preview/

echo "Copying convert service..."
cp $WORKSPACE/fileview-convert/target/lib/fileview-convert.jar \
   $DEPLOY_ROOT/lib/convert/

cp $WORKSPACE/fileview-convert/target/config/* \
   $DEPLOY_ROOT/config/convert/

echo "Copying start/stop scripts..."
cp $WORKSPACE/start-convert-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/start-preview-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/stop-preview-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/stop-convert-service.sh $DEPLOY_ROOT/bin/
cp $WORKSPACE/init-rocketmq-topics.sh $DEPLOY_ROOT/bin/


# Set permissions
chmod 644 $DEPLOY_ROOT/lib/*/*.jar
chmod 644 $DEPLOY_ROOT/config/*/*.yml
chmod 644 $DEPLOY_ROOT/config/*/*.xml 2>/dev/null || true

# Set executable file permissions
chmod +x $DEPLOY_ROOT/bin/*.sh

echo "✅ File copy complete!"
echo ""

# TODO: Package as zip