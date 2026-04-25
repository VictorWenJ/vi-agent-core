package com.vi.agent.core.runtime.memory.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationSummaryExtractionOutputParserTest {

    private final ConversationSummaryExtractionOutputParser parser = new ConversationSummaryExtractionOutputParser();

    @Test
    void parseShouldAcceptValidSummaryJson() {
        ConversationSummaryExtractionResult result = parser.parse("{\"summaryText\":\"new durable summary\"}");

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertFalse(result.isSkipped());
        assertNotNull(result.getConversationSummary());
        assertEquals("new durable summary", result.getConversationSummary().getSummaryText());
    }

    @Test
    void parseShouldRecognizeSkippedOutput() {
        ConversationSummaryExtractionResult result = parser.parse("{\"skipped\":true,\"reason\":\"no meaningful update\"}");

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertFalse(result.isDegraded());
        assertNull(result.getConversationSummary());
    }

    @Test
    void parseShouldRejectInvalidJson() {
        ConversationSummaryExtractionResult result = parser.parse("{not json");

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("invalid summary extraction json"));
    }

    @Test
    void parseShouldRejectUnknownField() {
        ConversationSummaryExtractionResult result = parser.parse("{\"summaryText\":\"x\",\"unexpected\":\"y\"}");

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("unexpected"));
    }

    @Test
    void parseShouldRejectSystemFieldsGeneratedByLlm() {
        ConversationSummaryExtractionResult result = parser.parse("{\"summaryText\":\"x\",\"summaryId\":\"summary-x\"}");

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("summaryId"));
    }

    @Test
    void parseShouldRejectForbiddenMemoryStateEvidenceDebugAndOperationFields() {
        assertRejected("{\"summaryText\":\"x\",\"memory\":{}}", "memory");
        assertRejected("{\"summaryText\":\"x\",\"messages\":[]}", "messages");
        assertRejected("{\"summaryText\":\"x\",\"stateDelta\":{}}", "stateDelta");
        assertRejected("{\"summaryText\":\"x\",\"evidence\":[]}", "evidence");
        assertRejected("{\"summaryText\":\"x\",\"debug\":{}}", "debug");
        assertRejected("{\"summaryText\":\"x\",\"upsert\":[]}", "upsert");
        assertRejected("{\"summaryText\":\"x\",\"remove\":[]}", "remove");
    }

    private void assertRejected(String json, String fieldName) {
        ConversationSummaryExtractionResult result = parser.parse(json);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains(fieldName));
    }
}
