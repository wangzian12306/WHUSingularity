#!/usr/bin/env bash
# 全量迁移：拆掉旧 compose 项目 deploy + 当前 singularity 项目，再重建所有容器。
# 用法（在仓库根）:
#   chmod +x deploy/rebuild-stack.sh
#   ./deploy/rebuild-stack.sh
#   REMOVE_VOLUMES=1 ./deploy/rebuild-stack.sh          # compose down -v
#   REMOVE_NAMED_VOLUMES=all ./deploy/rebuild-stack.sh  # 另删 singularity_mysql_data 等
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.backend.yml"
export REPO_ROOT

cd "${REPO_ROOT}"

echo "==> [1/4] 停止旧项目 deploy ..."
docker compose -f "${COMPOSE_FILE}" -p deploy down --remove-orphans 2>/dev/null || true

echo "==> [2/4] 停止当前项目 (name: singularity) ..."
if [[ "${REMOVE_VOLUMES:-0}" == "1" ]]; then
  docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans 2>/dev/null || true
else
  docker compose -f "${COMPOSE_FILE}" down --remove-orphans 2>/dev/null || true
fi

case "${REMOVE_NAMED_VOLUMES:-none}" in
  old|all)
    echo "==> [2b] 删除遗留 deploy_* 卷 ..."
    docker volume rm deploy_mysql_data deploy_maven_repo 2>/dev/null || true
    ;;
esac
case "${REMOVE_NAMED_VOLUMES:-none}" in
  all)
    echo "==> [2c] 删除 singularity_* 数据卷（库与 m2 将清空）..."
    docker volume rm singularity_mysql_data singularity_maven_repo 2>/dev/null || true
    ;;
esac

echo "==> [3/4] 删除名称含 singularity 的旧容器 ..."
docker ps -aq -f "name=singularity" | while read -r id; do
  [ -n "$id" ] && docker rm -f "$id" 2>/dev/null || true
done

echo "==> [4/4] 重建并启动 ..."
docker compose -f "${COMPOSE_FILE}" up -d --build --force-recreate --remove-orphans --scale singularity-order=3

echo "[DONE]"
docker compose -f "${COMPOSE_FILE}" ps -a
