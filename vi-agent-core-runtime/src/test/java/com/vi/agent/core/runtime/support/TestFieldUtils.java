package com.vi.agent.core.runtime.support;

import java.lang.reflect.Field;

/**
 * 测试环境下的反射字段注入工具。
 */
public final class TestFieldUtils {

    private TestFieldUtils() {
    }

    public static void setField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("set field failed: " + fieldName, e);
            }
        }
        throw new IllegalArgumentException("field not found: " + fieldName);
    }
}
