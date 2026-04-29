package com.fan.lazyday.infrastructure.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "csrf_token";

    private CookieUtils() {
    }

    /**
     * 开发环境（localhost）下浏览器不会在 HTTP 连接上发送 Secure cookie，
     * 因此仅在非 localhost 时启用 Secure 标志。
     */
    private static boolean isSecureEnabled() {
        String env = System.getenv("SPRING_PROFILES_ACTIVE");
        return env != null && (env.contains("prod") || env.contains("staging"));
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
                .secure(isSecureEnabled())
                .sameSite("Strict")
                .path("/")
                .build();
    }

    public static ResponseCookie clearCookie(String name) {
        return buildSecureCookie(name, "", Duration.ZERO, true);
    }

    private static ResponseCookie buildSecureCookie(String name, String value, Duration maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(isSecureEnabled())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

}
