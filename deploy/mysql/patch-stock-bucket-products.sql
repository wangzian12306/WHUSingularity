-- 将秒杀槽位对应商品在 singularity_stock.stock 中拉大库存（可与 tests/order-stress-test/refill-stock-buckets.* 配合）
-- 执行：docker exec -i singularity-mysql mysql -uroot -proot < deploy/mysql/patch-stock-bucket-products.sql

USE singularity_stock;
INSERT INTO stock (product_id, available_quantity, reserved_quantity, total_quantity, version)
VALUES
  ('PROD_001', 99999999, 0, 99999999, 0),
  ('PROD_002', 99999999, 0, 99999999, 0)
ON DUPLICATE KEY UPDATE
  available_quantity = VALUES(available_quantity),
  reserved_quantity = 0,
  total_quantity = VALUES(total_quantity);
