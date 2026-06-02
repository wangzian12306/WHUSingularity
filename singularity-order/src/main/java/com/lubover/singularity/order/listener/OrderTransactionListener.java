package com.lubover.singularity.order.listener;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 事务消息适配器，仅用于 crash recovery（broker 回查）。
 *
 * <p>
 * {@link #executeLocalTransaction}：永远返回 UNKNOW。半消息发送和本地事务执行、
 * commit/rollback 由 {@code OrderServiceImpl} 在主线程显式三步完成。
 *
 * <p>
 * {@link #checkLocalTransaction}：主线程崩溃/超时时 broker 回查此接口。
 * 检查 Redis 中是否已有对应 order 记录来确定事务最终状态。
 */
@Component
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTransactionListener.class);

    private final StringRedisTemplate redisTemplate;

    public OrderTransactionListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        return RocketMQLocalTransactionState.UNKNOWN;
    }

    /**
     * broker 回查接口：检查 Redis 中是否已有对应的 order 记录。
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String orderId = (String) msg.getHeaders().get("orderId");
        Boolean exists = redisTemplate.hasKey("order:" + orderId);
        RocketMQLocalTransactionState state = Boolean.TRUE.equals(exists)
                ? RocketMQLocalTransactionState.COMMIT
                : RocketMQLocalTransactionState.ROLLBACK;
        log.info("check tx callback: orderId={} -> {}", orderId, state);
        return state;
    }
}
