package com.fan.lazyday.infrastructure.utils.encrypt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES 单元测试
 * 验证加密/解密对称性和密钥生成
 */
class AESTest {

    @Test
    @DisplayName("encrypt + decrypt: 无 IV 模式的对称性")
    void encryptAndDecrypt_withoutIv_shouldBeSymmetric() {
        byte[] key = AES.generateKey();
        byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AES.encrypt(plaintext, key);
        byte[] decrypted = AES.decrypt(ciphertext, key);

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("encrypt: 带 IV 方法使用 AES/CBC（但源码实际用 ECB，ECB 不支持 IV）")
    void encrypt_withIv_ecbDoesNotSupportIv() {
        // AES 源码使用 "AES" 算法（ECB 模式），ECB 模式不支持 IV
        byte[] key = AES.generateKey();
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AES.encrypt(plaintext, key, iv))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("ECB mode cannot use IV");
    }

    @Test
    @DisplayName("encrypt: 相同明文相同密钥应产生相同密文（ECB 模式）")
    void encrypt_sameInput_shouldProduceSameCiphertext() {
        byte[] key = AES.generateKey();
        byte[] plaintext = "deterministic".getBytes(StandardCharsets.UTF_8);

        byte[] cipher1 = AES.encrypt(plaintext, key);
        byte[] cipher2 = AES.encrypt(plaintext, key);

        assertThat(Arrays.equals(cipher1, cipher2)).isTrue();
    }

    @Test
    @DisplayName("encrypt: 不同密钥应产生不同密文")
    void encrypt_differentKeys_shouldProduceDifferentCiphertext() {
        byte[] key1 = AES.generateKey();
        byte[] key2 = AES.generateKey();
        byte[] plaintext = "same input".getBytes(StandardCharsets.UTF_8);

        byte[] cipher1 = AES.encrypt(plaintext, key1);
        byte[] cipher2 = AES.encrypt(plaintext, key2);

        assertThat(Arrays.equals(cipher1, cipher2)).isFalse();
    }

    @Test
    @DisplayName("encrypt(base64Key): 使用 Base64 编码的密钥加密/解密")
    void encryptWithBase64Key_shouldWork() {
        byte[] key = AES.generateKey();
        String base64Key = Base64.encode(key);
        byte[] plaintext = "base64 key test".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AES.encrypt(plaintext, base64Key);
        byte[] decrypted = AES.decrypt(ciphertext, key);

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("base64 key test");
    }

    @Test
    @DisplayName("generateKey: 每次应生成不同密钥")
    void generateKey_shouldProduceDifferentKeys() {
        byte[] key1 = AES.generateKey();
        byte[] key2 = AES.generateKey();

        assertThat(Arrays.equals(key1, key2)).isFalse();
    }

    @Test
    @DisplayName("decrypt: 使用错误密钥解密应抛出异常")
    void decrypt_withWrongKey_shouldThrowException() {
        byte[] correctKey = AES.generateKey();
        byte[] wrongKey = AES.generateKey();
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AES.encrypt(plaintext, correctKey);

        assertThatThrownBy(() -> AES.decrypt(ciphertext, wrongKey))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Base64: encode + decode 往返对称性")
    void base64_roundTrip_shouldBeSymmetric() {
        byte[] original = {0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE};
        String encoded = Base64.encode(original);
        byte[] decoded = Base64.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }
}
