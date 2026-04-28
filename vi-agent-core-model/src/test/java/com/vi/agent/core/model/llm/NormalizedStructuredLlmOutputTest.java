package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizedStructuredLlmOutputTest {

    @Test
    void shouldCarryNormalizedJsonObjectAndContractMetadataOnly() {
        NormalizedStructuredLlmOutput output = NormalizedStructuredLlmOutput.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .outputJson("{\"sourceCandidateIds\":[]}")
            .providerName("test-provider")
            .modelName("test-model")
            .providerResponseId("response-1")
            .build();

        assertEquals(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, output.getStructuredOutputContractKey());
        assertEquals(StructuredLlmOutputMode.JSON_OBJECT, output.getActualStructuredOutputMode());
        assertEquals("{\"sourceCandidateIds\":[]}", output.getOutputJson());
        assertEquals("test-provider", output.getProviderName());
        assertEquals("test-model", output.getModelName());
        assertEquals("response-1", output.getProviderResponseId());
    }
}
