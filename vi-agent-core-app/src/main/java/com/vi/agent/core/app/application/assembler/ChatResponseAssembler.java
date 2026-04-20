package com.vi.agent.core.app.application.assembler;

import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.app.api.dto.response.UsageInfo;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import org.springframework.stereotype.Component;

/**
 * Assembles API chat response.
 */
@Component
public class ChatResponseAssembler {

    public ChatResponse toResponse(AgentExecutionResult executionResult) {
        return ChatResponse.builder()
            .requestId(executionResult.getRequestId())
            .runStatus(executionResult.getRunStatus())
            .conversationId(executionResult.getConversationId())
            .sessionId(executionResult.getSessionId())
            .turnId(executionResult.getTurnId())
            .userMessageId(executionResult.getUserMessageId())
            .assistantMessageId(executionResult.getAssistantMessageId())
            .runId(executionResult.getRunId())
            .content(executionResult.getFinalAssistantMessage() == null ? null : executionResult.getFinalAssistantMessage().getContent())
            .finishReason(executionResult.getFinishReason())
            .usage(toUsage(executionResult))
            .createdAt(executionResult.getCreatedAt())
            .build();
    }

    private UsageInfo toUsage(AgentExecutionResult executionResult) {
        if (executionResult.getUsage() == null) {
            return null;
        }
        return UsageInfo.builder()
            .inputTokens(executionResult.getUsage().getInputTokens())
            .outputTokens(executionResult.getUsage().getOutputTokens())
            .totalTokens(executionResult.getUsage().getTotalTokens())
            .provider(executionResult.getUsage().getProvider())
            .model(executionResult.getUsage().getModel())
            .build();
    }
}
