CREATE TABLE IF NOT EXISTS `order` (
  `order_id` VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '订单ID（UUID）',
  `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
  `slot_id` VARCHAR(64) NOT NULL COMMENT 'Slot ID',
  `product_id` VARCHAR(64) NOT NULL COMMENT '商品ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '订单状态: CREATED-已创建, PAID-已支付, CANCELLED-已取消',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_product_id` (`product_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表 - baseline';
