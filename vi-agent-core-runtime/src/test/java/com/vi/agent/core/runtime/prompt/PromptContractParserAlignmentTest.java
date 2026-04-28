package com.vi.agent.core.runtime.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptContractParserAlignmentTest {

    @Test
    void stateDeltaParserShouldUseContractGuardInsteadOfIndependentFieldLists() {
        String source = PromptContractTestSupport.readWorkspaceFile(
            "vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/StateDeltaExtractionOutputParser.java"
        );

        assertTrue(source.contains("StructuredLlmOutputContractGuard"));
        assertTrue(source.contains("StructuredLlmOutputContract"));
        assertFalse(source.contains("ALLOWED_TOP_LEVEL_FIELDS"));
        assertFalse(source.contains("FORBIDDEN_FIELDS"));
        assertFalse(source.contains("ALLOWED_CONFIRMED_FACT_FIELDS"));
        assertFalse(source.contains("invalidFields("));
    }

    @Test
    void conversationSummaryParserShouldUseContractGuardInsteadOfIndependentFieldLists() {
        String source = PromptContractTestSupport.readWorkspaceFile(
            "vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/ConversationSummaryExtractionOutputParser.java"
        );

        assertTrue(source.contains("StructuredLlmOutputContractGuard"));
        assertTrue(source.contains("StructuredLlmOutputContract"));
        assertFalse(source.contains("ALLOWED_FIELDS"));
        assertFalse(source.contains("FORBIDDEN_FIELDS"));
        assertFalse(source.contains("invalidFields("));
    }
}
