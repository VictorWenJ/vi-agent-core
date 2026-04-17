package com.vi.agent.core.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一错误码定义。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /** 运行时执行失败。 */
    RUNTIME_EXECUTION_FAILED("RUNTIME-0001", "运行时执行失败"),

    /** 运行时达到最大迭代次数。 */
    RUNTIME_MAX_ITERATIONS_EXCEEDED("RUNTIME-0002", "运行时达到最大迭代次数"),

    /** 工具不存在。 */
    TOOL_NOT_FOUND("TOOL-0001", "工具不存在"),

    /** 工具执行失败。 */
    TOOL_EXECUTION_FAILED("TOOL-0002", "工具执行失败"),

    /** 工具未注册。 */
    TOOL_NOT_REGISTERED("TOOL-0003", "工具未注册"),

    /** 模型提供方调用失败。 */
    PROVIDER_CALL_FAILED("PROVIDER-0001", "模型提供方调用失败"),

    /** 模型提供方配置初始化失败。 */
    PROVIDER_CONFIG_INVALID_FAILED("PROVIDER-0002", "模型提供方配置初始化失败"),

    /** Transcript 存储失败。 */
    TRANSCRIPT_STORE_FAILED("PERSIST-0001", "Transcript 存储失败"),

    /** JSON 序列化或反序列化失败。 */
    JSON_SERIALIZATION_FAILED("COMMON-0002", "JSON 序列化或反序列化失败"),

    /** 参数不合法。 */
    INVALID_ARGUMENT("COMMON-0001", "参数不合法");

    /** 错误码。 */
    private final String code;

    /** 错误描述。 */
    private final String message;
}
