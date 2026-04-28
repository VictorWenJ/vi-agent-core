package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModelResponseTest {

    @Test
    void shouldCarryStructuredOutputChannelResult() {
        NormalizedStructuredLlmOutput output = NormalizedStructuredLlmOutput.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .outputJson("{\"sourceCandidateIds\":[]}")
            .build();
        StructuredOutputChannelResult channelResult = StructuredOutputChannelResult.builder()
            .success(true)
            .output(output)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .retryCount(0)
            .build();

        ModelResponse response = ModelResponse.builder()
            .structuredOutputChannelResult(channelResult)
            .build();

        assertSame(channelResult, response.getStructuredOutputChannelResult());
    }

    @Test
    void ordinaryResponseShouldNotCarryStructuredOutputByDefault() {
        ModelResponse response = ModelResponse.builder().build();

        assertNull(response.getStructuredOutputChannelResult());
    }
}
