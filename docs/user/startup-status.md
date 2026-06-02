# 当前启动状态（2026-04-09，最终回归后）

## 1) 本机环境状态

- Java: `openjdk 21.0.10`（Homebrew）
- Maven: `3.9.14`
- Docker/OrbStack: 可用

## 2) 依赖服务状态（Docker）

当前容器均已运行：

- `singularity-mysql`（`3306`）
- `singularity-redis`（`6379`）
- `singularity-rmq-namesrv`（`9876`）
- `singularity-rmq-broker`（`10911` / `10909`）

## 3) 数据库初始化状态

MySQL 中已确认：

- 数据库 `singularity_user` 已存在，表 `user` 已存在
- 数据库 `singularity_order` 已存在，表 `t_order` 已存在

## 4) 项目构建状态

在仓库根目录执行：

```bash
mvn clean package
```

结果：**成功**（EXIT_CODE=0）。

补充（最终回归）：

- 本次执行中先发现 `singularity-user` 两个启动阻断并已修复：
	- `application.yml` 的 `auth.blacklist.prefix` 需要引号包裹；
	- `JwtProvider` 在存在测试构造函数时需显式标注运行时构造器注入。
- 修复后再次执行 `mvn clean package`，结果仍为 **成功**。

补充：

- 已统一依赖版本为 Spring Boot `3.5.0` + Spring Cloud `2025.0.0`
- 已补齐可执行 jar 打包（spring-boot-maven-plugin repackage）
- `singularity-eureka/user/order` 三个模块产物均包含 `Main-Class` 与 `Start-Class`

补充（当前仍待治理项）：

- `singularity-eureka/user/order` 模块的 `spring-boot-maven-plugin` 仍存在未显式声明版本告警（当前不阻断构建）。
- `mvn -pl singularity-user test` 过程中出现 Mockito 动态 agent 自附加告警（当前不阻断测试）。
- 上述两项不影响本轮“可启动/可测试”结论，但建议在后续工程治理阶段尽快收口。

## 5) 应用启动状态

按 `CLAUDE.md` 中方式启动并做冒烟探测，当前结论如下：

- `java -jar singularity-eureka/target/singularity-eureka-1.0-SNAPSHOT.jar` **成功**（8761）
- `java -jar singularity-user/target/singularity-user-1.0-SNAPSHOT.jar` **成功**（8090，已注册到 Eureka）
- `java -jar singularity-order/target/singularity-order-1.0-SNAPSHOT.jar` **成功**（8081，已注册到 Eureka）

HTTP 冒烟：

- `GET http://localhost:8761/` -> `200`
- `GET http://localhost:8090/api/user/me`（无 token）-> `401`
- `GET http://localhost:8090/api/user/admin/ping`（无 token）-> `401`
- `GET http://localhost:8081/` -> `404`（表示 order web 容器已启动，根路径无映射）

本轮新增修复：

- 在 `singularity-order` 增加运行时 `Registry` 与 `ShardPolicy` Bean（静态 slot + 哈希分片），消除 `Registry` 注入缺口。

## 6) 当前结论

- **环境与中间件已就绪**
- **代码可编译并生成可执行 jar**
- **eureka / user / order 三服务均可启动并注册到 Eureka**
- **冒烟探测通过：核心端口可达，user 鉴权语义符合预期**

## 7) 后续最小实现建议

下一步建议聚焦功能联调而非启动修复：

- 在 order 侧补齐与 user 对齐的 Auth Layer（401/403 语义与身份注入）。
- 完成 S10（冒充他人下单）端到端验收。
