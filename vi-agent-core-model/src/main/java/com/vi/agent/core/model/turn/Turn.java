package com.vi.agent.core.model.turn;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * A single user request execution turn.
 */
@Getter
@Builder
public class Turn {

    private final String turnId;

    private final String conversationId;

    private final String sessionId;

    private final String requestId;

    private final String runId;

    private TurnStatus status;

    private final String userMessageId;

    private String assistantMessageId;

    private FinishReason finishReason;

    private UsageInfo usage;

    private String errorCode;

    private String errorMessage;

    private final Instant createdAt;

    private Instant completedAt;

    public void markRunning() {
        this.status = TurnStatus.RUNNING;
    }

    public void markCompleted(FinishReason reason, UsageInfo usageInfo, Instant completedTime, String assistantMsgId) {
        this.status = TurnStatus.COMPLETED;
        this.finishReason = reason;
        this.usage = usageInfo;
        this.assistantMessageId = assistantMsgId;
        this.completedAt = completedTime;
    }

    public void markFailed(String code, String message, Instant completedTime) {
        this.status = TurnStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        this.completedAt = completedTime;
    }
}
