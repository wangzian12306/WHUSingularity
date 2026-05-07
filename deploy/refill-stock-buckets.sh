#!/usr/bin/env bash
# 用法：在仓库根或 deploy 下执行 ./refill-stock-buckets.sh
# 依赖：docker（mysql 容器 singularity-mysql）、curl、网关 8080 可访问
set -euo pipefail
QTY="${1:-99999999}"
MYSQL_C="${MYSQL_CONTAINER:-singularity-mysql}"
BASE="${STOCK_BASE_URL:-http://localhost:8080}"

echo "[1/2] MySQL singularity_stock PROD_001 / PROD_002 -> ${QTY}"
docker exec "${MYSQL_C}" mysql -uroot -proot -e "
USE singularity_stock;
INSERT INTO stock (product_id, available_quantity, reserved_quantity, total_quantity, version)
VALUES
  ('PROD_001', ${QTY}, 0, ${QTY}, 0),
  ('PROD_002', ${QTY}, 0, ${QTY}, 0)
ON DUPLICATE KEY UPDATE
  available_quantity = VALUES(available_quantity),
  reserved_quantity = 0,
  total_quantity = VALUES(total_quantity);
"

echo "[2/2] Redis preheat via ${BASE}/api/stock/slots/preheat"
curl -fsS -X POST "${BASE}/api/stock/slots/preheat" \
  -H "Content-Type: application/json" \
  -d "{\"slotId\":\"bucket-1\",\"redisKey\":\"stock:bucket-1\",\"quantity\":${QTY},\"overwrite\":true}"
echo
curl -fsS -X POST "${BASE}/api/stock/slots/preheat" \
  -H "Content-Type: application/json" \
  -d "{\"slotId\":\"bucket-2\",\"redisKey\":\"stock:bucket-2\",\"quantity\":${QTY},\"overwrite\":true}"
echo
echo "Done."
