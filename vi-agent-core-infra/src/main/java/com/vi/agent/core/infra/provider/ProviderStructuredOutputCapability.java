package com.vi.agent.core.infra.provider;

import lombok.Builder;
import lombok.Value;

/**
 * Provider 结构化输出能力声明。
 */
@Value
@Builder(toBuilder = true)
public class ProviderStructuredOutputCapability {

    /** provider 稳定名称。 */
    String providerName;

    /** 模型名称。 */
    String modelName;

    /** 是否支持 strict tool call。 */
    Boolean supportsStrictToolCall;

    /** 是否支持 JSON Schema response_format。 */
    Boolean supportsJsonSchemaResponseFormat;

    /** 是否支持 JSON object response_format。 */
    Boolean supportsJsonObject;

    /**
     * DeepSeek 当前 structured output 能力。
     */
    public static ProviderStructuredOutputCapability deepSeek() {
        return ProviderStructuredOutputCapability.builder()
            .providerName("deepseek")
            .modelName("deepseek-chat")
            .supportsStrictToolCall(true)
            .supportsJsonSchemaResponseFormat(false)
            .supportsJsonObject(true)
            .build();
    }

    /**
     * 仅支持 JSON object 的 provider 能力。
     */
    public static ProviderStructuredOutputCapability jsonObjectOnly(String providerName, String modelName) {
        return ProviderStructuredOutputCapability.builder()
            .providerName(providerName)
            .modelName(modelName)
            .supportsStrictToolCall(false)
            .supportsJsonSchemaResponseFormat(false)
            .supportsJsonObject(true)
            .build();
    }
}
