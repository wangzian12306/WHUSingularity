package com.lubover.singularity.orderbaseline.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class UserHttpClient {

    private final RestClient restClient;

    public UserHttpClient(RestClient.Builder restClientBuilder,
            @Value("${baseline.user-service.base-url:http://localhost:8090}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> deductBalance(Long userId, int amount) {
        Map<String, BigDecimal> body = Map.of("amount", BigDecimal.valueOf(amount));

        return restClient.post()
                .uri("/api/user/{id}/deduct", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }
}
