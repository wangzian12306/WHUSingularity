package com.lubover.singularity.gateway;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Adds debug headers to help verify gateway forwarding behavior.
 */
@Component
@ConditionalOnProperty(prefix = "singularity.gateway.trace-header", name = "enabled", havingValue = "true")
public class GatewayTraceHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

        if (route != null) {
            exchange.getResponse().getHeaders().add("X-Forwarded-Service", route.getUri().toString());
            exchange.getResponse().getHeaders().add("X-Gateway-Route-Id", route.getId());
        }
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            URI target = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            if (target != null && target.getHost() != null) {
                exchange.getResponse().getHeaders().add("X-Instance-Id", target.getHost() + ":" + target.getPort());
            }
        }));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
