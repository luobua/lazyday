package com.fan.lazyday.infrastructure.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID_KEY, requestId));
    }

    public static Mono<String> getRequestId() {
        return Mono.deferContextual(ctx ->
                ctx.getOrEmpty(REQUEST_ID_KEY)
                        .map(o -> (String) o)
                        .map(Mono::just)
                        .orElse(Mono.empty())
        );
    }
}