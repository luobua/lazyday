package com.fan.lazyday;

import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import com.fan.lazyday.infrastructure.security.CookieUtils;
import com.fan.lazyday.interfaces.request.CreatePlanRequest;
import com.fan.lazyday.interfaces.request.OverrideQuotaRequest;
import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class QuotaAndLoggingAcceptanceTest extends PostgresSpringBootIntegrationTestSupport {

    private static final String CSRF_TOKEN = "phase2a-csrf-token";

    @Test
    @DisplayName("19.3 超过 Free 5 QPS 后 /quota 返回 429 与 Retry-After")
    void portalQuota_whenQpsExceeded_shouldReturn429WithRetryAfter() {
        Tenant tenant = createTenant("qps-limit");
        bindTenantToPlan(tenant, "Free");
        User tenantAdmin = createTenantAdmin(tenant, "qps-limit");
        String accessToken = accessToken(tenantAdmin);

        EntityExchangeResult<String> throttledResponse = null;
        for (int i = 0; i < 10; i++) {
            EntityExchangeResult<String> result = authenticatedGet("/api/portal/v1/quota", accessToken)
                    .exchange()
                    .expectBody(String.class)
                    .returnResult();
            if (result.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                throttledResponse = result;
                break;
            }
        }

        assertThat(throttledResponse).isNotNull();
        assertThat(throttledResponse.getResponseHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(throttledResponse.getResponseBody()).contains(ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    @DisplayName("成功请求应返回 request-id 与限流响应头且不记录响应提交异常")
    void portalQuota_whenAllowed_shouldExposeHeadersWithoutCommittedResponseErrors(CapturedOutput output) {
        Tenant tenant = createTenant("quota-headers");
        bindTenantToPlan(tenant, "Enterprise");
        User tenantAdmin = createTenantAdmin(tenant, "quota-headers");

        EntityExchangeResult<String> result = authenticatedGet("/api/portal/v1/quota", accessToken(tenantAdmin))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getResponseHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("200");
        assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("199");
        assertNoCommittedResponseErrors(output);
    }

    @Test
    @DisplayName("19.4 当日成功调用达到 1000 次后下一次返回 QUOTA_DAILY_EXCEEDED")
    void portalQuota_whenDailyQuotaExceeded_shouldReturn429() {
        Tenant tenant = createTenant("daily-limit");
        bindTenantToPlan(tenant, "Free");
        User tenantAdmin = createTenantAdmin(tenant, "daily-limit");

        Instant requestTime = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .plusSeconds(60)
                .toInstant();
        insertSuccessfulCallLogs(tenant.getId(), 1000, "/api/portal/v1/quota", requestTime);

        EntityExchangeResult<String> result = authenticatedGet("/api/portal/v1/quota", accessToken(tenantAdmin))
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getResponseBody()).contains(ErrorCode.QUOTA_DAILY_EXCEEDED.getCode());
    }

    @Test
    @DisplayName("19.5 发起一次 /quota 后 5 秒内可通过 /logs 查询到该调用")
    void portalLogs_shouldExposeRecentQuotaCallWithinFiveSeconds() {
        Tenant tenant = createTenant("log-query");
        bindTenantToPlan(tenant, "Enterprise");
        User tenantAdmin = createTenantAdmin(tenant, "log-query");
        String accessToken = accessToken(tenantAdmin);
        Instant from = Instant.now().minusSeconds(60);
        Instant to = Instant.now().plusSeconds(60);

        authenticatedGet("/api/portal/v1/quota", accessToken)
                .exchange()
                .expectStatus()
                .isOk();

        String responseBody = waitForLogsContaining(accessToken, from, to, "/api/portal/v1/quota");
        assertThat(responseBody).contains("/api/portal/v1/quota");
    }

    @Test
    @DisplayName("19.6 Tenant A 查询 /logs 只看到自己的记录")
    void portalLogs_shouldRemainTenantIsolated() {
        Tenant tenantA = createTenant("tenant-a");
        Tenant tenantB = createTenant("tenant-b");
        bindTenantToPlan(tenantA, "Enterprise");
        bindTenantToPlan(tenantB, "Enterprise");
        User tenantAdminA = createTenantAdmin(tenantA, "tenant-a");

        Instant requestTime = Instant.now().minusSeconds(30);
        insertCallLog(tenantA.getId(), "ak-tenant-a", "/tenant-a-only", (short) 200, requestTime);
        insertCallLog(tenantB.getId(), "ak-tenant-b", "/tenant-b-only", (short) 200, requestTime);

        String responseBody = authenticatedGet(logsUri(requestTime.minusSeconds(30), requestTime.plusSeconds(30)),
                accessToken(tenantAdminA))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).contains("/tenant-a-only");
        assertThat(responseBody).doesNotContain("/tenant-b-only");
    }

    @Test
    @DisplayName("19.7 Admin 创建套餐并绑定后 Tenant A 的 /quota 立即返回新套餐限额")
    void adminBindPlan_shouldImmediatelyReflectInPortalQuota() {
        Long controlTenantId = createAdminRateLimitTenantId("bind-plan");
        Tenant tenant = createTenant("bind-target");
        User tenantAdmin = createTenantAdmin(tenant, "bind-target");
        String adminToken = adminAccessToken();

        String planName = "Spec-Pro-" + System.nanoTime();
        CreatePlanRequest createPlanRequest = new CreatePlanRequest();
        createPlanRequest.setName(planName);
        createPlanRequest.setQpsLimit(50);
        createPlanRequest.setDailyLimit(50_000L);
        createPlanRequest.setMonthlyLimit(500_000L);
        createPlanRequest.setMaxAppKeys(-1);

        adminPost("/api/admin/v1/plans", adminToken, controlTenantId)
                .bodyValue(createPlanRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo(planName);

        QuotaPlan createdPlan = planByName(planName);
        assertThat(createdPlan).isNotNull();

        OverrideQuotaRequest bindRequest = new OverrideQuotaRequest();
        bindRequest.setPlanId(createdPlan.getId());

        adminPut("/api/admin/v1/tenants/" + tenant.getId() + "/quota", adminToken, controlTenantId)
                .bodyValue(bindRequest)
                .exchange()
                .expectStatus()
                .isOk();

        authenticatedGet("/api/portal/v1/quota", accessToken(tenantAdmin))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.plan_name").isEqualTo(planName)
                .jsonPath("$.data.qps_limit").isEqualTo(50)
                .jsonPath("$.data.daily_limit").isEqualTo(50_000)
                .jsonPath("$.data.monthly_limit").isEqualTo(500_000);
    }

    @Test
    @DisplayName("19.8 Admin custom_qps=200 后 Tenant A 的 effective_qps 立即生效")
    void adminOverrideQuota_shouldImmediatelyReflectCustomQps() {
        Long controlTenantId = createAdminRateLimitTenantId("override-qps");
        Tenant tenant = createTenant("override-target");
        bindTenantToPlan(tenant, "Pro");
        User tenantAdmin = createTenantAdmin(tenant, "override-target");

        OverrideQuotaRequest overrideRequest = new OverrideQuotaRequest();
        overrideRequest.setPlanId(planByName("Pro").getId());
        overrideRequest.setCustomQpsLimit(200);

        adminPut("/api/admin/v1/tenants/" + tenant.getId() + "/quota", adminAccessToken(), controlTenantId)
                .bodyValue(overrideRequest)
                .exchange()
                .expectStatus()
                .isOk();

        authenticatedGet("/api/portal/v1/quota", accessToken(tenantAdmin))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.plan_name").isEqualTo("Pro")
                .jsonPath("$.data.qps_limit").isEqualTo(200);
    }

    @Test
    @DisplayName("19.9 Internal API 使用错误 X-Internal-Api-Key 返回 403")
    void internalQuota_withWrongApiKey_shouldReturn403(CapturedOutput output) {
        Tenant tenant = createTenant("internal-auth");
        bindTenantToPlan(tenant, "Free");

        EntityExchangeResult<String> result = webTestClient().get()
                .uri("/internal/v1/quota/effective?tenantId=" + tenant.getId())
                .header("X-Internal-Api-Key", "wrong-key")
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getResponseHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(result.getResponseBody()).contains(ErrorCode.INTERNAL_AUTH_FAILED.getCode());
        assertNoCommittedResponseErrors(output);
    }

    @Test
    @DisplayName("19.11 套餐已被绑定时 DELETE 返回 409 PLAN_IN_USE")
    void deletePlan_whenPlanIsInUse_shouldReturn409() {
        Long controlTenantId = createAdminRateLimitTenantId("delete-plan");
        Tenant tenant = createTenant("plan-in-use");
        bindTenantToPlan(tenant, "Pro");

        EntityExchangeResult<String> result = adminDelete(
                "/api/admin/v1/plans/" + planByName("Pro").getId(),
                adminAccessToken(),
                controlTenantId
        )
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.getResponseBody()).contains(ErrorCode.PLAN_IN_USE.getCode());
    }

    private void insertSuccessfulCallLogs(Long tenantId, int count, String path, Instant requestTime) {
        long baseId = System.currentTimeMillis() * 10_000;
        String sql = """
                INSERT INTO t_call_log
                (id, tenant_id, app_key, path, method, status_code, latency_ms, client_ip, request_time)
                SELECT %d + seq, %d, 'bulk-app-key', '%s', 'GET', 200, 10, '127.0.0.1', TIMESTAMPTZ '%s'
                FROM generate_series(1, %d) AS seq
                """.formatted(baseId, tenantId, path, requestTime, count);
        databaseClient.sql(sql).then().block();
    }

    private Long createAdminRateLimitTenantId(String suffix) {
        Tenant tenant = createTenant("admin-control-" + suffix);
        bindTenantToPlan(tenant, "Enterprise");
        return tenant.getId();
    }

    private String waitForLogsContaining(String accessToken, Instant from, Instant to, String expectedPath) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        String lastResponseBody = "";
        while (System.nanoTime() < deadline) {
            lastResponseBody = authenticatedGet(logsUri(from, to), accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();
            if (lastResponseBody != null && lastResponseBody.contains(expectedPath)) {
                return lastResponseBody;
            }
            LockSupport.parkNanos(Duration.ofMillis(200).toNanos());
        }
        return lastResponseBody;
    }

    private String logsUri(Instant from, Instant to) {
        return UriComponentsBuilder.fromPath("/api/portal/v1/logs")
                .queryParam("startTime", from)
                .queryParam("endTime", to)
                .queryParam("page", 0)
                .queryParam("size", 20)
                .build()
                .toUriString();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.RequestBodySpec adminPost(
            String uri, String adminToken, Long tenantId) {
        return webTestClient().post()
                .uri(uri)
                .cookie(CookieUtils.ACCESS_TOKEN_COOKIE, adminToken)
                .cookie(CookieUtils.CSRF_TOKEN_COOKIE, CSRF_TOKEN)
                .header("X-CSRF-Token", CSRF_TOKEN)
                .header("X-Tenant-Id", String.valueOf(tenantId));
    }

    private org.springframework.test.web.reactive.server.WebTestClient.RequestBodySpec adminPut(
            String uri, String adminToken, Long tenantId) {
        return webTestClient().put()
                .uri(uri)
                .cookie(CookieUtils.ACCESS_TOKEN_COOKIE, adminToken)
                .cookie(CookieUtils.CSRF_TOKEN_COOKIE, CSRF_TOKEN)
                .header("X-CSRF-Token", CSRF_TOKEN)
                .header("X-Tenant-Id", String.valueOf(tenantId));
    }

    private org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec<?> adminDelete(
            String uri, String adminToken, Long tenantId) {
        return webTestClient().delete()
                .uri(uri)
                .cookie(CookieUtils.ACCESS_TOKEN_COOKIE, adminToken)
                .cookie(CookieUtils.CSRF_TOKEN_COOKIE, CSRF_TOKEN)
                .header("X-CSRF-Token", CSRF_TOKEN)
                .header("X-Tenant-Id", String.valueOf(tenantId));
    }

    private void assertNoCommittedResponseErrors(CapturedOutput output) {
        assertThat(output.getAll())
                .doesNotContain("ReadOnlyHttpHeaders")
                .doesNotContain("UnsupportedOperationException")
                .doesNotContain("response already committed");
    }
}
