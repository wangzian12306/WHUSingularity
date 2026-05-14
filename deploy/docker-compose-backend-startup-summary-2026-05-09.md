# docker-compose.backend.yml 启动复盘（2026-05-09）

## 1. 目标

- 使用 `deploy/docker-compose.backend.yml` 启动后端容器
- 重点确认 `singularity-product` 容器可正常启动并稳定运行

---

## 2. 实际执行的关键命令

## 2.1 基础检查

```powershell
ls
ls deploy
```

```powershell
ls singularity-product/target
ls singularity-user/target
ls singularity-order/target
ls singularity-stock/target
ls singularity-merchant/target
ls singularity-gateway/target
ls singularity-scaler/target
```

## 2.2 补充 compose 所需环境文件

```powershell
"REPO_ROOT=E:/Study/第六学期课程/分布式/WHUSingularity" | Set-Content deploy/.env
```

## 2.3 清理旧容器并重启

```powershell
docker rm singularity-product-1 singularity-product-2 singularity-product-0 singularity-rmq-broker singularity-redis singularity-nacos singularity-mysql singularity-rmq-namesrv
```

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env up -d mysql redis nacos rmq-namesrv rmq-broker singularity-user singularity-stock singularity-order singularity-product singularity-merchant singularity-gateway singularity-scaler-0 singularity-order-lb-1 singularity-order-lb-2 singularity-order-lb-3
```

## 2.4 关键排障命令

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env ps
docker logs singularity-singularity-product-1 --tail 120
docker logs singularity-singularity-order-1 --tail 120
docker logs singularity-scaler-0 --tail 120
```

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env run --rm --entrypoint sh singularity-product -c "pwd; ls -la /workspace; ls -la /workspace/singularity-product/target"
```

## 2.5 解决中文路径挂载问题（关键）

```powershell
subst W: "E:\Study\第六学期课程\分布式\WHUSingularity"
"REPO_ROOT=W:/" | Set-Content deploy/.env
```

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env down
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env up -d mysql redis nacos rmq-namesrv rmq-broker singularity-user singularity-stock singularity-order singularity-product singularity-merchant singularity-gateway singularity-scaler-0 singularity-order-lb-1 singularity-order-lb-2 singularity-order-lb-3
```

## 2.6 修复 product 启动依赖（RocketMQ）

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env up -d singularity-product
```

> 同时在 `deploy/docker-compose.backend.yml` 的 `singularity-product.environment` 增加：
>
> - `ROCKETMQ_NAME_SERVER: rmq-namesrv:9876`
> - `ROCKETMQ_PRODUCER_GROUP: product-producer-group`
> - `ROCKETMQ_CONSUMER_GROUP: product-cache-consumer-group`

## 2.7 修复脚本行尾导致的容器重启问题（order/scaler）

```powershell
python -c "from pathlib import Path; p=Path(r'E:/Study/第六学期课程/分布式/WHUSingularity/deploy/order-docker-cmd.sh'); s=p.read_text(encoding='utf-8', errors='ignore'); s=s.replace('\r\n','\n').replace('\r','\n'); p.write_text(s, encoding='utf-8', newline='\n')"
python -c "from pathlib import Path; p=Path(r'E:/Study/第六学期课程/分布式/WHUSingularity/deploy/scaler-entrypoint.sh'); s=p.read_text(encoding='utf-8', errors='ignore'); s=s.replace('\r\n','\n').replace('\r','\n'); p.write_text(s, encoding='utf-8', newline='\n')"
```

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env restart singularity-order singularity-scaler-0
```

## 2.8 尝试启动全部服务（含 front）

```powershell
docker compose -f deploy/docker-compose.backend.yml --env-file deploy/.env up -d
```

（前端镜像拉取 `node:20-alpine` 网络偶发失败时，曾单独执行）

```powershell
docker pull node:20-alpine
```

---

## 3. 遇到的问题、根因与解决方案

## 问题 A：`couldn't find env file: deploy/.env`

- 现象：compose 启动时报找不到 env 文件
- 根因：`deploy/.env` 不存在
- 解决：按 `deploy/.env.example` 新建 `deploy/.env` 并设置 `REPO_ROOT`

## 问题 B：容器报 `Unable to access jarfile ...`

- 现象：`product/user/gateway` 等容器反复重启，日志显示找不到 jar
- 根因：`REPO_ROOT` 指向中文路径时，在当前 Docker 挂载场景下容器内实际映射为空目录
- 解决：
  1. 用 `subst W:` 映射 ASCII 路径
  2. `deploy/.env` 设置 `REPO_ROOT=W:/`
  3. `docker compose down && up -d` 重建容器

## 问题 C：`product` 启动失败，缺少 `RocketMQTemplate`

- 现象：`ProductEventPublisher` 注入失败，提示 `No qualifying bean of type RocketMQTemplate`
- 根因：`singularity-product` 容器缺少 RocketMQ 连接相关环境变量
- 解决：在 `singularity-product.environment` 补充：
  - `ROCKETMQ_NAME_SERVER`
  - `ROCKETMQ_PRODUCER_GROUP`
  - `ROCKETMQ_CONSUMER_GROUP`

## 问题 D：`order` / `scaler` 重启，日志 `set: Illegal option -`

- 现象：`order-docker-cmd.sh`、`scaler-entrypoint.sh` 在第 2 行 `set -e` 报错并循环重启
- 根因：脚本文件行尾格式异常（CRLF 等）导致 `/bin/sh` 解析异常
- 解决：将两个脚本统一转为 LF 后重启容器

## 问题 E：全量 `up -d` 时前端 build 失败

- 现象：`singularity-front` 构建阶段拉取 `node:20-alpine` 超时
- 根因：访问 Docker Hub 网络抖动
- 解决：重试拉取镜像（`docker pull node:20-alpine`）后再重试启动

---

## 4. 最终状态结论

- `singularity-product`：已成功启动并稳定运行（可继续开发）
- 后端核心服务（mysql/redis/nacos/rmq/user/stock/product/gateway/order/scaler）均已拉起
- `product` 当前日志中的 `NoResourceFoundException: /actuator/prometheus` 为运行期请求路径未暴露导致，并非启动失败

---

## 5. 可复用建议（下次启动）

1. 固定使用 ASCII 路径挂载（如 `W:/`）避免中文路径挂载异常
2. 先确认各模块 `target/*.jar` 存在，再执行 compose
3. `deploy/.env` 保持如下形式：

```env
REPO_ROOT=W:/
```

4. 启动顺序建议：
   - 基础设施（mysql/redis/nacos/rmq）
   - 业务服务（user/stock/order/product/merchant/gateway/scaler）
   - 前端（网络正常时再拉起）
