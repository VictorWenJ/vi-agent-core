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
    RUNTIME_EXECUTION_FAILED("RUNTIME-0001", "Runtime execution failed"),

    /** 运行时循环超过最大迭代次数。 */
    RUNTIME_MAX_ITERATIONS_EXCEEDED("RUNTIME-0002", "Max iterations exceeded"),

    /** 工具注册表中未找到目标工具。 */
    TOOL_NOT_FOUND("TOOL-0001", "Tool not found"),

    /** 工具执行过程中失败。 */
    TOOL_EXECUTION_FAILED("TOOL-0002", "Tool execution failed"),

    /** 当前运行时未注册该工具。 */
    TOOL_NOT_REGISTERED("TOOL-0003", "Tool not registered"),

    /** 模型提供方调用失败。 */
    PROVIDER_CALL_FAILED("PROVIDER-0001", "Model provider call failed"),

    /** 模型提供方配置非法。 */
    PROVIDER_CONFIG_INVALID_FAILED("PROVIDER-0002", "Model provider configuration invalid"),

    /** 对话转录持久化失败。 */
    TRANSCRIPT_STORE_FAILED("PERSIST-0001", "Transcript persistence failed"),

    /** JSON 序列化或反序列化失败。 */
    JSON_SERIALIZATION_FAILED("COMMON-0002", "JSON serialization/deserialization failed"),

    /** 入参不满足校验约束。 */
    INVALID_ARGUMENT("COMMON-0001", "Invalid argument"),

    /** 根据标识未找到 conversation。 */
    CONVERSATION_NOT_FOUND("SESSION-0001", "Conversation not found"),

    /** 根据标识未找到 session。 */
    SESSION_NOT_FOUND("SESSION-0002", "Session not found"),

    /** session 模式缺失或非法。 */
    SESSION_MODE_INVALID("SESSION-0003", "Session mode is invalid"),

    /** session 与 conversation 关系不匹配。 */
    SESSION_CONVERSATION_MISMATCH("SESSION-0004", "Session does not belong to conversation"),

    /** 同一 session 并发请求被锁策略拒绝。 */
    SESSION_CONCURRENT_REQUEST("SESSION-0005", "Concurrent request in same session is rejected");

    /** 稳定错误码值。 */
    private final String code;

    /** 人类可读的默认错误信息。 */
    private final String message;
}
