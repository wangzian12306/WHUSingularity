# WHUSingularity 安装维护手册 — 填入内容

> 基于模板 `安装维护手册 - 模板.docx` 结构，此处整理所有需填入的文本。
> 字体规格已在代码注释中标注，填充脚本将严格按此执行。

---

## 封面页

| 字段 | 值 |
|------|-----|
| 文档标题行1 (主题) | WHUSingularity |
| 文档标题行2 (主题) | 安装维护手册 |
| 文档状态 | [ √ ] 草稿  [  ] 修订  [  ] 发布 |
| 保密级别 | 内部 |
| 文档编号 | WHU-SING-DEPLOY-001 |
| 管理部门 | 研发部 |
| 修订年月 | 2026-05 |
| 版本号 | V1.0 |
| 修订人签字 | (留空) |
| 审核人签字 | (留空) |
| 批准人签字 | (留空) |
| 日期 | (留空) |

## 变更履历（表格2）

| 序号 | 变更日期 | 版本 | 变更位置 | 变更原因 | 变更内容 | 修订人 | 审核人 | 批准人 |
|------|----------|------|----------|----------|----------|--------|--------|--------|
| 1 | 2026-05-30 | V1.0 | 全文 | 建立初稿 | 初始版本 | — | — | — |
| 2 | | | | | | | | |
| 3 | | | | | | | | |
| 4 | | | | | | | | |

变更原因说明（List Paragraph）：
- 建立初稿
- 内容修订
- 正式发布

---

## 1. 引言

### 1.1 编写目的

本文档旨在为 WHUSingularity 高并发秒杀系统的部署与运维人员提供完整的安装、配置和维护指南。通过本文档，读者可以：

1. 了解系统的整体架构和组件依赖关系
2. 掌握基础设施环境（MySQL、Redis、Nacos、RocketMQ）的搭建方法
3. 完成各微服务模块的编译、配置与部署
4. 掌握日常运维操作，包括启停、监控、日志查看、故障排查与扩容缩容

预期读者：系统运维工程师、后端开发工程师、项目管理人员。

### 1.2 软件背景

WHUSingularity 是一套基于 Spring Cloud 微服务架构的高并发抢单（秒杀）系统。系统核心为 `singularity-core` 自定义的高并发资源分配框架，上层业务为电商秒杀场景，配套 React + TypeScript 前端。

**技术栈：**
- Java 21, Spring Boot 3.2.6, Spring Cloud 2023.0.3, Spring Cloud Alibaba 2023.0.3.2
- Nacos 2.x（服务注册与配置中心）
- Spring Cloud Gateway（API 网关，路由转发 + 负载均衡）
- OpenFeign（服务间调用）
- MyBatis 3.0.4, MySQL 8.0, Redis 7.x
- RocketMQ 5.x（异步消息与分布式事务）
- Caffeine（本地缓存）
- Flyway（数据库版本迁移）
- React 19 + TypeScript + Vite + Ant Design（前端）
- Docker + Docker Compose（容器化部署）

**微服务模块：**

| 模块 | 端口 | 说明 |
|------|------|------|
| singularity-user | 8090 | 用户服务：注册/登录/JWT认证/余额管理 |
| singularity-stock | 8082 | 库存服务：库存管理，MQ驱动，Flyway迁移 |
| singularity-order | 8081 | 订单服务：高并发抢单核心 |
| singularity-product | 8087 | 商品服务：商品CRUD + 两级缓存 |
| singularity-merchant | 8091 | 商户服务：商户注册/认证、商品/库存管理 |
| singularity-gateway | 8080 | API网关：统一入口、路由转发 |
| singularity-front | — | 前端：React SPA（Vite构建，Nginx托管） |
| singularity-scaler | 9090 | 自动伸缩服务：Prometheus指标采集 + 容器启停 |

---

## 2. 环境准备

### 2.1 硬件环境

**推荐配置（开发/测试环境）：**

| 硬件 | 配置 |
|------|------|
| CPU | 4 核及以上 |
| 内存 | 16 GB 及以上 |
| 磁盘 | 50 GB 可用空间 |
| 操作系统 | Linux（Ubuntu 22.04 / CentOS 7+）、macOS 12+ |
| 网络 | 各服务端口互通 |

**生产环境建议：** 至少 3 台节点，每节点 8 核 / 32 GB，配合容器编排（Kubernetes / Docker Swarm）。

### 2.2 基础软件环境

部署 WHUSingularity 前需安装以下基础软件：

| 软件 | 版本要求 | 用途 |
|------|----------|------|
| Docker | 24.0+ | 容器运行时 |
| Docker Compose | v2.20+ | 多容器编排 |
| JDK | 21 (OpenJDK / GraalVM) | Java 运行时 |
| Maven | 3.9+ | 项目构建 |
| Git | 2.40+ | 源码管理 |

#### 2.2.1 Docker 安装

**Ubuntu 22.04：**
```bash
# 第1步：卸载旧版本
sudo apt-get remove docker docker-engine docker.io containerd runc

# 第2步：安装依赖
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# 第3步：添加 Docker 官方 GPG 密钥
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 第4步：添加 Docker 仓库
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 第5步：安装 Docker Engine
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 第6步：验证安装
sudo docker --version
sudo docker compose version

# 第7步（可选）：将当前用户加入 docker 组，免 sudo
sudo usermod -aG docker $USER
newgrp docker
```

**macOS：** 下载并安装 Docker Desktop for Mac（https://www.docker.com/products/docker-desktop/）。

#### 2.2.2 JDK 21 安装

**Ubuntu 22.04：**
```bash
# 第1步：安装 OpenJDK 21
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk

# 第2步：验证安装
java -version
# 预期输出：openjdk version "21.0.x" ...

# 第3步：配置 JAVA_HOME（可选）
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

**macOS：**
```bash
brew install openjdk@21
```

#### 2.2.3 Maven 安装

```bash
# 第1步：下载 Maven
wget https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz

# 第2步：解压至 /opt
sudo tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt
sudo ln -s /opt/apache-maven-3.9.9 /opt/maven

# 第3步：配置环境变量
echo 'export M2_HOME=/opt/maven' >> ~/.bashrc
echo 'export PATH=$M2_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 第4步：验证
mvn --version
```

---

## 3. 基础设施安装

WHUSingularity 依赖以下基础设施组件，全部通过 Docker Compose 一键部署。

### 3.1 基础设施组件清单

| 组件 | 版本 | 端口 | 用途 |
|------|------|------|------|
| MySQL | 8.0 | 3306 | 持久化存储 |
| Redis | 7.x | 6379 | 缓存 + 分布式计数 |
| Nacos | 2.3.x | 8848, 9848 | 服务注册发现 + 配置中心 |
| RocketMQ NameServer | 5.x | 9876 | 消息队列命名服务 |
| RocketMQ Broker | 5.x | 10911, 10909 | 消息队列代理 |

### 3.2 前期准备

1. 确保 Docker 和 Docker Compose 已安装（见 2.2.1）
2. 创建持久化数据目录：
```bash
mkdir -p /data/mysql /data/redis /data/nacos /data/rocketmq
```
3. 确保以下端口未被占用：3306, 6379, 8848, 9848, 9876, 10911, 10909

### 3.3 安装流程

#### 第1步：获取部署文件

项目源码中 `deploy/` 目录包含 Docker Compose 编排文件 `docker-compose-infra.yml`。

```bash
cd /path/to/whu-singularity/deploy
ls docker-compose*.yml
```

#### 第2步：启动基础设施

```bash
docker compose -f docker-compose-infra.yml up -d
```

等待所有容器启动完成（约 30-60 秒）。

#### 第3步：验证各组件状态

```bash
# 查看容器状态
docker compose -f docker-compose-infra.yml ps

# MySQL
docker exec mysql-container mysqladmin ping -h localhost -u root -proot123

# Redis
docker exec redis-container redis-cli ping
# 应返回 PONG

# Nacos
curl -s http://localhost:8848/nacos/v1/console/health/readiness
# 应返回 ok

# RocketMQ NameServer
docker exec rmq-namesrv sh -c "tail -5 /home/rocketmq/logs/namesrv.log"
```

#### 第4步：初始化 Nacos 配置

在 Nacos 控制台（http://localhost:8848/nacos，默认账号/密码：nacos/nacos）创建以下配置文件：

| Data ID | 说明 |
|---------|------|
| `singularity-gateway.yaml` | 网关路由配置 |
| `singularity-user.yaml` | 用户服务配置（JWT、Redis） |
| `singularity-order.yaml` | 订单服务配置（Redis、RocketMQ、Slot） |
| `singularity-stock.yaml` | 库存服务配置（RocketMQ消费者） |
| `singularity-product.yaml` | 商品服务配置（缓存） |
| `singularity-scaler.yaml` | 自动伸缩策略配置 |

> 具体配置内容见附录 A 或项目 `docs/nacos/` 目录。

#### 第5步：初始化数据库

MySQL 容器启动后，数据库迁移由各服务启动时通过 Flyway 自动执行。也可手动初始化：

```bash
# 创建数据库
docker exec mysql-container mysql -u root -proot123 -e "
  CREATE DATABASE IF NOT EXISTS whu_user DEFAULT CHARSET utf8mb4;
  CREATE DATABASE IF NOT EXISTS whu_order DEFAULT CHARSET utf8mb4;
  CREATE DATABASE IF NOT EXISTS whu_stock DEFAULT CHARSET utf8mb4;
  CREATE DATABASE IF NOT EXISTS whu_product DEFAULT CHARSET utf8mb4;
"
```

---

## 4. 项目应用部署

### 4.1 应用程序构建

#### 4.1.1 获取源码

```bash
git clone <repository-url> /opt/whu-singularity
cd /opt/whu-singularity
```

#### 4.1.2 编译打包

```bash
# 第1步：跳过测试，全量构建
mvn clean package -DskipTests

# 第2步：验证构建产物
ls singularity-user/target/*.jar
ls singularity-order/target/*.jar
ls singularity-stock/target/*.jar
ls singularity-product/target/*.jar
ls singularity-merchant/target/*.jar
ls singularity-gateway/target/*.jar
ls singularity-scaler/target/*.jar
```

#### 4.1.3 前端构建

```bash
cd singularity-front
npm install
npm run build
# 产物在 singularity-front/dist/
```

### 4.2 使用 Docker Compose 一键部署

项目根目录提供 `dev-run.sh` 和 `deploy/docker-compose.yml` 可用于一键拉起所有服务。

```bash
# 第1步：确认基础设施已就绪（见第3章）
docker compose -f deploy/docker-compose-infra.yml ps

# 第2步：启动全部微服务
./dev-run.sh
# 或手动：
# docker compose -f deploy/docker-compose.yml up -d

# 第3步：查看启动日志
docker compose -f deploy/docker-compose.yml logs -f

# 第4步：验证所有服务
curl -s http://localhost:8080/actuator/health
# 通过网关验证各服务：
curl -s http://localhost:8080/api/user/actuator/health
```

### 4.3 各服务单独部署（可选）

若不使用 Docker，可直接以 Jar 包方式运行：

```bash
# 启动顺序：Nacos → user → stock → order → product → merchant → gateway → scaler

# singularity-user (8090)
java -jar -Dserver.port=8090 singularity-user/target/singularity-user-*.jar &

# singularity-stock (8082)
java -jar -Dserver.port=8082 singularity-stock/target/singularity-stock-*.jar &

# singularity-order (8081)
java -jar -Dserver.port=8081 singularity-order/target/singularity-order-*.jar &

# singularity-product (8087)
java -jar -Dserver.port=8087 singularity-product/target/singularity-product-*.jar &

# singularity-merchant (8091)
java -jar -Dserver.port=8091 singularity-merchant/target/singularity-merchant-*.jar &

# singularity-gateway (8080)
java -jar -Dserver.port=8080 singularity-gateway/target/singularity-gateway-*.jar &

# singularity-scaler (9090)
java -jar -Dserver.port=9090 singularity-scaler/target/singularity-scaler-*.jar &
```

### 4.4 数据库部署

数据库使用 MySQL 8.0（已在 3.3 中通过 Docker 部署），各服务通过 Flyway 自动管理表结构迁移。

**Flyway 迁移脚本位置：**
- `singularity-stock/src/main/resources/db/migration/`
- `singularity-product/src/main/resources/db/migration/`

首次启动服务时 Flyway 自动执行迁移。迁移历史记录存储在数据库的 `flyway_schema_history` 表中。

### 4.5 系统部署信息汇总

| 组件 | 版本 | 部署方式 | 端口 | 路径/说明 |
|------|------|----------|------|-----------|
| JDK | 21 (OpenJDK) | 系统包管理 / Docker镜像 | — | `$JAVA_HOME` |
| Maven | 3.9.x | 系统包管理 | — | — |
| MySQL | 8.0 | Docker | 3306 | `jdbc:mysql://localhost:3306/` |
| Redis | 7.x | Docker | 6379 | 无密码（开发环境） |
| Nacos | 2.3.x | Docker | 8848 | 控制台: `http://localhost:8848/nacos` |
| RocketMQ NameServer | 5.x | Docker | 9876 | — |
| RocketMQ Broker | 5.x | Docker | 10911 | — |
| singularity-gateway | 1.0-SNAPSHOT | Jar / Docker | 8080 | API 统一入口 |
| singularity-user | 1.0-SNAPSHOT | Jar / Docker | 8090 | 用户服务 |
| singularity-order | 1.0-SNAPSHOT | Jar / Docker | 8081 | 订单服务 |
| singularity-stock | 1.0-SNAPSHOT | Jar / Docker | 8082 | 库存服务 |
| singularity-product | 1.0-SNAPSHOT | Jar / Docker | 8087 | 商品服务 |
| singularity-merchant | 1.0-SNAPSHOT | Jar / Docker | 8091 | 商户服务 |
| singularity-scaler | 1.0-SNAPSHOT | Jar / Docker | 9090 | 自动伸缩服务 |
| singularity-front | — | Nginx / Vite dev | 5173 | 前端 SPA |

---

## 5. 系统运维管理

### 5.1 Nacos 配置管理

Nacos 控制台地址：`http://<host>:8848/nacos`（默认账号/密码：nacos/nacos）

**常用配置项：**

| 配置文件名 | 关键配置 | 说明 |
|-----------|---------|------|
| `singularity-order.yaml` | `product-id`, `redis`, `rocketmq` | 订单服务核心配置 |
| `singularity-user.yaml` | `jwt.secret`, `redis` | JWT 密钥与 Redis 连接 |
| `singularity-stock.yaml` | `rocketmq.consumer` | 库存服务的 MQ 消费者 |
| `singularity-gateway.yaml` | `spring.cloud.gateway.routes` | 路由规则 |
| `singularity-scaler.yaml` | `threshold`, `min-instances`, `max-instances` | 自动伸缩策略 |

**修改配置后：** 在 Nacos 控制台发布配置后，对应服务会自动刷新（热更新，无需重启）。

### 5.2 服务启停

**全部服务（Docker Compose）：**
```bash
# 启动
docker compose -f deploy/docker-compose.yml up -d

# 停止
docker compose -f deploy/docker-compose.yml stop

# 重启
docker compose -f deploy/docker-compose.yml restart

# 停止并删除容器
docker compose -f deploy/docker-compose.yml down
```

**单个服务（Docker）：**
```bash
docker restart singularity-order
docker stop singularity-order
docker start singularity-order
```

### 5.3 日志查看

**Docker 部署：**
```bash
# 查看所有服务日志
docker compose -f deploy/docker-compose.yml logs -f

# 查看单个服务日志
docker compose -f deploy/docker-compose.yml logs -f singularity-order

# 查看最近 100 行
docker compose -f deploy/docker-compose.yml logs --tail 100 singularity-order
```

**Jar 包部署：** 日志默认输出到标准输出，可通过重定向到文件持久化：
```bash
java -jar singularity-order.jar > logs/order.log 2>&1 &
```

### 5.4 健康检查与监控

**服务健康端点：**
```bash
# 网关聚合健康检查
curl http://localhost:8080/actuator/health

# 各服务独立检查
curl http://localhost:8081/actuator/health   # order
curl http://localhost:8082/actuator/health   # stock
curl http://localhost:8090/actuator/health   # user
curl http://localhost:8087/actuator/health   # product
```

**Prometheus 指标（自动伸缩服务使用）：**
```bash
# 各服务暴露 Prometheus 指标端点
curl http://localhost:8081/actuator/prometheus
```

**Nacos 服务列表：** 登录 Nacos 控制台 → 服务管理 → 服务列表，可查看所有已注册微服务的健康状态。

### 5.5 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 服务启动失败 | Nacos 未就绪 | 确认 Nacos 已在 8848 端口运行 |
| 数据库连接失败 | MySQL 未启动或密码错误 | 检查 `docker ps` 和配置文件中的数据库连接信息 |
| MQ 消息发送失败 | RocketMQ 未就绪 | 确认 NameServer (9876) 和 Broker (10911) 端口可达 |
| 网关返回 503 | 目标服务未注册到 Nacos | 检查 Nacos 服务列表，确认服务已注册 |
| Redis 连接失败 | Redis 未启动或配置错误 | `redis-cli ping` 验证 |
| 库存扣减异常 | Redis bucket 计数器未初始化 | 检查 Nacos 配置中 `product-id` 是否正确 |
| Flyway 迁移失败 | 数据库权限或SQL冲突 | 查看服务启动日志中的 Flyway 错误详情 |

### 5.6 备份与恢复

**MySQL 数据备份：**
```bash
# 备份全部数据库
docker exec mysql-container mysqldump -u root -proot123 --all-databases > backup_$(date +%Y%m%d).sql

# 备份单个数据库
docker exec mysql-container mysqldump -u root -proot123 whu_order > backup_order.sql
```

**MySQL 数据恢复：**
```bash
docker exec -i mysql-container mysql -u root -proot123 < backup.sql
```

**Redis 数据备份：**
```bash
# Redis 默认开启 RDB 持久化，备份文件位于容器的 /data/dump.rdb
docker cp redis-container:/data/dump.rdb ./redis_backup.rdb
```

**Nacos 配置备份：** 在 Nacos 控制台逐个导出配置文件，或备份 Nacos 数据目录。

### 5.7 扩容与缩容

`singularity-scaler` 服务提供基于 Prometheus 指标的自动伸缩能力。

**自动伸缩策略配置（Nacos `singularity-scaler.yaml`）：**
```yaml
scaler:
  threshold-cpu: 80          # CPU 使用率阈值（%）
  threshold-qps: 1000        # QPS 阈值
  min-instances: 1           # 最小实例数
  max-instances: 5           # 最大实例数
  scale-up-cooldown: 60      # 扩容冷却时间（秒）
  scale-down-cooldown: 300   # 缩容冷却时间（秒）
  port-start: 8100           # 动态实例起始端口
```

**手动扩容（Docker）：**
```bash
# 启动额外实例
docker run -d --name singularity-order-2 \
  -e SERVER_PORT=8181 \
  singularity-order:latest
```

---

## 页脚

- 首页（节1）：`WHUSingularity 版权所有 | 本文档中所包含的信息属于 WHUSingularity 项目组的机密信息 | 未经许可，不可全部或部分发表、复制、使用于任何目的`
- 正文页（节2）：`WHUSingularity | 高并发秒杀系统`
