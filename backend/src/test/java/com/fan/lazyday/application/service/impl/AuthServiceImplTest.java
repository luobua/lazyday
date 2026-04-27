package com.fan.lazyday.application.service.impl;

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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 * 覆盖：createUser（全分支）、authenticate（全分支）
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private AuthServiceImpl authService;

    private static final Long USER_ID = 11111111L;
    private static final Long TENANT_ID = 100L;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository);
    }

    private User buildUser(String username, String email, String role, String status) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(username);
        user.setEmail(email);
        user.setTenantId(TENANT_ID);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("用户名已存在 → 抛 CONFLICT")
        void createUser_duplicateUsername_shouldThrowConflict() {
            User existing = buildUser("alice", "alice@test.com", "USER", "ACTIVE");
            when(userRepository.findByUsername("alice")).thenReturn(Mono.just(existing));

            StepVerifier.create(authService.createUser("alice", "new@test.com", "pass123", TENANT_ID, "USER"))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("DUPLICATE_USERNAME")
                                    && be.getMessage().equals("用户名已被使用")
                    )
                    .verify();
        }

        @Test
        @DisplayName("邮箱已存在 → 抛 CONFLICT")
        void createUser_duplicateEmail_shouldThrowConflict() {
            when(userRepository.findByUsername("bob")).thenReturn(Mono.empty());
            User emailUser = buildUser("other", "dup@test.com", "USER", "ACTIVE");
            when(userRepository.findByEmail("dup@test.com")).thenReturn(Mono.just(emailUser));

            StepVerifier.create(authService.createUser("bob", "dup@test.com", "pass123", TENANT_ID, "USER"))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("DUPLICATE_EMAIL")
                                    && be.getMessage().equals("邮箱已被使用")
                    )
                    .verify();
        }

        @Test
        @DisplayName("创建成功（含邮箱）→ 返回 User")
        void createUser_withEmail_shouldReturnUser() {
            when(userRepository.findByUsername("newuser")).thenReturn(Mono.empty());
            when(userRepository.findByEmail("new@test.com")).thenReturn(Mono.empty());

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            when(userRepository.insert(any(User.class)))
                    .thenAnswer(inv -> {
                        User u = inv.getArgument(0);
                        u.setId(USER_ID);
                        return Mono.just(u);
                    });

            StepVerifier.create(authService.createUser("newuser", "new@test.com", "password", TENANT_ID, "TENANT_ADMIN"))
                    .assertNext(user -> {
                        assertThat(user.getUsername()).isEqualTo("newuser");
                        assertThat(user.getEmail()).isEqualTo("new@test.com");
                        assertThat(user.getTenantId()).isEqualTo(TENANT_ID);
                        assertThat(user.getRole()).isEqualTo("TENANT_ADMIN");
                        assertThat(user.getStatus()).isEqualTo("ACTIVE");
                        // 密码应是 BCrypt 哈希
                        assertThat(user.getPasswordHash()).startsWith("$2a$");
                    })
                    .verifyComplete();

            verify(userRepository).insert(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isNotBlank();
        }

        @Test
        @DisplayName("创建成功（无邮箱）→ 不校验邮箱唯一性")
        void createUser_withoutEmail_shouldSkipEmailCheck() {
            when(userRepository.findByUsername("noemail")).thenReturn(Mono.empty());

            when(userRepository.insert(any(User.class)))
                    .thenAnswer(inv -> {
                        User u = inv.getArgument(0);
                        u.setId(USER_ID);
                        return Mono.just(u);
                    });

            StepVerifier.create(authService.createUser("noemail", null, "password", TENANT_ID, "USER"))
                    .assertNext(user -> assertThat(user.getEmail()).isNull())
                    .verifyComplete();

            // 不应调用 findByEmail
            verify(userRepository, never()).findByEmail(anyString());
        }
    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("用户不存在 → 抛 UNAUTHORIZED")
        void authenticate_userNotFound_shouldThrowUnauthorized() {
            when(userRepository.findByUsername("ghost")).thenReturn(Mono.empty());

            StepVerifier.create(authService.authenticate("ghost", "pass"))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("INVALID_CREDENTIALS")
                                    && be.getMessage().equals("用户名或密码错误")
                    )
                    .verify();
        }

        @Test
        @DisplayName("账户已禁用 → 抛 FORBIDDEN")
        void authenticate_disabledAccount_shouldThrowForbidden() {
            User disabled = buildUser("disabled", "disabled@test.com", "USER", "DISABLED");
            when(userRepository.findByUsername("disabled")).thenReturn(Mono.just(disabled));

            StepVerifier.create(authService.authenticate("disabled", "pass"))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("ACCOUNT_DISABLED")
                                    && be.getMessage().equals("账户已禁用")
                    )
                    .verify();
        }

        @Test
        @DisplayName("密码错误 → 抛 UNAUTHORIZED")
        void authenticate_wrongPassword_shouldThrowUnauthorized() {
            // 先创建一个正确密码的哈希
            User user = buildUser("bob", "bob@test.com", "USER", "ACTIVE");
            String hash = com.fan.lazyday.domain.user.entity.UserEntity.hashPassword("correct_pass")
                    .block(java.time.Duration.ofSeconds(5));
            user.setPasswordHash(hash);

            when(userRepository.findByUsername("bob")).thenReturn(Mono.just(user));

            StepVerifier.create(authService.authenticate("bob", "wrong_pass"))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("INVALID_CREDENTIALS")
                    )
                    .verify();
        }

        @Test
        @DisplayName("认证成功 → 返回 User")
        void authenticate_success_shouldReturnUser() {
            User user = buildUser("valid", "valid@test.com", "USER", "ACTIVE");
            String hash = com.fan.lazyday.domain.user.entity.UserEntity.hashPassword("correct")
                    .block(java.time.Duration.ofSeconds(5));
            user.setPasswordHash(hash);

            when(userRepository.findByUsername("valid")).thenReturn(Mono.just(user));

            StepVerifier.create(authService.authenticate("valid", "correct"))
                    .assertNext(u -> {
                        assertThat(u.getId()).isEqualTo(USER_ID);
                        assertThat(u.getUsername()).isEqualTo("valid");
                    })
                    .verifyComplete();
        }
    }
}
