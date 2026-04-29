package com.fan.lazyday.infrastructure.security;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RoleAuthorizationFilter implements WebFilter {

    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith("/api/portal/") && !path.startsWith("/api/admin/")) {
            return chain.filter(exchange);
        }
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        // Pass through CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        return TenantContext.current()
                .flatMap(ctx -> {
                    String requiredRole = path.startsWith("/api/admin/") ? ROLE_PLATFORM_ADMIN : ROLE_TENANT_ADMIN;
                    if (!requiredRole.equals(ctx.getRole())) {
                        return FilterResponseHelper.writeError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "权限不足")
                                .thenReturn(Boolean.TRUE);
                    }
                    return chain.filter(exchange).thenReturn(Boolean.TRUE);
                })
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.TRUE)))
                .then();
    }

    private boolean isPublicPath(String path) {
        return path.endsWith("/auth/login")
                || path.endsWith("/auth/register")
                || path.endsWith("/auth/csrf-token")
                || path.endsWith("/auth/refresh")
                || path.endsWith("/auth/verify-email");
    }
}
