package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.domain.event.QuotaPlanChangedEvent;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.quotaplan.repository.QuotaPlanRepository;
import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.domain.tenantquota.repository.TenantQuotaRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.interfaces.request.OverrideQuotaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.relational.core.query.Update;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaFacadeImplTest {

    @Mock
    private QuotaPlanRepository quotaPlanRepository;
    @Mock
    private TenantQuotaRepository tenantQuotaRepository;
    @Mock
    private CallLogRepository callLogRepository;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private QuotaFacadeImpl quotaFacade;

    @BeforeEach
    void setUp() {
        quotaFacade = new QuotaFacadeImpl(
                quotaPlanRepository,
                tenantQuotaRepository,
                callLogRepository,
                transactionalOperator,
                domainEventPublisher
        );
    }

    @Test
    @DisplayName("listPlans: 返回绑定租户数")
    void listPlans_shouldIncludeBindingCount() {
        QuotaPlan plan = plan(2L, "Pro");
        when(quotaPlanRepository.findAllWithBindingCount())
                .thenReturn(reactor.core.publisher.Flux.just(new QuotaPlanRepository.QuotaPlanWithBindingCount(plan, 3L)));

        StepVerifier.create(quotaFacade.listPlans())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.getFirst().getBindingCount()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getEffectiveQuota: custom 值优先于套餐默认值")
    void getEffectiveQuota_shouldPreferCustomValues() {
        TenantQuota tenantQuota = new TenantQuota();
        tenantQuota.setTenantId(100L);
        tenantQuota.setPlanId(2L);
        tenantQuota.setCustomQpsLimit(200);

        QuotaPlan plan = new QuotaPlan();
        plan.setId(2L);
        plan.setName("Pro");
        plan.setQpsLimit(50);
        plan.setDailyLimit(50_000L);
        plan.setMonthlyLimit(500_000L);
        plan.setMaxAppKeys(-1);

        when(tenantQuotaRepository.findByTenantId(100L)).thenReturn(Mono.just(tenantQuota));
        when(quotaPlanRepository.findById(2L)).thenReturn(Mono.just(plan));

        StepVerifier.create(quotaFacade.getEffectiveQuota(100L))
                .assertNext(response -> {
                    assertThat(response.getPlanName()).isEqualTo("Pro");
                    assertThat(response.getQpsLimit()).isEqualTo(200);
                    assertThat(response.getDailyLimit()).isEqualTo(50_000L);
                    assertThat(response.getMonthlyLimit()).isEqualTo(500_000L);
                    assertThat(response.getMaxAppKeys()).isEqualTo(-1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("disablePlan: 套餐已被绑定 -> 抛 PLAN_IN_USE")
    void disablePlan_inUse_shouldThrowConflict() {
        QuotaPlan plan = new QuotaPlan();
        plan.setId(2L);
        plan.setStatus("ACTIVE");

        when(quotaPlanRepository.findById(2L)).thenReturn(Mono.just(plan));
        when(tenantQuotaRepository.countByPlanId(2L)).thenReturn(Mono.just(1L));

        StepVerifier.create(quotaFacade.disablePlan(2L))
                .expectErrorMatches(ex ->
                        ex instanceof BizException be
                                && be.getErrorCode().equals("PLAN_IN_USE")
                                && be.getMessage().equals("套餐已被租户绑定")
                )
                .verify();
    }

    @Test
    @DisplayName("overrideTenantQuota: 更新 planId 与 custom 配额并立即返回有效值")
    void overrideTenantQuota_shouldUpdateAndReturnEffectiveQuota() {
        TenantQuota existing = new TenantQuota();
        existing.setTenantId(100L);
        existing.setPlanId(1L);

        QuotaPlan updatedPlan = new QuotaPlan();
        updatedPlan.setId(2L);
        updatedPlan.setName("Pro");
        updatedPlan.setQpsLimit(50);
        updatedPlan.setDailyLimit(50_000L);
        updatedPlan.setMonthlyLimit(500_000L);
        updatedPlan.setMaxAppKeys(-1);

        TenantQuota updatedTenantQuota = new TenantQuota();
        updatedTenantQuota.setTenantId(100L);
        updatedTenantQuota.setPlanId(2L);
        updatedTenantQuota.setCustomQpsLimit(200);

        OverrideQuotaRequest request = new OverrideQuotaRequest();
        request.setPlanId(2L);
        request.setCustomQpsLimit(200);

        when(tenantQuotaRepository.findByTenantId(100L))
                .thenReturn(Mono.just(existing), Mono.just(updatedTenantQuota));
        when(quotaPlanRepository.findById(2L)).thenAnswer(invocation -> Mono.just(updatedPlan));
        when(tenantQuotaRepository.updateByTenantId(eq(100L), any(Update.class))).thenReturn(Mono.just(1L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(quotaFacade.overrideTenantQuota(100L, request))
                .assertNext(response -> {
                    assertThat(response.getPlanName()).isEqualTo("Pro");
                    assertThat(response.getQpsLimit()).isEqualTo(200);
                    assertThat(response.getDailyLimit()).isEqualTo(50_000L);
                    assertThat(response.getMonthlyLimit()).isEqualTo(500_000L);
                    assertThat(response.getMaxAppKeys()).isEqualTo(-1);
                })
                .verifyComplete();

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(tenantQuotaRepository).updateByTenantId(eq(100L), updateCaptor.capture());
        assertThat(updateCaptor.getValue()).isNotNull();

        ArgumentCaptor<QuotaPlanChangedEvent> eventCaptor = ArgumentCaptor.forClass(QuotaPlanChangedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().previousPlanId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().newPlanId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("overrideTenantQuota: planId 相同但 custom 变更也发布事件")
    void overrideTenantQuota_customChange_shouldPublishEvent() {
        TenantQuota existing = new TenantQuota();
        existing.setTenantId(100L);
        existing.setPlanId(2L);
        existing.setCustomQpsLimit(100);

        TenantQuota updatedTenantQuota = new TenantQuota();
        updatedTenantQuota.setTenantId(100L);
        updatedTenantQuota.setPlanId(2L);
        updatedTenantQuota.setCustomQpsLimit(200);

        QuotaPlan plan = plan(2L, "Pro");

        OverrideQuotaRequest request = new OverrideQuotaRequest();
        request.setPlanId(2L);
        request.setCustomQpsLimit(200);

        when(tenantQuotaRepository.findByTenantId(100L))
                .thenReturn(Mono.just(existing), Mono.just(updatedTenantQuota));
        when(quotaPlanRepository.findById(2L)).thenReturn(Mono.just(plan));
        when(tenantQuotaRepository.updateByTenantId(eq(100L), any(Update.class))).thenReturn(Mono.just(1L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(quotaFacade.overrideTenantQuota(100L, request))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<QuotaPlanChangedEvent> eventCaptor = ArgumentCaptor.forClass(QuotaPlanChangedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().previousPlanId()).isEqualTo(2L);
        assertThat(eventCaptor.getValue().newPlanId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("overrideTenantQuota: 请求与当前绑定完全一致时不发布事件")
    void overrideTenantQuota_idempotent_shouldSuppressEvent() {
        TenantQuota existing = new TenantQuota();
        existing.setTenantId(100L);
        existing.setPlanId(2L);
        existing.setCustomQpsLimit(200);
        existing.setCustomDailyLimit(50_000L);

        QuotaPlan plan = plan(2L, "Pro");

        OverrideQuotaRequest request = new OverrideQuotaRequest();
        request.setPlanId(2L);
        request.setCustomQpsLimit(200);
        request.setCustomDailyLimit(50_000L);

        when(tenantQuotaRepository.findByTenantId(100L))
                .thenReturn(Mono.just(existing), Mono.just(existing));
        when(quotaPlanRepository.findById(2L)).thenReturn(Mono.just(plan));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(quotaFacade.overrideTenantQuota(100L, request))
                .expectNextCount(1)
                .verifyComplete();

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("bindTenantPlan: plan 变更后发布事件")
    void bindTenantPlan_changedPlan_shouldPublishEvent() {
        TenantQuota existing = new TenantQuota();
        existing.setTenantId(100L);
        existing.setPlanId(1L);

        TenantQuota updated = new TenantQuota();
        updated.setTenantId(100L);
        updated.setPlanId(2L);

        QuotaPlan plan = plan(2L, "Pro");

        when(quotaPlanRepository.findById(2L)).thenReturn(Mono.just(plan));
        when(tenantQuotaRepository.findByTenantId(100L))
                .thenReturn(Mono.just(existing), Mono.just(updated));
        when(tenantQuotaRepository.updateByTenantId(eq(100L), any(Update.class))).thenReturn(Mono.just(1L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(quotaFacade.bindTenantPlan(100L, 2L))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<QuotaPlanChangedEvent> eventCaptor = ArgumentCaptor.forClass(QuotaPlanChangedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().previousPlanId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().newPlanId()).isEqualTo(2L);
    }

    private QuotaPlan plan(Long id, String name) {
        QuotaPlan plan = new QuotaPlan();
        plan.setId(id);
        plan.setName(name);
        plan.setQpsLimit(50);
        plan.setDailyLimit(50_000L);
        plan.setMonthlyLimit(500_000L);
        plan.setMaxAppKeys(-1);
        return plan;
    }
}
