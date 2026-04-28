package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
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
            String semanticFailureReason = validateBusinessSemantics(stateDelta);
            if (semanticFailureReason != null) {
                return degraded(rawOutput, semanticFailureReason);
            }
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
    /**
     * 校验 schema 通过后仍需确认的最低业务语义。
     */
    private String validateBusinessSemantics(StateDelta stateDelta) {
        if (stateDelta == null) {
            return null;
        }
        String appendFailureReason = validateAppendRecords(stateDelta);
        if (appendFailureReason != null) {
            return appendFailureReason;
        }
        String openLoopIdFailureReason = validateStringItems("openLoopIdsToClose", stateDelta.getOpenLoopIdsToClose());
        if (openLoopIdFailureReason != null) {
            return openLoopIdFailureReason;
        }
        return validateStringItems("sourceCandidateIds", stateDelta.getSourceCandidateIds());
    }

    /**
     * 校验 append record 的 required 字段在 trim 后仍具备有效业务内容。
     */
    private String validateAppendRecords(StateDelta stateDelta) {
        if (CollectionUtils.isNotEmpty(stateDelta.getConfirmedFactsAppend())) {
            for (ConfirmedFactRecord item : stateDelta.getConfirmedFactsAppend()) {
                String failureReason = validateTextPair("confirmedFactsAppend", item == null ? null : item.getFactId(), "factId", item == null ? null : item.getContent(), "content");
                if (failureReason != null) {
                    return failureReason;
                }
            }
        }
        if (CollectionUtils.isNotEmpty(stateDelta.getConstraintsAppend())) {
            for (ConstraintRecord item : stateDelta.getConstraintsAppend()) {
                String failureReason = validateTextPair("constraintsAppend", item == null ? null : item.getConstraintId(), "constraintId", item == null ? null : item.getContent(), "content");
                if (failureReason != null) {
                    return failureReason;
                }
            }
        }
        if (CollectionUtils.isNotEmpty(stateDelta.getDecisionsAppend())) {
            for (DecisionRecord item : stateDelta.getDecisionsAppend()) {
                String failureReason = validateTextPair("decisionsAppend", item == null ? null : item.getDecisionId(), "decisionId", item == null ? null : item.getContent(), "content");
                if (failureReason != null) {
                    return failureReason;
                }
            }
        }
        if (CollectionUtils.isNotEmpty(stateDelta.getOpenLoopsAppend())) {
            for (OpenLoop item : stateDelta.getOpenLoopsAppend()) {
                String failureReason = validateTextPair("openLoopsAppend", item == null ? null : item.getLoopId(), "loopId", item == null ? null : item.getContent(), "content");
                if (failureReason != null) {
                    return failureReason;
                }
            }
        }
        if (CollectionUtils.isNotEmpty(stateDelta.getRecentToolOutcomesAppend())) {
            for (ToolOutcomeDigest item : stateDelta.getRecentToolOutcomesAppend()) {
                String failureReason = validateTextPair("recentToolOutcomesAppend", item == null ? null : item.getDigestId(), "digestId", item == null ? null : item.getSummary(), "summary");
                if (failureReason != null) {
                    return failureReason;
                }
            }
        }
        return null;
    }

    /**
     * 校验一组 ID / 正文字段均非空白。
     */
    private String validateTextPair(
        String path,
        String idValue,
        String idFieldName,
        String contentValue,
        String contentFieldName
    ) {
        if (StringUtils.isBlank(idValue)) {
            return "Invalid StateDelta JSON: " + path + "." + idFieldName + " is blank";
        }
        if (StringUtils.isBlank(contentValue)) {
            return "Invalid StateDelta JSON: " + path + "." + contentFieldName + " is blank";
        }
        return null;
    }

    /**
     * 校验字符串数组中的每个值在 trim 后非空。
     */
    private String validateStringItems(String path, List<String> items) {
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }
        for (String item : items) {
            if (StringUtils.isBlank(item)) {
                return "Invalid StateDelta JSON: " + path + " contains blank item";
            }
        }
        return null;
    }

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
