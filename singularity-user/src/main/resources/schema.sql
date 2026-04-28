-- 用户服务数据库初始化脚本

CREATE TABLE IF NOT EXISTS "user" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    nickname VARCHAR(64),
    role VARCHAR(32) DEFAULT 'normal',
    balance DECIMAL(15, 2) DEFAULT 0.00,
    shop_name VARCHAR(128),
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    address VARCHAR(256),
    description TEXT,
    status INT DEFAULT 1,
    avatar VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_username ON "user"(username);
CREATE INDEX IF NOT EXISTS idx_role ON "user"(role);

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    description TEXT,
    price DECIMAL(15, 2) NOT NULL,
    original_price DECIMAL(15, 2),
    image_url VARCHAR(512),
    category VARCHAR(64),
    status INT DEFAULT 0,
    sort_order INT DEFAULT 0,
    is_hot INT DEFAULT 0,
    is_recommend INT DEFAULT 0,
    sales_count BIGINT DEFAULT 0,
    view_count BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_merchant_id ON product(merchant_id);
CREATE INDEX IF NOT EXISTS idx_product_status ON product(status);
CREATE INDEX IF NOT EXISTS idx_product_category ON product(category);

-- 商品库存表
CREATE TABLE IF NOT EXISTS product_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    total_quantity BIGINT DEFAULT 0,
    available_quantity BIGINT DEFAULT 0,
    locked_quantity BIGINT DEFAULT 0,
    sold_quantity BIGINT DEFAULT 0,
    warning_quantity BIGINT DEFAULT 10,
    version BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inventory_product_id ON product_inventory(product_id);

-- 库存变动记录表
CREATE TABLE IF NOT EXISTS inventory_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    change_type INT NOT NULL,
    change_quantity BIGINT NOT NULL,
    before_quantity BIGINT NOT NULL,
    after_quantity BIGINT NOT NULL,
    order_id VARCHAR(64),
    remark VARCHAR(256),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_log_product_id ON inventory_change_log(product_id);
CREATE INDEX IF NOT EXISTS idx_log_merchant_id ON inventory_change_log(merchant_id);
CREATE INDEX IF NOT EXISTS idx_log_order_id ON inventory_change_log(order_id);
