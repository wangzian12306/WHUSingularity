package com.lubover.singularity.orderbaseline;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lubover.singularity.orderbaseline.mapper")
public class BaselineOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaselineOrderApplication.class, args);
    }
}
