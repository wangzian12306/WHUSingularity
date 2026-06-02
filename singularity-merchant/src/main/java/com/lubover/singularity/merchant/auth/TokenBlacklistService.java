package com.lubover.singularity.merchant.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "merchant:token:blacklist:";
    private static final long TOKEN_EXPIRATION_HOURS = 24;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    public void blacklistToken(String token) {
        if (redisTemplate == null) {
            log.warn("Redis not available, token blacklist operation skipped");
            return;
        }
        try {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.opsForValue().set(key, "1", TOKEN_EXPIRATION_HOURS, TimeUnit.HOURS);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed, token blacklist operation skipped: {}", e.getMessage());
        }
    }

    public boolean isTokenBlacklisted(String token) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String key = BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed, assuming token is not blacklisted: {}", e.getMessage());
            return false;
        }
    }
}
