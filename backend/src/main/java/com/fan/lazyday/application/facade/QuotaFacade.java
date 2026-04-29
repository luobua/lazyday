package com.fan.lazyday.application.facade;

import com.fan.lazyday.interfaces.request.CreatePlanRequest;
import com.fan.lazyday.interfaces.request.OverrideQuotaRequest;
import com.fan.lazyday.interfaces.request.UpdatePlanRequest;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import com.fan.lazyday.interfaces.response.QuotaPlanResponse;
import com.fan.lazyday.interfaces.response.QuotaUsageResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface QuotaFacade {

    Mono<List<QuotaPlanResponse>> listPlans();
    Mono<QuotaPlanResponse> createPlan(CreatePlanRequest request);
    Mono<QuotaPlanResponse> updatePlan(Long id, UpdatePlanRequest request);
    Mono<QuotaPlanResponse> disablePlan(Long id);

    Mono<EffectiveQuotaResponse> getEffectiveQuota(Long tenantId);
    Mono<EffectiveQuotaResponse> bindTenantPlan(Long tenantId, Long planId);
    Mono<EffectiveQuotaResponse> overrideTenantQuota(Long tenantId, OverrideQuotaRequest request);

    Mono<QuotaUsageResponse> getQuotaUsage(Long tenantId);
}
