package com.lubover.singularity.merchant;

import com.lubover.singularity.merchant.config.JwtProperties;
import com.lubover.singularity.merchant.config.ProductServiceProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.lubover.singularity.merchant.mapper")
@EnableFeignClients
@EnableConfigurationProperties({JwtProperties.class, ProductServiceProperties.class})
public class MerchantApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
