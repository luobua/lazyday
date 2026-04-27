package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.LoginRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.CsrfTokenResponse;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface AdminAuthApi {

    @PostMapping("/auth/login")
    Mono<ApiResponse<UserInfoResponse>> login(@RequestBody @Validated Mono<LoginRequest> request, ServerWebExchange exchange);

    @PostMapping("/auth/logout")
    Mono<ApiResponse<Void>> logout(ServerWebExchange exchange);

    @GetMapping("/auth/me")
    Mono<ApiResponse<UserInfoResponse>> me();

    @GetMapping("/auth/csrf-token")
    Mono<ApiResponse<CsrfTokenResponse>> csrfToken(ServerWebExchange exchange);
}
