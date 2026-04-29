package com.fan.lazyday.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class JwtAuthWebFilter implements WebFilter {

    private final JwtService jwtService;

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Always pass through CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        if (!requiresAuth(path)) {
            return chain.filter(exchange);
        }

        HttpCookie cookie = request.getCookies().getFirst(CookieUtils.ACCESS_TOKEN_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            return FilterResponseHelper.writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
        }

        JwtService.JwtClaims claims = jwtService.validateToken(cookie.getValue());
        if (claims == null) {
            return FilterResponseHelper.writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "无效的令牌");
        }
        if (claims.isExpired()) {
            return FilterResponseHelper.writeError(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "令牌已过期");
        }

        return chain.filter(exchange)
                .contextWrite(TenantContext.write(claims.getUserId(), claims.getTenantId(), claims.getRole()));
    }

    private boolean requiresAuth(String path) {
        if (path.startsWith("/internal/")) {
            return false;
        }
        if (!path.startsWith("/api/portal/") && !path.startsWith("/api/admin/")) {
            return false;
        }
        return !isPublicPath(path);
    }

    private boolean isPublicPath(String path) {
        return path.endsWith("/auth/login")
                || path.endsWith("/auth/register")
                || path.endsWith("/auth/csrf-token")
                || path.endsWith("/auth/refresh");
    }
}
