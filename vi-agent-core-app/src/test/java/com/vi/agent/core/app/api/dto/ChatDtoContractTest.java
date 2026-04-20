package com.vi.agent.core.app.api.dto;

import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ChatDtoContractTest {

    @Test
    void responseDtosShouldNotExposeTraceId() {
        Set<String> responseFields = Arrays.stream(ChatResponse.class.getDeclaredFields())
            .map(field -> field.getName())
            .collect(Collectors.toSet());
        Set<String> streamFields = Arrays.stream(ChatStreamEvent.class.getDeclaredFields())
            .map(field -> field.getName())
            .collect(Collectors.toSet());

        assertFalse(responseFields.contains("traceId"));
        assertFalse(streamFields.contains("traceId"));
        assertTrue(responseFields.contains("runStatus"));
        assertTrue(streamFields.contains("runStatus"));
    }
}
