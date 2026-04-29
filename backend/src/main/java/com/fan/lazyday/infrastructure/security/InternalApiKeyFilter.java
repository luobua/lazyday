package com.fan.lazyday.infrastructure.security;

import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class InternalApiKeyFilter implements WebFilter {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ServiceProperties serviceProperties;

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(INTERNAL_API_KEY_HEADER);
        if (serviceProperties.getInternalApiKey().equals(apiKey)) {
            return chain.filter(exchange);
        }

        return FilterResponseHelper.writeError(
                exchange,
                HttpStatus.FORBIDDEN,
                ErrorCode.INTERNAL_AUTH_FAILED.getCode(),
                "Internal API 认证失败"
        );
    }
}
