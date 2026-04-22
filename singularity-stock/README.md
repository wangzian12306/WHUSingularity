# singularity-stock 库存微服务

独立的库存管理微服务，实现削峰填谷的MQ异步落库功能。

## 模块概述

- **端口**: 8082
- **数据库**: `singularity_stock`
- **服务**: 库存查询、库存扣减、库存还原、库存变更日志记录
- **MQ消费**: 消费 `stock-topic` 主题
- **消费组**: `stock-consumer-group`

## 核心特性

### 1. 削峰填谷异步落库
- MQ消息到达时，先写入 `stock_change_log` 日志表（快速应答）
- 然后在事务中执行库存操作（原子性保证）
- 通过日志表记录实现幂等性（防重）

### 2. 库存防超卖
- 使用 **乐观锁** + **版本号** 防止并发扣库存导致的超卖
- 检查可用库存是否充足
- 原子性更新库存表

### 3. 幂等性设计
- 每条MQ消息有唯一的 `messageId`
- 消费前检查 `stock_change_log` 表中是否已处理该消息ID
- 若已处理，直接返回之前的处理结果

### 4. 库存分类管理
- `available_quantity`: 可用库存
- `reserved_quantity`: 已占用库存（待支付/已支付待发货）
- `total_quantity`: 总库存

## 数据库结构

### stock 表
```sql
- id: 主键
- product_id: 商品ID（唯一）
- available_quantity: 可用库存数
- reserved_quantity: 已占用库存数
- total_quantity: 总库存数
- version: 版本号（乐观锁）
- create_time: 创建时间
- update_time: 更新时间
```

### stock_change_log 表
```sql
- id: 主键
- message_id: MQ消息ID（唯一，防重）
- product_id: 商品ID
- change_quantity: 变更数量
- change_type: 变更类型（1-扣库存、2-还库存、3-销售）
- order_id: 订单ID
- status: 处理状态（0-待处理、1-已处理、2-处理失败）
- remark: 处理备注
- create_time: 创建时间
- update_time: 更新时间
```

## MQ消息格式

### 订阅主题
- **Topic**: `stock-topic`
- **ConsumerGroup**: `stock-consumer-group`
- **消费模式**: 顺序消费（ORDERLY）

### 消息内容格式
```
productId|quantity|changeType|orderId|messageId

示例：
PROD_001|100|1|ORD_20240101_001|MSG_UUID_001
```

**参数说明**:
- `productId`: 商品ID
- `quantity`: 变更数量
- `changeType`: 1(扣库存) 2(还库存) 3(销售)
- `orderId`: 订单ID
- `messageId`: 唯一消息ID，用于幂等性

## 使用示例

### 1. 初始化库存
```java
stockService.initializeStock("PROD_001", 1000L);
```

### 2. 查询库存
```java
Stock stock = stockService.getStock("PROD_001");
System.out.println("可用库存: " + stock.getAvailableQuantity());
```

### 3. 扣库存（通过MQ）
```
// 生产者发送消息到 stock-topic
PROD_001|100|1|ORD_20240101_001|MSG_UUID_001
```

### 4. 还库存（通过MQ）
```
// 生产者发送消息到 stock-topic
PROD_001|50|2|ORD_20240101_001|MSG_UUID_002
```

### 5. 查询变更日志
```java
StockChangeLog log = stockService.getChangeLog("MSG_UUID_001");
System.out.println("处理状态: " + log.getStatus()); // 0=待处理, 1=已处理, 2=失败
```

### 6. 预热 Redis slot（新增接口）

接口路径：`POST /api/stock/slots/preheat`

请求体：
```json
{
   "slotId": "A",
   "redisKey": "stock:bucket-1",
   "quantity": 1000,
   "overwrite": false
}
```

参数说明：
- `slotId`: slot标识
- `redisKey`: 可选，显式指定要预热的 Redis key。未提供时，服务将优先按配置 `singularity.order.slots` 解析对应 key；若仍未命中则回退到 `stock:{slotId}`
- `quantity`: 预热库存，必须大于 0
- `overwrite`: 
   - `true` 覆盖已有值
   - `false` 仅在 key 不存在时写入

返回示例：
```json
{
   "redisKey": "stock:A",
   "written": true,
   "currentValue": "1000",
   "message": "slot预热成功"
}
```

## 配置说明

### application.yml 关键配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/singularity_stock?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

rocketmq:
  name-server: localhost:9876
  consumer:
    group: stock-consumer-group
```

## 启动步骤

1. **创建数据库**
```sql
CREATE DATABASE singularity_stock CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **环境要求**
   - Java 21
   - MySQL 5.7+
   - RocketMQ (可选,如果需要MQ消费)

3. **启动服务**
```bash
# 在项目根目录执行
mvn clean package
java -jar singularity-stock/target/singularity-stock-1.0-SNAPSHOT.jar
```

4. **验证服务**
   - 服务应该在 `http://localhost:8082` 启动
   - 数据库表应该自动创建（Flyway Migration）

## 与 order 服务的交互

1. **Order服务**: 负责订单创建、支付确认等业务逻辑
2. **Stock服务**: 负责库存管理、削峰填谷
3. **交互流程**:
   ```
   Order服务创建订单 
   → Order服务发送库存扣减消息到 stock-topic
   → Stock服务消费消息 
   → Stock服务写入 stock_change_log
   → Stock服务执行库存扣减
   → Stock服务回复消息处理结果
   ```

## 性能优化

1. **索引设计**: 
   - `message_id` 唯一索引用于快速防重检查
   - `product_id` 索引用于库存查询
   - `status` 索引用于日志查询

2. **乐观锁**:
   - 避免行级锁竞争
   - 高并发场景下更高效

3. **异步落库**:
   - MQ消息快速写入日志表
   - 防止消息堆积

## 故障处理

### 库存扣减失败
- 检查 `stock_change_log` 表中 `status=2` 的记录
- 查看 `remark` 字段获取失败原因
- 可通过重新发送MQ消息进行重试

### 库存不足
- 消息处理结果: `status=2, remark='库存不足'`
- 需要在Order服务中处理库存不足的业务逻辑

## 监控指标

建议监控以下指标:
- `stock_change_log` 表中 `status=0` 的记录数（待处理消息）
- `stock_change_log` 表中 `status=2` 的记录数（失败消息）
- 库存表的版本号变化频率（乐观锁冲突频率）

## 常见问题

**Q: 为什么Stock服务是独立的微服务?**
A: 为了实现微服务的独立部署和扩展，防止Order服务过载，实现削峰填谷。

**Q: 如何保证库存扣减的准确性?**
A: 通过以下机制保证:
1. 乐观锁 + 版本号（防并发超卖）
2. MQ消息 + 幂等操作（防重复扣减）
3. 日志记录 + 防重检查（可查证）

**Q: 消息处理失败了怎么办?**
A: 
1. MQ会自动重试同一条消息
2. 日志表中记录了失败原因
3. 管理员可根据 `status=2` 的记录进行人工处理

## 相关文档

- [RocketMQ Spring Boot 文档](https://github.com/apache/rocketmq-spring)
- [Flyway 数据库迁移文档](https://flywaydb.org/)
- [MyBatis 文档](https://mybatis.org/)
