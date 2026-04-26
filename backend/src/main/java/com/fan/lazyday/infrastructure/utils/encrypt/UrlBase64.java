package com.fan.lazyday.infrastructure.utils.encrypt;

/**
 * @author chenbin
 */
public class UrlBase64 {
    public static String encode(byte[] bytes) {
        return java.util.Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static byte[] decode(String s) {
        return java.util.Base64.getUrlDecoder().decode(s);
    }
}
