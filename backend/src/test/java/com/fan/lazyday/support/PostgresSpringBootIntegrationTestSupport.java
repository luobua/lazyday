package com.fan.lazyday.support;

import com.fan.lazyday.domain.calllog.po.CallLog;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.quotaplan.repository.QuotaPlanRepository;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.domain.tenantquota.repository.TenantQuotaRepository;
import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.security.CookieUtils;
import com.fan.lazyday.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lazyday.partition.bootstrap.enabled=false"
})
public abstract class PostgresSpringBootIntegrationTestSupport {

    protected static final String DB_NAME = PostgresTestDatabaseSupport.randomDatabaseName("lazyday_phase2a_it");
    private static final AtomicLong CALL_LOG_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis() * 10_000);

    static {
        PostgresTestDatabaseSupport.createDatabase(DB_NAME);
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("database.host", () -> PostgresTestDatabaseSupport.HOST);
        registry.add("database.port", () -> PostgresTestDatabaseSupport.PORT);
        registry.add("database.dbname", () -> DB_NAME);
        registry.add("database.username", () -> PostgresTestDatabaseSupport.USERNAME);
        registry.add("database.password", () -> PostgresTestDatabaseSupport.PASSWORD);
        registry.add("spring.datasource.url", () -> PostgresTestDatabaseSupport.jdbcUrl(DB_NAME));
        registry.add("spring.datasource.username", () -> PostgresTestDatabaseSupport.USERNAME);
        registry.add("spring.datasource.password", () -> PostgresTestDatabaseSupport.PASSWORD);
        registry.add("spring.r2dbc.url", () -> PostgresTestDatabaseSupport.r2dbcUrl(DB_NAME));
        registry.add("spring.r2dbc.username", () -> PostgresTestDatabaseSupport.USERNAME);
        registry.add("spring.r2dbc.password", () -> PostgresTestDatabaseSupport.PASSWORD);
        registry.add("spring.flyway.url", () -> PostgresTestDatabaseSupport.jdbcUrl(DB_NAME));
        registry.add("spring.flyway.user", () -> PostgresTestDatabaseSupport.USERNAME);
        registry.add("spring.flyway.password", () -> PostgresTestDatabaseSupport.PASSWORD);
    }

    @Autowired
    protected DatabaseClient databaseClient;
    @Autowired
    protected QuotaPlanRepository quotaPlanRepository;
    @Autowired
    protected TenantRepository tenantRepository;
    @Autowired
    protected TenantQuotaRepository tenantQuotaRepository;
    @Autowired
    protected CallLogRepository callLogRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected JwtService jwtService;
    @LocalServerPort
    protected int port;

    @BeforeEach
    void resetDatabaseState() {
        executeStatements(List.of(
                "DELETE FROM t_call_log",
                "DELETE FROM t_app_key",
                "DELETE FROM t_tenant_quota WHERE tenant_id IN (SELECT id FROM t_tenant WHERE name <> 'Lazyday Platform')",
                "DELETE FROM t_user WHERE username <> 'admin'",
                "DELETE FROM t_tenant WHERE name <> 'Lazyday Platform'",
                "DELETE FROM t_quota_plan WHERE name NOT IN ('Free','Pro','Enterprise')",
                "UPDATE t_quota_plan SET qps_limit = 5, daily_limit = 1000, monthly_limit = 10000, max_app_keys = 5, status = 'ACTIVE' WHERE name = 'Free'",
                "UPDATE t_quota_plan SET qps_limit = 50, daily_limit = 50000, monthly_limit = 500000, max_app_keys = -1, status = 'ACTIVE' WHERE name = 'Pro'",
                "UPDATE t_quota_plan SET qps_limit = 200, daily_limit = 500000, monthly_limit = 5000000, max_app_keys = -1, status = 'ACTIVE' WHERE name = 'Enterprise'",
                """
                INSERT INTO t_tenant_quota (tenant_id, plan_id)
                SELECT t.id, p.id
                FROM t_tenant t
                CROSS JOIN LATERAL (
                    SELECT id FROM t_quota_plan WHERE name = 'Free' ORDER BY id LIMIT 1
                ) p
                WHERE t.name = 'Lazyday Platform'
                  AND NOT EXISTS (
                      SELECT 1 FROM t_tenant_quota tq WHERE tq.tenant_id = t.id
                  )
                """,
                """
                UPDATE t_tenant_quota
                SET plan_id = (SELECT id FROM t_quota_plan WHERE name = 'Free' ORDER BY id LIMIT 1),
                    custom_qps_limit = NULL,
                    custom_daily_limit = NULL,
                    custom_monthly_limit = NULL,
                    custom_max_app_keys = NULL
                WHERE tenant_id = (SELECT id FROM t_tenant WHERE name = 'Lazyday Platform' ORDER BY id LIMIT 1)
                """
        ));
    }

    protected QuotaPlan planByName(String name) {
        return quotaPlanRepository.findAll()
                .filter(plan -> name.equals(plan.getName()))
                .blockFirst();
    }

    protected Tenant createTenant(String suffix) {
        Tenant tenant = new Tenant();
        tenant.setName("Tenant-" + suffix);
        tenant.setStatus("ACTIVE");
        tenant.setPlanType("FREE");
        tenant.setContactEmail(suffix + "@example.com");
        return tenantRepository.insert(tenant).block();
    }

    protected User createTenantAdmin(Tenant tenant, String suffix) {
        User user = new User();
        user.setName("Tenant Admin " + suffix);
        user.setUsername("tenant_" + suffix);
        user.setEmail("tenant_" + suffix + "@example.com");
        user.setPasswordHash("test-hash");
        user.setRole("TENANT_ADMIN");
        user.setStatus("ACTIVE");
        user.setTenantId(tenant.getId());
        return userRepository.insert(user).block();
    }

    protected TenantQuota bindTenantToPlan(Tenant tenant, String planName) {
        QuotaPlan plan = planByName(planName);
        TenantQuota tenantQuota = new TenantQuota();
        tenantQuota.setTenantId(tenant.getId());
        tenantQuota.setPlanId(plan.getId());
        return tenantQuotaRepository.insert(tenantQuota).block();
    }

    protected String accessToken(User user) {
        return jwtService.generateAccessToken(user.getId(), user.getTenantId(), user.getRole());
    }

    protected String adminAccessToken() {
        User admin = userRepository.findByUsername("admin").block();
        return accessToken(admin);
    }

    protected void insertCallLog(Long tenantId, String appKey, String path, short statusCode, Instant requestTime) {
        CallLog callLog = new CallLog();
        callLog.setId(CALL_LOG_ID_SEQUENCE.incrementAndGet());
        callLog.setTenantId(tenantId);
        callLog.setAppKey(appKey);
        callLog.setPath(path);
        callLog.setMethod("GET");
        callLog.setStatusCode(statusCode);
        callLog.setLatencyMs(10);
        callLog.setClientIp("127.0.0.1");
        callLog.setRequestTime(requestTime);

        databaseClient.sql("""
                        INSERT INTO t_call_log
                        (id, tenant_id, app_key, path, method, status_code, latency_ms, client_ip, request_time)
                        VALUES (:id, :tenantId, :appKey, :path, :method, :statusCode, :latencyMs, :clientIp, :requestTime)
                        """)
                .bind("id", callLog.getId())
                .bind("tenantId", callLog.getTenantId())
                .bind("appKey", callLog.getAppKey())
                .bind("path", callLog.getPath())
                .bind("method", callLog.getMethod())
                .bind("statusCode", callLog.getStatusCode())
                .bind("latencyMs", callLog.getLatencyMs())
                .bind("clientIp", callLog.getClientIp())
                .bind("requestTime", callLog.getRequestTime())
                .then()
                .block();
    }

    protected WebTestClient.RequestHeadersSpec<?> authenticatedGet(String uri, String accessToken) {
        return webTestClient().get()
                .uri(uri)
                .cookie(CookieUtils.ACCESS_TOKEN_COOKIE, accessToken);
    }

    protected WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    protected void executeStatements(List<String> statements) {
        statements.forEach(sql -> databaseClient.sql(sql).then().block());
    }
}
