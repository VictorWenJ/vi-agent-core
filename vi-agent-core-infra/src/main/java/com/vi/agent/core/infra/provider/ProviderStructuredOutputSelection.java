package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import lombok.Builder;
import lombok.Value;

/**
 * 单次 provider 请求的结构化输出模式选择结果。
 */
@Value
@Builder(toBuilder = true)
public class ProviderStructuredOutputSelection {

    /** 是否启用结构化输出通道。 */
    Boolean enabled;

    /** 结构化输出契约 key。 */
    StructuredLlmOutputContractKey structuredOutputContractKey;

    /** 请求前选定的结构化输出模式。 */
    StructuredLlmOutputMode selectedStructuredOutputMode;

    /** provider 请求使用的 schema view 对象。 */
    Object providerSchemaView;

    /** provider 请求使用的 schema view JSON。 */
    String providerSchemaViewJson;

    /** 内部 structured output function name。 */
    String functionName;

    /** 内部 structured output function 描述。 */
    String functionDescription;

    /** provider 名称。 */
    String providerName;

    /** 模型名称。 */
    String modelName;

    /** 选择失败或降级原因。 */
    String failureReason;

    /** P2-E 当前不做自动重试，固定为 0。 */
    Integer retryCount;

    /**
     * 未启用结构化输出的选择结果。
     */
    public static ProviderStructuredOutputSelection disabled() {
        return ProviderStructuredOutputSelection.builder()
            .enabled(false)
            .retryCount(0)
            .build();
    }
}
