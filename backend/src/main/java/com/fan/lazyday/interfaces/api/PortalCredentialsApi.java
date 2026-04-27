package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.CreateAppKeyRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.AppKeyResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PortalCredentialsApi {

    @GetMapping("/credentials")
    Mono<ApiResponse<List<AppKeyResponse>>> list();

    @PostMapping("/credentials")
    Mono<ApiResponse<AppKeyResponse>> create(@RequestBody @Validated Mono<CreateAppKeyRequest> request);

    @PutMapping("/credentials/{id}/disable")
    Mono<ApiResponse<Void>> disable(@PathVariable Long id);

    @PutMapping("/credentials/{id}/enable")
    Mono<ApiResponse<Void>> enable(@PathVariable Long id);

    @PostMapping("/credentials/{id}/rotate-secret")
    Mono<ApiResponse<AppKeyResponse>> rotateSecret(@PathVariable Long id);

    @DeleteMapping("/credentials/{id}")
    Mono<ApiResponse<Void>> delete(@PathVariable Long id);
}
