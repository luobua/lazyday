package com.fan.lazyday.infrastructure.filter;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.event.QuotaExceededEvent;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.infrastructure.event.DomainEventDeduplicator;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RateLimitWebFilterTest {

    @Mock
    private QuotaFacade quotaFacade;
    @Mock
    private CallLogRepository callLogRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private DomainEventDeduplicator domainEventDeduplicator;
    @Mock
    private WebFilterChain chain;

    private RateLimitWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitWebFilter(
                quotaFacade,
                callLogRepository,
                tenantRepository,
                domainEventPublisher,
                domainEventDeduplicator
        );
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("auth 路径不参与限流")
    void authPath_shouldBypass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/auth/login").build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verify(quotaFacade, never()).getEffectiveQuota(anyLong());
    }

    @Test
    @DisplayName("没有 tenant 上下文时直接放行")
    void missingTenant_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota").build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verify(quotaFacade, never()).getEffectiveQuota(anyLong());
    }

    @Test
    @DisplayName("tenant 命中限流时成功请求只执行一次下游链")
    void resolvedTenant_shouldInvokeChainOnceOnAllowedRequest() {
        EffectiveQuotaResponse quota = new EffectiveQuotaResponse();
        quota.setPlanName("Enterprise");
        quota.setQpsLimit(200);
        quota.setDailyLimit(500_000L);
        quota.setMonthlyLimit(5_000_000L);
        quota.setMaxAppKeys(-1);
        when(quotaFacade.getEffectiveQuota(100L)).thenReturn(Mono.just(quota));
        when(tenantRepository.findById(100L)).thenReturn(Mono.just(activeTenant(100L)));
        when(callLogRepository.countByTenantIdAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(0L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota").build()
        );

        StepVerifier.create(
                        filter.filter(exchange, chain)
                                .contextWrite(com.fan.lazyday.infrastructure.security.TenantContext.write(1L, 100L, "TENANT_ADMIN"))
                )
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("200");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("199");
    }

    @Test
    @DisplayName("X-Tenant-Id 优先于 TenantContext，QPS 超限返回 429 与响应头")
    void headerTenant_shouldOverrideContextAndReturn429() {
        EffectiveQuotaResponse quota = new EffectiveQuotaResponse();
        quota.setPlanName("Free");
        quota.setQpsLimit(1);
        quota.setDailyLimit(1_000L);
        quota.setMonthlyLimit(10_000L);
        quota.setMaxAppKeys(5);
        when(quotaFacade.getEffectiveQuota(5L)).thenReturn(Mono.just(quota));
        when(tenantRepository.findById(5L)).thenReturn(Mono.just(activeTenant(5L)));
        when(callLogRepository.countByTenantIdAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(0L));

        Context tenantContext = com.fan.lazyday.infrastructure.security.TenantContext.write(1L, 7L, "TENANT_ADMIN");

        MockServerWebExchange first = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota")
                        .header("X-Tenant-Id", "5")
                        .build()
        );
        StepVerifier.create(filter.filter(first, chain).contextWrite(tenantContext))
                .verifyComplete();

        MockServerWebExchange second = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota")
                        .header("X-Tenant-Id", "5")
                        .build()
        );
        StepVerifier.create(filter.filter(second, chain).contextWrite(tenantContext))
                .verifyComplete();

        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("1");
        assertThat(second.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(second.getResponse().getHeaders().getFirst("X-RateLimit-Reset")).isNotBlank();
        assertThat(second.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        verify(quotaFacade).getEffectiveQuota(5L);
        verify(quotaFacade, never()).getEffectiveQuota(7L);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("daily quota 连续超限只发布一次 QuotaExceededEvent")
    void dailyQuotaExceeded_shouldPublishOnceWithinDedupWindow() {
        EffectiveQuotaResponse quota = quota(100, 1L, 10_000L);
        when(tenantRepository.findById(100L)).thenReturn(Mono.just(activeTenant(100L)));
        when(quotaFacade.getEffectiveQuota(100L)).thenReturn(Mono.just(quota));
        when(callLogRepository.countByTenantIdAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(1L));
        when(domainEventDeduplicator.tryRecord("quota-exceeded:100:day")).thenReturn(true, false);

        for (int i = 0; i < 100; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/portal/v1/quota")
                            .header("X-Tenant-Id", "100")
                            .build()
            );
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        ArgumentCaptor<QuotaExceededEvent> eventCaptor = ArgumentCaptor.forClass(QuotaExceededEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().period()).isEqualTo("day");
        assertThat(eventCaptor.getValue().limit()).isEqualTo(1L);
    }

    @Test
    @DisplayName("monthly quota 超限发布 month 事件")
    void monthlyQuotaExceeded_shouldPublishMonthlyEvent() {
        EffectiveQuotaResponse quota = quota(100, 10_000L, 1L);
        when(tenantRepository.findById(100L)).thenReturn(Mono.just(activeTenant(100L)));
        when(quotaFacade.getEffectiveQuota(100L)).thenReturn(Mono.just(quota));
        when(callLogRepository.countByTenantIdAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(0L), Mono.just(1L));
        when(domainEventDeduplicator.tryRecord("quota-exceeded:100:month")).thenReturn(true);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota")
                        .header("X-Tenant-Id", "100")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<QuotaExceededEvent> eventCaptor = ArgumentCaptor.forClass(QuotaExceededEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().period()).isEqualTo("month");
        assertThat(eventCaptor.getValue().limit()).isEqualTo(1L);
    }

    @Test
    @DisplayName("dedup 允许再次记录时会再次发布 quota exceeded")
    void quotaExceeded_shouldPublishAgainWhenDedupWindowExpires() {
        EffectiveQuotaResponse quota = quota(100, 1L, 10_000L);
        when(tenantRepository.findById(100L)).thenReturn(Mono.just(activeTenant(100L)));
        when(quotaFacade.getEffectiveQuota(100L)).thenReturn(Mono.just(quota));
        when(callLogRepository.countByTenantIdAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(1L));
        when(domainEventDeduplicator.tryRecord("quota-exceeded:100:day")).thenReturn(true, false, true);

        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/portal/v1/quota")
                            .header("X-Tenant-Id", "100")
                            .build()
            );
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        verify(domainEventPublisher, times(2)).publish(any(QuotaExceededEvent.class));
    }

    @Test
    @DisplayName("QPS 拒绝不发布 quota exceeded")
    void qpsRejected_shouldNotPublishQuotaExceededEvent() {
        EffectiveQuotaResponse quota = quota(1, 1_000L, 10_000L);
        when(tenantRepository.findById(100L)).thenReturn(Mono.just(activeTenant(100L)));
        when(quotaFacade.getEffectiveQuota(100L)).thenReturn(Mono.just(quota));
        when(callLogRepository.countByTenantIdAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(filter.filter(exchangeForTenant(100L), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(exchangeForTenant(100L), chain))
                .verifyComplete();

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("SUSPENDED tenant 在配额和 QPS 前被拒绝")
    void suspendedTenant_shouldReturn403BeforeRateLimitEvaluation() {
        Tenant tenant = new Tenant();
        tenant.setId(100L);
        tenant.setStatus("SUSPENDED");
        when(tenantRepository.findById(100L)).thenReturn(Mono.just(tenant));

        MockServerWebExchange exchange = exchangeForTenant(100L);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("TENANT_SUSPENDED");
        verify(quotaFacade, never()).getEffectiveQuota(anyLong());
        verify(callLogRepository, never()).countByTenantIdAndTimeRange(anyLong(), any(), any());
    }

    private MockServerWebExchange exchangeForTenant(Long tenantId) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota")
                        .header("X-Tenant-Id", String.valueOf(tenantId))
                        .build()
        );
    }

    private Tenant activeTenant(Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus("ACTIVE");
        return tenant;
    }

    private EffectiveQuotaResponse quota(int qpsLimit, long dailyLimit, long monthlyLimit) {
        EffectiveQuotaResponse quota = new EffectiveQuotaResponse();
        quota.setPlanName("Test");
        quota.setQpsLimit(qpsLimit);
        quota.setDailyLimit(dailyLimit);
        quota.setMonthlyLimit(monthlyLimit);
        quota.setMaxAppKeys(5);
        return quota;
    }
}
