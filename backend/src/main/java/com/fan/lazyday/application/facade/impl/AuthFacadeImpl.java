package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.AuthFacade;
import com.fan.lazyday.application.service.AuthService;
import com.fan.lazyday.application.service.EmailService;
import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.quotaplan.repository.QuotaPlanRepository;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.domain.tenantquota.repository.TenantQuotaRepository;
import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.infrastructure.security.JwtService;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {

    private final AuthService authService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TransactionalOperator transactionalOperator;
    private final QuotaPlanRepository quotaPlanRepository;
    private final TenantQuotaRepository tenantQuotaRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final ServiceProperties serviceProperties;

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
                                .flatMap(user -> assignFreePlan(savedTenant.getId())
                                        .thenReturn(toUserInfoResponse(user, savedTenant.getId())))
                )
        ).flatMap(response -> sendRegistrationVerifyEmail(response)
                .onErrorResume(ex -> {
                    log.warn("registration verify email failed: userId={}", response.getId(), ex);
                    return Mono.empty();
                })
                .thenReturn(response));
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

    @Override
    public Mono<Void> verifyEmail(String token) {
        Long userId = jwtService.validateEmailVerificationToken(token);
        if (userId == null) {
            return Mono.error(BizException.badRequest("EMAIL_VERIFY_INVALID", "邮箱验证链接无效或已过期"));
        }
        return userRepository.updateById(userId, Update.update("email_verified", true)).then();
    }

    private Mono<Void> sendRegistrationVerifyEmail(UserInfoResponse response) {
        String token = jwtService.generateEmailVerificationToken(response.getId());
        String verifyUrl = serviceProperties.getDomainHost()
                + serviceProperties.getPortalContextPathV1()
                + "/auth/verify-email?token="
                + token;
        return emailService.send(
                List.of(response.getEmail()),
                "欢迎注册 Lazyday - 请验证邮箱",
                "registration-verify",
                Map.of(
                        "userEmail", response.getEmail(),
                        "verifyUrl", verifyUrl,
                        "expiresInHours", 24
                )
        );
    }

    private Mono<Void> assignFreePlan(Long tenantId) {
        return quotaPlanRepository.findAllActive()
                .filter(plan -> "Free".equalsIgnoreCase(plan.getName()))
                .next()
                .flatMap(freePlan -> {
                    TenantQuota tq = new TenantQuota();
                    tq.setTenantId(tenantId);
                    tq.setPlanId(freePlan.getId());
                    return tenantQuotaRepository.insert(tq).then();
                });
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
