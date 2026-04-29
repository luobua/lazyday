package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.domain.appkey.repository.AppKeyRepository;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.event.TenantSuspendedEvent;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.interfaces.response.AdminOverviewMetricsResponse;
import com.fan.lazyday.interfaces.response.AdminTenantDetailResponse;
import com.fan.lazyday.interfaces.response.AdminTenantSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTenantFacadeImplTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CallLogRepository callLogRepository;
    @Mock
    private AppKeyRepository appKeyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private AdminTenantFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new AdminTenantFacadeImpl(
                tenantRepository,
                callLogRepository,
                appKeyRepository,
                userRepository,
                domainEventPublisher
        );
    }

    @Test
    @DisplayName("listTenants: 映射分页租户摘要")
    void listTenants_shouldMapPageResponse() {
        AdminTenantSummaryResponse summary = new AdminTenantSummaryResponse();
        summary.setId(1L);
        summary.setName("Acme");
        summary.setEmail("admin@acme.test");
        summary.setStatus("ACTIVE");
        summary.setPlanId(2L);
        summary.setPlanName("Pro");

        when(tenantRepository.findPage(eq("acme"), eq("ACTIVE"), any(PageRequest.class)))
                .thenReturn(Mono.just(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1)));

        StepVerifier.create(facade.listTenants("acme", "ACTIVE", 0, 20))
                .assertNext(page -> {
                    assertThat(page.getList()).hasSize(1);
                    assertThat(page.getList().getFirst().getPlanName()).isEqualTo("Pro");
                    assertThat(page.getTotal()).isEqualTo(1);
                    assertThat(page.getPage()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTenantDetail: 返回租户详情聚合")
    void getTenantDetail_shouldAggregateDetail() {
        AdminTenantDetailResponse base = new AdminTenantDetailResponse();
        base.setId(1L);
        base.setName("Acme");
        base.setStatus("ACTIVE");
        base.setPlanId(2L);
        base.setPlanName("Pro");
        base.setQpsLimit(50);
        base.setDailyLimit(10_000L);
        base.setMonthlyLimit(100_000L);
        base.setMaxAppKeys(5);

        when(tenantRepository.findDetailWithQuota(1L)).thenReturn(Mono.just(base));
        when(callLogRepository.countByTenantIdAndTimeRange(eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(7L), Mono.just(30L));
        when(appKeyRepository.countByTenantId(1L)).thenReturn(Mono.just(3L));
        when(userRepository.findTenantAdminEmailsByTenantId(1L)).thenReturn(Flux.just("admin@acme.test"));

        StepVerifier.create(facade.getTenantDetail(1L))
                .assertNext(detail -> {
                    assertThat(detail.getDailyUsage()).isEqualTo(7L);
                    assertThat(detail.getMonthlyUsage()).isEqualTo(30L);
                    assertThat(detail.getAppKeyCount()).isEqualTo(3L);
                    assertThat(detail.getTenantAdminEmails()).containsExactly("admin@acme.test");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTenantDetail: tenant 不存在返回 TENANT_NOT_FOUND")
    void getTenantDetail_notFound_shouldThrow() {
        when(tenantRepository.findDetailWithQuota(404L)).thenReturn(Mono.empty());

        StepVerifier.create(facade.getTenantDetail(404L))
                .expectErrorMatches(error -> error instanceof BizException be
                        && be.getErrorCode().equals("TENANT_NOT_FOUND"))
                .verify();
    }

    @Test
    @DisplayName("suspendTenant: ACTIVE -> SUSPENDED 发布事件")
    void suspendTenant_active_shouldPublishEvent() {
        Tenant active = tenant(1L, "ACTIVE");
        AdminTenantSummaryResponse suspended = new AdminTenantSummaryResponse();
        suspended.setId(1L);
        suspended.setStatus("SUSPENDED");

        when(tenantRepository.findById(1L)).thenReturn(Mono.just(active));
        when(tenantRepository.update(eq(1L), any(Update.class))).thenReturn(Mono.just(1L));
        when(tenantRepository.findSummaryById(1L)).thenReturn(Mono.just(suspended));

        StepVerifier.create(facade.suspendTenant(1L))
                .assertNext(response -> assertThat(response.getStatus()).isEqualTo("SUSPENDED"))
                .verifyComplete();

        ArgumentCaptor<TenantSuspendedEvent> eventCaptor = ArgumentCaptor.forClass(TenantSuspendedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("suspendTenant: 已暂停保持幂等且不发布事件")
    void suspendTenant_alreadySuspended_shouldNotPublishEvent() {
        Tenant suspended = tenant(1L, "SUSPENDED");
        AdminTenantSummaryResponse summary = new AdminTenantSummaryResponse();
        summary.setId(1L);
        summary.setStatus("SUSPENDED");

        when(tenantRepository.findById(1L)).thenReturn(Mono.just(suspended));
        when(tenantRepository.findSummaryById(1L)).thenReturn(Mono.just(summary));

        StepVerifier.create(facade.suspendTenant(1L))
                .expectNextCount(1)
                .verifyComplete();

        verify(tenantRepository, never()).update(eq(1L), any(Update.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("getOverview: 映射平台聚合指标")
    void getOverview_shouldReturnMetrics() {
        AdminOverviewMetricsResponse metrics = new AdminOverviewMetricsResponse();
        metrics.setTotalTenants(10L);
        metrics.setActiveTenants7d(4L);
        metrics.setTodayCalls(100L);
        metrics.setTodaySuccessRate(0.95);

        when(tenantRepository.getOverviewMetrics()).thenReturn(Mono.just(metrics));

        StepVerifier.create(facade.getOverview())
                .assertNext(response -> {
                    assertThat(response.getTotalTenants()).isEqualTo(10L);
                    assertThat(response.getTodaySuccessRate()).isEqualTo(0.95);
                })
                .verifyComplete();
    }

    private Tenant tenant(Long id, String status) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setStatus(status);
        return tenant;
    }
}
