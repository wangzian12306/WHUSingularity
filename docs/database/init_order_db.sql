-- 订单服务数据库初始化脚本

CREATE DATABASE IF NOT EXISTS singularity_order DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE singularity_order;

-- 表结构由 Flyway 自动创建，无需手动执行
-- 详见: singularity-order/src/main/resources/db/migration/V1__Create_Order_Tables.sql
