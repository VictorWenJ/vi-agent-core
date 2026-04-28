package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.runtime.prompt.PromptContractTestSupport;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationSummaryExtractionOutputParserTest {

    private final ConversationSummaryExtractionOutputParser parser = new ConversationSummaryExtractionOutputParser(
        PromptContractTestSupport.conversationSummaryContract(),
        new StructuredLlmOutputContractGuard()
    );

    @Test
    void parseShouldAcceptValidSummaryJsonAfterSchemaGuardPasses() {
        ConversationSummaryExtractionResult result = parser.parse(
            PromptContractTestSupport.fixture("prompt-fixtures/summary/valid-output.json")
        );

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertFalse(result.isSkipped());
        assertNotNull(result.getConversationSummary());
        assertEquals("The user prefers concise checklist answers.", result.getConversationSummary().getSummaryText());
    }

    @Test
    void parseShouldRecognizeSkippedOutput() {
        ConversationSummaryExtractionResult result = parser.parse(
            PromptContractTestSupport.fixture("prompt-fixtures/summary/skip-output.json")
        );

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertFalse(result.isDegraded());
        assertNull(result.getConversationSummary());
    }

    @Test
    void parseShouldRejectInvalidJsonAsDegraded() {
        ConversationSummaryExtractionResult result = parser.parse("{not json");

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("invalid summary extraction json"));
    }

    @Test
    void schemaGuardShouldRejectSystemFieldsGeneratedByLlm() {
        assertRejected(PromptContractTestSupport.fixture("prompt-fixtures/summary/invalid-system-field-output.json"), "summaryId");
        assertRejected("{\"summaryText\":\"x\",\"messages\":[]}", "messages");
        assertRejected("{\"summaryText\":\"x\",\"sourceMessageIds\":[\"msg-1\"]}", "sourceMessageIds");
        assertRejected("{\"summaryText\":\"x\",\"debug\":{}}", "debug");
    }

    private void assertRejected(String json, String fieldName) {
        ConversationSummaryExtractionResult result = parser.parse(json);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains(fieldName));
    }
}
