package com.fan.lazyday.application.facade;

import com.fan.lazyday.interfaces.response.UserInfoResponse;
import reactor.core.publisher.Mono;

public interface AuthFacade {
    Mono<UserInfoResponse> register(String username, String email, String password, String tenantName);
    Mono<UserInfoResponse> login(String username, String password);
    Mono<UserInfoResponse> adminLogin(String username, String password);
    Mono<UserInfoResponse> getUserInfo(Long userId);
}
