package com.lubover.singularity.orderbaseline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisStockGate {

    private static final Logger log = LoggerFactory.getLogger(RedisStockGate.class);

    private static final String DECREMENT_LUA = """
            local current = redis.call('GET', KEYS[1])
            if not current then
                return -2
            end

            local stock = tonumber(current)
            local amount = tonumber(ARGV[1])

            if stock < amount then
                return -1
            end

            return redis.call('DECRBY', KEYS[1], amount)
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> decrementScript;

    public RedisStockGate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.decrementScript = new DefaultRedisScript<>(DECREMENT_LUA, Long.class);
    }

    public Long tryDecrement(String stockKey, long quantity) {
        Long result = redisTemplate.execute(decrementScript, List.of(stockKey), String.valueOf(quantity));
        if (result == null) {
            log.error("Redis Lua script returned null for key={}", stockKey);
            return -2L;
        }
        return result;
    }

    public Long compensate(String stockKey, long quantity) {
        Long current = redisTemplate.opsForValue().increment(stockKey, quantity);
        log.warn("Redis compensation: key={} incremented by {}, current={}", stockKey, quantity, current);
        return current;
    }

    public String getStock(String stockKey) {
        String value = redisTemplate.opsForValue().get(stockKey);
        return value != null ? value : "0";
    }

    public void setStock(String stockKey, long quantity) {
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity));
        log.info("Redis stock set: key={} quantity={}", stockKey, quantity);
    }
}
