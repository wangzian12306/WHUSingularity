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

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    product_name VARCHAR(256) NOT NULL,
    description TEXT,
    price DECIMAL(15, 2) NOT NULL,
    original_price DECIMAL(15, 2),
    image_url VARCHAR(1024),
    category VARCHAR(128),
    status TINYINT NOT NULL DEFAULT 0,
    sort_order INT DEFAULT 0,
    is_hot TINYINT DEFAULT 0,
    is_recommend TINYINT DEFAULT 0,
    sales_count BIGINT DEFAULT 0,
    view_count BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商品库存表
CREATE TABLE IF NOT EXISTS product_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    total_quantity BIGINT NOT NULL DEFAULT 0,
    available_quantity BIGINT NOT NULL DEFAULT 0,
    locked_quantity BIGINT NOT NULL DEFAULT 0,
    sold_quantity BIGINT NOT NULL DEFAULT 0,
    warning_quantity BIGINT DEFAULT 10,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 库存变更日志表
CREATE TABLE IF NOT EXISTS inventory_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    change_type TINYINT NOT NULL,
    change_quantity BIGINT NOT NULL,
    before_quantity BIGINT NOT NULL,
    after_quantity BIGINT NOT NULL,
    order_id VARCHAR(128),
    remark VARCHAR(512),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
