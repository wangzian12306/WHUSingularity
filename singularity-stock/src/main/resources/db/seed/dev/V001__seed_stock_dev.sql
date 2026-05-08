-- Dev/Test seed script (manual execution only)
-- Not managed by Flyway shared migration locations.
-- Purpose: initialize sample stock data in non-production environments.
--
-- Usage:
-- mysql -h 127.0.0.1 -uroot -proot singularity_stock < singularity-stock/src/main/resources/db/seed/dev/V001__seed_stock_dev.sql

INSERT INTO `stock` (`product_id`, `available_quantity`, `reserved_quantity`, `total_quantity`, `version`)
VALUES
  ('PROD_001', 99999999, 0, 99999999, 0),
  ('PROD_002', 99999999, 0, 99999999, 0),
  ('PROD_003', 2000, 0, 2000, 0),
  ('PROD_004', 100, 0, 100, 0),
  ('PROD_005', 300, 0, 300, 0),
  ('PROD_006', 800, 0, 800, 0),
  ('PROD_007', 50, 0, 50, 0),
  ('PROD_008', 1200, 0, 1200, 0)
ON DUPLICATE KEY UPDATE
  `available_quantity` = VALUES(`available_quantity`),
  `reserved_quantity` = VALUES(`reserved_quantity`),
  `total_quantity` = VALUES(`total_quantity`),
  `version` = VALUES(`version`);
