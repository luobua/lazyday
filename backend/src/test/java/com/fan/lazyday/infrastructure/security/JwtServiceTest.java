package com.fan.lazyday.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtService 单元测试
 * 验证 JWT 签发、验证、过期判断
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        jwtService.init();
    }

    @Test
    @DisplayName("generateAccessToken + validateToken: 正常签发和验证")
    void accessToken_shouldRoundtrip() {
        Long userId = 22222222L;
        Long tenantId = 100L;
        String role = "TENANT_ADMIN";

        String token = jwtService.generateAccessToken(userId, tenantId, role);
        JwtService.JwtClaims claims = jwtService.validateToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getUserId()).isEqualTo(userId);
        assertThat(claims.getTenantId()).isEqualTo(tenantId);
        assertThat(claims.getRole()).isEqualTo(role);
        assertThat(claims.isExpired()).isFalse();
    }

    @Test
    @DisplayName("generateRefreshToken: 正常签发和验证")
    void refreshToken_shouldRoundtrip() {
        Long userId = 22222222L;
        String token = jwtService.generateRefreshToken(userId, 200L, "TENANT_ADMIN", false);

        JwtService.JwtClaims claims = jwtService.validateToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getUserId()).isEqualTo(userId);
        assertThat(claims.isExpired()).isFalse();
    }

    @Test
    @DisplayName("generateRefreshToken: remember=true 时使用 30 天有效期")
    void refreshToken_rememberTrue_shouldUse30Days() {
        Duration expiry = jwtService.getRefreshTokenExpiry(true);
        assertThat(expiry).isEqualTo(Duration.ofDays(30));
    }

    @Test
    @DisplayName("generateRefreshToken: remember=false 时使用 7 天有效期")
    void refreshToken_rememberFalse_shouldUse7Days() {
        Duration expiry = jwtService.getRefreshTokenExpiry(false);
        assertThat(expiry).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("getAccessTokenExpiry: 应返回 2 小时")
    void getAccessTokenExpiry_shouldReturn2Hours() {
        assertThat(jwtService.getAccessTokenExpiry()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("validateToken: 无效令牌应返回 null")
    void validateToken_invalidToken_shouldReturnNull() {
        JwtService.JwtClaims claims = jwtService.validateToken("invalid.token.here");
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("validateToken: 空字符串应返回 null")
    void validateToken_emptyString_shouldReturnNull() {
        JwtService.JwtClaims claims = jwtService.validateToken("");
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("validateToken: null 应抛出 NullPointerException")
    void validateToken_null_shouldThrowNPE() {
        // SignedJWT.parse(null) 内部调用 trim() 导致 NPE，这是源码的实际行为
        assertThatThrownBy(() -> jwtService.validateToken(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("validateToken: 不同用户签发的 token 不应混淆")
    void validateToken_differentUsers_shouldNotConfuse() {
        Long user1 = 22222222L;
        Long user2 = 22222221L;

        String token1 = jwtService.generateAccessToken(user1, 100L, "TENANT_ADMIN");
        String token2 = jwtService.generateAccessToken(user2, 200L, "PLATFORM_ADMIN");

        JwtService.JwtClaims claims1 = jwtService.validateToken(token1);
        JwtService.JwtClaims claims2 = jwtService.validateToken(token2);

        assertThat(claims1.getUserId()).isEqualTo(user1);
        assertThat(claims1.getTenantId()).isEqualTo(100L);
        assertThat(claims2.getUserId()).isEqualTo(user2);
        assertThat(claims2.getTenantId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("JwtClaims.expired: 静态工厂方法")
    void jwtClaims_expired_staticFactory() {
        JwtService.JwtClaims expired = JwtService.JwtClaims.expired();

        assertThat(expired.isExpired()).isTrue();
        assertThat(expired.getUserId()).isNull();
        assertThat(expired.getTenantId()).isNull();
        assertThat(expired.getRole()).isNull();
    }
}
