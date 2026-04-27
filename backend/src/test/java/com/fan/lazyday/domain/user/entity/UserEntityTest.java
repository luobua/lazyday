package com.fan.lazyday.domain.user.entity;

import com.fan.lazyday.domain.user.po.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserEntity 单元测试
 * 覆盖：hashPassword、verifyPassword、fromPo
 */
class UserEntityTest {

    @Test
    @DisplayName("fromPo: 从 PO 创建 Entity 并正确映射字段")
    void fromPo_shouldMapFields() {
        User po = new User();
        po.setId(6666L);
        po.setUsername("testuser");
        po.setEmail("test@example.com");
        po.setPasswordHash("$2a$10$hash");
        po.setTenantId(100L);
        po.setRole("TENANT_ADMIN");
        po.setStatus("ACTIVE");

        UserEntity entity = UserEntity.fromPo(po);

        assertThat(entity.getId()).isEqualTo(po.getId());
        assertThat(entity.getDelegate()).isEqualTo(po);
        assertThat(entity.getDelegate().getUsername()).isEqualTo("testuser");
        assertThat(entity.getDelegate().getEmail()).isEqualTo("test@example.com");
        assertThat(entity.getDelegate().getTenantId()).isEqualTo(100L);
        assertThat(entity.getDelegate().getRole()).isEqualTo("TENANT_ADMIN");
    }

    @Test
    @DisplayName("fromPo: 多次调用应创建独立 Entity 实例")
    void fromPo_shouldCreateIndependentInstances() {
        User po = new User();
        po.setId(new Random().nextLong());

        UserEntity a = UserEntity.fromPo(po);
        UserEntity b = UserEntity.fromPo(po);

        assertThat(a).isNotSameAs(b);
        assertThat(a.getId()).isEqualTo(b.getId());
    }

    @Test
    @DisplayName("hashPassword: 应返回 BCrypt 格式的哈希")
    void hashPassword_shouldReturnBcryptHash() {
        String hash = UserEntity.hashPassword("mypassword").block(Duration.ofSeconds(5));

        assertThat(hash).isNotNull();
        assertThat(hash).startsWith("$2a$");
        // BCrypt 哈希长度为 60
        assertThat(hash).hasSize(60);
    }

    @Test
    @DisplayName("hashPassword: 相同密码每次哈希结果不同（含随机盐）")
    void hashPassword_samePasswordDifferentHashes() {
        String hash1 = UserEntity.hashPassword("samepass").block(Duration.ofSeconds(5));
        String hash2 = UserEntity.hashPassword("samepass").block(Duration.ofSeconds(5));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("hashPassword + verifyPassword: 正确密码应返回 true")
    void hashAndVerify_correctPassword_returnsTrue() {
        String rawPassword = "correct_password_123";
        String hash = UserEntity.hashPassword(rawPassword).block(Duration.ofSeconds(5));

        User po = new User();
        po.setPasswordHash(hash);
        UserEntity entity = UserEntity.fromPo(po);

        Boolean result = entity.verifyPassword(rawPassword).block(Duration.ofSeconds(5));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyPassword: 错误密码应返回 false")
    void verifyPassword_wrongPassword_returnsFalse() {
        String hash = UserEntity.hashPassword("correct_pass").block(Duration.ofSeconds(5));

        User po = new User();
        po.setPasswordHash(hash);
        UserEntity entity = UserEntity.fromPo(po);

        Boolean result = entity.verifyPassword("wrong_pass").block(Duration.ofSeconds(5));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyPassword: 空密码应返回 false")
    void verifyPassword_emptyPassword_returnsFalse() {
        String hash = UserEntity.hashPassword("somepass").block(Duration.ofSeconds(5));

        User po = new User();
        po.setPasswordHash(hash);
        UserEntity entity = UserEntity.fromPo(po);

        Boolean result = entity.verifyPassword("").block(Duration.ofSeconds(5));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getId: delegate 为 null 时返回 null")
    void getId_nullDelegate_returnsNull() {
        UserEntity entity = new UserEntity();
        assertThat(entity.getId()).isNull();
    }
}
