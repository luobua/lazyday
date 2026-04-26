package com.fan.lazyday.infrastructure.utils.encrypt;

import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author chenbin
 */
public class AES {
    private final static String ALGORITHM = "AES";
    private final static String KEY_ALGORITHM = "AES";

    private AES() {
        throw new IllegalStateException("Utility class");
    }

    @SneakyThrows
    public static byte[] generateKey() {
        SecretKey secretKey = KeyGenerator.getInstance(KEY_ALGORITHM).generateKey();
        return secretKey.getEncoded();
    }

    // 加密
    public static byte[] encrypt(byte[] data, String base64Key) {
        return encrypt(data, Base64.decode(base64Key));
    }

    // 加密
    @SneakyThrows
    public static byte[] encrypt(byte[] data, byte[] key) {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data);
    }

    @SneakyThrows
    public static byte[] encrypt(byte[] data, byte[] key, byte[] iv) {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(data);
    }

    // 解密
    @SneakyThrows
    public static byte[] decrypt(byte[] data, byte[] key) {
        Cipher decryptCipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);
        return decryptCipher.doFinal(data);
    }

    @SneakyThrows
    public static byte[] decrypt(byte[] data, byte[] key, byte[] iv) {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(data);
    }
}
