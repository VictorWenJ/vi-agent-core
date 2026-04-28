package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModelRequestTest {

    @Test
    void shouldCarryProviderNeutralStructuredOutputFields() {
        StructuredLlmOutputContract contract = StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .outputTarget(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT)
            .schemaJson("{\"type\":\"object\",\"additionalProperties\":false}")
            .description("state delta")
            .build();

        ModelRequest request = ModelRequest.builder()
            .structuredOutputContract(contract)
            .preferredStructuredOutputMode(StructuredLlmOutputMode.STRICT_TOOL_CALL)
            .structuredOutputFunctionName("emit_state_delta")
            .build();

        assertSame(contract, request.getStructuredOutputContract());
        assertEquals(StructuredLlmOutputMode.STRICT_TOOL_CALL, request.getPreferredStructuredOutputMode());
        assertEquals("emit_state_delta", request.getStructuredOutputFunctionName());
    }

    @Test
    void ordinaryRequestShouldNotEnableStructuredOutputByDefault() {
        ModelRequest request = ModelRequest.builder().build();

        assertNull(request.getStructuredOutputContract());
        assertNull(request.getPreferredStructuredOutputMode());
        assertNull(request.getStructuredOutputFunctionName());
    }
}
