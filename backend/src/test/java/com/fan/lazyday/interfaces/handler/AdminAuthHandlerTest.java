package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.AuthFacade;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.JwtService;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.request.LoginRequest;
import com.fan.lazyday.interfaces.response.CsrfTokenResponse;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * AdminAuthHandler 单元测试
 * 覆盖：login/logout/me/csrfToken
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthHandlerTest {

    @Mock private AuthFacade authFacade;
    @Mock private JwtService jwtService;

    private AdminAuthHandler handler;

    private static final Long USER_ID = 33333333L;
    private static final Long TENANT_ID = 100L;
    private static final Context REQ_CTX = Context.of(RequestIdFilter.REQUEST_ID_KEY, "test-req-id");

    @BeforeEach
    void setUp() {
        handler = new AdminAuthHandler(authFacade, jwtService);
        when(jwtService.generateAccessToken(any(Long.class), any(Long.class), anyString())).thenReturn("access_token_mock");
        when(jwtService.generateRefreshToken(any(Long.class), any(Long.class), anyString(), anyBoolean())).thenReturn("refresh_token_mock");
        when(jwtService.getAccessTokenExpiry()).thenReturn(Duration.ofHours(2));
        when(jwtService.getRefreshTokenExpiry(anyBoolean())).thenReturn(Duration.ofDays(7));
    }

    private UserInfoResponse buildUserInfo() {
        UserInfoResponse info = new UserInfoResponse();
        info.setId(USER_ID);
        info.setUsername("admin");
        info.setEmail("admin@test.com");
        info.setRole("PLATFORM_ADMIN");
        info.setTenantId(TENANT_ID);
        return info;
    }

    @Test
    @DisplayName("login: 管理员登录成功，设置 cookie 并返回用户信息")
    void login_success_shouldSetCookiesAndReturnUserInfo() {
        when(authFacade.adminLogin("admin", "adminpass")).thenReturn(Mono.just(buildUserInfo()));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("adminpass");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/admin/v1/auth/login").build()
        );

        StepVerifier.create(handler.login(Mono.just(request), exchange).contextWrite(REQ_CTX))
                .assertNext(response -> {
                    assertThat(response.getCode()).isEqualTo(0);
                    UserInfoResponse data = response.getData();
                    assertThat(data.getUsername()).isEqualTo("admin");
                    assertThat(data.getRole()).isEqualTo("PLATFORM_ADMIN");
                    assertThat(exchange.getResponse().getCookies()).containsKey("access_token");
                    assertThat(exchange.getResponse().getCookies()).containsKey("refresh_token");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("login: adminLogin 失败 → 错误传播")
    void login_failure_shouldPropagateError() {
        when(authFacade.adminLogin("bad", "wrong")).thenReturn(
                Mono.error(com.fan.lazyday.infrastructure.exception.BizException.unauthorized("INVALID", "错误"))
        );

        LoginRequest request = new LoginRequest();
        request.setUsername("bad");
        request.setPassword("wrong");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/admin/v1/auth/login").build()
        );

        StepVerifier.create(handler.login(Mono.just(request), exchange).contextWrite(REQ_CTX))
                .expectError(com.fan.lazyday.infrastructure.exception.BizException.class)
                .verify();
    }

    @Test
    @DisplayName("logout: 清除 cookie 并返回成功")
    void logout_shouldClearCookies() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/admin/v1/auth/logout").build()
        );

        StepVerifier.create(handler.logout(exchange).contextWrite(REQ_CTX))
                .assertNext(response -> {
                    assertThat(response.getCode()).isEqualTo(0);
                    assertThat(response.getData()).isNull();
                    var cookies = exchange.getResponse().getCookies();
                    assertThat(cookies.get("access_token")).isNotNull();
                    assertThat(cookies.get("refresh_token")).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("me: 从 Context 获取用户信息")
    void me_shouldReturnUserInfoFromContext() {
        UserInfoResponse info = buildUserInfo();
        when(authFacade.getUserInfo(USER_ID)).thenReturn(Mono.just(info));

        Context ctx = TenantContext.write(USER_ID, TENANT_ID, "PLATFORM_ADMIN")
                .put(RequestIdFilter.REQUEST_ID_KEY, "req-123");

        StepVerifier.create(handler.me().contextWrite(ctx))
                .assertNext(response -> {
                    assertThat(response.getCode()).isEqualTo(0);
                    assertThat(response.getData().getUsername()).isEqualTo("admin");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("csrfToken: 生成 CSRF token 并设置 cookie")
    void csrfToken_shouldGenerateAndSetCookie() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/auth/csrf-token").build()
        );

        StepVerifier.create(handler.csrfToken(exchange).contextWrite(REQ_CTX))
                .assertNext(response -> {
                    assertThat(response.getCode()).isEqualTo(0);
                    CsrfTokenResponse data = response.getData();
                    assertThat(data.getToken()).isNotNull().isNotEmpty();
                    assertThat(exchange.getResponse().getCookies()).containsKey("csrf_token");
                    assertThat(exchange.getResponse().getCookies().getFirst("csrf_token").getValue()).isEqualTo(data.getToken());
                })
                .verifyComplete();
    }
}
