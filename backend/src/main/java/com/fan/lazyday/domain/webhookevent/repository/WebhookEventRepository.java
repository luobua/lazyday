package com.fan.lazyday.domain.webhookevent.repository;

import com.fan.lazyday.domain.webhookevent.po.WebhookEventPO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

@Component
@RequiredArgsConstructor
public class WebhookEventRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final DatabaseClient databaseClient;

    public Mono<WebhookEventPO> insert(WebhookEventPO event) {
        return r2dbcEntityTemplate.insert(event);
    }

    public Flux<WebhookEventPO> selectDueForDispatch(int limit) {
        String sql = """
                SELECT *
                FROM t_webhook_event
                WHERE status = 'pending'
                  AND next_retry_at <= NOW()
                ORDER BY next_retry_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """;
        return databaseClient.sql(sql)
                .bind("limit", limit)
                .map((row, metadata) -> mapRow(row))
                .all();
    }

    public Mono<Long> updateToDelivering(Collection<Long> ids, String lockedBy) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(0L);
        }
        String sql = """
                UPDATE t_webhook_event
                SET status = 'delivering',
                    locked_at = NOW(),
                    locked_by = :lockedBy
                WHERE id = ANY(:ids)
                """;
        return databaseClient.sql(sql)
                .bind("lockedBy", lockedBy)
                .bind("ids", ids.toArray(Long[]::new))
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> updateToFailedForRetry(Long id,
                                             int retryCount,
                                             Instant nextRetryAt,
                                             Integer lastHttpStatus,
                                             String lastResponseExcerpt,
                                             String lastError) {
        String sql = """
                UPDATE t_webhook_event
                SET status = 'pending',
                    retry_count = :retryCount,
                    next_retry_at = :nextRetryAt,
                    locked_at = NULL,
                    locked_by = NULL,
                    last_http_status = :lastHttpStatus,
                    last_response_excerpt = :lastResponseExcerpt,
                    last_error = :lastError
                WHERE id = :id
                """;
        return bindFailureFields(databaseClient.sql(sql), id, retryCount, nextRetryAt,
                        lastHttpStatus, lastResponseExcerpt, lastError)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> updateToSucceeded(Long id, Integer lastHttpStatus) {
        String sql = """
                UPDATE t_webhook_event
                SET status = 'succeeded',
                    delivered_time = NOW(),
                    locked_at = NULL,
                    locked_by = NULL,
                    last_http_status = :lastHttpStatus
                WHERE id = :id
                """;
        return databaseClient.sql(sql)
                .bind("id", id)
                .bind("lastHttpStatus", lastHttpStatus)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> updateToPermanentFailed(Long id,
                                              int retryCount,
                                              Integer lastHttpStatus,
                                              String lastResponseExcerpt,
                                              String lastError) {
        String sql = """
                UPDATE t_webhook_event
                SET status = 'permanent_failed',
                    retry_count = :retryCount,
                    delivered_time = NOW(),
                    locked_at = NULL,
                    locked_by = NULL,
                    last_http_status = :lastHttpStatus,
                    last_response_excerpt = :lastResponseExcerpt,
                    last_error = :lastError
                WHERE id = :id
                """;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", id)
                .bind("retryCount", retryCount);
        spec = bindOrNull(spec, "lastHttpStatus", lastHttpStatus, Integer.class);
        spec = bindOrNull(spec, "lastResponseExcerpt", truncate(lastResponseExcerpt, 1024), String.class);
        spec = bindOrNull(spec, "lastError", truncate(lastError, 500), String.class);
        return spec
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> recoverGhostLocks() {
        String sql = """
                UPDATE t_webhook_event
                SET status = 'pending',
                    locked_at = NULL,
                    locked_by = NULL
                WHERE status = 'delivering'
                  AND locked_at < NOW() - INTERVAL '60 seconds'
                """;
        return databaseClient.sql(sql)
                .fetch()
                .rowsUpdated();
    }

    private DatabaseClient.GenericExecuteSpec bindFailureFields(DatabaseClient.GenericExecuteSpec spec,
                                                               Long id,
                                                               int retryCount,
                                                               Instant nextRetryAt,
                                                               Integer lastHttpStatus,
                                                               String lastResponseExcerpt,
                                                               String lastError) {
        spec = spec.bind("id", id)
                .bind("retryCount", retryCount)
                .bind("nextRetryAt", nextRetryAt);
        spec = bindOrNull(spec, "lastHttpStatus", lastHttpStatus, Integer.class);
        spec = bindOrNull(spec, "lastResponseExcerpt", truncate(lastResponseExcerpt, 1024), String.class);
        spec = bindOrNull(spec, "lastError", truncate(lastError, 500), String.class);
        return spec;
    }

    private <T> DatabaseClient.GenericExecuteSpec bindOrNull(DatabaseClient.GenericExecuteSpec spec,
                                                            String name,
                                                            T value,
                                                            Class<T> type) {
        if (value == null) {
            return spec.bindNull(name, type);
        }
        return spec.bind(name, value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private WebhookEventPO mapRow(io.r2dbc.spi.Row row) {
        return new WebhookEventPO()
                .setId(row.get("id", Long.class))
                .setTenantId(row.get("tenant_id", Long.class))
                .setConfigId(row.get("config_id", Long.class))
                .setEventType(row.get("event_type", String.class))
                .setPayload(row.get("payload", String.class))
                .setStatus(row.get("status", String.class))
                .setRetryCount(row.get("retry_count", Integer.class))
                .setNextRetryAt(row.get("next_retry_at", Instant.class))
                .setLockedAt(row.get("locked_at", Instant.class))
                .setLockedBy(row.get("locked_by", String.class))
                .setLastHttpStatus(row.get("last_http_status", Integer.class))
                .setLastResponseExcerpt(row.get("last_response_excerpt", String.class))
                .setLastError(row.get("last_error", String.class))
                .setCreatedTime(row.get("created_time", Instant.class))
                .setDeliveredTime(row.get("delivered_time", Instant.class));
    }
}
