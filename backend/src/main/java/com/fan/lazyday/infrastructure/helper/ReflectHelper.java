package com.fan.lazyday.infrastructure.helper;


import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectHelper {
    private ReflectHelper() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    public static <T> T getDeclaredFieldValue(Object obj, String fieldName) throws ReflectiveOperationException {
        Field field = obj.getClass().getDeclaredField(fieldName);
        boolean isInAccessible = !field.canAccess(obj);
        if (isInAccessible) {
            field.setAccessible(true);
        }

        Object var4;
        try {
            var4 = field.get(obj);
        } finally {
            if (isInAccessible) {
                field.setAccessible(false);
            }

        }

        return (T)var4;
    }

    public static <T> T invokeDeclaredMethod(Object obj, String methodName) throws ReflectiveOperationException {
        Method method = obj.getClass().getDeclaredMethod(methodName);
        boolean isInAccessible = !method.canAccess(obj);
        if (isInAccessible) {
            method.setAccessible(true);
        }

        Object var4;
        try {
            var4 = method.invoke(obj);
        } finally {
            if (isInAccessible) {
                method.setAccessible(false);
            }

        }

        return (T)var4;
    }
}
