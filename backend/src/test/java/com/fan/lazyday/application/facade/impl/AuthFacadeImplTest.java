package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.service.AuthService;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthFacadeImpl 单元测试
 * 覆盖：register（事务 + 租户创建 + 用户创建）、login、adminLogin（权限检查）、getUserInfo
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthFacadeImplTest {

    @Mock private AuthService authService;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionalOperator transactionalOperator;

    private AuthFacadeImpl authFacade;

    private static final Long USER_ID = 22222222L;
    private static final Long TENANT_ID = 200L;

    @BeforeEach
    void setUp() {
        authFacade = new AuthFacadeImpl(authService, tenantRepository, userRepository, transactionalOperator);
        // 让 transactionalOperator.transactional() 直接透传 Mono
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private User buildUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setTenantId(TENANT_ID);
        user.setRole("TENANT_ADMIN");
        return user;
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("注册成功 → 创建租户 + 用户")
        void register_success_shouldCreateTenantAndUser() {
            Tenant savedTenant = new Tenant();
            savedTenant.setId(TENANT_ID);
            savedTenant.setName("MyTenant");
            when(tenantRepository.insert(any(Tenant.class))).thenReturn(Mono.just(savedTenant));

            User user = buildUser();
            when(authService.createUser("testuser", "test@example.com", "pass123", TENANT_ID, "TENANT_ADMIN"))
                    .thenReturn(Mono.just(user));

            StepVerifier.create(authFacade.register("testuser", "test@example.com", "pass123", "MyTenant"))
                    .assertNext(response -> {
                        assertThat(response.getId()).isEqualTo(USER_ID);
                        assertThat(response.getUsername()).isEqualTo("testuser");
                        assertThat(response.getEmail()).isEqualTo("test@example.com");
                        assertThat(response.getRole()).isEqualTo("TENANT_ADMIN");
                        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
                    })
                    .verifyComplete();

            // 验证租户创建参数
            ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).insert(tenantCaptor.capture());
            assertThat(tenantCaptor.getValue().getName()).isEqualTo("MyTenant");
            assertThat(tenantCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
            assertThat(tenantCaptor.getValue().getPlanType()).isEqualTo("FREE");
            assertThat(tenantCaptor.getValue().getContactEmail()).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("登录成功")
        void login_success_shouldReturnUserInfo() {
            User user = buildUser();
            when(authService.authenticate("testuser", "pass123")).thenReturn(Mono.just(user));

            StepVerifier.create(authFacade.login("testuser", "pass123"))
                    .assertNext(response -> {
                        assertThat(response.getUsername()).isEqualTo("testuser");
                        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("adminLogin")
    class AdminLogin {

        @Test
        @DisplayName("PLATFORM_ADMIN 登录成功")
        void adminLogin_platformAdmin_shouldSucceed() {
            User admin = buildUser();
            admin.setRole("PLATFORM_ADMIN");
            when(authService.authenticate("admin", "adminpass")).thenReturn(Mono.just(admin));

            StepVerifier.create(authFacade.adminLogin("admin", "adminpass"))
                    .assertNext(response -> assertThat(response.getRole()).isEqualTo("PLATFORM_ADMIN"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非 PLATFORM_ADMIN 登录 → 抛 FORBIDDEN")
        void adminLogin_nonAdmin_shouldThrowForbidden() {
            User user = buildUser();
            user.setRole("TENANT_ADMIN");
            when(authService.authenticate("user", "pass")).thenReturn(Mono.just(user));

            StepVerifier.create(authFacade.adminLogin("user", "pass"))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("FORBIDDEN_ROLE")
                                    && be.getMessage().equals("权限不足")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("getUserInfo")
    class GetUserInfo {

        @Test
        @DisplayName("用户存在 → 返回 UserInfoResponse")
        void getUserInfo_found_shouldReturnResponse() {
            User user = buildUser();
            when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user));

            StepVerifier.create(authFacade.getUserInfo(USER_ID))
                    .assertNext(response -> {
                        assertThat(response.getId()).isEqualTo(USER_ID);
                        assertThat(response.getUsername()).isEqualTo("testuser");
                        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("用户不存在 → 抛 NOT_FOUND")
        void getUserInfo_notFound_shouldThrowNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(authFacade.getUserInfo(USER_ID))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("USER_NOT_FOUND")
                                    && be.getMessage().equals("用户不存在")
                    )
                    .verify();
        }
    }
}
