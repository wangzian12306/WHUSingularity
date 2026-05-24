package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.PipelineExecutor;
import com.lubover.singularity.pipeline.impl.DefaultPipelineExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductReadPipelineConfig {

    @Bean
    public PipelineExecutor productPipelineExecutor() {
        return new DefaultPipelineExecutor();
    }
}
