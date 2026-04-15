package com.vi.agent.core.common.exception;

/**
 * 统一错误码定义。
 */
public enum ErrorCode {

    /** 运行时执行失败。 */
    RUNTIME_EXECUTION_FAILED("RUNTIME-0001", "运行时执行失败"),

    /** 工具不存在。 */
    TOOL_NOT_FOUND("TOOL-0001", "工具不存在"),

    /** 参数不合法。 */
    INVALID_ARGUMENT("COMMON-0001", "参数不合法");

    /** 错误码。 */
    private final String code;

    /** 错误描述。 */
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
