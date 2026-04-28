package com.vi.agent.core.app.api.dto;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatDtoContractTest {

    @Test
    void chatRequestShouldNotExposeStructuredOutputFields() {
        assertNoStructuredOutputFields(ChatRequest.class);
    }

    @Test
    void chatResponseShouldNotExposeStructuredOutputFields() {
        assertNoStructuredOutputFields(ChatResponse.class);
    }

    @Test
    void chatStreamEventShouldNotExposeStructuredOutputFields() {
        assertNoStructuredOutputFields(ChatStreamEvent.class);
    }

    private void assertNoStructuredOutputFields(Class<?> dtoClass) {
        Set<String> fieldNames = Arrays.stream(dtoClass.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertFalse(fieldNames.contains("structuredOutputContract"));
        assertFalse(fieldNames.contains("preferredStructuredOutputMode"));
        assertFalse(fieldNames.contains("structuredOutputFunctionName"));
        assertFalse(fieldNames.contains("structuredOutputChannelResult"));
        assertFalse(fieldNames.contains("promptRenderMetadata"));
    }
}
