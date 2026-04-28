package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * provider 承载结构化输出契约的模式。
 */
@Getter
@AllArgsConstructor
public enum StructuredLlmOutputMode {

    /** 严格工具调用结构化输出。 */
    STRICT_TOOL_CALL("strict_tool_call", "严格工具调用结构化输出"),

    /** JSON Schema 响应格式结构化输出。 */
    JSON_SCHEMA_RESPONSE_FORMAT("json_schema_response_format", "JSON Schema 响应格式结构化输出"),

    /** 普通 JSON 对象输出。 */
    JSON_OBJECT("json_object", "普通 JSON 对象输出");

    /** 审计中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}
