package com.fan.lazyday.infrastructure.utils;


public class VersionUtils {
    public static final long LIMIT = (long)Math.pow((double)2.0F, (double)16.0F) - 1L;
    public static final int SEGMENT_LENGTH = 3;

    public static long toNumber(int x, int y, int z) {
        if (x >= 0 && (long)x <= LIMIT && y >= 0 && (long)y <= LIMIT && z >= 0 && (long)z <= LIMIT) {
            return (long)x << 32 | (long)y << 16 | (long)z;
        } else {
            throw new IllegalArgumentException(String.format("版本数字超出范围 [0, %d]: %d.%d.%d", LIMIT, x, y, z));
        }
    }

    public static long toNumber(String version) {
        String[] split = version.split("\\.");
        if (split.length == 3) {
            try {
                return toNumber(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            } catch (Exception var3) {
            }
        }

        throw new IllegalArgumentException(String.format("版本号格式错误 (x.y.z): %s", version));
    }

    public static String toString(long version) {
        return String.format("%d.%d.%d", version >> 32 & 4095L, version >> 16 & 65535L, version & 65535L);
    }
}
