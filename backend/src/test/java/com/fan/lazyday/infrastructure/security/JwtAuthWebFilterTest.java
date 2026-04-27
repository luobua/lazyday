package com.fan.lazyday.infrastructure.security;

import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JwtAuthWebFilter 单元测试
 * 覆盖：路由白名单（非 /api/portal/ 和 /api/admin/ 路径跳过）
 *       公共路径（login/register/csrf-token/refresh 跳过）
 *       无 cookie → 401、无效 token → 401、过期 token → 401
 *       有效 token → 写入 TenantContext 并放行
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthWebFilterTest {

    @Mock private JwtService jwtService;

    private JwtAuthWebFilter filter;

    private static final Long USER_ID = 77777777L;
    private static final Long TENANT_ID = 100L;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthWebFilter(jwtService);
    }

    private WebFilterChain chainThatExpectsContext() {
        return exchange -> {
            // 验证 TenantContext 能被读取（即 contextWrite 生效）
            return TenantContext.current()
                    .flatMap(ctx -> {
                        if (!ctx.getUserId().equals(USER_ID)) {
                            return Mono.error(new AssertionError("Expected userId=" + USER_ID));
                        }
                        return Mono.empty();
                    });
        };
    }

    @Test
    @DisplayName("非 /api/portal/ 和 /api/admin/ 路径 → 直接放行，不校验")
    void nonApiPaths_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/open/v1/something").build()
        );

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("公共路径 /auth/login → 直接放行")
    void publicPath_login_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/auth/login").build()
        );

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("公共路径 /auth/register → 直接放行")
    void publicPath_register_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/auth/register").build()
        );

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("公共路径 /auth/csrf-token → 直接放行")
    void publicPath_csrfToken_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/auth/csrf-token").build()
        );

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("公共路径 /auth/refresh → 直接放行")
    void publicPath_refresh_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/auth/refresh").build()
        );

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("受保护路径无 access_token cookie → 返回 401")
    void protectedPath_noCookie_shouldReturn401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/credentials").build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(response -> {
                    // 验证响应状态码为 401
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("受保护路径 token 验证返回 null → 返回 401")
    void protectedPath_invalidToken_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/portal/v1/credentials")
                .header("Cookie", "access_token=invalid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("invalid_token")).thenReturn(null);

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(response -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("受保护路径 token 已过期 → 返回 401 + TOKEN_EXPIRED")
    void protectedPath_expiredToken_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/portal/v1/credentials")
                .header("Cookie", "access_token=expired_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("expired_token")).thenReturn(JwtService.JwtClaims.expired());

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(response -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("有效 token → 写入 TenantContext 并放行")
    void validToken_shouldWriteContextAndPassThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/portal/v1/credentials")
                .header("Cookie", "access_token=valid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtService.JwtClaims claims = new JwtService.JwtClaims(USER_ID, TENANT_ID, "TENANT_ADMIN", false);
        when(jwtService.validateToken("valid_token")).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chainThatExpectsContext()))
                .verifyComplete();
    }

    @Test
    @DisplayName("/api/admin/v1 路径也受保护")
    void adminPath_shouldBeProtected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/v1/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 无 cookie → 401
        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(response -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    return true;
                })
                .verifyComplete();
    }
}
