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

    /** provider base URL，用于判断 beta / capability 边界。 */
    String baseUrl;

    /** 是否支持 strict tool call。 */
    Boolean supportsStrictToolCall;

    /** 是否支持 JSON Schema response_format。 */
    Boolean supportsJsonSchemaResponseFormat;

    /** 是否支持 JSON object response_format。 */
    Boolean supportsJsonObject;

    /** strict tool call 不可用时的原因。 */
    String strictToolCallUnavailableReason;

    /**
     * DeepSeek 默认 structured output 能力；普通 endpoint 默认不启用 strict tool call。
     */
    public static ProviderStructuredOutputCapability deepSeek() {
        return deepSeek("https://api.deepseek.com", "deepseek-chat", false);
    }

    /**
     * DeepSeek structured output 能力，strict tool call 需同时满足显式开关和 beta endpoint。
     */
    public static ProviderStructuredOutputCapability deepSeek(
        String baseUrl,
        String modelName,
        Boolean strictToolCallEnabled
    ) {
        boolean betaEndpoint = baseUrl != null && baseUrl.contains("/beta");
        boolean strictEnabled = Boolean.TRUE.equals(strictToolCallEnabled);
        boolean supportsStrict = strictEnabled && betaEndpoint;
        String unavailableReason = null;
        if (!supportsStrict) {
            unavailableReason = strictEnabled
                ? "DeepSeek strict tool call requires beta endpoint"
                : "DeepSeek strict tool call is disabled";
        }
        return ProviderStructuredOutputCapability.builder()
            .providerName("deepseek")
            .modelName(modelName)
            .baseUrl(baseUrl)
            .supportsStrictToolCall(supportsStrict)
            .supportsJsonSchemaResponseFormat(false)
            .supportsJsonObject(true)
            .strictToolCallUnavailableReason(unavailableReason)
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
