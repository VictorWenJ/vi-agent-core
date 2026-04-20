package com.vi.agent.core.app.api.dto.response;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.runtime.RunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Chat response DTO.
 */
@Getter
@Builder
public class ChatResponse {

    private String requestId;

    private RunStatus runStatus;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String userMessageId;

    private String assistantMessageId;

    private String runId;

    private String content;

    private FinishReason finishReason;

    private UsageInfo usage;

    private Instant createdAt;
}
