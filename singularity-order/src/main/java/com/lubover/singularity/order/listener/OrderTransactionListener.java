package com.lubover.singularity.order.listener;

import com.lubover.singularity.order.tx.OrderLocalTransaction;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 事务消息适配器，不包含任何业务逻辑。
 *
 * <p>
 * {@link #executeLocalTransaction}：半消息发送成功后，broker 回调此方法。
 * 直接委托给由 handler 构造并通过 arg 传入的 {@link OrderLocalTransaction} 执行
 * （Redis 减库存 + 写 order）。
 *
 * <p>
 * {@link #checkLocalTransaction}：broker 在半消息长时间未确认时回查此接口。
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
        if (!(arg instanceof OrderLocalTransaction localTx)) {
            log.error("unexpected arg type: {}", arg == null ? "null" : arg.getClass());
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        boolean ok = localTx.execute();
        return ok ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
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
