package com.vi.agent.core.app.api.advice;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapInvalidArgumentTo400() {
        HttpStatus status = (HttpStatus) handler.handleAgentRuntimeException(
            new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "bad")
        ).getStatusCode();
        assertEquals(HttpStatus.BAD_REQUEST, status);
    }

    @Test
    void shouldMapToolNotFoundTo404() {
        HttpStatus status = (HttpStatus) handler.handleAgentRuntimeException(
            new AgentRuntimeException(ErrorCode.TOOL_NOT_FOUND, "not found")
        ).getStatusCode();
        assertEquals(HttpStatus.NOT_FOUND, status);
    }

    @Test
    void shouldMapProviderFailureTo502() {
        HttpStatus status = (HttpStatus) handler.handleAgentRuntimeException(
            new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "provider failed")
        ).getStatusCode();
        assertEquals(HttpStatus.BAD_GATEWAY, status);
    }

    @Test
    void shouldMapTranscriptStoreFailureTo500() {
        HttpStatus status = (HttpStatus) handler.handleAgentRuntimeException(
            new AgentRuntimeException(ErrorCode.TRANSCRIPT_STORE_FAILED, "store failed")
        ).getStatusCode();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status);
    }

    @Test
    void shouldMapRuntimeMaxIterationsTo409() {
        HttpStatus status = (HttpStatus) handler.handleAgentRuntimeException(
            new AgentRuntimeException(ErrorCode.RUNTIME_MAX_ITERATIONS_EXCEEDED, "too many loops")
        ).getStatusCode();
        assertEquals(HttpStatus.CONFLICT, status);
    }
}
