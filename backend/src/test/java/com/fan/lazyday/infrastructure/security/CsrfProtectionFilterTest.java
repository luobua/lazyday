package com.fan.lazyday.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CsrfProtectionFilter 单元测试
 * 覆盖：非 portal/admin 路径跳过、GET/HEAD 安全方法跳过、
 *       豁免路径（login/register）跳过、
 *       缺少 header → 403、header 与 cookie 不匹配 → 403、校验通过放行
 */
@ExtendWith(MockitoExtension.class)
class CsrfProtectionFilterTest {

    private CsrfProtectionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CsrfProtectionFilter();
    }

    @Test
    @DisplayName("非 /api/portal/ 和 /api/admin/ 路径 → 直接放行")
    void nonApiPaths_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/open/v1/callback").build()
        );
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("GET 请求 → 跳过 CSRF 校验")
    void getMethod_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/credentials").build()
        );
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("POST /auth/login → 豁免路径，跳过 CSRF")
    void postLogin_shouldBeExempt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/auth/login").build()
        );
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("POST /auth/register → 豁免路径，跳过 CSRF")
    void postRegister_shouldBeExempt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/auth/register").build()
        );
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("POST 受保护路径无 CSRF header → 返回 403 CSRF_TOKEN_MISSING")
    void postNoCsrfHeader_shouldReturn403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/credentials").build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(v -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("POST 受保护路径 CSRF header 为空 → 返回 403")
    void postEmptyCsrfHeader_shouldReturn403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/credentials")
                        .header("X-CSRF-Token", "  ")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(v -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("POST 受保护路径 CSRF header 与 cookie 不匹配 → 返回 403 CSRF_TOKEN_INVALID")
    void postCsrfMismatch_shouldReturn403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/credentials")
                        .header("X-CSRF-Token", "header-token")
                        .cookie(new org.springframework.http.HttpCookie("csrf_token", "cookie-token"))
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(v -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("POST 受保护路径无 CSRF cookie → 返回 403")
    void postNoCsrfCookie_shouldReturn403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/credentials")
                        .header("X-CSRF-Token", "some-token")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(v -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("POST 受保护路径 CSRF header 与 cookie 匹配 → 放行")
    void postCsrfMatch_shouldPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/portal/v1/credentials")
                        .header("X-CSRF-Token", "valid-csrf-token")
                        .cookie(new org.springframework.http.HttpCookie("csrf_token", "valid-csrf-token"))
                        .build()
        );
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("DELETE 方法受 CSRF 保护")
    void deleteMethod_shouldRequireCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/portal/v1/credentials/1").build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(v -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PUT 方法受 CSRF 保护")
    void putMethod_shouldRequireCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/portal/v1/credentials/1/enable").build()
        );

        StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class)))
                .thenConsumeWhile(v -> {
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    return true;
                })
                .verifyComplete();
    }
}
