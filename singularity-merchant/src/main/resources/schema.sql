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

-- 商品表
CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID，自增主键',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `product_name` VARCHAR(256) NOT NULL COMMENT '商品名称',
    `description` TEXT COMMENT '商品描述',
    `price` DECIMAL(15, 2) NOT NULL COMMENT '商品价格',
    `original_price` DECIMAL(15, 2) COMMENT '原价',
    `image_url` VARCHAR(1024) COMMENT '商品图片URL（多个用逗号分隔）',
    `category` VARCHAR(128) COMMENT '商品分类',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-下架，1-上架，2-售罄',
    `sort_order` INT DEFAULT 0 COMMENT '排序权重',
    `is_hot` TINYINT DEFAULT 0 COMMENT '是否热门：0-否，1-是',
    `is_recommend` TINYINT DEFAULT 0 COMMENT '是否推荐：0-否，1-是',
    `sales_count` BIGINT DEFAULT 0 COMMENT '销量',
    `view_count` BIGINT DEFAULT 0 COMMENT '浏览量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_merchant_id` (`merchant_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_category` (`category`),
    INDEX `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 商品库存表（独立表，方便后续扩展）
CREATE TABLE IF NOT EXISTS `product_inventory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存ID，自增主键',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `total_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '总库存',
    `available_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '可用库存',
    `locked_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '锁定库存',
    `sold_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '已售数量',
    `warning_quantity` BIGINT DEFAULT 10 COMMENT '预警库存数量',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品库存表';

-- 库存变更日志表
CREATE TABLE IF NOT EXISTS `inventory_change_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID，自增主键',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `change_type` TINYINT NOT NULL COMMENT '变更类型：1-增加库存，2-扣减库存，3-释放库存，4-人工调整',
    `change_quantity` BIGINT NOT NULL COMMENT '变更数量（正数增加，负数减少）',
    `before_quantity` BIGINT NOT NULL COMMENT '变更前可用库存',
    `after_quantity` BIGINT NOT NULL COMMENT '变更后可用库存',
    `order_id` VARCHAR(128) COMMENT '关联订单ID',
    `remark` VARCHAR(512) COMMENT '备注',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(64) COMMENT '操作人姓名',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_product_id` (`product_id`),
    INDEX `idx_merchant_id` (`merchant_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变更日志表';
