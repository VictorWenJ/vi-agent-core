package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.memory.ConversationSummary;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 会话摘要抽取输出解析器。
 */
@Component
public class ConversationSummaryExtractionOutputParser {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
        "summaryText",
        "skipped",
        "reason"
    );

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
        "summaryId",
        "sessionId",
        "conversationId",
        "summaryVersion",
        "coveredFromSequenceNo",
        "coveredToSequenceNo",
        "summaryTemplateKey",
        "summaryTemplateVersion",
        "generatorProvider",
        "generatorModel",
        "createdAt",
        "memory",
        "messages",
        "stateDelta",
        "evidence",
        "debug",
        "upsert",
        "remove"
    );

    public ConversationSummaryExtractionResult parse(String rawOutput) {
        if (StringUtils.isBlank(rawOutput)) {
            return degraded(rawOutput, "invalid summary extraction json: output is blank");
        }

        String json = normalizeJson(rawOutput);
        Map<?, ?> root;
        try {
            root = JsonUtils.jsonToBean(json, Map.class);
        } catch (Exception ex) {
            return degraded(rawOutput, "invalid summary extraction json: " + ex.getMessage());
        }
        if (root == null) {
            return degraded(rawOutput, "invalid summary extraction json: output is not a JSON object");
        }

        Set<String> invalidFields = invalidFields(root);
        if (!invalidFields.isEmpty()) {
            return degraded(rawOutput, "invalid summary extraction json fields: " + String.join(", ", invalidFields));
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

    private Set<String> invalidFields(Map<?, ?> root) {
        Set<String> invalid = new LinkedHashSet<>();
        for (Object field : root.keySet()) {
            String fieldName = String.valueOf(field);
            if (FORBIDDEN_FIELDS.contains(fieldName) || !ALLOWED_FIELDS.contains(fieldName)) {
                invalid.add(fieldName);
            }
        }
        return invalid;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

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

    private ConversationSummaryExtractionResult degraded(String rawOutput, String failureReason) {
        return ConversationSummaryExtractionResult.builder()
            .success(false)
            .degraded(true)
            .rawOutput(rawOutput)
            .failureReason(failureReason)
            .build();
    }
}
