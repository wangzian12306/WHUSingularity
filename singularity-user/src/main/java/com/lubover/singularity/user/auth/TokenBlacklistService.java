package com.lubover.singularity.user.auth;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public TokenBlacklistService(
            StringRedisTemplate redisTemplate,
            @Value("${auth.blacklist.prefix:auth:blacklist:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    public void blacklist(String jti, long expEpochSeconds) {
        long ttl = expEpochSeconds - Instant.now().getEpochSecond();
        if (ttl <= 0) {
            ttl = 1;
        }
        redisTemplate.opsForValue().set(key(jti), "1", ttl, TimeUnit.SECONDS);
    }

    private String key(String jti) {
        return keyPrefix + jti;
    }
}
