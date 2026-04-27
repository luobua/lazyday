package com.fan.lazyday.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CookieUtils 单元测试
 * 验证 Cookie 构建的安全属性
 */
class CookieUtilsTest {

    @Test
    @DisplayName("createAccessTokenCookie: 应包含安全属性")
    void createAccessTokenCookie_shouldHaveSecureAttributes() {
        var cookie = CookieUtils.createAccessTokenCookie("token-abc", Duration.ofHours(2));

        assertThat(cookie.getName()).isEqualTo(CookieUtils.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("token-abc");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("createRefreshTokenCookie: 应包含安全属性")
    void createRefreshTokenCookie_shouldHaveSecureAttributes() {
        var cookie = CookieUtils.createRefreshTokenCookie("refresh-xyz", Duration.ofDays(7));

        assertThat(cookie.getName()).isEqualTo(CookieUtils.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("refresh-xyz");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("createCsrfTokenCookie: CSRF cookie 应不设 httpOnly（前端 JS 需要读取）")
    void createCsrfTokenCookie_shouldNotBeHttpOnly() {
        var cookie = CookieUtils.createCsrfTokenCookie("csrf-token-123");

        assertThat(cookie.getName()).isEqualTo(CookieUtils.CSRF_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("csrf-token-123");
        assertThat(cookie.isHttpOnly()).isFalse();  // JS 需要读取
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("clearCookie: 清除 cookie 的 maxAge 应为 0")
    void clearCookie_shouldHaveZeroMaxAge() {
        var cookie = CookieUtils.clearCookie(CookieUtils.ACCESS_TOKEN_COOKIE);

        assertThat(cookie.getName()).isEqualTo(CookieUtils.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    @DisplayName("常量: Cookie 名称应与文档一致")
    void constants_shouldMatchExpectedValues() {
        assertThat(CookieUtils.ACCESS_TOKEN_COOKIE).isEqualTo("access_token");
        assertThat(CookieUtils.REFRESH_TOKEN_COOKIE).isEqualTo("refresh_token");
        assertThat(CookieUtils.CSRF_TOKEN_COOKIE).isEqualTo("csrf_token");
    }
}
