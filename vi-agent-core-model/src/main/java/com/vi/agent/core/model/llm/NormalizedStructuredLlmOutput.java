package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Provider 结构化输出归一化后的 JSON object 承载对象。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class NormalizedStructuredLlmOutput {

    /** 结构化输出契约 key。 */
    StructuredLlmOutputContractKey structuredOutputContractKey;

    /** Provider 实际使用的结构化输出模式。 */
    StructuredLlmOutputMode actualStructuredOutputMode;

    /** Provider 返回并归一化后的 JSON object 字符串。 */
    String outputJson;

    /** 生成输出的 provider 名称。 */
    String providerName;

    /** 生成输出的模型名称。 */
    String modelName;

    /** Provider 原始响应 ID。 */
    String providerResponseId;
}
