package com.fan.lazyday.domain.calllog.repository;

import com.fan.lazyday.domain.calllog.po.CallLog;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CallLogRepository {

    private static final Class<CallLog> PO_CLASS = CallLog.class;

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final DatabaseClient databaseClient;

    public Mono<CallLog> insert(CallLog callLog) {
        return r2dbcEntityTemplate.insert(callLog);
    }

    public Mono<Page<CallLog>> findByTenantIdPaged(Long tenantId, Instant from, Instant to,
                                                   Short statusCode, int page, int size) {
        return findByTenantId(tenantId, null, null, statusCode, null, from, to, page, size);
    }

    public Mono<Page<CallLog>> findByTenantId(Long tenantId, String appKey, String path,
                                              Short statusCode, Integer statusCodeGroup,
                                              Instant from, Instant to, int page, int size) {
        List<Criteria> criteriaList = buildFilterCriteria(tenantId, appKey, path, statusCode, statusCodeGroup, from, to);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "request_time"));
        return R2dbcHelper.findPage(criteriaList, pageable, PO_CLASS, c -> c);
    }

    public Flux<CallLog> findAllByTenantId(Long tenantId, String appKey, String path,
                                           Short statusCode, Integer statusCodeGroup,
                                           Instant from, Instant to) {
        List<Criteria> criteriaList = buildFilterCriteria(tenantId, appKey, path, statusCode, statusCodeGroup, from, to);
        Query query = Query.query(Criteria.from(criteriaList))
                .sort(Sort.by(Sort.Direction.DESC, "request_time"));
        return r2dbcEntityTemplate.select(query, PO_CLASS);
    }

    /**
     * Count successful calls (2xx/3xx) for a tenant in a time range.
     */
    public Mono<Long> countByTenantIdAndTimeRange(Long tenantId, Instant from, Instant to) {
        String sql = """
                SELECT COUNT(*) AS success_count
                FROM t_call_log
                WHERE tenant_id = :tenantId
                  AND request_time >= :from
                  AND request_time < :to
                  AND status_code >= 200
                  AND status_code < 400
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> row.get("success_count", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    /**
     * Count calls grouped by status code category (2xx/3xx, 4xx, 5xx).
     */
    public Mono<CallLogStats> getStats(Long tenantId, Instant from, Instant to) {
        String sql = """
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status_code >= 200 AND status_code < 400) AS success_count,
                    COUNT(*) FILTER (WHERE status_code >= 400 AND status_code < 500) AS client_error_count,
                    COUNT(*) FILTER (WHERE status_code >= 500) AS server_error_count,
                    COALESCE(AVG(latency_ms), 0) AS avg_latency_ms
                FROM t_call_log
                WHERE tenant_id = :tenantId
                  AND request_time >= :from
                  AND request_time < :to
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new CallLogStats(
                        row.get("total", Long.class),
                        row.get("success_count", Long.class),
                        row.get("client_error_count", Long.class),
                        row.get("server_error_count", Long.class),
                        row.get("avg_latency_ms", Double.class)
                ))
                .one()
                .defaultIfEmpty(new CallLogStats(0L, 0L, 0L, 0L, 0.0));
    }

    public Flux<TimeBucketStats> aggregateByHour(Long tenantId, Instant from, Instant to) {
        String sql = """
                SELECT
                    DATE_TRUNC('hour', request_time AT TIME ZONE 'UTC') AS bucket_time,
                    COUNT(*) AS total_count,
                    COUNT(*) FILTER (WHERE status_code >= 200 AND status_code < 400) AS success_count,
                    COUNT(*) FILTER (WHERE status_code >= 400) AS error_count
                FROM t_call_log
                WHERE tenant_id = :tenantId
                  AND request_time >= :from
                  AND request_time < :to
                GROUP BY bucket_time
                ORDER BY bucket_time
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new TimeBucketStats(
                        row.get("bucket_time", java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),
                        row.get("total_count", Long.class),
                        row.get("success_count", Long.class),
                        row.get("error_count", Long.class)
                ))
                .all();
    }

    public Flux<TimeBucketStats> aggregateByDay(Long tenantId, Instant from, Instant to) {
        String sql = """
                SELECT
                    DATE_TRUNC('day', request_time AT TIME ZONE 'UTC') AS bucket_time,
                    COUNT(*) AS total_count,
                    COUNT(*) FILTER (WHERE status_code >= 200 AND status_code < 400) AS success_count,
                    COUNT(*) FILTER (WHERE status_code >= 400) AS error_count
                FROM t_call_log
                WHERE tenant_id = :tenantId
                  AND request_time >= :from
                  AND request_time < :to
                GROUP BY bucket_time
                ORDER BY bucket_time
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new TimeBucketStats(
                        row.get("bucket_time", java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),
                        row.get("total_count", Long.class),
                        row.get("success_count", Long.class),
                        row.get("error_count", Long.class)
                ))
                .all();
    }

    private List<Criteria> buildFilterCriteria(Long tenantId, String appKey, String path,
                                               Short statusCode, Integer statusCodeGroup,
                                               Instant from, Instant to) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("tenant_id").is(tenantId));

        if (appKey != null && !appKey.isBlank()) {
            criteriaList.add(Criteria.where("app_key").is(appKey));
        }
        if (path != null && !path.isBlank()) {
            criteriaList.add(Criteria.where("path").like("%" + path + "%"));
        }
        if (statusCode != null) {
            criteriaList.add(Criteria.where("status_code").is(statusCode));
        } else if (statusCodeGroup != null) {
            int lower = statusCodeGroup * 100;
            int upper = lower + 100;
            criteriaList.add(Criteria.where("status_code").greaterThanOrEquals((short) lower));
            criteriaList.add(Criteria.where("status_code").lessThan((short) upper));
        }
        if (from != null) {
            criteriaList.add(Criteria.where("request_time").greaterThanOrEquals(from));
        }
        if (to != null) {
            criteriaList.add(Criteria.where("request_time").lessThan(to));
        }
        return criteriaList;
    }

    public record CallLogStats(Long total, Long successCount, Long clientErrorCount,
                                Long serverErrorCount, Double avgLatencyMs) {}

    public record TimeBucketStats(java.time.Instant bucketTime, Long totalCount,
                                  Long successCount, Long errorCount) {}

    public record DailyCallVolume(java.time.LocalDate day, Long count, Long successCount) {}

    public record PathCallCount(String path, Long count) {}
}
