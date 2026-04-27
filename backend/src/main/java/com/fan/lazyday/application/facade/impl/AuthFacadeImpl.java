package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.AuthFacade;
import com.fan.lazyday.application.service.AuthService;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {

    private final AuthService authService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<UserInfoResponse> register(String username, String email, String password, String tenantName) {
        return transactionalOperator.transactional(
                Mono.defer(() -> {
                    Tenant tenant = new Tenant();
                    tenant.setName(tenantName);
                    tenant.setStatus("ACTIVE");
                    tenant.setPlanType("FREE");
                    tenant.setContactEmail(email);
                    return tenantRepository.insert(tenant);
                }).flatMap(savedTenant ->
                        authService.createUser(username, email, password, savedTenant.getId(), "TENANT_ADMIN")
                                .map(user -> toUserInfoResponse(user, savedTenant.getId()))
                )
        );
    }

    @Override
    public Mono<UserInfoResponse> login(String username, String password) {
        return authService.authenticate(username, password)
                .map(user -> toUserInfoResponse(user, user.getTenantId()));
    }

    @Override
    public Mono<UserInfoResponse> adminLogin(String username, String password) {
        return authService.authenticate(username, password)
                .flatMap(user -> {
                    if (!"PLATFORM_ADMIN".equals(user.getRole())) {
                        return Mono.error(BizException.forbidden("FORBIDDEN_ROLE", "权限不足"));
                    }
                    return Mono.just(toUserInfoResponse(user, user.getTenantId()));
                });
    }

    @Override
    public Mono<UserInfoResponse> getUserInfo(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(BizException.notFound("USER_NOT_FOUND", "用户不存在")))
                .map(user -> toUserInfoResponse(user, user.getTenantId()));
    }

    private UserInfoResponse toUserInfoResponse(User user, Long tenantId) {
        UserInfoResponse response = new UserInfoResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setTenantId(tenantId);
        return response;
    }
}
