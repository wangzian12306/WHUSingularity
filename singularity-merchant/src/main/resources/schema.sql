-- ========================================
-- 商家模块数据库表结构
-- ========================================

-- 商家表
CREATE TABLE IF NOT EXISTS `merchant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商家ID，自增主键',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名，唯一索引',
    `password` VARCHAR(256) NOT NULL COMMENT '密码（BCrypt加密）',
    `shop_name` VARCHAR(128) NOT NULL COMMENT '店铺名称',
    `contact_name` VARCHAR(64) COMMENT '联系人姓名',
    `contact_phone` VARCHAR(32) COMMENT '联系电话',
    `address` VARCHAR(512) COMMENT '店铺地址',
    `description` TEXT COMMENT '店铺描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常，2-审核中',
    `avatar` VARCHAR(512) COMMENT '店铺头像URL',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_username` (`username`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

CREATE TABLE IF NOT EXISTS `merchant_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `product_id` VARCHAR(64) NOT NULL COMMENT '商品ID（来自product服务）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
    `sort_order` INT DEFAULT 0 COMMENT '排序权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_product` (`merchant_id`, `product_id`),
    INDEX `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户商品关联表';
