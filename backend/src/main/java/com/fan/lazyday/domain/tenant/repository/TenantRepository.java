package com.fan.lazyday.domain.tenant.repository;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import com.fan.lazyday.interfaces.response.AdminOverviewMetricsResponse;
import com.fan.lazyday.interfaces.response.AdminTenantDetailResponse;
import com.fan.lazyday.interfaces.response.AdminTenantSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.fan.lazyday.domain.tenant.entity.TenantEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

@Component
@RequiredArgsConstructor
public class TenantRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final DatabaseClient databaseClient;

    public Mono<Tenant> insert(Tenant tenant) {
        return r2dbcEntityTemplate.insert(tenant);
    }

    public Mono<Tenant> findById(Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(Tenant::getId)).is(tenantId);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<Long> update(Long tenantId, Update update) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(Tenant::getId)).is(tenantId);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }

    public Mono<Page<AdminTenantSummaryResponse>> findPage(String keyword, String status, Pageable pageable) {
        List<String> where = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.add("(LOWER(t.name) LIKE :keyword OR LOWER(t.contact_email) LIKE :keyword)");
        }
        if (status != null && !status.isBlank()) {
            where.add("t.status = :status");
        }
        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        String selectSql = """
                SELECT t.id, t.name, t.contact_email AS email, t.status, t.create_time AS created_time,
                       tq.plan_id, qp.name AS plan_name
                FROM t_tenant t
                LEFT JOIN t_tenant_quota tq ON tq.tenant_id = t.id
                LEFT JOIN t_quota_plan qp ON qp.id = tq.plan_id
                """ + whereSql + """
                ORDER BY t.create_time DESC
                LIMIT :limit OFFSET :offset
                """;
        String countSql = "SELECT COUNT(*) AS total FROM t_tenant t" + whereSql;

        Flux<AdminTenantSummaryResponse> rows = bindTenantFilters(databaseClient.sql(selectSql), keyword, status)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map((row, metadata) -> {
                    AdminTenantSummaryResponse response = new AdminTenantSummaryResponse();
                    response.setId(row.get("id", Long.class));
                    response.setName(row.get("name", String.class));
                    response.setEmail(row.get("email", String.class));
                    response.setStatus(row.get("status", String.class));
                    response.setCreatedTime(row.get("created_time", Instant.class));
                    response.setPlanId(row.get("plan_id", Long.class));
                    response.setPlanName(row.get("plan_name", String.class));
                    return response;
                })
                .all();

        Mono<Long> total = bindTenantFilters(databaseClient.sql(countSql), keyword, status)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        return Mono.zip(rows.collectList(), total)
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

    public Mono<AdminTenantSummaryResponse> findSummaryById(Long tenantId) {
        String sql = """
                SELECT t.id, t.name, t.contact_email AS email, t.status, t.create_time AS created_time,
                       tq.plan_id, qp.name AS plan_name
                FROM t_tenant t
                LEFT JOIN t_tenant_quota tq ON tq.tenant_id = t.id
                LEFT JOIN t_quota_plan qp ON qp.id = tq.plan_id
                WHERE t.id = :tenantId
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .map((row, metadata) -> {
                    AdminTenantSummaryResponse response = new AdminTenantSummaryResponse();
                    response.setId(row.get("id", Long.class));
                    response.setName(row.get("name", String.class));
                    response.setEmail(row.get("email", String.class));
                    response.setStatus(row.get("status", String.class));
                    response.setCreatedTime(row.get("created_time", Instant.class));
                    response.setPlanId(row.get("plan_id", Long.class));
                    response.setPlanName(row.get("plan_name", String.class));
                    return response;
                })
                .one();
    }

    public Mono<AdminTenantDetailResponse> findDetailWithQuota(Long tenantId) {
        String sql = """
                SELECT t.id, t.name, t.contact_email AS email, t.status, t.create_time AS created_time,
                       tq.plan_id, qp.name AS plan_name,
                       COALESCE(tq.custom_qps_limit, qp.qps_limit) AS qps_limit,
                       COALESCE(tq.custom_daily_limit, qp.daily_limit) AS daily_limit,
                       COALESCE(tq.custom_monthly_limit, qp.monthly_limit) AS monthly_limit,
                       COALESCE(tq.custom_max_app_keys, qp.max_app_keys) AS max_app_keys
                FROM t_tenant t
                LEFT JOIN t_tenant_quota tq ON tq.tenant_id = t.id
                LEFT JOIN t_quota_plan qp ON qp.id = tq.plan_id
                WHERE t.id = :tenantId
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .map((row, metadata) -> {
                    AdminTenantDetailResponse response = new AdminTenantDetailResponse();
                    response.setId(row.get("id", Long.class));
                    response.setName(row.get("name", String.class));
                    response.setEmail(row.get("email", String.class));
                    response.setStatus(row.get("status", String.class));
                    response.setCreatedTime(row.get("created_time", Instant.class));
                    response.setPlanId(row.get("plan_id", Long.class));
                    response.setPlanName(row.get("plan_name", String.class));
                    response.setQpsLimit(row.get("qps_limit", Integer.class));
                    response.setDailyLimit(row.get("daily_limit", Long.class));
                    response.setMonthlyLimit(row.get("monthly_limit", Long.class));
                    response.setMaxAppKeys(row.get("max_app_keys", Integer.class));
                    return response;
                })
                .one();
    }

    public Mono<AdminOverviewMetricsResponse> getOverviewMetrics() {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant activeFrom = Instant.now().minusSeconds(7 * 24 * 60 * 60L);

        Mono<Long> totalTenants = databaseClient.sql("SELECT COUNT(*) AS total FROM t_tenant")
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
        Mono<Long> activeTenants = databaseClient.sql("""
                        SELECT COUNT(DISTINCT tenant_id) AS total
                        FROM t_call_log
                        WHERE request_time >= :activeFrom
                        """)
                .bind("activeFrom", activeFrom)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
        Mono<TodayStats> todayStats = databaseClient.sql("""
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE status_code >= 200 AND status_code < 300) AS success_count
                        FROM t_call_log
                        WHERE request_time >= :todayStart
                        """)
                .bind("todayStart", todayStart)
                .map((row, metadata) -> new TodayStats(row.get("total", Long.class), row.get("success_count", Long.class)))
                .one()
                .defaultIfEmpty(new TodayStats(0L, 0L));
        Mono<List<AdminOverviewMetricsResponse.TopPath>> topPaths = databaseClient.sql("""
                        SELECT path, COUNT(*) AS call_count
                        FROM t_call_log
                        WHERE request_time >= :todayStart
                        GROUP BY path
                        ORDER BY call_count DESC
                        LIMIT 10
                        """)
                .bind("todayStart", todayStart)
                .map((row, metadata) -> {
                    AdminOverviewMetricsResponse.TopPath topPath = new AdminOverviewMetricsResponse.TopPath();
                    topPath.setPath(row.get("path", String.class));
                    topPath.setCallCount(row.get("call_count", Long.class));
                    return topPath;
                })
                .all()
                .collectList();

        return Mono.zip(totalTenants, activeTenants, todayStats, topPaths)
                .map(tuple -> {
                    AdminOverviewMetricsResponse response = new AdminOverviewMetricsResponse();
                    response.setTotalTenants(tuple.getT1());
                    response.setActiveTenants7d(tuple.getT2());
                    response.setTodayCalls(tuple.getT3().total());
                    response.setTodaySuccessRate(tuple.getT3().total() == 0
                            ? null
                            : (double) tuple.getT3().successCount() / tuple.getT3().total());
                    response.setTopPathsToday(tuple.getT4());
                    return response;
                });
    }

    private DatabaseClient.GenericExecuteSpec bindTenantFilters(DatabaseClient.GenericExecuteSpec spec,
                                                               String keyword,
                                                               String status) {
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.bind("keyword", "%" + keyword.toLowerCase() + "%");
        }
        if (status != null && !status.isBlank()) {
            spec = spec.bind("status", status);
        }
        return spec;
    }

    private record TodayStats(Long total, Long successCount) {
    }
}
