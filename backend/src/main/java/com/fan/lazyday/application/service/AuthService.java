package com.fan.lazyday.application.service;

import com.fan.lazyday.domain.user.po.User;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<User> createUser(String username, String email, String password, Long tenantId, String role);
    Mono<User> authenticate(String username, String password);
}
