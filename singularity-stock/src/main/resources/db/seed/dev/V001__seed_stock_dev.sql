-- Dev/Test seed script (manual execution only)
-- Not managed by Flyway shared migration locations.
-- Purpose: initialize sample stock data in non-production environments.

INSERT INTO `stock` (product_id, available_quantity, reserved_quantity, total_quantity, version)
VALUES
  ('PROD_001', 1000, 0, 1000, 0),
  ('PROD_002', 500, 0, 500, 0),
  ('PROD_003', 2000, 0, 2000, 0),
  ('PROD_004', 100, 0, 100, 0),
  ('PROD_005', 300, 0, 300, 0);
