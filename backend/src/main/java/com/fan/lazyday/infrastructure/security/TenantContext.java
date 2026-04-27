package com.fan.lazyday.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Getter
@AllArgsConstructor
public class TenantContext {

    public static final String CONTEXT_KEY = TenantContext.class.getName();

    private final Long userId;
    private final Long tenantId;
    private final String role;

    public static Context write(Long userId, Long tenantId, String role) {
        return Context.of(CONTEXT_KEY, new TenantContext(userId, tenantId, role));
    }

    public static Mono<TenantContext> current() {
        return Mono.deferContextual(ctx ->
                ctx.getOrEmpty(CONTEXT_KEY)
                        .map(o -> (TenantContext) o)
                        .map(Mono::just)
                        .orElse(Mono.empty())
        );
    }
}
