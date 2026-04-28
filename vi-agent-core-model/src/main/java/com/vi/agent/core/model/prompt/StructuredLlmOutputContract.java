package com.vi.agent.core.model.prompt;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * LLM 输出映射成业务对象前的结构化输出契约。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StructuredLlmOutputContract {

    /** 结构化输出契约 key。 */
    StructuredLlmOutputContractKey structuredOutputContractKey;

    /** 输出最终映射目标。 */
    StructuredLlmOutputTarget outputTarget;

    /** 结构化输出 JSON Schema。 */
    String schemaJson;

    /** 契约说明。 */
    String description;
}
