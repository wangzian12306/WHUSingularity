package com.lubover.singularity.order.service.impl;

import com.lubover.singularity.api.*;
import com.lubover.singularity.api.impl.DefaultAllocator;
import com.lubover.singularity.order.registry.EurekaSlotRegistry;
import com.lubover.singularity.order.service.OrderService;
import com.lubover.singularity.order.tx.OrderLocalTransaction;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final Allocator allocator;

    @Autowired
    public OrderServiceImpl(
            Registry registry,
            ShardPolicy shardPolicy,
            List<Interceptor> interceptors,
            RocketMQTemplate rocketMQTemplate,
            StringRedisTemplate redisTemplate,
            EurekaSlotRegistry eurekaSlotRegistry) {
        this.allocator = new DefaultAllocator(
                registry,
                shardPolicy,
                interceptors != null ? interceptors : Collections.emptyList(),
                handler(rocketMQTemplate, redisTemplate, eurekaSlotRegistry));
    }

    @Override
    public Result snagOrder(Actor actor) {
        return allocator.allocate(actor);
    }

    private Interceptor handler(RocketMQTemplate rocketMQTemplate,
            StringRedisTemplate redisTemplate,
            EurekaSlotRegistry eurekaSlotRegistry) {
        return context -> {
            Actor actor = context.getCurrActor();
            Slot slot = context.getCurrSlot();
            String orderId = UUID.randomUUID().toString();
            String redisStockKey = (String) slot.getMetadata().get("redisStockKey");

            // Step 1: 构造本地事务对象（Redis 减库存 + 写 order），作为 arg 传入
            // RocketMQ 在半消息确认后会回调 OrderTransactionListener.executeLocalTransaction，
            // 后者直接委托给该对象执行，不再包含任何业务逻辑
            OrderLocalTransaction localTx = new OrderLocalTransaction(
                    orderId, actor.getId(), slot.getId(),
                    redisStockKey, redisTemplate, eurekaSlotRegistry);

            // Step 2: 发送 RocketMQ 半消息，触发本地事务
            // sendMessageInTransaction 同步等待 executeLocalTransaction 执行完毕再返回
            Message<String> msg = MessageBuilder.withPayload(orderId)
                    .setHeader("orderId", orderId)
                    .build();

            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(
                    "order-topic", msg, localTx);

            // Step 3: 本地事务（Redis 减库存 + 写 order）与半消息提交均已完成
            // checkLocalTransaction 接口供 broker 在超时时回查 Redis order 是否存在
            if (sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
                context.setResult(new Result(true, orderId));
            } else {
                context.setResult(new Result(false, "stock insufficient or tx failed for slot: " + slot.getId()));
            }
        };
    }
}
