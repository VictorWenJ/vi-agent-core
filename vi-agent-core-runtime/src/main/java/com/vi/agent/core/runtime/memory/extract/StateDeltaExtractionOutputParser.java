package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.memory.StateDelta;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates model output for StateDelta extraction.
 */
@Component
public class StateDeltaExtractionOutputParser {

    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
        "taskGoalOverride",
        "confirmedFactsAppend",
        "constraintsAppend",
        "userPreferencesPatch",
        "decisionsAppend",
        "openLoopsAppend",
        "openLoopIdsToClose",
        "recentToolOutcomesAppend",
        "workingModeOverride",
        "phaseStatePatch",
        "sourceCandidateIds"
    );

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
        "upsert",
        "remove",
        "patches",
        "operations",
        "memory",
        "messages",
        "title",
        "decisionText",
        "rationale",
        "description",
        "digestText",
        "expiresAt",
        "openLoopId",
        "evidenceIds",
        "updatedAt"
    );

    private static final Set<String> ALLOWED_CONFIRMED_FACT_FIELDS = Set.of(
        "factId",
        "content",
        "confidence",
        "lastVerifiedAt",
        "stalePolicy"
    );

    private static final Set<String> ALLOWED_CONSTRAINT_FIELDS = Set.of(
        "constraintId",
        "content",
        "scope",
        "confidence",
        "lastVerifiedAt"
    );

    private static final Set<String> ALLOWED_DECISION_FIELDS = Set.of(
        "decisionId",
        "content",
        "decidedBy",
        "decidedAt",
        "confidence"
    );

    private static final Set<String> ALLOWED_OPEN_LOOP_FIELDS = Set.of(
        "loopId",
        "kind",
        "content",
        "status",
        "sourceType",
        "sourceRef",
        "createdAt",
        "closedAt"
    );

    private static final Set<String> ALLOWED_TOOL_OUTCOME_FIELDS = Set.of(
        "digestId",
        "toolCallRecordId",
        "toolExecutionId",
        "toolName",
        "summary",
        "freshnessPolicy",
        "validUntil",
        "lastVerifiedAt"
    );

    private static final Set<String> ALLOWED_USER_PREFERENCE_PATCH_FIELDS = Set.of(
        "answerStyle",
        "detailLevel",
        "termFormat"
    );

    private static final Set<String> ALLOWED_PHASE_STATE_PATCH_FIELDS = Set.of(
        "promptEngineeringEnabled",
        "contextAuditEnabled",
        "summaryEnabled",
        "stateExtractionEnabled",
        "compactionEnabled"
    );

    public StateDeltaExtractionResult parse(String rawOutput) {
        if (StringUtils.isBlank(rawOutput)) {
            return degraded(rawOutput, "Invalid StateDelta JSON: output is blank");
        }

        String json = normalizeJson(rawOutput);
        Map<?, ?> root;
        try {
            root = JsonUtils.jsonToBean(json, Map.class);
        } catch (Exception ex) {
            return degraded(rawOutput, "Invalid StateDelta JSON: " + ex.getMessage());
        }
        if (root == null) {
            return degraded(rawOutput, "Invalid StateDelta JSON: output is not a JSON object");
        }

        Set<String> invalidFields = invalidFields(root);
        if (!invalidFields.isEmpty()) {
            return degraded(rawOutput, "Invalid StateDelta JSON fields: " + String.join(", ", invalidFields));
        }

        try {
            StateDelta stateDelta = JsonUtils.jsonToBean(json, StateDelta.class);
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

    private Set<String> invalidFields(Map<?, ?> root) {
        Set<String> invalid = new LinkedHashSet<>();
        for (Object field : root.keySet()) {
            String fieldName = String.valueOf(field);
            if (FORBIDDEN_FIELDS.contains(fieldName) || !ALLOWED_TOP_LEVEL_FIELDS.contains(fieldName)) {
                invalid.add(fieldName);
            }
        }
        invalid.addAll(invalidListFields("confirmedFactsAppend", root.get("confirmedFactsAppend"), ALLOWED_CONFIRMED_FACT_FIELDS));
        invalid.addAll(invalidListFields("constraintsAppend", root.get("constraintsAppend"), ALLOWED_CONSTRAINT_FIELDS));
        invalid.addAll(invalidListFields("decisionsAppend", root.get("decisionsAppend"), ALLOWED_DECISION_FIELDS));
        invalid.addAll(invalidListFields("openLoopsAppend", root.get("openLoopsAppend"), ALLOWED_OPEN_LOOP_FIELDS));
        invalid.addAll(invalidListFields("recentToolOutcomesAppend", root.get("recentToolOutcomesAppend"), ALLOWED_TOOL_OUTCOME_FIELDS));
        invalid.addAll(invalidObjectFields("userPreferencesPatch", root.get("userPreferencesPatch"), ALLOWED_USER_PREFERENCE_PATCH_FIELDS));
        invalid.addAll(invalidObjectFields("phaseStatePatch", root.get("phaseStatePatch"), ALLOWED_PHASE_STATE_PATCH_FIELDS));
        return invalid;
    }

    private Set<String> invalidListFields(String path, Object value, Set<String> allowedFields) {
        if (!(value instanceof List<?> items)) {
            return Set.of();
        }
        Set<String> invalid = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> itemFields)) {
                continue;
            }
            for (Object field : itemFields.keySet()) {
                String fieldName = String.valueOf(field);
                if (FORBIDDEN_FIELDS.contains(fieldName) || !allowedFields.contains(fieldName)) {
                    invalid.add(path + "[" + i + "]." + fieldName);
                }
            }
        }
        return invalid;
    }

    private Set<String> invalidObjectFields(String path, Object value, Set<String> allowedFields) {
        if (!(value instanceof Map<?, ?> fields)) {
            return Set.of();
        }
        Set<String> invalid = new LinkedHashSet<>();
        for (Object field : fields.keySet()) {
            String fieldName = String.valueOf(field);
            if (FORBIDDEN_FIELDS.contains(fieldName) || !allowedFields.contains(fieldName)) {
                invalid.add(path + "." + fieldName);
            }
        }
        return invalid;
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
