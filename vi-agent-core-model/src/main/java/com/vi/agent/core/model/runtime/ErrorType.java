package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行错误类型枚举。
 */
@Getter
@AllArgsConstructor
public enum ErrorType {

    /** 业务错误。 */
    BUSINESS("business", "业务错误"),

    /** 系统错误。 */
    SYSTEM("system", "系统错误"),

    /** Provider 错误。 */
    PROVIDER("provider", "Provider 错误"),

    /** 上下文错误。 */
    CONTEXT("context", "上下文错误");

    private final String value;

    private final String desc;
}
