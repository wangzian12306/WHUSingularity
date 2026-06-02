# Singularity 核心框架部分设计

## 面向场景

优化对于某种 **可分 slot** 的高并发 resource （每个 slot 中有若干个资源可抢）进行以 actor 为单位的抢占。

## allocate 流程

开发者调用 Allocator.allocate 函数，其内部依次做如下操作：

- 调用 Registry 获取可用的 slot 列表，如果为空，则直接设置失败结果并返回（result 应该解释这个错误）。
- 调用 ShardPolicy 检查用户应该被分流到哪个 Slot（根据 Metadata），如果没有符合这个 actor 的 slot，则返回 null（result 应该解释这个错误）。
- 依次执行各个 interceptor（均以 around 语义执行，注意进入到 interceptor 阶段后必须都以 transactional 语义运行）。
- 最后调用业务 handler。

## 目标适配

在 Spring Boot/Spring Cloud 的环境下，能够在最终的业务 handler 中，实现如下流程：

- 先向 RocketMQ 发送一个 half message，表示预抢占了一个 slot 中的可用资源。
- 调用 Redis，原地减对应 slot 的库存，并添加 order 记录。
- 完全成功后，确认 RocketMQ 的 half message。
- 提供一个可供 RocketMQ 访问的回访端点，以让其可以在 half message 长时间不确定的情况下回调询问。该回访点会查询 Redis 以确定是否有对应的 Order。
