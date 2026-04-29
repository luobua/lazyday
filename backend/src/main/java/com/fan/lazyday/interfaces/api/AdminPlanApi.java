package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.CreatePlanRequest;
import com.fan.lazyday.interfaces.request.UpdatePlanRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.QuotaPlanResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AdminPlanApi {

    @GetMapping("/plans")
    Mono<ApiResponse<List<QuotaPlanResponse>>> listPlans();

    @PostMapping("/plans")
    Mono<ApiResponse<QuotaPlanResponse>> createPlan(@RequestBody @Validated Mono<CreatePlanRequest> request);

    @PutMapping("/plans/{id}")
    Mono<ApiResponse<QuotaPlanResponse>> updatePlan(@PathVariable Long id, @RequestBody Mono<UpdatePlanRequest> request);

    @DeleteMapping("/plans/{id}")
    Mono<ApiResponse<QuotaPlanResponse>> deletePlan(@PathVariable Long id);
}
