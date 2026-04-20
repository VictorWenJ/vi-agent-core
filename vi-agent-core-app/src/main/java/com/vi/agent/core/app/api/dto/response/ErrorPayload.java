package com.vi.agent.core.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Error event payload.
 */
@Getter
@Builder
public class ErrorPayload {

    private String errorCode;

    private String errorMessage;

    private String errorType;

    private boolean retryable;

    private Instant createdAt;
}
