package com.fan.lazyday.infrastructure.filter;

import com.fan.lazyday.application.facade.CallLogFacade;
import com.fan.lazyday.domain.calllog.po.CallLog;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.infrastructure.utils.id.SnowflakeIdWorker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
public class CallLogWebFilter implements WebFilter {

    private final CallLogFacade callLogFacade;
    private final SnowflakeIdWorker snowflakeIdWorker;
    @Nullable
    private final MeterRegistry meterRegistry;

    public CallLogWebFilter(CallLogFacade callLogFacade, @Nullable MeterRegistry meterRegistry) {
        this(callLogFacade, new SnowflakeIdWorker(1, 1), meterRegistry);
    }

    @Autowired
    public CallLogWebFilter(CallLogFacade callLogFacade,
                            SnowflakeIdWorker snowflakeIdWorker,
                            @Nullable MeterRegistry meterRegistry) {
        this.callLogFacade = callLogFacade;
        this.snowflakeIdWorker = snowflakeIdWorker;
        this.meterRegistry = meterRegistry;
    }

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!shouldFilter(path)) {
            return chain.filter(exchange);
        }

        Instant requestTime = Instant.now();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        return resolveTenantId(exchange)
                .flatMap(tenantId -> chain.filter(exchange)
                        .doOnError(errorRef::set)
                        .doFinally(signalType -> scheduleLogWrite(exchange, requestTime, tenantId, errorRef.get()))
                        .thenReturn(Boolean.TRUE))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.TRUE)))
                .then();
    }

    private boolean shouldFilter(String path) {
        if (path.startsWith("/internal/") || path.startsWith("/actuator/")) {
            return false;
        }
        if (path.startsWith("/api/portal/v1/auth/") || path.startsWith("/api/admin/v1/auth/")) {
            return false;
        }
        return path.startsWith("/api/open/v1/")
                || path.startsWith("/api/portal/v1/")
                || path.startsWith("/api/admin/v1/");
    }

    private void scheduleLogWrite(ServerWebExchange exchange, Instant requestTime, Long tenantId, @Nullable Throwable capturedError) {
        Mono.just(buildCallLog(exchange, requestTime, tenantId, capturedError))
                .flatMap(callLogFacade::recordAsync)
                .doOnError(error -> {
                    log.warn("Failed to write call log", error);
                    if (meterRegistry != null) {
                        meterRegistry.counter("lazyday.calllog.write.failed").increment();
                    }
                })
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    private Mono<Long> resolveTenantId(ServerWebExchange exchange) {
        String headerTenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            try {
                return Mono.just(Long.parseLong(headerTenantId));
            } catch (NumberFormatException ex) {
                log.warn("Ignoring invalid X-Tenant-Id header: {}", headerTenantId);
            }
        }
        return TenantContext.current().map(TenantContext::getTenantId);
    }

    private CallLog buildCallLog(ServerWebExchange exchange, Instant requestTime, Long tenantId, @Nullable Throwable error) {
        ServerHttpRequest request = exchange.getRequest();
        Instant now = Instant.now();
        int statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;

        CallLog callLog = new CallLog();
        callLog.setId(snowflakeIdWorker.nextId());
        callLog.setTenantId(tenantId);
        callLog.setAppKey(resolveAppKey(request));
        callLog.setPath(request.getPath().value());
        callLog.setMethod(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN");
        callLog.setStatusCode((short) statusCode);
        callLog.setLatencyMs((int) (now.toEpochMilli() - requestTime.toEpochMilli()));
        callLog.setClientIp(extractClientIp(request));
        callLog.setErrorMsg(resolveErrorMessage(exchange, error));
        callLog.setRequestTime(requestTime);
        return callLog;
    }

    private String resolveAppKey(ServerHttpRequest request) {
        String appKey = request.getHeaders().getFirst("X-App-Key");
        if (appKey != null && !appKey.isBlank()) {
            return appKey;
        }
        String path = request.getPath().value();
        if (path.startsWith("/api/admin/v1/")) {
            return "__ADMIN__";
        }
        if (path.startsWith("/api/portal/v1/")) {
            return "__PORTAL__";
        }
        return "";
    }

    @Nullable
    private String resolveErrorMessage(ServerWebExchange exchange, @Nullable Throwable error) {
        if (error != null) {
            return truncate(error.getClass().getSimpleName() + ":" + defaultString(error.getMessage()));
        }
        if (exchange.getResponse().getStatusCode() != null && exchange.getResponse().getStatusCode().is5xxServerError()) {
            return exchange.getResponse().getStatusCode().toString();
        }
        return null;
    }

    private String truncate(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
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

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
