package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class StructuredOutputChannelResultTest {

    @Test
    void shouldCarryStructuredOutputChannelStatus() {
        NormalizedStructuredLlmOutput output = NormalizedStructuredLlmOutput.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .outputJson("{\"skipped\":true,\"reason\":\"no update\"}")
            .build();

        StructuredOutputChannelResult result = StructuredOutputChannelResult.builder()
            .success(false)
            .output(output)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .retryCount(0)
            .failureReason("schema validation failed")
            .build();

        assertFalse(result.getSuccess());
        assertSame(output, result.getOutput());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, result.getActualStructuredOutputMode());
        assertEquals(0, result.getRetryCount());
        assertEquals("schema validation failed", result.getFailureReason());
    }
}
