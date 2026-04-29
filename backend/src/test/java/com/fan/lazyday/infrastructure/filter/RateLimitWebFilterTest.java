package com.fan.lazyday.infrastructure.filter;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitWebFilterTest {

    @Mock
    private QuotaFacade quotaFacade;
    @Mock
    private CallLogRepository callLogRepository;
    @Mock
    private WebFilterChain chain;

    private RateLimitWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitWebFilter(quotaFacade, callLogRepository);
        when(chain.filter(any())).thenReturn(Mono.empty());
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
    }
}
