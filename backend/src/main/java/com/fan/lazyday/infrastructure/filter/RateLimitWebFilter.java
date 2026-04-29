package com.fan.lazyday.infrastructure.filter;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.event.QuotaExceededEvent;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.infrastructure.event.DomainEventDeduplicator;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Phase 2a 的进程内限流入口。
 * Phase 2b Edge 上线后，该 Filter 退化为回源兜底，防止绕过 Edge 直连 Backend。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class RateLimitWebFilter implements WebFilter {

    private final QuotaFacade quotaFacade;
    private final CallLogRepository callLogRepository;
    private final TenantRepository tenantRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final DomainEventDeduplicator domainEventDeduplicator;
    @Nullable
    private final MeterRegistry meterRegistry;

    public RateLimitWebFilter(QuotaFacade quotaFacade,
                              CallLogRepository callLogRepository,
                              TenantRepository tenantRepository,
                              DomainEventPublisher domainEventPublisher,
                              DomainEventDeduplicator domainEventDeduplicator) {
        this(quotaFacade, callLogRepository, tenantRepository, domainEventPublisher, domainEventDeduplicator, null);
    }

    @Autowired
    public RateLimitWebFilter(QuotaFacade quotaFacade,
                              CallLogRepository callLogRepository,
                              TenantRepository tenantRepository,
                              DomainEventPublisher domainEventPublisher,
                              DomainEventDeduplicator domainEventDeduplicator,
                              @Nullable MeterRegistry meterRegistry) {
        this.quotaFacade = quotaFacade;
        this.callLogRepository = callLogRepository;
        this.tenantRepository = tenantRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.domainEventDeduplicator = domainEventDeduplicator;
        this.meterRegistry = meterRegistry;
    }

    private final Cache<Long, BucketHolder> bucketCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private final Cache<Long, EffectiveQuotaResponse> quotaCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    private final Cache<Long, Boolean> suspendedTenantCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    private final Cache<String, LongAdder> dailyUsageCache = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofDays(1))
            .build();

    private final Cache<String, LongAdder> monthlyUsageCache = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofDays(31))
            .build();

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!shouldFilter(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        return resolveTenantId(exchange)
                .flatMap(tenantId -> enforceRateLimit(tenantId, exchange, chain).thenReturn(Boolean.TRUE))
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

    private Mono<Void> enforceRateLimit(Long tenantId, ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        return isTenantSuspended(tenantId)
                .flatMap(suspended -> {
                    if (suspended) {
                        recordRejected(tenantId, "suspended", startNanos);
                        return rejectSuspended(exchange);
                    }
                    return enforceQuotaAndQps(tenantId, exchange, chain, startNanos);
                });
    }

    private Mono<Void> enforceQuotaAndQps(Long tenantId, ServerWebExchange exchange, WebFilterChain chain, long startNanos) {
        return getEffectiveQuota(tenantId)
                .flatMap(quota -> {
                    BucketHolder bucketHolder = bucketCache.get(tenantId, key -> newBucketHolder(quota.getQpsLimit()));
                    if (bucketHolder.limit() != quota.getQpsLimit()) {
                        bucketHolder = newBucketHolder(quota.getQpsLimit());
                        bucketCache.put(tenantId, bucketHolder);
                    }

                    ConsumptionProbe probe = bucketHolder.bucket().tryConsumeAndReturnRemaining(1);
                    if (!probe.isConsumed()) {
                        recordRejected(tenantId, "qps", startNanos);
                        return reject(exchange, ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                                quota.getQpsLimit(), probe.getRemainingTokens(), getResetMs(probe));
                    }

                    return getDailyCounter(tenantId).flatMap(dailyCounter -> {
                        long dailyUsage = dailyCounter.sum();
                        if (dailyUsage >= quota.getDailyLimit()) {
                            recordRejected(tenantId, "daily", startNanos);
                            publishQuotaExceeded(tenantId, "day", quota.getDailyLimit());
                            return reject(exchange, ErrorCode.QUOTA_DAILY_EXCEEDED.getCode(),
                                    quota.getDailyLimit(), 0, getTomorrowStartMs());
                        }

                        return getMonthlyCounter(tenantId).flatMap(monthlyCounter -> {
                            long monthlyUsage = monthlyCounter.sum();
                            if (monthlyUsage >= quota.getMonthlyLimit()) {
                                recordRejected(tenantId, "monthly", startNanos);
                                publishQuotaExceeded(tenantId, "month", quota.getMonthlyLimit());
                                return reject(exchange, ErrorCode.QUOTA_MONTHLY_EXCEEDED.getCode(),
                                        quota.getMonthlyLimit(), 0, getNextMonthStartMs());
                            }

                            exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(quota.getQpsLimit()));
                            exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
                            dailyCounter.increment();
                            monthlyCounter.increment();
                            recordAllowed(tenantId, startNanos);
                            return chain.filter(exchange);
                        });
                    });
                });
    }

    private Mono<Boolean> isTenantSuspended(Long tenantId) {
        Boolean cached = suspendedTenantCache.getIfPresent(tenantId);
        if (cached != null) {
            return Mono.just(cached);
        }

        return tenantRepository.findById(tenantId)
                .map(tenant -> "SUSPENDED".equalsIgnoreCase(tenant.getStatus()))
                .defaultIfEmpty(false)
                .doOnNext(suspended -> suspendedTenantCache.put(tenantId, suspended));
    }

    private Mono<EffectiveQuotaResponse> getEffectiveQuota(Long tenantId) {
        EffectiveQuotaResponse cached = quotaCache.getIfPresent(tenantId);
        if (cached != null) {
            return Mono.just(cached);
        }

        return quotaFacade.getEffectiveQuota(tenantId)
                .doOnNext(quota -> quotaCache.put(tenantId, quota));
    }

    private Mono<LongAdder> getDailyCounter(Long tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = "daily:" + tenantId + ":" + today;
        LongAdder cached = dailyUsageCache.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }

        Instant from = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return warmUpCounter(tenantId, key, from, to, dailyUsageCache);
    }

    private Mono<LongAdder> getMonthlyCounter(Long tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = "monthly:" + tenantId + ":" + today.getYear() + "-" + today.getMonthValue();
        LongAdder cached = monthlyUsageCache.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }

        Instant from = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(ZoneOffset.UTC).toInstant();
        return warmUpCounter(tenantId, key, from, to, monthlyUsageCache);
    }

    private Mono<LongAdder> warmUpCounter(Long tenantId, String key, Instant from, Instant to, Cache<String, LongAdder> cache) {
        long startNanos = System.nanoTime();
        return callLogRepository.countByTenantIdAndTimeRange(tenantId, from, to)
                .map(count -> {
                    LongAdder adder = new LongAdder();
                    adder.add(count);
                    cache.put(key, adder);
                    recordWarmup(tenantId, startNanos);
                    return adder;
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, String errorCode, long limit, long remaining, long resetMs) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));
        exchange.getResponse().getHeaders().set("X-RateLimit-Reset", String.valueOf(resetMs));
        long retryAfter = Math.max(1, (resetMs - System.currentTimeMillis()) / 1000);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfter));

        String body = "{\"code\":42901,\"error_code\":\"" + errorCode + "\",\"message\":\"quota exceeded\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> rejectSuspended(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":40300,\"error_code\":\"TENANT_SUSPENDED\",\"message\":\"Tenant has been suspended\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private void publishQuotaExceeded(Long tenantId, String period, Long limit) {
        String dedupKey = "quota-exceeded:" + tenantId + ":" + period;
        if (domainEventDeduplicator.tryRecord(dedupKey)) {
            domainEventPublisher.publish(new QuotaExceededEvent(tenantId, period, limit, Instant.now()));
        }
    }

    private long getTomorrowStartMs() {
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    private long getNextMonthStartMs() {
        return LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfNextMonth())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    private BucketHolder newBucketHolder(int qpsLimit) {
        int effectiveLimit = Math.max(qpsLimit, 1);
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(effectiveLimit, Refill.greedy(effectiveLimit, Duration.ofSeconds(1))))
                .build();
        return new BucketHolder(bucket, effectiveLimit);
    }

    private long getResetMs(ConsumptionProbe probe) {
        return System.currentTimeMillis() + TimeUnit.NANOSECONDS.toMillis(probe.getNanosToWaitForRefill());
    }

    private record BucketHolder(Bucket bucket, int limit) {
    }

    private void recordAllowed(Long tenantId, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("lazyday.ratelimit.allowed", "tenantId", String.valueOf(tenantId), "reason", "allowed").increment();
        recordLatency(startNanos);
    }

    private void recordRejected(Long tenantId, String reason, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("lazyday.ratelimit.rejected", "tenantId", String.valueOf(tenantId), "reason", reason).increment();
        recordLatency(startNanos);
    }

    private void recordWarmup(Long tenantId, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        meterRegistry.counter("lazyday.quota.counter.warmup", "tenantId", String.valueOf(tenantId)).increment();
        if (TimeUnit.NANOSECONDS.toMillis(elapsedNanos) > 200) {
            meterRegistry.counter("lazyday.quota.counter.warmup.slow", "tenantId", String.valueOf(tenantId)).increment();
        }
    }

    private void recordLatency(long startNanos) {
        Timer.builder("lazyday.ratelimit.latency")
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
