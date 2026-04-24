package com.lubover.singularity.merchant.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "merchant:token:blacklist:";
    private static final long TOKEN_EXPIRATION_HOURS = 24;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void blacklistToken(String token) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", TOKEN_EXPIRATION_HOURS, TimeUnit.HOURS);
    }

    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
