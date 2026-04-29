package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.CallLogFacade;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.response.CallLogStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalLogHandlerTest {

    private static final Long TENANT_ID = 100L;
    private static final Context TENANT_CTX = TenantContext.write(1L, TENANT_ID, "TENANT_ADMIN")
            .put(RequestIdFilter.REQUEST_ID_KEY, "req-id");

    @Mock
    private CallLogFacade callLogFacade;

    private PortalLogHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PortalLogHandler(callLogFacade);
    }

    @Test
    @DisplayName("GET /logs 缺少时间范围 -> INVALID_PARAMETER")
    void listLogs_withoutTimeRange_shouldThrowInvalidParameter() {
        StepVerifier.create(handler.listLogs(null, null, null, null, null, null, 0, 20).contextWrite(TENANT_CTX))
                .expectErrorMatches(ex ->
                        ex instanceof BizException be
                                && "INVALID_PARAMETER".equals(be.getErrorCode())
                                && "startTime 和 endTime 为必填参数".equals(be.getMessage())
                )
                .verify();
    }

    @Test
    @DisplayName("GET /logs/stats granularity=hour -> 调用按小时聚合")
    void getStats_hourGranularity_shouldDelegateToAggregateByHour() {
        Instant from = Instant.parse("2026-04-29T00:00:00Z");
        Instant to = Instant.parse("2026-04-30T00:00:00Z");

        CallLogStatsResponse response = new CallLogStatsResponse();
        response.setGranularity("hour");

        when(callLogFacade.aggregateByHour(TENANT_ID, from, to)).thenReturn(Mono.just(response));

        StepVerifier.create(handler.getStats(from, to, "hour").contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> {
                    assertThat(apiResponse.getCode()).isEqualTo(0);
                    assertThat(apiResponse.getData().getGranularity()).isEqualTo("hour");
                    assertThat(apiResponse.getRequestId()).isEqualTo("req-id");
                })
                .verifyComplete();

        verify(callLogFacade).aggregateByHour(TENANT_ID, from, to);
    }

    @Test
    @DisplayName("GET /logs/stats 未传 granularity -> 默认按天聚合")
    void getStats_withoutGranularity_shouldDefaultToDay() {
        Instant from = Instant.parse("2026-04-29T00:00:00Z");
        Instant to = Instant.parse("2026-04-30T00:00:00Z");

        CallLogStatsResponse response = new CallLogStatsResponse();
        response.setGranularity("day");

        when(callLogFacade.aggregateByDay(TENANT_ID, from, to)).thenReturn(Mono.just(response));

        StepVerifier.create(handler.getStats(from, to, null).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getData().getGranularity()).isEqualTo("day"))
                .verifyComplete();

        verify(callLogFacade).aggregateByDay(TENANT_ID, from, to);
    }
}
