package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * 结构化 LLM 输出契约校验结果。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StructuredLlmOutputContractValidationResult {

    /** 是否通过结构化输出契约校验。 */
    Boolean success;

    /** 本次校验使用的结构化输出契约 key。 */
    StructuredLlmOutputContractKey contractKey;

    /** 失败原因摘要。 */
    String failureReason;

    /** 详细错误信息列表。 */
    @Singular("errorMessage")
    List<String> errorMessages;

    /**
     * 构造通过结果。
     */
    public static StructuredLlmOutputContractValidationResult passed(StructuredLlmOutputContractKey contractKey) {
        return StructuredLlmOutputContractValidationResult.builder()
            .success(true)
            .contractKey(contractKey)
            .errorMessages(List.of())
            .build();
    }

    /**
     * 构造失败结果。
     */
    public static StructuredLlmOutputContractValidationResult failed(
        StructuredLlmOutputContractKey contractKey,
        String failureReason
    ) {
        return StructuredLlmOutputContractValidationResult.builder()
            .success(false)
            .contractKey(contractKey)
            .failureReason(failureReason)
            .errorMessages(List.of(failureReason))
            .build();
    }

    /**
     * 构造包含多条错误的失败结果。
     */
    public static StructuredLlmOutputContractValidationResult failed(
        StructuredLlmOutputContractKey contractKey,
        String failureReason,
        List<String> errorMessages
    ) {
        return StructuredLlmOutputContractValidationResult.builder()
            .success(false)
            .contractKey(contractKey)
            .failureReason(failureReason)
            .errorMessages(errorMessages == null ? List.of() : List.copyOf(errorMessages))
            .build();
    }
}
