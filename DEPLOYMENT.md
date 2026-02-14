# 文件预览转换服务 - 部署指南

本文档提供从代码编译到服务运行的完整部署流程。

---

## 📋 目录

1. [环境要求](#环境要求)
2. [编译打包](#编译打包)
3. [准备部署目录](#准备部署目录)
4. [部署文件](#部署文件)
5. [启动服务](#启动服务)
6. [验证服务](#验证服务)
7. [常见问题](#常见问题)

---

## 🔧 环境要求

### 必需环境

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| Java | 17+ | 运行环境 |
| Maven | 3.8+ | 编译工具 |
| Redis | 6.0+ | 缓存服务 |
| RocketMQ | 5.0+ | 消息队列 |
| LibreOffice | 7.0+ | 文档转换引擎 |

### 可选环境

- **ImageMagick**：图片格式转换优化
- **CAD2X**：CAD 文件转换支持
- **字体库**：支持中文字符渲染
- **p7zip-full**：支持在WLS2环境下解析7Z压缩包

> 提示：预览与转换服务为两个独立微服务，通过 HTTP 和 RocketMQ 协同，可部署在同一或不同主机上。
---

## 📦 编译打包

### 1. 进入项目目录

```bash
cd /var/app/fileview-backend
```

### 2. 清理并编译

**完整编译（推荐）：**
```bash
./mvnw clean package -DskipTests
```

**单独编译预览服务：**
```bash
./mvnw clean package -DskipTests -pl fileview-preview
```

**单独编译转换服务：**
```bash
./mvnw clean package -DskipTests -pl fileview-convert
```

### 3. 验证编译产物

```bash
# 检查预览服务 JAR
ls -lh fileview-preview/target/lib/fileview-preview-1.0.0.jar

# 检查转换服务 JAR
ls -lh fileview-convert/target/lib/fileview-convert-1.0.0.jar

# 检查外部化配置文件
ls -la fileview-preview/target/config/
ls -la fileview-convert/target/config/
```

**预期输出：**
- `fileview-preview-1.0.0.jar` (~30MB)
- `fileview-convert-1.0.0.jar` (~40MB)
- `target/config/` 包含 `application*.yml` 和 `logback-spring.xml`

---

## 📁 准备部署目录

### 1. 使用一步部署脚本创建目录结构

项目提供了一键构建+部署脚本 `deploy.sh`，会自动完成：

- Maven 编译打包（preview / convert 两个服务）
- 创建标准发布目录结构
- 复制 JAR、配置文件、外部转换引擎（ CAD2X）
- 复制启停脚本并设置权限

在项目根目录执行：

```bash
chmod +x deploy.sh
./deploy.sh

```
脚本默认的发布根目录为：
```bash
.release/opt/fileview
```
如果你期望最终运行目录是 /opt/fileview，可以在部署完成后将 .release/opt/fileview 同步到目标机器或挂载到对应路径。


### 2. 目录结构说明
deploy.sh 执行完成后，发布目录结构类似：
```
/opt/fileview/
├── bin/                          # 可执行文件和脚本                
│   ├── cad2x/                    # CAD2X 转换引擎
│   │   └── cad2x                 # 可执行文件                
│   ├── start-preview-service.sh  # 预览服务启动脚本
│   ├── stop-preview-service.sh   # 预览服务停止脚本
│   ├── start-convert-service.sh  # 转换服务启动脚本
│   └── stop-convert-service.sh   # 转换服务停止脚本
│
├── config/                       # 配置文件
│   ├── preview/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── logback-spring.xml
│   └── convert/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-test.yml
│       ├── application-prod.yml
│       └── logback-spring.xml
│
├── lib/                          # JAR 文件
│   ├── preview/
│   │   └── fileview-preview-1.0.0.jar
│   └── convert/
│       └── fileview-convert-1.0.0.jar
│
├── logs/                         # 日志文件
│   ├── preview/
│   │   ├── stdout.log            # 标准输出
│   │   └── fileview-preview.log  # 应用日志
│   └── convert/
│       ├── stdout.log
│       └── file-convert-service.log
│
├── data/                         # 运行时数据
│   ├── preview/                  # 预览数据
│   ├── convert/                  # 转换数据
│   ├── temp/                     # 临时文件
│   ├── target/                   # 转换目标文件
│   ├── uploads/                  # 上传文件
│   ├── downloads/                # 下载文件
│   ├── uncompress/               # 解压文件
│   ├── libreoffice/              # LibreOffice 临时目录
│   └── cad2x/                    # CAD2X 临时目录
│
└── resources/                    # 资源文件
    └── fonts/                    # 字体文件
```
实际运行环境中，建议将 .release/opt/fileview 的结构同步或映射到 /opt/fileview，以与配置文件中约定的路径保持一致。


## 🚀 部署文件

### 1. 推荐：使用一步部署脚本

```bash
# 确保脚本可执行
chmod +x deploy.sh

# 执行部署
./deploy.sh
```
执行完成后，检查：
```bash
ls -lh .release/opt/fileview/lib/preview/
ls -lh .release/opt/fileview/lib/convert/
ls -la .release/opt/fileview/config/preview/
ls -la .release/opt/fileview/config/convert/
ls -la .release/opt/fileview/bin/
```
确认：
   - 预览 / 转换服务 JAR 已放入 lib/preview、lib/convert
   - 对应的 application*.yml / logback-spring.xml 已放入 config/preview、config/convert
   - bin 下存在 cad2x 目录及启停脚本

如果需要将 .release/opt/fileview 部署到生产机，可整体复制或打包后下发，再按实际路径（例如 /opt/fileview）调整启动脚本中的目录。

### 2. 手动部署（可选）

如果不使用 deploy.sh，可按以下步骤手动部署到 /opt/fileview：

```bash
# 创建目录（幂等）
mkdir -p /opt/fileview/{bin,config,lib,logs,data,resources}
mkdir -p /opt/fileview/config/{preview,convert}
mkdir -p /opt/fileview/lib/{preview,convert}

# 复制预览服务
cp fileview-preview/target/fileview-preview-1.0.0.jar \
   /opt/fileview/lib/preview/

cp fileview-preview/target/config/* \
   /opt/fileview/config/preview/

# 复制转换服务
cp fileview-convert/target/fileview-convert-1.0.0.jar \
   /opt/fileview/lib/convert/

cp fileview-convert/target/config/* \
   /opt/fileview/config/convert/

# 复制外部引擎（如需）
cp -r fileview-convert/cad2x/linux_x64 /opt/fileview/bin/cad2x

# 复制启动脚本
cp start-preview-service.sh /opt/fileview/bin/
cp stop-preview-service.sh /opt/fileview/bin/
cp start-convert-service.sh /opt/fileview/bin/
cp stop-convert-service.sh /opt/fileview/bin/

# 设置权限
chmod 644 /opt/fileview/lib/*/*.jar
chmod 644 /opt/fileview/config/*/*.yml
chmod 644 /opt/fileview/config/*/*.xml 2>/dev/null || true

chmod +x /opt/fileview/bin/*.sh
chmod +x /opt/fileview/bin/cad2x/linux_x64/cad2x 2>/dev/null || true

```
### 3. 验证部署

```bash
# 检查 JAR 文件
ls -lh /opt/fileview/lib/preview/
ls -lh /opt/fileview/lib/convert/

# 检查配置文件
ls -la /opt/fileview/config/preview/
ls -la /opt/fileview/config/convert/

# 检查脚本
ls -la /opt/fileview/bin/*.sh

# 检查转换引擎
ls -la /opt/fileview/bin/cad2x/cad2x
```

---

## ▶️ 启动服务

### 1. 启动预览服务

```bash
cd /opt/fileview/bin

# 开发环境
./start-preview-service.sh dev

# 生产环境
./start-preview-service.sh prod
# 或
./start-preview-service.sh  # 默认 prod
```

**启动参数说明：**
- JVM 内存：512MB-2GB
- 端口：8184
- 配置路径：`/opt/fileview/config/preview/`
- 日志路径：`/opt/fileview/logs/preview/`

### 2. 启动转换服务

```bash
cd /opt/fileview/bin

# 开发环境
./start-convert-service.sh dev

# 生产环境
./start-convert-service.sh prod
# 或
./start-convert-service.sh  # 默认 prod
```

**启动参数说明：**
- JVM 内存：1GB-4GB
- 端口：8183
- 配置路径：`/opt/fileview/config/convert/`
- 日志路径：`/opt/fileview/logs/convert/`

### 3. 查看启动日志

```bash
# 预览服务
tail -f /opt/fileview/logs/preview/stdout.log
tail -f /opt/fileview/logs/preview/fileview-preview.log

# 转换服务
tail -f /opt/fileview/logs/convert/stdout.log
tail -f /opt/fileview/logs/convert/file-convert-service.log
```

---

## ✅ 验证服务

### 1. 检查进程

```bash
# 查看 Java 进程
ps -ef | grep fileview

# 应该看到两个进程：
# - fileview-preview-1.0.0.jar
# - fileview-convert-1.0.0.jar
```

### 2. 检查端口

```bash
# 预览服务端口 8184
netstat -tlnp | grep 8184
# 或
lsof -i :8184

# 转换服务端口 8183
netstat -tlnp | grep 8183
# 或
lsof -i :8183
```

### 3. 健康检查

```bash
# 预览服务健康检查
curl http://localhost:8184/actuator/health

# 转换服务健康检查
curl http://localhost:8183/actuator/health

# 预期响应：
# {"status":"UP"}
```

### 4. 查看引擎状态（转换服务）

```bash
# 查看日志中的引擎健康检查信息
grep "引擎健康检查完成" /opt/fileview/logs/convert/file-convert-service.log

# 预期输出示例：
# ✅ 引擎健康检查完成 - 耗时: 5105ms, 状态: X2T=true, LibreOffice=true
```

### 5. 测试文件预览

```bash
# 访问预览接口（替换为实际文件路径）
curl "http://localhost:8184/preview/api/localFile?filePath=/path/to/test.pdf"
```

---

## 🛑 停止服务

### 1. 停止预览服务

```bash
cd /opt/fileview/bin
./stop-preview-service.sh
```

**停止逻辑：**
1. 发送 `SIGTERM` 信号（优雅停止）
2. 等待最多 30 秒
3. 超时则发送 `SIGKILL` 强制终止

### 2. 停止转换服务

```bash
cd /opt/fileview/bin
./stop-convert-service.sh
```

### 3. 验证停止

```bash
# 检查进程是否已停止
ps -ef | grep fileview

# 检查 PID 文件是否已删除
ls -la /opt/fileview/bin/preview/*.pid
ls -la /opt/fileview/bin/convert/*.pid
```

---

## 🔄 重启服务

```bash
cd /opt/fileview/bin

# 重启预览服务
./stop-preview-service.sh
./start-preview-service.sh dev

# 重启转换服务
./stop-convert-service.sh
./start-convert-service.sh dev
```

---

## ❓ 常见问题

### 1. 日志乱码问题

**现象：** 日志中中文显示为 `?` 或乱码

**解决：**
启动脚本已添加 UTF-8 编码参数：
```bash
-Dfile.encoding=UTF-8
-Dsun.jnu.encoding=UTF-8
```

### 2. 日志路径错误

**现象：** 日志输出到 `/opt/fileview/bin/logs` 而非 `/opt/fileview/logs/`

**解决：**
确保启动脚本包含：
```bash
-Dlogging.config=file:${CONFIG_DIR}/logback-spring.xml
export LOG_PATH="$LOG_DIR"
```

### 3. Locale 警告

**现象：** 
```
warning: setlocale: LC_ALL: cannot change locale (en_US.UTF-8)
```

**解决：**
启动脚本已修改为使用通用的 `C.UTF-8`：
```bash
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
```

### 4. Redis 连接失败

**现象：** 
```
Unable to connect to Redis
```

**解决：**
```bash
# 检查 Redis 是否运行
redis-cli ping

# 检查配置
cat /opt/fileview/config/convert/application-dev.yml | grep -A 5 "redis:"

# 确保 host 和 port 正确
```

### 5. RocketMQ 连接失败

**现象：**
```
connect to <127.0.0.1:9876> failed
```

**解决：**
```bash
# 检查 RocketMQ 是否运行
telnet 127.0.0.1 9876

# 检查配置
cat /opt/fileview/config/convert/application-dev.yml | grep -A 5 "rocketmq:"
```

### 6. 端口被占用

**现象：**
```
Port 8184 is already in use
```

**解决：**
```bash
# 查找占用端口的进程
lsof -i :8184

# 停止旧进程
./stop-preview-service.sh

# 或手动 kill
kill -9 <PID>
```

### 7. 服务启动后立即退出

**解决步骤：**

```bash
# 1. 查看启动日志
tail -100 /opt/fileview/logs/convert/stdout.log

# 2. 检查配置文件语法
cat /opt/fileview/config/convert/application-dev.yml

# 3. 检查 JAR 文件完整性
ls -lh /opt/fileview/lib/convert/fileview-convert-1.0.0.jar

# 4. 手动启动查看详细错误
cd /opt/fileview/lib/convert
java -jar fileview-convert-1.0.0.jar --spring.profiles.active=dev
```

---

## 📊 监控建议

### 1. 日志监控

```bash
# 实时监控所有日志
tail -f /opt/fileview/logs/preview/*.log
tail -f /opt/fileview/logs/convert/*.log
```

### 2. 磁盘空间监控

```bash
# 检查数据目录大小
du -sh /opt/fileview/data/*

# 定期清理临时文件（建议配置定时任务）
find /opt/fileview/data/temp -mtime +1 -delete
```

### 3. JVM 监控

```bash
# 查看 Java 进程内存使用
jps -l
jmap -heap <PID>
```

### 4. 性能监控

使用 Spring Boot Actuator：
```bash
# Metrics
curl http://localhost:8183/actuator/metrics

# JVM 信息
curl http://localhost:8183/actuator/metrics/jvm.memory.used
```

---

## 📝 部署检查清单

部署前请确认以下项目：

- [ ] Java 17+ 已安装
- [ ] Redis 服务已启动
- [ ] RocketMQ 服务已启动
- [ ] LibreOffice 已安装（如需使用）
- [ ] 目录结构已创建 (`prepare-deploy.sh`)
- [ ] JAR 文件已编译
- [ ] 配置文件已复制
- [ ] 启动脚本已复制并授权
- [ ] 转换引擎已复制并授权（x2t, cad2x）
- [ ] 端口 8183, 8184 未被占用
- [ ] 配置文件中的路径正确
- [ ] 日志目录有写入权限

---

## 🔗 相关文档

- [README.md](README.md) - 项目概述
- [配置说明](fileview-convert/src/main/resources/application.yml) - 配置参数详解

---

## 📞 技术支持

如遇到部署问题，请提供：
1. 完整的启动日志
2. 配置文件内容
3. 环境信息（Java 版本、OS 版本）
4. 错误截图或详细描述

---

**最后更新：** 2025-11-26
