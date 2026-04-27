package com.fan.lazyday.domain.appkey.entity;

import com.fan.lazyday.domain.appkey.po.AppKey;
import com.fan.lazyday.infrastructure.utils.encrypt.AES;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppKeyEntity 单元测试
 * 覆盖：generateAppKey/SecretKey、加密/解密、密钥轮换、密钥验证、宽限期
 */
class AppKeyEntityTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-12345"; // 27 bytes, padKey 截取前16

    @Test
    @DisplayName("generateAppKey: 格式为 ak_ 前缀 + 32 位 hex")
    void generateAppKey_shouldHaveCorrectFormat() {
        String appKey = AppKeyEntity.generateAppKey();
        assertThat(appKey).startsWith("ak_");
        assertThat(appKey.substring(3)).hasSize(32);
    }

    @Test
    @DisplayName("generateAppKey: 每次生成应唯一")
    void generateAppKey_shouldBeUnique() {
        String a = AppKeyEntity.generateAppKey();
        String b = AppKeyEntity.generateAppKey();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("generateSecretKey: 格式为 sk_ 前缀 + 48 位 hex")
    void generateSecretKey_shouldHaveCorrectFormat() {
        String sk = AppKeyEntity.generateSecretKey();
        assertThat(sk).startsWith("sk_");
        assertThat(sk.substring(3)).hasSize(48);
    }

    @Test
    @DisplayName("encryptSecretKey + decryptSecretKey: 对称性验证")
    void encryptAndDecryptSecretKey_shouldBeSymmetric() {
        String plainSk = "sk_test_secret_key_value_12345678";

        String encrypted = AppKeyEntity.encryptSecretKey(plainSk, ENCRYPTION_KEY);
        String decrypted = AppKeyEntity.decryptSecretKey(encrypted, ENCRYPTION_KEY);

        assertThat(decrypted).isEqualTo(plainSk);
    }

    @Test
    @DisplayName("encryptSecretKey: 两次加密同一密钥应产生不同密文（AES ECB 除外，但本实现是 ECB）")
    void encryptSecretKey_sameInput_shouldProduceSameOutput() {
        // AES/ECB 是确定性的（无 IV），同一输入产生相同输出
        String plainSk = "sk_deterministic_test";
        String a = AppKeyEntity.encryptSecretKey(plainSk, ENCRYPTION_KEY);
        String b = AppKeyEntity.encryptSecretKey(plainSk, ENCRYPTION_KEY);
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("fromPo: 从 PO 创建 Entity 应正确设置 delegate")
    void fromPo_shouldSetDelegate() throws Exception {
        AppKey po = buildTestAppKey();
        AppKeyEntity entity = AppKeyEntity.fromPo(po);

        assertThat(entity.getId()).isEqualTo(po.getId());
        assertThat(entity.getDelegate()).isEqualTo(po);
        assertThat(entity.getDelegate().getAppKey()).isEqualTo("ak_test123");
    }

    @Test
    @DisplayName("isInGracePeriod: 无 gracePeriodEnd 时返回 false")
    void isInGracePeriod_noGracePeriod_returnsFalse() {
        AppKey po = buildTestAppKey();
        po.setGracePeriodEnd(null);

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        assertThat(entity.isInGracePeriod()).isFalse();
    }

    @Test
    @DisplayName("isInGracePeriod: gracePeriodEnd 在未来时返回 true")
    void isInGracePeriod_futureGraceEnd_returnsTrue() {
        AppKey po = buildTestAppKey();
        po.setGracePeriodEnd(Instant.now().plusSeconds(3600));

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        assertThat(entity.isInGracePeriod()).isTrue();
    }

    @Test
    @DisplayName("isInGracePeriod: gracePeriodEnd 已过期时返回 false")
    void isInGracePeriod_pastGraceEnd_returnsFalse() {
        AppKey po = buildTestAppKey();
        po.setGracePeriodEnd(Instant.now().minusSeconds(10));

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        assertThat(entity.isInGracePeriod()).isFalse();
    }

    @Test
    @DisplayName("verifySecretKey: 当前密钥匹配应返回 true")
    void verifySecretKey_currentKeyMatches_returnsTrue() {
        String plainSk = "sk_verify_current_key";
        AppKey po = buildTestAppKey();
        po.setSecretKeyEncrypted(AppKeyEntity.encryptSecretKey(plainSk, ENCRYPTION_KEY));

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        assertThat(entity.verifySecretKey(plainSk, ENCRYPTION_KEY)).isTrue();
    }

    @Test
    @DisplayName("verifySecretKey: 当前密钥不匹配且无旧密钥时应返回 false")
    void verifySecretKey_wrongKeyNoOld_returnsFalse() {
        String plainSk = "sk_verify_current_key";
        AppKey po = buildTestAppKey();
        po.setSecretKeyEncrypted(AppKeyEntity.encryptSecretKey(plainSk, ENCRYPTION_KEY));
        po.setSecretKeyOld(null);

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        assertThat(entity.verifySecretKey("sk_wrong_key", ENCRYPTION_KEY)).isFalse();
    }

    @Test
    @DisplayName("verifySecretKey: 宽限期内旧密钥匹配应返回 true")
    void verifySecretKey_oldKeyInGracePeriod_returnsTrue() {
        String oldSk = "sk_old_key_12345";
        String newSk = "sk_new_key_12345";

        AppKey po = buildTestAppKey();
        po.setSecretKeyOld(AppKeyEntity.encryptSecretKey(oldSk, ENCRYPTION_KEY));
        po.setSecretKeyEncrypted(AppKeyEntity.encryptSecretKey(newSk, ENCRYPTION_KEY));
        po.setGracePeriodEnd(Instant.now().plusSeconds(3600));

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        // 用旧密钥验证应成功（宽限期内）
        assertThat(entity.verifySecretKey(oldSk, ENCRYPTION_KEY)).isTrue();
    }

    @Test
    @DisplayName("verifySecretKey: 宽限期已过旧密钥不生效")
    void verifySecretKey_oldKeyAfterGracePeriod_returnsFalse() {
        String oldSk = "sk_old_key_expired";
        String newSk = "sk_new_key_after";

        AppKey po = buildTestAppKey();
        po.setSecretKeyOld(AppKeyEntity.encryptSecretKey(oldSk, ENCRYPTION_KEY));
        po.setSecretKeyEncrypted(AppKeyEntity.encryptSecretKey(newSk, ENCRYPTION_KEY));
        po.setGracePeriodEnd(Instant.now().minusSeconds(10)); // 已过期

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        assertThat(entity.verifySecretKey(oldSk, ENCRYPTION_KEY)).isFalse();
    }

    @Test
    @DisplayName("rotateSecretKey: 应更新密钥并设置宽限期")
    void rotateSecretKey_shouldRotateAndSetGracePeriod() {
        String originalSk = "sk_original_key";
        AppKey po = buildTestAppKey();
        po.setSecretKeyEncrypted(AppKeyEntity.encryptSecretKey(originalSk, ENCRYPTION_KEY));
        po.setSecretKeyOld(null);

        AppKeyEntity entity = AppKeyEntity.fromPo(po);
        Instant before = Instant.now();
        String newSk = entity.rotateSecretKey(ENCRYPTION_KEY);
        Instant after = Instant.now();

        // 新密钥应被返回
        assertThat(newSk).isNotBlank().startsWith("sk_");
        // 旧密钥应被保存
        assertThat(po.getSecretKeyOld()).isNotNull();
        // 新加密密钥应不同于旧加密密钥
        assertThat(po.getSecretKeyEncrypted()).isNotEqualTo(po.getSecretKeyOld());
        // rotatedAt 应在合理时间范围内
        assertThat(po.getRotatedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        // gracePeriodEnd 应为 24 小时后
        assertThat(po.getGracePeriodEnd()).isAfter(Instant.now().plusSeconds(86000));
        // 新密钥应可验证
        assertThat(entity.verifySecretKey(newSk, ENCRYPTION_KEY)).isTrue();
        // 旧密钥在宽限期内也应可验证
        assertThat(entity.verifySecretKey(originalSk, ENCRYPTION_KEY)).isTrue();
    }

    @Test
    @DisplayName("getId: delegate 为 null 时返回 null")
    void getId_nullDelegate_returnsNull() {
        AppKeyEntity entity = new AppKeyEntity();
        assertThat(entity.getId()).isNull();
    }

    private AppKey buildTestAppKey() {
        AppKey po = new AppKey();
        po.setId(1L);
        po.setTenantId(100L);
        po.setName("test-app");
        po.setAppKey("ak_test123");
        po.setStatus("ACTIVE");
        po.setScopes("read,write");
        po.setCreateTime(Instant.now());
        return po;
    }
}
