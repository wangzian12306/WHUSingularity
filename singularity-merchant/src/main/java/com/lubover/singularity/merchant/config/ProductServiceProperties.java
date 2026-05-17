package com.lubover.singularity.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.service")
public class ProductServiceProperties {

    private String url = "http://localhost:8081";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
