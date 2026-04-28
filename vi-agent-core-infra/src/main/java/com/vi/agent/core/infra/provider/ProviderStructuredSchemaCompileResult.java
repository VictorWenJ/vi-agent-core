package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import lombok.Builder;
import lombok.Value;

/**
 * Provider schema view 编译结果。
 */
@Value
@Builder(toBuilder = true)
public class ProviderStructuredSchemaCompileResult {

    /** provider schema view 是否可用。 */
    Boolean available;

    /** 本次编译对应的结构化输出模式。 */
    StructuredLlmOutputMode structuredOutputMode;

    /** provider 请求中直接使用的 schema view 对象。 */
    Object providerSchemaView;

    /** provider schema view 的 JSON 字符串。 */
    String providerSchemaViewJson;

    /** 不可用时的失败原因。 */
    String failureReason;
}
