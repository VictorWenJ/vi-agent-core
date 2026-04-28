package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractGuard;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractValidationResult;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 会话摘要抽取输出解析器。
 */
@Component
public class ConversationSummaryExtractionOutputParser {

    /** 会话摘要结构化输出契约。 */
    private final StructuredLlmOutputContract contract;

    /** 结构化输出契约守卫。 */
    private final StructuredLlmOutputContractGuard contractGuard;

    /**
     * 通过系统 prompt 注册表注入会话摘要输出契约。
     */
    @Autowired
    public ConversationSummaryExtractionOutputParser(
        SystemPromptRegistry systemPromptRegistry,
        StructuredLlmOutputContractGuard contractGuard
    ) {
        this(
            systemPromptRegistry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT),
            contractGuard
        );
    }

    /**
     * 构造测试可直接注入的会话摘要输出解析器。
     */
    ConversationSummaryExtractionOutputParser(
        StructuredLlmOutputContract contract,
        StructuredLlmOutputContractGuard contractGuard
    ) {
        this.contract = Objects.requireNonNull(contract, "contract must not be null");
        this.contractGuard = Objects.requireNonNull(contractGuard, "contractGuard must not be null");
    }

    /**
     * 解析旧主链路传入的原始 JSON 字符串。
     */
    public ConversationSummaryExtractionResult parse(String rawOutput) {
        if (StringUtils.isBlank(rawOutput)) {
            return degraded(rawOutput, "invalid summary extraction json: output is blank");
        }
        String json = normalizeJson(rawOutput);
        return parse(NormalizedStructuredLlmOutput.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .outputJson(json)
            .build(), rawOutput);
    }

    /**
     * 解析 provider 归一化后的结构化输出。
     */
    public ConversationSummaryExtractionResult parse(NormalizedStructuredLlmOutput output) {
        if (output == null) {
            return degraded(null, "invalid summary extraction json: output is null");
        }
        return parse(output, output.getOutputJson());
    }

    /**
     * 使用 contract guard 完成 schema 校验后再映射会话摘要结果。
     */
    private ConversationSummaryExtractionResult parse(
        NormalizedStructuredLlmOutput output,
        String rawOutput
    ) {
        StructuredLlmOutputContractValidationResult validationResult = contractGuard.validate(contract, output);
        if (!Boolean.TRUE.equals(validationResult.getSuccess())) {
            return degraded(rawOutput, "invalid summary extraction json: " + validationResult.getFailureReason());
        }

        Map<?, ?> root;
        try {
            root = JsonUtils.jsonToBean(output.getOutputJson(), Map.class);
        } catch (Exception ex) {
            return degraded(rawOutput, "invalid summary extraction json: " + ex.getMessage());
        }
        if (root == null) {
            return degraded(rawOutput, "invalid summary extraction json: output is not a JSON object");
        }

        if (Boolean.TRUE.equals(root.get("skipped"))) {
            return ConversationSummaryExtractionResult.builder()
                .success(true)
                .skipped(true)
                .rawOutput(rawOutput)
                .failureReason(asString(root.get("reason")))
                .build();
        }

        String summaryText = asString(root.get("summaryText"));
        if (StringUtils.isBlank(summaryText)) {
            return ConversationSummaryExtractionResult.builder()
                .success(true)
                .skipped(true)
                .rawOutput(rawOutput)
                .failureReason("summaryText is blank")
                .build();
        }

        return ConversationSummaryExtractionResult.builder()
            .success(true)
            .rawOutput(rawOutput)
            .conversationSummary(ConversationSummary.builder()
                .summaryText(summaryText)
                .build())
            .build();
    }

    /**
     * 将字段值转成字符串。
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
    private ConversationSummaryExtractionResult degraded(String rawOutput, String failureReason) {
        return ConversationSummaryExtractionResult.builder()
            .success(false)
            .degraded(true)
            .rawOutput(rawOutput)
            .failureReason(failureReason)
            .build();
    }
}
