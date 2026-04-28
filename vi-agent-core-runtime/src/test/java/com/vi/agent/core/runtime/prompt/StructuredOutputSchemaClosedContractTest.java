package com.vi.agent.core.runtime.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredOutputSchemaClosedContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> STATE_DELTA_FIELDS = Set.of(
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

    private static final Set<String> SUMMARY_FIELDS = Set.of("summaryText", "skipped", "reason");

    @Test
    void appAndInfraStateDeltaContractsShouldBeClosedAndUseDesignFields() throws Exception {
        assertStateDeltaContract("vi-agent-core-app/src/main/resources/prompt-catalog/system/state_delta_extract/contract.json");
        assertStateDeltaContract("vi-agent-core-infra/src/test/resources/prompt-catalog/system/state_delta_extract/contract.json");
    }

    @Test
    void appAndInfraSummaryContractsShouldBeClosedAndUseDesignFields() throws Exception {
        assertSummaryContract("vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_extract/contract.json");
        assertSummaryContract("vi-agent-core-infra/src/test/resources/prompt-catalog/system/conversation_summary_extract/contract.json");
    }

    private void assertStateDeltaContract(String relativePath) throws Exception {
        String schemaJson = PromptContractTestSupport.readWorkspaceFile(relativePath);
        JsonNode root = OBJECT_MAPPER.readTree(schemaJson);

        assertTrue(root.path("x-structuredOutputContractKey").asText().equals("state_delta_output"));
        assertTrue(root.path("x-outputTarget").asText().equals("state_delta_extraction_result"));
        assertTrue(root.has("x-description"));
        assertObjectSchemasClosed(root);
        assertFields(root.path("properties"), STATE_DELTA_FIELDS);
        assertNestedFields(root, "confirmedFactsAppend", Set.of("factId", "content", "confidence", "lastVerifiedAt", "stalePolicy"));
        assertNestedFields(root, "constraintsAppend", Set.of("constraintId", "content", "scope", "confidence", "lastVerifiedAt"));
        assertNestedFields(root, "decisionsAppend", Set.of("decisionId", "content", "decidedBy", "decidedAt", "confidence"));
        assertNestedFields(root, "openLoopsAppend", Set.of("loopId", "kind", "content", "status", "sourceType", "sourceRef", "createdAt", "closedAt"));
        assertNestedFields(root, "recentToolOutcomesAppend", Set.of("digestId", "toolCallRecordId", "toolExecutionId", "toolName", "summary", "freshnessPolicy", "validUntil", "lastVerifiedAt"));
        assertObjectFields(root, "userPreferencesPatch", Set.of("answerStyle", "detailLevel", "termFormat"));
        assertObjectFields(root, "phaseStatePatch", Set.of("promptEngineeringEnabled", "contextAuditEnabled", "summaryEnabled", "stateExtractionEnabled", "compactionEnabled"));
        assertForbiddenAbsent(schemaJson, Set.of("upsert", "remove", "patches", "operations", "memory", "messages",
            "fullState", "sessionState", "debug", "phaseKey", "phaseName", "locale", "timezone", "statePatches", "evidenceItems"));
    }

    private void assertSummaryContract(String relativePath) throws Exception {
        String schemaJson = PromptContractTestSupport.readWorkspaceFile(relativePath);
        JsonNode root = OBJECT_MAPPER.readTree(schemaJson);

        assertTrue(root.path("x-structuredOutputContractKey").asText().equals("conversation_summary_output"));
        assertTrue(root.path("x-outputTarget").asText().equals("conversation_summary_extraction_result"));
        assertTrue(root.has("x-description"));
        assertObjectSchemasClosed(root);
        assertFields(root.path("properties"), SUMMARY_FIELDS);
        assertForbiddenAbsent(schemaJson, Set.of("sourceMessageIds", "summaryId", "sessionId", "conversationId",
            "summaryVersion", "coveredFromSequenceNo", "coveredToSequenceNo", "summaryTemplateKey",
            "generatorProvider", "generatorModel", "createdAt", "memory", "messages", "stateDelta",
            "evidence", "debug", "upsert", "remove"));
    }

    private void assertFields(JsonNode propertiesNode, Set<String> expectedFields) {
        Iterator<String> fieldNames = propertiesNode.fieldNames();
        Set<String> actualFields = new java.util.LinkedHashSet<>();
        while (fieldNames.hasNext()) {
            actualFields.add(fieldNames.next());
        }
        assertTrue(actualFields.equals(expectedFields), "actual fields: " + actualFields);
    }

    private void assertNestedFields(JsonNode root, String fieldName, Set<String> expectedFields) {
        assertFields(root.path("properties").path(fieldName).path("items").path("properties"), expectedFields);
    }

    private void assertObjectFields(JsonNode root, String fieldName, Set<String> expectedFields) {
        assertFields(root.path("properties").path(fieldName).path("properties"), expectedFields);
    }

    private void assertForbiddenAbsent(String schemaJson, Set<String> forbiddenFields) {
        for (String forbiddenField : forbiddenFields) {
            assertFalse(schemaJson.contains("\"" + forbiddenField + "\""), forbiddenField + " must not appear");
        }
    }

    private void assertObjectSchemasClosed(JsonNode node) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) {
                assertTrue(node.has("additionalProperties"), "object schema must declare additionalProperties: " + node);
                assertFalse(node.path("additionalProperties").asBoolean(true), "object schema must be closed: " + node);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                assertObjectSchemasClosed(fields.next().getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                assertObjectSchemasClosed(item);
            }
        }
    }
}
