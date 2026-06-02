-- 用户服务数据库初始化脚本

CREATE DATABASE IF NOT EXISTS singularity_user DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE singularity_user;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(256) NOT NULL COMMENT '密码',
    nickname VARCHAR(64) COMMENT '昵称',
    role VARCHAR(32) DEFAULT 'normal' COMMENT '角色: normal/admin',
    balance DECIMAL(15, 2) DEFAULT 0.00 COMMENT '账户余额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
