package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredLlmOutputContractGuardTest {

    private final StructuredLlmOutputContractGuard guard = new StructuredLlmOutputContractGuard();

    @Test
    void validStateDeltaOutputShouldPassSchemaGuard() {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.stateDeltaContract(),
            output(
                StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
                PromptContractTestSupport.fixture("prompt-fixtures/state-delta/valid-output.json")
            )
        );

        assertTrue(result.getSuccess());
    }

    @Test
    void stateDeltaTopLevelDebugShouldFailSchemaGuard() {
        assertStateDeltaRejected("{\"sourceCandidateIds\":[],\"debug\":{}}", "debug");
    }

    @Test
    void stateDeltaTopLevelUpsertShouldFailSchemaGuard() {
        assertStateDeltaRejected("{\"sourceCandidateIds\":[],\"upsert\":[]}", "upsert");
    }

    @Test
    void stateDeltaTopLevelLocaleShouldFailSchemaGuard() {
        assertStateDeltaRejected("{\"sourceCandidateIds\":[],\"locale\":\"zh-CN\"}", "locale");
    }

    @Test
    void stateDeltaNestedEvidenceIdsShouldFailSchemaGuard() {
        assertStateDeltaRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-nested-extra-field-output.json"),
            "evidenceIds"
        );
    }

    @Test
    void validSummaryOutputShouldPassSchemaGuard() {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.conversationSummaryContract(),
            output(
                StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT,
                PromptContractTestSupport.fixture("prompt-fixtures/summary/valid-output.json")
            )
        );

        assertTrue(result.getSuccess());
    }

    @Test
    void skippedSummaryOutputShouldPassSchemaGuard() {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.conversationSummaryContract(),
            output(
                StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT,
                PromptContractTestSupport.fixture("prompt-fixtures/summary/skip-output.json")
            )
        );

        assertTrue(result.getSuccess());
    }

    @Test
    void summarySystemFieldsShouldFailSchemaGuard() {
        assertSummaryRejected("{\"summaryText\":\"x\",\"summaryId\":\"summary-1\"}", "summaryId");
        assertSummaryRejected("{\"summaryText\":\"x\",\"messages\":[]}", "messages");
        assertSummaryRejected("{\"summaryText\":\"x\",\"sourceMessageIds\":[\"msg-1\"]}", "sourceMessageIds");
        assertSummaryRejected("{\"summaryText\":\"x\",\"debug\":{}}", "debug");
    }

    @Test
    void schemaExtensionMetadataShouldNotBecomeOutputFields() {
        assertSummaryRejected(
            "{\"summaryText\":\"x\",\"x-structuredOutputContractKey\":\"conversation_summary_output\"}",
            "x-structuredOutputContractKey"
        );
    }

    @Test
    void outputJsonMustBeJsonObject() {
        assertSummaryRejected("[]", "JSON object");
        assertSummaryRejected("\"text\"", "JSON object");
        assertSummaryRejected("1", "JSON object");
    }

    @Test
    void outputContractKeyMustMatchContract() {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.conversationSummaryContract(),
            output(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, "{\"summaryText\":\"x\"}")
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains("contract key"));
    }

    @Test
    void invalidJsonShouldFailSchemaGuard() {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.stateDeltaContract(),
            output(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, "{not-json")
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains("JSON"));
    }

    @Test
    void originalSchemaJsonShouldKeepXMetadataAfterValidation() {
        StructuredLlmOutputContract contract = PromptContractTestSupport.stateDeltaContract();
        String originalSchemaJson = contract.getSchemaJson();

        guard.validate(
            contract,
            output(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, "{\"sourceCandidateIds\":[]}")
        );

        assertTrue(originalSchemaJson.contains("\"x-structuredOutputContractKey\""));
        assertTrue(contract.getSchemaJson().contains("\"x-outputTarget\""));
        assertTrue(contract.getSchemaJson().contains("\"x-description\""));
    }

    private void assertStateDeltaRejected(String json, String expectedReasonPart) {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.stateDeltaContract(),
            output(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, json)
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains(expectedReasonPart));
    }

    private void assertSummaryRejected(String json, String expectedReasonPart) {
        StructuredLlmOutputContractValidationResult result = guard.validate(
            PromptContractTestSupport.conversationSummaryContract(),
            output(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT, json)
        );

        assertFalse(result.getSuccess());
        assertTrue(result.getFailureReason().contains(expectedReasonPart));
    }

    private NormalizedStructuredLlmOutput output(
        StructuredLlmOutputContractKey contractKey,
        String outputJson
    ) {
        return NormalizedStructuredLlmOutput.builder()
            .structuredOutputContractKey(contractKey)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .outputJson(outputJson)
            .providerName("test-provider")
            .modelName("test-model")
            .providerResponseId("response-1")
            .build();
    }
}
