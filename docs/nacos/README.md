# Nacos 配置指南

## 1. Nacos 安装与启动

### 1.1 下载 Nacos

从 [Nacos Release](https://github.com/alibaba/nacos/releases) 下载最新稳定版本（推荐 2.4.x）。

### 1.2 启动 Nacos（单机模式）

```bash
# Linux/Mac
cd nacos/bin
sh startup.sh -m standalone

# Windows
cd nacos\bin
startup.cmd -m standalone
```

### 1.3 访问 Nacos 控制台

- 地址：http://localhost:8848/nacos
- 默认用户名/密码：nacos/nacos

---

## 2. Nacos 配置中心设置

所有服务的业务配置都迁移到 Nacos 配置中心，需要在 Nacos 控制台手动创建以下配置文件。

### 2.1 singularity-order 配置

**Data ID**: `singularity-order.yaml`  
**Group**: `DEFAULT_GROUP`  
**配置格式**: `YAML`  
**配置内容**:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

rocketmq:
  name-server: localhost:9876
  producer:
    group: order-producer-group
  consumer:
    order:
      topic: order-topic
      group: order-consumer-group

# Slot（Redis 库存桶）配置
# 每个 slot 对应一个 Redis 库存 key，id 用于分槽路由
singularity:
  order:
    slots:
      - id: bucket-1
        redis-key: stock:bucket-1
        product-id: PROD_001
      - id: bucket-2
        redis-key: stock:bucket-2
        product-id: PROD_002
```

---

### 2.2 singularity-user 配置

**Data ID**: `singularity-user.yaml`  
**Group**: `DEFAULT_GROUP`  
**配置格式**: `YAML`  
**配置内容**:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

auth:
  jwt:
    secret: ${JWT_SECRET:dev-secret-key}
    expire-seconds: 7200
  blacklist:
    prefix: "auth:blacklist:"
```

---

### 2.3 singularity-stock 配置

**Data ID**: `singularity-stock.yaml`  
**Group**: `DEFAULT_GROUP`  
**配置格式**: `YAML`  
**配置内容**:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

rocketmq:
  name-server: localhost:9876
  consumer:
    stock:
      topic: stock-topic
      group: stock-consumer-group
    order:
      topic: order-topic
      group: stock-order-consumer-group
```

---

### 2.4 singularity-product 配置

**Data ID**: `singularity-product.yaml`
**Group**: `DEFAULT_GROUP`
**配置格式**: `YAML`
**配置内容**（可选，仅当需要覆盖默认 Redis 缓存配置时创建）:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

### 2.5 singularity-gateway 配置

**Data ID**: `singularity-gateway.yaml`
**Group**: `DEFAULT_GROUP`
**配置格式**: `YAML`
**配置内容**（可选，仅当需要覆盖默认路由配置时创建）:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-route
          uri: lb://singularity-user
          predicates:
            - Path=/api/user/**
        - id: order-route
          uri: lb://singularity-order
          predicates:
            - Path=/api/order/**
        - id: stock-route
          uri: lb://singularity-stock
          predicates:
            - Path=/api/stock/**
        - id: product-route
          uri: lb://singularity-product
          predicates:
            - Path=/api/product/**
```

### 2.6 singularity-scaler 配置

**Data ID**: `singularity-scaler.yaml`
**Group**: `DEFAULT_GROUP`
**配置格式**: `YAML`
**配置内容**（可选，覆盖默认阈值或策略时创建）:

```yaml
scaler:
  interval-seconds: 15
  cooldown-seconds: 120
  services:
    - name: singularity-order
      base-port: 8081
      port-step: 2
      min-instances: 1
      max-instances: 5
      qps-scale-up-threshold: 100
      qps-scale-down-threshold: 20
    - name: singularity-user
      base-port: 8090
      port-step: 1
      min-instances: 1
      max-instances: 5
      qps-scale-up-threshold: 100
      qps-scale-down-threshold: 20
    - name: singularity-stock
      base-port: 8082
      port-step: 2
      min-instances: 1
      max-instances: 5
      qps-scale-up-threshold: 100
      qps-scale-down-threshold: 20
    - name: singularity-product
      base-port: 8087
      port-step: 1
      min-instances: 1
      max-instances: 5
      qps-scale-up-threshold: 100
      qps-scale-down-threshold: 20
```

> **注意**: `singularity-merchant` 默认不启用 Nacos（`discovery.enabled: false`, `config.enabled: false`），无需创建 Nacos 配置。如需启用，切换到 local profile 并取消注释 bootstrap.yml 中的 Nacos 配置。

---

## 3. 配置创建步骤

1. 登录 Nacos 控制台：http://localhost:8848/nacos
2. 点击左侧菜单「配置管理」→「配置列表」
3. 点击右上角「+」按钮创建配置
4. 填写 Data ID、Group、配置格式，粘贴配置内容
5. 点击「发布」按钮

---

## 4. 验证配置

启动服务后，观察日志中是否有以下提示：

```
Located property source: [BootstrapPropertySource {name='bootstrapProperties-singularity-order.yaml,DEFAULT_GROUP'}]
```

表示服务成功从 Nacos 加载配置。

---

## 5. 动态刷新配置

修改 Nacos 配置后，服务会自动刷新配置（bootstrap.yml 中已开启 `refresh-enabled: true`），无需重启。

---

## 6. 注意事项

1. **命名空间**：本项目使用 `public` 命名空间，生产环境建议使用独立命名空间（如 `prod`）
2. **配置格式**：必须选择 `YAML` 格式，且 Data ID 后缀必须是 `.yaml`
3. **配置优先级**：Nacos 配置优先级高于 application.yml 中的配置
4. **环境隔离**：可以通过不同的 Group 或命名空间实现多环境配置隔离（dev/test/prod）
