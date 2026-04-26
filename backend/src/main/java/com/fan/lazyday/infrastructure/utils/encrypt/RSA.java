package com.fan.lazyday.infrastructure.utils.encrypt;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author chenbin
 */
public class RSA {
    private final static String ALGORITHM = "RSA";
    private final static String SIGNATURE_ALGORITHM = "MD5withRSA";
    private final static int KEY_SIZE = 512;

    enum Mode {
        ENCRYPT,
        DECRYPT,
    }

    /**
     * 用私钥对信息生成数字签名
     *
     * @param data
     * @param privateKey
     * @return
     * @throws Exception
     */
    public static String signByPrivateKey(byte[] data, PrivateKey privateKey) throws Exception {
        return signByPrivateKey(data, privateKey.getEncoded());
    }

    public static String signByPrivateKey(byte[] data, String privateKey) throws Exception {
        byte[] encodedKey = Base64.decode(privateKey);
        return signByPrivateKey(data, encodedKey);
    }

    public static String signByPrivateKey(byte[] data, byte[] primaryKey) throws Exception {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(primaryKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateK);
        signature.update(data);
        return Base64.encode(signature.sign());
    }

    /**
     * 用公钥进行验签
     *
     * @param data
     * @param publicKey
     * @param sign
     * @return
     * @throws Exception
     */
    public static boolean verifyByPublicKey(byte[] data, PublicKey publicKey, String sign) throws Exception {
        return verifyByPublicKey(data, publicKey.getEncoded(), Base64.decode(sign));
    }

    public static boolean verifyByPublicKey(byte[] data, String publicKey, String sign) throws Exception {
        return verifyByPublicKey(data, Base64.decode(publicKey), Base64.decode(sign));
    }

    public static boolean verifyByPublicKey(byte[] data, byte[] publicKey, byte[] sign) throws Exception {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PublicKey publicK = keyFactory.generatePublic(x509EncodedKeySpec);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicK);
        signature.update(data);
        return signature.verify(sign);
    }

    /**
     * 私钥加密
     *
     * @param data
     * @param privateKey base64-privateKey
     * @return
     * @throws Exception
     */
    public static byte[] encryptByPrivateKey(byte[] data, String privateKey) throws Exception {
        byte[] encodedKey = Base64.decode(privateKey);
        return encryptByPrivateKey(data, encodedKey);
    }

    /**
     * 私钥加密
     *
     * @param data
     * @param privateKey
     * @return
     */
    public static byte[] encryptByPrivateKey(byte[] data, byte[] privateKey) throws Exception {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, privateK);
        return process(data, cipher, Mode.ENCRYPT);
    }

    /**
     * 私钥解密
     *
     * @param encryptedData
     * @param privateKey    base64-privateKey
     * @return
     * @throws Exception
     */
    public static byte[] decryptByPrivateKey(byte[] encryptedData, String privateKey) throws Exception {
        byte[] encodedKey = Base64.decode(privateKey);
        return decryptByPrivateKey(encryptedData, encodedKey);
    }

    /**
     * 私钥解密
     *
     * @param encryptedData
     * @param privateKey
     * @return
     */
    public static byte[] decryptByPrivateKey(byte[] encryptedData, byte[] privateKey) throws Exception {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        Key privateK = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, privateK);
        return process(encryptedData, cipher, Mode.DECRYPT);
    }

    /**
     * 公钥加密
     *
     * @param data
     * @param publicKey base64-privateKey
     * @return
     * @throws Exception
     */
    public static byte[] encryptByPublicKey(byte[] data, String publicKey) throws Exception {
        byte[] encodedKey = Base64.decode(publicKey);
        return encryptByPublicKey(data, encodedKey);
    }

    /**
     * 公钥加密
     *
     * @param data
     * @param publicKey encoded-key
     * @return
     */
    public static byte[] encryptByPublicKey(byte[] data, byte[] publicKey) throws Exception {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PublicKey publicK = keyFactory.generatePublic(x509EncodedKeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, publicK);
        return process(data, cipher, Mode.ENCRYPT);
    }

    public static byte[] decryptByPublicKey(byte[] encryptedData, String publicKey) throws Exception {
        byte[] encodedKey = Base64.decode(publicKey);
        return decryptByPublicKey(encryptedData, encodedKey);
    }

    /**
     * 公钥解密
     *
     * @param encryptedData
     * @param publicKey
     * @return
     */
    public static byte[] decryptByPublicKey(byte[] encryptedData, byte[] publicKey) throws Exception {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509EncodedKeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, publicK);
        return process(encryptedData, cipher, Mode.DECRYPT);
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
        keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    public static byte[] process(byte[] data, Cipher cipher, Mode mode) throws IOException, IllegalBlockSizeException, BadPaddingException {
        int len = data.length;
        int blockSize = cipher.getOutputSize(data.length);
        if (mode == Mode.ENCRYPT) {
            blockSize -= 11;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            int offset = 0;
            byte[] cache;
            while (len - offset > 0) {
                if (len - offset > blockSize) {
                    cache = cipher.doFinal(data, offset, blockSize);
                    offset += blockSize;
                } else {
                    cache = cipher.doFinal(data, offset, len - offset);
                    offset = len;
                }
                bos.write(cache, 0, cache.length);
            }
            return bos.toByteArray();
        }
    }

    private static PrivateKey pkcs8PrivateKey(byte[] privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        return keyFactory.generatePrivate(pkcs8EncodedKeySpec);
    }

    public static void main2(String[] args) throws Exception {
        KeyPair keyPair = generateKeyPair();

        String primaryKey = Base64.encode(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.encode(keyPair.getPublic().getEncoded());

        String content = "123456";

        byte[] encryptContent = encryptByPrivateKey(content.getBytes(StandardCharsets.UTF_8), primaryKey);
        String encryptData = Base64.encode(encryptContent);
        byte[] decryptContent = decryptByPublicKey(encryptContent, publicKey);
        String decryptData = new String(decryptContent, StandardCharsets.UTF_8);

        Files.write(Paths.get("/Users/chenbin/Desktop/20230329/rsa/primary.key"), primaryKey.getBytes(StandardCharsets.UTF_8));

        Files.write(Paths.get("/Users/chenbin/Desktop/20230329/rsa/public.key"), publicKey.getBytes(StandardCharsets.UTF_8));

        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        String privateKey = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAKIj/bU5QfaPfrvi9rWdAIzMokIslu4A8Vw25bKF1YuMtVO7cM+IHJ88+Zb2NRpzF15drEa4NXHdSH4Xi3kTMfMHukwouFENHnXbiBXbx0ywqDM+sg0gvHyTG3NzA3/KkS9GlJv0OcAQcgT3mlPQM4rBeHOgSkvoOQ4SDoeAZ2EvAgMBAAECgYBkgUrS+xsYavp0HMww55Fftbkeq+UiwxLZxm27q5ojVqVPsfDLs+OKEZHlMdjY/F6P8CDJ9YWrfy9gedfKxUtADe2OEv7UeS1akeHMFh47OxTt+uDvPDuTWHTM2B5SyfTcCkJ9DgyrbqfczwB8qJwOS3nKmf6GrvOIyeL2DFk/gQJBANFkgFxaZyXpIZ74xfdZOVWO0nOd5O97DrySuXWaqC+Gbz7hxCQ6vlFma4CovjLWuCgtJFk4+pSOecX5CgzwZRUCQQDGOwBTZNLdNwT9xHX0f078n4WYrAop7YsnPslvk4YvnOlf7nyPgZ52BpYx5OhWH62MPIz1vlUslPQZZQsEQcYzAkEAmAT7HBGWKXPkMOIz96wTcAZMzBuqBiO1QzrS4orx5+8V/PUzzYnIeph9G99mlspE0QZVCWHQquH1jsGLhnFRMQJAZ57b8rvXhg+GqmiSCDasQV3Z5g64WWZ0wRUPEyYYlLb/P0hZEGL/RE75ICe6U5rHi3DzdloCCAXs+4FYGuePBQJALu7L6f0ML0snenjPhk5Ld5udill6bRr/40utTBWM90LozO30FykYtH6cJw6P7IrNA+OgdrROl4P/t5JgwooS5A==";
        String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCiI/21OUH2j3674va1nQCMzKJCLJbuAPFcNuWyhdWLjLVTu3DPiByfPPmW9jUacxdeXaxGuDVx3Uh+F4t5EzHzB7pMKLhRDR5124gV28dMsKgzPrINILx8kxtzcwN/ypEvRpSb9DnAEHIE95pT0DOKwXhzoEpL6DkOEg6HgGdhLwIDAQAB";

        String content = "123";

        byte[] encryptContent = encryptByPrivateKey(
                content.toString().getBytes(StandardCharsets.UTF_8),
                privateKey);

        String encryptData = Base64.encode(encryptContent);
        System.out.println("- encrypt data -");
        System.out.println(encryptData);

        byte[] decryptContent = decryptByPublicKey(encryptContent, publicKey);

        String decryptData = new String(decryptContent, StandardCharsets.UTF_8);
        System.out.println("- decrypt data -");
        System.out.println(decryptData);
    }
}
