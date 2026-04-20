package com.vi.agent.core.app.api.dto.request;

import com.vi.agent.core.model.session.SessionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Chat request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * Client request id for idempotency.
     */
    @NotBlank(message = "requestId must not be blank")
    private String requestId;

    /**
     * Frontend conversation window id.
     */
    private String conversationId;

    /**
     * Runtime session id.
     */
    private String sessionId;

    /**
     * Session resolution mode.
     */
    @NotNull(message = "sessionMode must not be null")
    private SessionMode sessionMode;

    /**
     * Current user input text.
     */
    @NotBlank(message = "message must not be blank")
    private String message;

    /**
     * Optional extension metadata.
     */
    private Map<String, Object> metadata;
}