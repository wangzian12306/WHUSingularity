# 用户服务数据库 ER 图

```mermaid
erDiagram
    USER {
        bigint id PK "用户ID，自增主键"
        varchar username UK "用户名，唯一索引"
        varchar password "密码"
        varchar nickname "昵称"
        varchar role "角色：normal/admin"
        decimal balance "账户余额，默认0.00"
        datetime create_time "创建时间"
        datetime update_time "更新时间"
    }

    ORDER {
        bigint id PK "订单ID，自增主键"
        varchar order_id UK "订单号"
        varchar actor_id "操作用户ID，关联user表"
        varchar slot_id "商品/槽位ID"
        int status "订单状态"
        datetime create_time "创建时间"
    }

    STOCK {
        bigint id PK "库存ID，自增主键"
        varchar product_id UK "商品ID，唯一索引"
        bigint available_quantity "可用库存数"
        bigint reserved_quantity "已占用库存数"
        bigint total_quantity "总库存数"
        bigint version "版本号，用于乐观锁"
        datetime create_time "创建时间"
        datetime update_time "更新时间"
    }

    STOCK_CHANGE_LOG {
        bigint id PK "日志ID，自增主键"
        varchar message_id UK "消息ID，用于防重"
        varchar product_id "商品ID"
        bigint change_quantity "变更数量"
        tinyint change_type "变更类型：1-扣库存，2-还库存，3-销售"
        varchar order_id "关联的订单ID"
        tinyint status "处理状态：0-待处理，1-已处理，2-失败"
        varchar remark "处理结果说明"
        datetime create_time "创建时间"
        datetime update_time "更新时间"
    }

    USER ||--o{ ORDER : "创建"
    ORDER ||--o{ STOCK_CHANGE_LOG : "触发库存变更"
    STOCK ||--o{ STOCK_CHANGE_LOG : "记录变更历史"
```

## 实体说明

| 表名 | 说明 |
|------|------|
| USER | 用户表，存储用户基本信息和账户余额 |
| ORDER | 订单表，记录用户创建的订单 |
| STOCK | 库存表，管理商品库存信息 |
| STOCK_CHANGE_LOG | 库存变更日志表，记录所有库存变更事件 |

## 关系说明

- **USER** 1 -- * **ORDER**：一个用户可以创建多个订单
- **ORDER** 1 -- * **STOCK_CHANGE_LOG**：一个订单可以触发多条库存变更记录
- **STOCK** 1 -- * **STOCK_CHANGE_LOG**：一个商品的库存可以有多条变更历史
