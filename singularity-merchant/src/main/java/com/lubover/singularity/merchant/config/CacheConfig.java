package com.lubover.singularity.merchant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MERCHANT_CACHE_NAME = "merchant";
    public static final String PRODUCT_CACHE_NAME = "product";
    public static final String INVENTORY_CACHE_NAME = "inventory";

    @Bean
    public CacheManager cacheManager() {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES);

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
}
