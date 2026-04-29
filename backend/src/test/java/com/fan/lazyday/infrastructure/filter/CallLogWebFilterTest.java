package com.fan.lazyday.infrastructure.filter;

import com.fan.lazyday.application.facade.CallLogFacade;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallLogWebFilterTest {

    @Mock
    private CallLogFacade callLogFacade;
    @Mock
    private WebFilterChain chain;

    private CallLogWebFilter filter;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new CallLogWebFilter(callLogFacade, meterRegistry);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("命中路径时异步记录日志且不阻塞响应")
    void matchedPath_shouldRecordAsyncWithoutBlocking() {
        when(callLogFacade.recordAsync(any())).thenReturn(Mono.never());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota")
                        .header("X-App-Key", "ak_test")
                        .header("X-Forwarded-For", "1.1.1.1")
                        .build()
        );
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        StepVerifier.create(
                        filter.filter(exchange, chain)
                                .contextWrite(com.fan.lazyday.infrastructure.security.TenantContext.write(1L, 100L, "TENANT_ADMIN"))
                )
                .verifyComplete();

        ArgumentCaptor<com.fan.lazyday.domain.calllog.po.CallLog> captor =
                ArgumentCaptor.forClass(com.fan.lazyday.domain.calllog.po.CallLog.class);
        verify(chain).filter(exchange);
        verify(callLogFacade).recordAsync(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(100L);
        assertThat(captor.getValue().getAppKey()).isEqualTo("ak_test");
        assertThat(captor.getValue().getPath()).isEqualTo("/api/portal/v1/quota");
    }

    @Test
    @DisplayName("日志写入失败时不抛错并递增失败指标")
    void recordFailure_shouldNotBreakResponse() {
        when(callLogFacade.recordAsync(any())).thenReturn(Mono.error(new RuntimeException("write failed")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/plans").build()
        );
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        StepVerifier.create(
                        filter.filter(exchange, chain)
                                .contextWrite(com.fan.lazyday.infrastructure.security.TenantContext.write(1L, 100L, "PLATFORM_ADMIN"))
                )
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(meterRegistry.counter("lazyday.calllog.write.failed").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("未命中路径时跳过日志采集")
    void unmatchedPath_shouldSkip() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/quota/effective").build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(callLogFacade, never()).recordAsync(any());
    }
}
