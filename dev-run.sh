#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.backend.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker 未安装或不在 PATH 中"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] docker compose 插件不可用"
  exit 1
fi

wait_http_ok() {
  local url="$1"
  local retries="${2:-60}"
  local i
  for ((i = 1; i <= retries; i++)); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

publish_nacos_config() {
  local data_id="$1"
  local content="$2"

  local response
  response=$(curl -s -X POST "http://localhost:8848/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content=${content}")

  if [[ "${response}" != "true" ]]; then
    echo "[ERROR] 写入 Nacos 配置失败: ${data_id}, 返回: ${response}"
    return 1
  fi

  echo "[INFO] 已写入 Nacos 配置: ${data_id}"
}

read -r -d '' ORDER_CONFIG <<'EOF' || true
spring:
  data:
    redis:
      host: redis
      port: 6379

rocketmq:
  name-server: rmq-namesrv:9876
  producer:
    group: order-producer-group
  consumer:
    group: order-consumer-group

singularity:
  order:
    slots:
      - id: bucket-1
        redis-key: stock:bucket-1
      - id: bucket-2
        redis-key: stock:bucket-2
EOF

read -r -d '' USER_CONFIG <<'EOF' || true
spring:
  data:
    redis:
      host: redis
      port: 6379

auth:
  jwt:
    secret: ${JWT_SECRET:dev-secret-key}
    expire-seconds: 7200
  blacklist:
    prefix: "auth:blacklist:"
EOF

read -r -d '' STOCK_CONFIG <<'EOF' || true
spring:
  data:
    redis:
      host: redis
      port: 6379

rocketmq:
  name-server: rmq-namesrv:9876
  consumer:
    group: stock-consumer-group
EOF

echo "[STEP 1/4] 启动基础依赖容器 (MySQL/Redis/Nacos/RocketMQ)..."
docker compose -f "${COMPOSE_FILE}" up -d mysql redis nacos rmq-namesrv rmq-broker

echo "[STEP 2/4] 等待 Nacos 就绪..."
if ! wait_http_ok "http://localhost:8848/nacos" 90; then
  echo "[ERROR] Nacos 未在预期时间内就绪"
  exit 1
fi

echo "[STEP 3/4] 写入 Nacos 配置..."
publish_nacos_config "singularity-order.yaml" "${ORDER_CONFIG}"
publish_nacos_config "singularity-user.yaml" "${USER_CONFIG}"
publish_nacos_config "singularity-stock.yaml" "${STOCK_CONFIG}"

echo "[STEP 4/4] 启动后端服务实例 (user/order/stock 各 1 个)..."
docker compose -f "${COMPOSE_FILE}" up -d --force-recreate \
  singularity-user-1 singularity-user-2 \
  singularity-order-1 singularity-order-2 \
  singularity-stock-1 singularity-stock-2

echo "[DONE] 后端开发环境已拉起"
echo "查看状态: docker compose -f deploy/docker-compose.backend.yml ps"
echo "查看日志: docker compose -f deploy/docker-compose.backend.yml logs -f singularity-user"
