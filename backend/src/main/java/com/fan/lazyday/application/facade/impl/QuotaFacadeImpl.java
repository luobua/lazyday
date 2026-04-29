package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.quotaplan.repository.QuotaPlanRepository;
import com.fan.lazyday.domain.tenantquota.entity.TenantQuotaEntity;
import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.domain.tenantquota.repository.TenantQuotaRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import com.fan.lazyday.interfaces.request.CreatePlanRequest;
import com.fan.lazyday.interfaces.request.OverrideQuotaRequest;
import com.fan.lazyday.interfaces.request.UpdatePlanRequest;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import com.fan.lazyday.interfaces.response.QuotaPlanResponse;
import com.fan.lazyday.interfaces.response.QuotaUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
@RequiredArgsConstructor
public class QuotaFacadeImpl implements QuotaFacade {

    private final QuotaPlanRepository quotaPlanRepository;
    private final TenantQuotaRepository tenantQuotaRepository;
    private final CallLogRepository callLogRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<List<QuotaPlanResponse>> listPlans() {
        return quotaPlanRepository.findAll()
                .map(this::toResponse)
                .collectList();
    }

    @Override
    public Mono<QuotaPlanResponse> createPlan(CreatePlanRequest request) {
        QuotaPlan po = new QuotaPlan();
        po.setName(request.getName());
        po.setQpsLimit(request.getQpsLimit());
        po.setDailyLimit(request.getDailyLimit());
        po.setMonthlyLimit(request.getMonthlyLimit());
        po.setMaxAppKeys(request.getMaxAppKeys());
        po.setStatus("ACTIVE");
        return quotaPlanRepository.save(po).map(this::toResponse);
    }

    @Override
    public Mono<QuotaPlanResponse> updatePlan(Long id, UpdatePlanRequest request) {
        return quotaPlanRepository.findById(id)
                .switchIfEmpty(Mono.error(BizException.of(ErrorCode.PLAN_NOT_FOUND, "套餐不存在")))
                .flatMap(plan -> {
                    Update update = Update.update("id", id); // no-op anchor
                    if (request.getName() != null) update = update.set("name", request.getName());
                    if (request.getQpsLimit() != null) update = update.set("qps_limit", request.getQpsLimit());
                    if (request.getDailyLimit() != null) update = update.set("daily_limit", request.getDailyLimit());
                    if (request.getMonthlyLimit() != null) update = update.set("monthly_limit", request.getMonthlyLimit());
                    if (request.getMaxAppKeys() != null) update = update.set("max_app_keys", request.getMaxAppKeys());
                    return quotaPlanRepository.updateById(id, update);
                })
                .then(quotaPlanRepository.findById(id))
                .map(this::toResponse);
    }

    @Override
    public Mono<QuotaPlanResponse> disablePlan(Long id) {
        return quotaPlanRepository.findById(id)
                .switchIfEmpty(Mono.error(BizException.of(ErrorCode.PLAN_NOT_FOUND, "套餐不存在")))
                .flatMap(plan -> tenantQuotaRepository.countByPlanId(id)
                        .flatMap(count -> {
                            if (count > 0) {
                                return Mono.error(BizException.of(ErrorCode.PLAN_IN_USE, "套餐已被租户绑定"));
                            }
                            return quotaPlanRepository.softDeleteById(id);
                        }))
                .then(quotaPlanRepository.findById(id))
                .map(this::toResponse);
    }

    @Override
    public Mono<EffectiveQuotaResponse> getEffectiveQuota(Long tenantId) {
        return tenantQuotaRepository.findByTenantId(tenantId)
                .switchIfEmpty(Mono.defer(() -> resolveDefaultTenantQuota(tenantId)))
                .flatMap(tq -> quotaPlanRepository.findById(tq.getPlanId())
                        .switchIfEmpty(Mono.error(BizException.of(ErrorCode.PLAN_NOT_FOUND, "套餐不存在")))
                        .map(plan -> toEffectiveQuotaResponse(TenantQuotaEntity.fromPo(tq), plan)));
    }

    @Override
    public Mono<EffectiveQuotaResponse> bindTenantPlan(Long tenantId, Long planId) {
        return quotaPlanRepository.findById(planId)
                .switchIfEmpty(Mono.error(BizException.of(ErrorCode.PLAN_NOT_FOUND, "套餐不存在")))
                .flatMap(plan -> transactionalOperator.transactional(
                        tenantQuotaRepository.findByTenantId(tenantId)
                                .flatMap(existing -> tenantQuotaRepository.updateByTenantId(
                                        tenantId,
                                        Update.update("plan_id", planId)
                                                .set("custom_qps_limit", null)
                                                .set("custom_daily_limit", null)
                                                .set("custom_monthly_limit", null)
                                                .set("custom_max_app_keys", null)
                                ))
                                .switchIfEmpty(Mono.defer(() -> {
                                    TenantQuota tenantQuota = new TenantQuota();
                                    tenantQuota.setTenantId(tenantId);
                                    tenantQuota.setPlanId(planId);
                                    return tenantQuotaRepository.save(tenantQuota).then(Mono.just(1L));
                                }))
                ))
                .then(Mono.defer(() -> getEffectiveQuota(tenantId)));
    }

    @Override
    public Mono<EffectiveQuotaResponse> overrideTenantQuota(Long tenantId, OverrideQuotaRequest request) {
        return quotaPlanRepository.findById(request.getPlanId())
                .switchIfEmpty(Mono.error(BizException.of(ErrorCode.PLAN_NOT_FOUND, "套餐不存在")))
                .flatMap(plan -> transactionalOperator.transactional(
                        tenantQuotaRepository.findByTenantId(tenantId)
                                .flatMap(existing -> tenantQuotaRepository.updateByTenantId(
                                        tenantId,
                                        Update.update("plan_id", request.getPlanId())
                                                .set("custom_qps_limit", request.getCustomQpsLimit())
                                                .set("custom_daily_limit", request.getCustomDailyLimit())
                                                .set("custom_monthly_limit", request.getCustomMonthlyLimit())
                                                .set("custom_max_app_keys", request.getCustomMaxAppKeys())
                                ))
                                .switchIfEmpty(Mono.defer(() -> {
                                    TenantQuota tenantQuota = new TenantQuota();
                                    tenantQuota.setTenantId(tenantId);
                                    tenantQuota.setPlanId(request.getPlanId());
                                    tenantQuota.setCustomQpsLimit(request.getCustomQpsLimit());
                                    tenantQuota.setCustomDailyLimit(request.getCustomDailyLimit());
                                    tenantQuota.setCustomMonthlyLimit(request.getCustomMonthlyLimit());
                                    tenantQuota.setCustomMaxAppKeys(request.getCustomMaxAppKeys());
                                    return tenantQuotaRepository.save(tenantQuota).then(Mono.just(1L));
                                }))
                ))
                .then(Mono.defer(() -> getEffectiveQuota(tenantId)));
    }

    @Override
    public Mono<QuotaUsageResponse> getQuotaUsage(Long tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        LocalDate nextMonthStart = today.with(TemporalAdjusters.firstDayOfNextMonth());
        Instant nextMonthStartInstant = nextMonthStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        Mono<Long> todayCalls = callLogRepository.countByTenantIdAndTimeRange(tenantId, todayStart, tomorrowStart);
        Mono<Long> monthCalls = callLogRepository.countByTenantIdAndTimeRange(tenantId, monthStartInstant, nextMonthStartInstant);

        return Mono.zip(getEffectiveQuota(tenantId), todayCalls, monthCalls)
                .map(tuple -> {
                    EffectiveQuotaResponse effectiveQuota = tuple.getT1();
                    QuotaUsageResponse response = new QuotaUsageResponse();
                    response.setPlanName(effectiveQuota.getPlanName());
                    response.setQpsLimit(effectiveQuota.getQpsLimit());
                    response.setDailyLimit(effectiveQuota.getDailyLimit());
                    response.setMonthlyLimit(effectiveQuota.getMonthlyLimit());
                    response.setMaxAppKeys(effectiveQuota.getMaxAppKeys());
                    response.setDailyUsed(tuple.getT2());
                    response.setMonthlyUsed(tuple.getT3());
                    return response;
                });
    }

    private QuotaPlanResponse toResponse(QuotaPlan plan) {
        QuotaPlanResponse response = new QuotaPlanResponse();
        response.setId(plan.getId());
        response.setName(plan.getName());
        response.setQpsLimit(plan.getQpsLimit());
        response.setDailyLimit(plan.getDailyLimit());
        response.setMonthlyLimit(plan.getMonthlyLimit());
        response.setMaxAppKeys(plan.getMaxAppKeys());
        response.setStatus(plan.getStatus());
        response.setCreateTime(plan.getCreateTime());
        return response;
    }

    private EffectiveQuotaResponse toEffectiveQuotaResponse(TenantQuotaEntity entity, QuotaPlan plan) {
        EffectiveQuotaResponse response = new EffectiveQuotaResponse();
        response.setPlanName(plan.getName());
        response.setQpsLimit(entity.getEffectiveQpsLimit(plan));
        response.setDailyLimit(entity.getEffectiveDailyLimit(plan));
        response.setMonthlyLimit(entity.getEffectiveMonthlyLimit(plan));
        response.setMaxAppKeys(entity.getEffectiveMaxAppKeys(plan));
        return response;
    }

    private Mono<TenantQuota> resolveDefaultTenantQuota(Long tenantId) {
        return quotaPlanRepository.findByStatus("ACTIVE")
                .filter(plan -> "Free".equalsIgnoreCase(plan.getName()))
                .next()
                .switchIfEmpty(Mono.error(BizException.notFound("TENANT_QUOTA_NOT_FOUND", "租户配额不存在")))
                .map(plan -> {
                    TenantQuota tenantQuota = new TenantQuota();
                    tenantQuota.setTenantId(tenantId);
                    tenantQuota.setPlanId(plan.getId());
                    return tenantQuota;
                });
    }
}
