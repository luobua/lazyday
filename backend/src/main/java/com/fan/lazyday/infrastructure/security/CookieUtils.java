package com.fan.lazyday.infrastructure.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "csrf_token";

    private CookieUtils() {
    }

    public static ResponseCookie createAccessTokenCookie(String token, Duration maxAge) {
        return buildSecureCookie(ACCESS_TOKEN_COOKIE, token, maxAge, true);
    }

    public static ResponseCookie createRefreshTokenCookie(String token, Duration maxAge) {
        return buildSecureCookie(REFRESH_TOKEN_COOKIE, token, maxAge, true);
    }

    public static ResponseCookie createCsrfTokenCookie(String token) {
        return ResponseCookie.from(CSRF_TOKEN_COOKIE, token)
                .httpOnly(false)
                .secure(isSecure())
                .sameSite("Lax")
                .path("/")
                .build();
    }

    public static ResponseCookie clearCookie(String name) {
        return buildSecureCookie(name, "", Duration.ZERO, true);
    }

    private static ResponseCookie buildSecureCookie(String name, String value, Duration maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /**
     * 根据域名协议判断 Cookie 是否需要 Secure 标志。
     * 开发环境 (http://localhost) 不需要 Secure，生产环境 (https) 需要。
     */
    private static boolean isSecure() {
        // 通过环境变量或系统属性判断，默认开发环境 http 不需要 Secure
        String env = System.getenv("LAZYDAY_COOKIE_SECURE");
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        // 默认非 Secure（兼容本地开发 http）
        return false;
    }
}
