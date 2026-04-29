package com.fan.lazyday.infrastructure.security;

import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class InternalApiKeyFilter implements WebFilter {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("lazyday.internal.audit");
    private final Bucket internalBucket = Bucket.builder()
            .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofSeconds(1))))
            .build();

    private final ServiceProperties serviceProperties;

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(INTERNAL_API_KEY_HEADER);
        if (!serviceProperties.getInternalApiKey().equals(apiKey)) {
            audit(exchange, "INTERNAL_AUTH_FAILED");
            return FilterResponseHelper.writeError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.INTERNAL_AUTH_FAILED.getCode(),
                    "Internal API 认证失败"
            );
        }

        if (!internalBucket.tryConsume(1)) {
            audit(exchange, "INTERNAL_RATE_LIMITED");
            return FilterResponseHelper.writeError(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.INTERNAL_RATE_LIMITED.getCode(),
                    "Internal API 请求过于频繁"
            );
        }

        audit(exchange, "SUCCESS");
        return chain.filter(exchange);
    }

    private void audit(ServerWebExchange exchange, String result) {
        ServerHttpRequest request = exchange.getRequest();
        AUDIT_LOGGER.info("caller_ip={} path={} tenantId={} result={}",
                extractClientIp(request),
                request.getPath().value(),
                request.getQueryParams().getFirst("tenantId"),
                result);
    }

    private String extractClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "";
    }
}
