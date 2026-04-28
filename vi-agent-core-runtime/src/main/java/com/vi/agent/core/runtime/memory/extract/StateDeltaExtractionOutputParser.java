package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractGuard;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractValidationResult;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistry;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * StateDelta 抽取输出解析器。
 */
@Component
public class StateDeltaExtractionOutputParser {

    /** 状态增量结构化输出契约。 */
    private final StructuredLlmOutputContract contract;

    /** 结构化输出契约守卫。 */
    private final StructuredLlmOutputContractGuard contractGuard;

    /**
     * 通过系统 prompt 注册表注入状态增量输出契约。
     */
    @Autowired
    public StateDeltaExtractionOutputParser(
        SystemPromptRegistry systemPromptRegistry,
        StructuredLlmOutputContractGuard contractGuard
    ) {
        this(
            systemPromptRegistry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT),
            contractGuard
        );
    }

    /**
     * 构造测试可直接注入的状态增量输出解析器。
     */
    StateDeltaExtractionOutputParser(
        StructuredLlmOutputContract contract,
        StructuredLlmOutputContractGuard contractGuard
    ) {
        this.contract = Objects.requireNonNull(contract, "contract must not be null");
        this.contractGuard = Objects.requireNonNull(contractGuard, "contractGuard must not be null");
    }

    /**
     * 解析旧主链路传入的原始 JSON 字符串。
     */
    public StateDeltaExtractionResult parse(String rawOutput) {
        if (StringUtils.isBlank(rawOutput)) {
            return degraded(rawOutput, "Invalid StateDelta JSON: output is blank");
        }
        String json = normalizeJson(rawOutput);
        return parse(NormalizedStructuredLlmOutput.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .outputJson(json)
            .build(), rawOutput);
    }

    /**
     * 解析 provider 归一化后的结构化输出。
     */
    public StateDeltaExtractionResult parse(NormalizedStructuredLlmOutput output) {
        if (output == null) {
            return degraded(null, "Invalid StateDelta JSON: output is null");
        }
        return parse(output, output.getOutputJson());
    }

    /**
     * 使用 contract guard 完成 schema 校验后再映射 StateDelta。
     */
    private StateDeltaExtractionResult parse(NormalizedStructuredLlmOutput output, String rawOutput) {
        StructuredLlmOutputContractValidationResult validationResult = contractGuard.validate(contract, output);
        if (!Boolean.TRUE.equals(validationResult.getSuccess())) {
            return degraded(rawOutput, "Invalid StateDelta JSON: " + validationResult.getFailureReason());
        }

        try {
            StateDelta stateDelta = JsonUtils.jsonToBean(output.getOutputJson(), StateDelta.class);
            List<String> sourceCandidateIds = stateDelta == null || CollectionUtils.isEmpty(stateDelta.getSourceCandidateIds())
                ? List.of()
                : List.copyOf(stateDelta.getSourceCandidateIds());
            return StateDeltaExtractionResult.builder()
                .success(true)
                .degraded(false)
                .stateDelta(stateDelta == null ? StateDelta.builder().build() : stateDelta)
                .rawOutput(rawOutput)
                .sourceCandidateIds(sourceCandidateIds)
                .build();
        } catch (Exception ex) {
            return degraded(rawOutput, "Invalid StateDelta JSON: " + ex.getMessage());
        }
    }

    /**
     * 去除模型可能返回的 Markdown 代码块边界。
     */
    private String normalizeJson(String rawOutput) {
        String trimmed = rawOutput.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFenceStart = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFenceStart > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFenceStart).trim();
            }
        }
        return trimmed;
    }

    /**
     * 构造 degraded 解析结果。
     */
    private StateDeltaExtractionResult degraded(String rawOutput, String failureReason) {
        return StateDeltaExtractionResult.builder()
            .success(false)
            .degraded(true)
            .rawOutput(rawOutput)
            .failureReason(failureReason)
            .sourceCandidateIds(List.of())
            .build();
    }
}
