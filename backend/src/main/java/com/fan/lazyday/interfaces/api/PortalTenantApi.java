package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.UpdateTenantRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.TenantResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public interface PortalTenantApi {

    @GetMapping("/tenant")
    Mono<ApiResponse<TenantResponse>> getTenant();

    @PutMapping("/tenant")
    Mono<ApiResponse<TenantResponse>> updateTenant(@RequestBody @Validated Mono<UpdateTenantRequest> request);
}
