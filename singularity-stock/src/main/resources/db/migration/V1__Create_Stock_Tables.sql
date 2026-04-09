-- Flyway Migration: V1__Create_Stock_Tables.sql
-- 创建库存相关的初始表结构

-- 创建库存表
CREATE TABLE IF NOT EXISTS `stock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `product_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '商品ID',
  `available_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '可用库存数',
  `reserved_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '已占用库存数',
  `total_quantity` BIGINT NOT NULL COMMENT '总库存数',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '版本号，用于乐观锁',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 创建库存变更日志表
CREATE TABLE IF NOT EXISTS `stock_change_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `message_id` VARCHAR(128) NOT NULL UNIQUE COMMENT '消息ID，用于防重',
  `product_id` VARCHAR(64) NOT NULL COMMENT '商品ID',
  `change_quantity` BIGINT NOT NULL COMMENT '变更数量',
  `change_type` TINYINT NOT NULL COMMENT '变更类型: 1-扣库存, 2-还库存, 3-销售',
  `order_id` VARCHAR(64) COMMENT '关联的订单ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态: 0-待处理, 1-已处理, 2-处理失败',
  `remark` VARCHAR(512) COMMENT '处理结果说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_product_id` (`product_id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变更日志表';
