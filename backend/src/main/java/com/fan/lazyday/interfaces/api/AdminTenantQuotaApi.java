package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.OverrideQuotaRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

public interface AdminTenantQuotaApi {

    @PutMapping("/tenants/{tenantId}/quota")
    Mono<ApiResponse<EffectiveQuotaResponse>> overrideTenantQuota(
            @PathVariable Long tenantId,
            @RequestBody @Validated Mono<OverrideQuotaRequest> request
    );
}
