package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.domain.calllog.po.CallLog;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.interfaces.response.CallLogStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallLogFacadeImplTest {

    @Mock
    private CallLogRepository callLogRepository;

    private CallLogFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new CallLogFacadeImpl(callLogRepository);
    }

    @Test
    @DisplayName("queryPaged: 映射分页元数据并脱敏 appKey")
    void queryPaged_shouldMapPageResponseAndMaskAppKey() {
        Instant from = Instant.parse("2026-04-29T00:00:00Z");
        Instant to = Instant.parse("2026-04-30T00:00:00Z");

        CallLog callLog = new CallLog();
        callLog.setId(1L);
        callLog.setAppKey("abcdefg1234567890");
        callLog.setPath("/api/portal/v1/quota");
        callLog.setMethod("GET");
        callLog.setStatusCode((short) 200);
        callLog.setLatencyMs(12);
        callLog.setClientIp("127.0.0.1");
        callLog.setRequestTime(Instant.parse("2026-04-29T09:00:00Z"));

        when(callLogRepository.findByTenantIdPaged(100L, from, to, (short) 200, 1, 20))
                .thenReturn(Mono.just(new PageImpl<>(List.of(callLog), PageRequest.of(1, 20), 21)));

        StepVerifier.create(facade.queryPaged(100L, from, to, (short) 200, 1, 20))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(21);
                    assertThat(page.getPage()).isEqualTo(1);
                    assertThat(page.getSize()).isEqualTo(20);
                    assertThat(page.getList()).hasSize(1);
                    assertThat(page.getList().getFirst().getAppKey()).isEqualTo("abcdefg****7890");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("aggregateByHour: 汇总 summary 与时间桶")
    void aggregateByHour_shouldCombineSummaryAndBuckets() {
        Instant from = Instant.parse("2026-04-29T00:00:00Z");
        Instant to = Instant.parse("2026-04-30T00:00:00Z");

        when(callLogRepository.getStats(100L, from, to))
                .thenReturn(Mono.just(new CallLogRepository.CallLogStats(10L, 7L, 2L, 1L, 12.5)));
        when(callLogRepository.aggregateByHour(100L, from, to))
                .thenReturn(Flux.just(
                        new CallLogRepository.TimeBucketStats(Instant.parse("2026-04-29T10:00:00Z"), 4L, 3L, 1L),
                        new CallLogRepository.TimeBucketStats(Instant.parse("2026-04-29T11:00:00Z"), 6L, 4L, 2L)
                ));

        StepVerifier.create(facade.aggregateByHour(100L, from, to))
                .assertNext(response -> {
                    assertThat(response.getGranularity()).isEqualTo("hour");
                    assertThat(response.getTotal()).isEqualTo(10L);
                    assertThat(response.getSuccessCount()).isEqualTo(7L);
                    assertThat(response.getClientErrorCount()).isEqualTo(2L);
                    assertThat(response.getServerErrorCount()).isEqualTo(1L);
                    assertThat(response.getAvgLatencyMs()).isEqualTo(12.5);
                    assertThat(response.getBuckets()).hasSize(2);
                    CallLogStatsResponse.BucketItem bucket = response.getBuckets().getFirst();
                    assertThat(bucket.getBucketTime()).isEqualTo(Instant.parse("2026-04-29T10:00:00Z"));
                    assertThat(bucket.getTotalCount()).isEqualTo(4L);
                    assertThat(bucket.getSuccessCount()).isEqualTo(3L);
                    assertThat(bucket.getErrorCount()).isEqualTo(1L);
                })
                .verifyComplete();
    }
}
