package com.fan.lazyday.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PartitionSchedulerTest {

    @Test
    @DisplayName("月末时应创建下下月分区 SQL")
    void shouldBuildPartitionSqlForMonthAfterNext() {
        PartitionScheduler scheduler = new PartitionScheduler(
                mock(DatabaseClient.class),
                Clock.fixed(Instant.parse("2026-01-31T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(scheduler.isLastDayOfMonth(LocalDate.of(2026, 1, 31))).isTrue();
        assertThat(scheduler.isLastDayOfMonth(LocalDate.of(2026, 1, 30))).isFalse();
        assertThat(scheduler.targetPartitionMonth(LocalDate.of(2026, 1, 31))).isEqualTo(YearMonth.of(2026, 3));
        assertThat(scheduler.buildCreatePartitionSql(YearMonth.of(2026, 3)))
                .isEqualTo("CREATE TABLE IF NOT EXISTS t_call_log_2026_03 PARTITION OF t_call_log FOR VALUES FROM ('2026-03-01') TO ('2026-04-01')");
    }
}
