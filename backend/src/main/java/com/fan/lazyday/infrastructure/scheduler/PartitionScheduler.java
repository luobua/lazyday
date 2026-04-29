package com.fan.lazyday.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class PartitionScheduler {

    private static final DateTimeFormatter PARTITION_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM");

    private final DatabaseClient databaseClient;
    private final Clock clock;

    @Autowired
    public PartitionScheduler(DatabaseClient databaseClient) {
        this(databaseClient, Clock.systemUTC());
    }

    PartitionScheduler(DatabaseClient databaseClient, Clock clock) {
        this.databaseClient = databaseClient;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 28-31 * ?")
    public void createPartitionForMonthAfterNext() {
        LocalDate today = LocalDate.now(clock);
        if (!isLastDayOfMonth(today)) {
            return;
        }

        createPartitionIfMissing(targetPartitionMonth(today))
                .doOnError(error -> log.error("Failed to create partition for month after next", error))
                .subscribe();
    }

    public void ensureNextTwoMonthsPartitions() {
        LocalDate today = LocalDate.now(clock);
        Mono.when(
                        createPartitionIfMissing(YearMonth.from(today)),
                        createPartitionIfMissing(YearMonth.from(today.plusMonths(1)))
                )
                .doOnError(error -> log.error("Failed to ensure future partitions", error))
                .subscribe();
    }

    boolean isLastDayOfMonth(LocalDate date) {
        return date.getDayOfMonth() == date.lengthOfMonth();
    }

    YearMonth targetPartitionMonth(LocalDate today) {
        return YearMonth.from(today).plusMonths(2);
    }

    String buildCreatePartitionSql(YearMonth month) {
        String partitionName = "t_call_log_" + month.format(PARTITION_SUFFIX_FORMATTER);
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        return "CREATE TABLE IF NOT EXISTS " + partitionName
                + " PARTITION OF t_call_log FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
    }

    private Mono<Void> createPartitionIfMissing(YearMonth month) {
        String partitionName = "t_call_log_" + month.format(PARTITION_SUFFIX_FORMATTER);
        String sql = buildCreatePartitionSql(month);

        return databaseClient.sql("SELECT 1 FROM pg_class WHERE relname = :partitionName")
                .bind("partitionName", partitionName)
                .map((row, metadata) -> row.get(0))
                .first()
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.empty();
                    }
                    return databaseClient.sql(sql).then();
                });
    }
}
