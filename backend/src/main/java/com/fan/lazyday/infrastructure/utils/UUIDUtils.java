package com.fan.lazyday.infrastructure.utils;

import java.util.UUID;

public class UUIDUtils {
    public static String toString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
