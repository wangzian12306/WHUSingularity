#!/usr/bin/env bash
# 输出 ORDER_TARGETS（前 N 个 order 容器）。例：
#   t=$(./tests/order-stress-test/k6-print-order-targets.sh)
#   ORDER_TARGETS="$t" docker compose -f deploy/docker-compose.backend.yml --profile k6 run --rm k6-order-load
set -euo pipefail
COUNT="${1:-3}"
mapfile -t names < <(docker ps --filter "label=com.docker.compose.service=singularity-order" --format '{{.Names}}' | sort -u)
if (("${#names[@]}" < COUNT)); then
  echo "need at least ${COUNT} singularity-order containers, got ${#names[@]}" >&2
  exit 1
fi
bases=()
for ((i = 0; i < COUNT; i++)); do
  bases+=("http://${names[i]}:8081")
done
IFS=','; echo "${bases[*]}"
