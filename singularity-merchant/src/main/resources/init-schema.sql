-- 商家表
CREATE TABLE IF NOT EXISTS merchant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    shop_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    address VARCHAR(512),
    description TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    avatar VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商户商品关联表（只存储商品ID数组）
CREATE TABLE IF NOT EXISTS merchant_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_product (merchant_id, product_id),
    INDEX idx_merchant_id (merchant_id)
);
