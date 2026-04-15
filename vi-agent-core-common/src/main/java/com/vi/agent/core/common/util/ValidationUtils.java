package com.vi.agent.core.common.util;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;

/**
 * 轻量参数校验工具。
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AgentRuntimeException(
                ErrorCode.INVALID_ARGUMENT,
                fieldName + " 不能为空"
            );
        }
    }
}
