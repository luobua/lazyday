package com.fan.lazyday.domain.calllog.repository;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CallLogRepositoryTest extends PostgresSpringBootIntegrationTestSupport {

    @Test
    @DisplayName("写入、分页与聚合查询都按 tenant 隔离")
    void repository_shouldInsertPageAndAggregateByTenant() {
        Tenant tenantA = createTenant("log-a");
        Tenant tenantB = createTenant("log-b");
        bindTenantToPlan(tenantA, "Free");
        bindTenantToPlan(tenantB, "Free");

        Instant hour10 = Instant.parse("2026-04-29T10:15:00Z");
        Instant hour11 = Instant.parse("2026-04-29T11:20:00Z");

        insertCallLog(tenantA.getId(), "akA", "/api/portal/v1/quota", (short) 200, hour10);
        insertCallLog(tenantA.getId(), "akA", "/api/portal/v1/quota", (short) 302, hour10.plusSeconds(10));
        insertCallLog(tenantA.getId(), "akA", "/api/portal/v1/logs", (short) 429, hour11);
        insertCallLog(tenantB.getId(), "akB", "/api/portal/v1/quota", (short) 200, hour11);

        var page = callLogRepository.findByTenantIdPaged(
                        tenantA.getId(),
                        Instant.parse("2026-04-29T00:00:00Z"),
                        Instant.parse("2026-04-30T00:00:00Z"),
                        null,
                        0,
                        10
                )
                .block();

        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(log -> log.getTenantId())
                .containsOnly(tenantA.getId());
        assertThat(page.getContent())
                .extracting(log -> log.getStatusCode())
                .containsExactly((short) 429, (short) 302, (short) 200);

        Long successCount = callLogRepository.countByTenantIdAndTimeRange(
                        tenantA.getId(),
                        Instant.parse("2026-04-29T00:00:00Z"),
                        Instant.parse("2026-04-30T00:00:00Z")
                )
                .block();
        assertThat(successCount).isEqualTo(2L);

        var hourly = callLogRepository.aggregateByHour(
                        tenantA.getId(),
                        Instant.parse("2026-04-29T00:00:00Z"),
                        Instant.parse("2026-04-30T00:00:00Z")
                )
                .collectList()
                .block();
        assertThat(hourly).hasSize(2);
        assertThat(hourly.getFirst().bucketTime()).isEqualTo(Instant.parse("2026-04-29T10:00:00Z"));
        assertThat(hourly.getFirst().totalCount()).isEqualTo(2L);
        assertThat(hourly.getFirst().successCount()).isEqualTo(2L);
        assertThat(hourly.getFirst().errorCount()).isEqualTo(0L);
        assertThat(hourly.get(1).bucketTime()).isEqualTo(Instant.parse("2026-04-29T11:00:00Z"));
        assertThat(hourly.get(1).totalCount()).isEqualTo(1L);
        assertThat(hourly.get(1).successCount()).isEqualTo(0L);
        assertThat(hourly.get(1).errorCount()).isEqualTo(1L);

        var daily = callLogRepository.aggregateByDay(
                        tenantA.getId(),
                        Instant.parse("2026-04-29T00:00:00Z"),
                        Instant.parse("2026-04-30T00:00:00Z")
                )
                .collectList()
                .block();
        assertThat(daily).hasSize(1);
        assertThat(daily.getFirst().bucketTime()).isEqualTo(Instant.parse("2026-04-29T00:00:00Z"));
        assertThat(daily.getFirst().totalCount()).isEqualTo(3L);
        assertThat(daily.getFirst().successCount()).isEqualTo(2L);
        assertThat(daily.getFirst().errorCount()).isEqualTo(1L);
    }
}
