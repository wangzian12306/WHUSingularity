# 将 PROD_001/PROD_002 在 MySQL(singularity_stock) 中设为大量库存，并对 Redis bucket 执行覆盖预热（走库存服务 API）。
# 默认经网关 8080；若直连 stock 可设 -StockBaseUrl http://localhost:8082
param(
    [long]$Quantity = 1000000,
    [long]$RedisQuantity = 1000000,
    [string]$MysqlContainer = "singularity-mysql",
    [string]$StockBaseUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"

$sql = @"
USE singularity_stock;
INSERT INTO stock (product_id, available_quantity, reserved_quantity, total_quantity, version)
VALUES
  ('PROD_001', $Quantity, 0, $Quantity, 0),
  ('PROD_002', $Quantity, 0, $Quantity, 0)
ON DUPLICATE KEY UPDATE
  available_quantity = VALUES(available_quantity),
  reserved_quantity = 0,
  total_quantity = VALUES(total_quantity);
"@

Write-Host "[1/2] Updating MySQL stock rows PROD_001, PROD_002 -> $Quantity"
docker exec $MysqlContainer mysql -uroot -proot -e $sql

$body1 = @{ slotId = "bucket-1"; redisKey = "stock:bucket-1"; quantity = $RedisQuantity; overwrite = $true } | ConvertTo-Json
$body2 = @{ slotId = "bucket-2"; redisKey = "stock:bucket-2"; quantity = $RedisQuantity; overwrite = $true } | ConvertTo-Json

Write-Host "[2/2] Preheating Redis (qty=$RedisQuantity) via $StockBaseUrl/api/stock/slots/preheat"
Invoke-RestMethod -Method Post -Uri "$StockBaseUrl/api/stock/slots/preheat" -ContentType "application/json" -Body $body1 | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$StockBaseUrl/api/stock/slots/preheat" -ContentType "application/json" -Body $body2 | ConvertTo-Json

Write-Host "Done."
