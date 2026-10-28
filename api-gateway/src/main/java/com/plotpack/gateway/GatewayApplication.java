package com.plotpack.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.UUID;

@SpringBootApplication
public class GatewayApplication {
    public static final String REQUEST_ID = "X-Request-Id";

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /** Stamps every inbound request with a correlation id that downstream services put in their logs. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    GlobalFilter requestIdFilter() {
        return (exchange, chain) -> {
            String id = exchange.getRequest().getHeaders().getFirst(REQUEST_ID);
            String requestId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
            ServerHttpRequest req = exchange.getRequest().mutate().header(REQUEST_ID, requestId).build();
            exchange.getResponse().getHeaders().set(REQUEST_ID, requestId);
            return chain.filter(exchange.mutate().request(req).build());
        };
    }
}
