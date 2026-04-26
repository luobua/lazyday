package com.fan.lazyday.infrastructure.utils.encrypt;

/**
 * @author chenbin
 */
public class Base64 {
    public static String encode(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decode(String s) {
        return java.util.Base64.getDecoder().decode(s);
    }
}
