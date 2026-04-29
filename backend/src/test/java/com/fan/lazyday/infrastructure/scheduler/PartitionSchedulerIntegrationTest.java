package com.fan.lazyday.infrastructure.scheduler;

import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionSchedulerIntegrationTest extends PostgresSpringBootIntegrationTestSupport {

    private static final DateTimeFormatter PARTITION_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM");

    @Test
    @DisplayName("19.10 手动触发 PartitionScheduler 后会创建下下月分区")
    void manualTrigger_shouldCreatePartitionForMonthAfterNext() {
        LocalDate lastDayOfCurrentMonth = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.lastDayOfMonth());
        YearMonth targetMonth = YearMonth.from(lastDayOfCurrentMonth).plusMonths(2);
        String partitionName = "t_call_log_" + targetMonth.format(PARTITION_SUFFIX_FORMATTER);

        assertThat(partitionExists(partitionName)).isFalse();

        PartitionScheduler scheduler = new PartitionScheduler(
                databaseClient,
                Clock.fixed(lastDayOfCurrentMonth.atTime(2, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
        scheduler.createPartitionForMonthAfterNext();

        waitForPartition(partitionName);
        assertThat(partitionExists(partitionName)).isTrue();
    }

    private void waitForPartition(String partitionName) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (partitionExists(partitionName)) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
    }

    private boolean partitionExists(String partitionName) {
        return Boolean.TRUE.equals(
                databaseClient.sql("SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename = :partitionName")
                        .bind("partitionName", partitionName)
                        .map((row, metadata) -> row.get(0))
                        .first()
                        .hasElement()
                        .block()
        );
    }
}
