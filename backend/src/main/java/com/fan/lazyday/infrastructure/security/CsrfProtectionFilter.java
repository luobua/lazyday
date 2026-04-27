package com.fan.lazyday.infrastructure.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class CsrfProtectionFilter implements WebFilter {

    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Set<HttpMethod> STATE_CHANGING_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE
    );

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!path.startsWith("/api/portal/") && !path.startsWith("/api/admin/")) {
            return chain.filter(exchange);
        }
        if (!STATE_CHANGING_METHODS.contains(method)) {
            return chain.filter(exchange);
        }
        if (isExemptPath(path)) {
            return chain.filter(exchange);
        }

        String headerToken = exchange.getRequest().getHeaders().getFirst(CSRF_HEADER);
        if (headerToken == null || headerToken.isBlank()) {
            return FilterResponseHelper.writeError(exchange, HttpStatus.FORBIDDEN, "CSRF_TOKEN_MISSING", "缺少 CSRF 令牌");
        }

        HttpCookie cookieToken = exchange.getRequest().getCookies().getFirst(CookieUtils.CSRF_TOKEN_COOKIE);
        if (cookieToken == null || !headerToken.equals(cookieToken.getValue())) {
            return FilterResponseHelper.writeError(exchange, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "CSRF 令牌无效");
        }

        return chain.filter(exchange);
    }

    private boolean isExemptPath(String path) {
        return path.endsWith("/auth/login")
                || path.endsWith("/auth/register")
                || path.endsWith("/auth/refresh");
    }
}
