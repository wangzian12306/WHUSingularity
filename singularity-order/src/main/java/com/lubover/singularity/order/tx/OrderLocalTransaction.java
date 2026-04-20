package com.lubover.singularity.order.tx;

import com.lubover.singularity.order.registry.EurekaSlotRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 封装一次抢单请求的本地事务逻辑，由 handler 构造并通过 arg 传给 RocketMQ 事务监听器执行。
 *
 * <p>
 * 执行顺序：
 * <ol>
 * <li>用 Lua 原子减对应 bucket 的 Redis 库存</li>
 * <li>库存不足时直接返回 false，不做任何后续写入</li>
 * <li>库存恰好减到 0 时，通知 Registry 将该 slot 标记为售罄</li>
 * <li>在同一个 Lua 原子执行里写入 order 记录（唯一信源，供 broker 回查及 stock 服务消费）</li>
 * </ol>
 */
public class OrderLocalTransaction {

    private static final String LUA_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then
                return -1
            end

            local remaining = redis.call('DECR', KEYS[1])
            if remaining < 0 then
                redis.call('INCR', KEYS[1])
                return -1
            end

            redis.call('HSET', KEYS[2],
                'orderId', ARGV[1],
                'actorId', ARGV[2],
                'slotId', ARGV[3],
                'status', ARGV[4],
                'createTime', ARGV[5])

            return remaining
            """;

    private static final Logger log = LoggerFactory.getLogger(OrderLocalTransaction.class);

    private final String orderId;
    private final String actorId;
    private final String slotId;
    private final String redisStockKey;
    private final StringRedisTemplate redisTemplate;
    private final EurekaSlotRegistry registry;

    public OrderLocalTransaction(String orderId, String actorId, String slotId,
            String redisStockKey,
            StringRedisTemplate redisTemplate,
            EurekaSlotRegistry registry) {
        this.orderId = orderId;
        this.actorId = actorId;
        this.slotId = slotId;
        this.redisStockKey = redisStockKey;
        this.redisTemplate = redisTemplate;
        this.registry = registry;
    }

    public String getOrderId() {
        return orderId;
    }

    /**
     * 执行本地事务
     *
     * @return true 表示成功（后续由监听器 COMMIT 半消息），false 表示失败（ROLLBACK）
     */
    public boolean execute() {
        try {
            String orderKey = "order:" + orderId;

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

            Long remaining = redisTemplate.execute(
                    script,
                    List.of(redisStockKey, orderKey),
                    orderId,
                    actorId,
                    slotId,
                    "1",
                    LocalDateTime.now().toString());

            if (remaining == null || remaining < 0) {
                log.warn("stock exhausted or lua failed: slot={} key={}", slotId, redisStockKey);
                return false;
            }

            // Step 2: 库存刚好减到 0，标记该 slot 售罄
            if (remaining == 0) {
                registry.markEmpty(slotId);
            }

            log.info("local tx ok: orderId={} slot={} remaining={}", orderId, slotId, remaining);

            return true;

        } catch (Exception e) {
            log.error("local tx error: orderId={}", orderId, e);

            return false;
        }
    }
}
