package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.AuthFacade;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.CookieUtils;
import com.fan.lazyday.infrastructure.security.JwtService;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.request.LoginRequest;
import com.fan.lazyday.interfaces.request.RegisterRequest;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PortalAuthHandler 单元测试
 * 覆盖：register/login/refresh/logout/me/csrfToken
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalAuthHandlerTest {

    @Mock private AuthFacade authFacade;
    @Mock private JwtService jwtService;

    private PortalAuthHandler handler;

    private static final Long USER_ID = 44444444L;
    private static final Long TENANT_ID = 100L;
    private static final Context REQ_CTX = Context.of(RequestIdFilter.REQUEST_ID_KEY, "test-req-id");

    @BeforeEach
    void setUp() {
        handler = new PortalAuthHandler(authFacade, jwtService);
        when(jwtService.generateAccessToken(any(Long.class), any(Long.class), anyString())).thenReturn("access_mock");
        when(jwtService.generateRefreshToken(any(Long.class), any(Long.class), anyString(), anyBoolean())).thenReturn("refresh_mock");
        when(jwtService.getAccessTokenExpiry()).thenReturn(Duration.ofHours(2));
        when(jwtService.getRefreshTokenExpiry(anyBoolean())).thenReturn(Duration.ofDays(7));
    }

    private UserInfoResponse buildUserInfo() {
        UserInfoResponse info = new UserInfoResponse();
        info.setId(USER_ID);
        info.setUsername("portaluser");
        info.setEmail("user@test.com");
        info.setRole("TENANT_ADMIN");
        info.setTenantId(TENANT_ID);
        return info;
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("注册成功 → 返回用户信息 + 设置 cookie")
        void register_success() {
            when(authFacade.register("newuser", "new@test.com", "pass123", "MyTenant"))
                    .thenReturn(Mono.just(buildUserInfo()));

            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setEmail("new@test.com");
            req.setPassword("pass123");
            req.setTenantName("MyTenant");

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/portal/v1/auth/register").build()
            );

            StepVerifier.create(handler.register(Mono.just(req), exchange).contextWrite(REQ_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData().getUsername()).isEqualTo("portaluser");
                        assertThat(exchange.getResponse().getCookies()).containsKey("access_token");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("登录成功（记住我）→ refresh token 使用长期过期")
        void login_withRemember_shouldUseLongExpiry() {
            when(authFacade.login("user", "pass")).thenReturn(Mono.just(buildUserInfo()));

            LoginRequest req = new LoginRequest();
            req.setUsername("user");
            req.setPassword("pass");
            req.setRemember(true);

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/portal/v1/auth/login").build()
            );

            StepVerifier.create(handler.login(Mono.just(req), exchange).contextWrite(REQ_CTX))
                    .assertNext(response -> assertThat(response.getCode()).isEqualTo(0))
                    .verifyComplete();

            verify(jwtService).getRefreshTokenExpiry(true);
        }

        @Test
        @DisplayName("登录成功（不记住）→ refresh token 使用短期过期")
        void login_withoutRemember_shouldUseShortExpiry() {
            when(authFacade.login("user", "pass")).thenReturn(Mono.just(buildUserInfo()));

            LoginRequest req = new LoginRequest();
            req.setUsername("user");
            req.setPassword("pass");
            req.setRemember(false);

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/portal/v1/auth/login").build()
            );

            StepVerifier.create(handler.login(Mono.just(req), exchange).contextWrite(REQ_CTX))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(jwtService).getRefreshTokenExpiry(false);
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("refresh token 缺失 → 抛 UNAUTHORIZED")
        void refresh_noCookie_shouldThrowUnauthorized() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/portal/v1/auth/refresh").build()
            );

            StepVerifier.create(handler.refresh(exchange).contextWrite(REQ_CTX))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("REFRESH_TOKEN_EXPIRED")
                    )
                    .verify();
        }

        @Test
        @DisplayName("refresh token 空白 → 抛 UNAUTHORIZED")
        void refresh_blankCookie_shouldThrowUnauthorized() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/portal/v1/auth/refresh")
                    .cookie(new org.springframework.http.HttpCookie(CookieUtils.REFRESH_TOKEN_COOKIE, " "))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(handler.refresh(exchange).contextWrite(REQ_CTX))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("REFRESH_TOKEN_EXPIRED")
                    )
                    .verify();
        }

        @Test
        @DisplayName("refresh token 过期 → 抛 UNAUTHORIZED")
        void refresh_expiredToken_shouldThrowUnauthorized() {
            JwtService.JwtClaims expiredClaims = JwtService.JwtClaims.expired();
            when(jwtService.validateToken("expired_token")).thenReturn(expiredClaims);

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/portal/v1/auth/refresh")
                    .cookie(new org.springframework.http.HttpCookie(CookieUtils.REFRESH_TOKEN_COOKIE, "expired_token"))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(handler.refresh(exchange).contextWrite(REQ_CTX))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("REFRESH_TOKEN_EXPIRED")
                    )
                    .verify();
        }

        @Test
        @DisplayName("refresh token 无效（null）→ 抛 UNAUTHORIZED")
        void refresh_invalidToken_shouldThrowUnauthorized() {
            when(jwtService.validateToken("bad_token")).thenReturn(null);

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/portal/v1/auth/refresh")
                    .cookie(new org.springframework.http.HttpCookie(CookieUtils.REFRESH_TOKEN_COOKIE, "bad_token"))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(handler.refresh(exchange).contextWrite(REQ_CTX))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("REFRESH_TOKEN_EXPIRED")
                    )
                    .verify();
        }

        @Test
        @DisplayName("refresh 成功 → 生成新 access token 并设置 cookie")
        void refresh_success_shouldSetNewAccessToken() {
            JwtService.JwtClaims validClaims = new JwtService.JwtClaims(USER_ID, TENANT_ID, "USER", false);
            when(jwtService.validateToken("valid_refresh")).thenReturn(validClaims);

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/portal/v1/auth/refresh")
                    .cookie(new org.springframework.http.HttpCookie(CookieUtils.REFRESH_TOKEN_COOKIE, "valid_refresh"))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(handler.refresh(exchange).contextWrite(REQ_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData()).isNull();
                        var cookie = exchange.getResponse().getCookies().getFirst("access_token");
                        assertThat(cookie).isNotNull();
                        assertThat(cookie.getValue()).isEqualTo("access_mock");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("登出 → 清除 cookie")
        void logout_shouldClearCookies() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/portal/v1/auth/logout").build()
            );

            StepVerifier.create(handler.logout(exchange).contextWrite(REQ_CTX))
                    .assertNext(response -> assertThat(response.getCode()).isEqualTo(0))
                    .verifyComplete();

            assertThat(exchange.getResponse().getCookies()).containsKey("access_token");
            assertThat(exchange.getResponse().getCookies()).containsKey("refresh_token");
        }
    }

    @Nested
    @DisplayName("me")
    class Me {

        @Test
        @DisplayName("获取当前用户信息")
        void me_shouldReturnUserInfo() {
            when(authFacade.getUserInfo(USER_ID)).thenReturn(Mono.just(buildUserInfo()));

            Context ctx = TenantContext.write(USER_ID, TENANT_ID, "TENANT_ADMIN")
                    .put(RequestIdFilter.REQUEST_ID_KEY, "req-id");

            StepVerifier.create(handler.me().contextWrite(ctx))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData().getUsername()).isEqualTo("portaluser");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("csrfToken")
    class CsrfToken {

        @Test
        @DisplayName("生成 CSRF token 并设置 cookie")
        void csrfToken_shouldGenerateAndSetCookie() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/portal/v1/auth/csrf-token").build()
            );

            StepVerifier.create(handler.csrfToken(exchange).contextWrite(REQ_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData().getToken()).isNotEmpty();
                        assertThat(exchange.getResponse().getCookies().getFirst("csrf_token")).isNotNull();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("verifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("验证成功后跳转到登录页")
        void verifyEmail_success_shouldRedirectToLogin() {
            when(authFacade.verifyEmail("token")).thenReturn(Mono.empty());
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/portal/v1/auth/verify-email?token=token").build()
            );

            StepVerifier.create(handler.verifyEmail("token", exchange))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(exchange.getResponse().getHeaders().getLocation().toString()).isEqualTo("/login?verified=1");
        }
    }
}
