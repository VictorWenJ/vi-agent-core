package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Unified factory for AgentExecutionResult.
 */
@Component
public class AgentExecutionResultFactory {

    public AgentExecutionResult completed(RuntimeExecutionContext context, LoopExecutionResult loopExecutionResult) {
        AssistantMessage assistantMessage = loopExecutionResult == null ? null : loopExecutionResult.getAssistantMessage();
        return AgentExecutionResult.builder()
            .requestId(context.requestId())
            .runStatus(RunStatus.COMPLETED)
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .userMessageId(context.getUserMessage() == null ? context.getTurn().getUserMessageId() : context.getUserMessage().getMessageId())
            .assistantMessageId(assistantMessage == null ? null : assistantMessage.getMessageId())
            .runId(context.runId())
            .finalAssistantMessage(assistantMessage)
            .finishReason(loopExecutionResult == null ? null : loopExecutionResult.getFinishReason())
            .usage(loopExecutionResult == null ? null : loopExecutionResult.getUsage())
            .createdAt(Instant.now())
            .build();
    }

    public AgentExecutionResult failed(RuntimeExecutionContext context, AgentRuntimeException exception) {
        return AgentExecutionResult.builder()
            .requestId(context.requestId())
            .runStatus(RunStatus.FAILED)
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .userMessageId(context.getUserMessage() == null ? (context.getTurn() == null ? null : context.getTurn().getUserMessageId()) : context.getUserMessage().getMessageId())
            .assistantMessageId(context.getTurn() == null ? null : context.getTurn().getAssistantMessageId())
            .runId(context.runId())
            .finishReason(FinishReason.ERROR)
            .createdAt(Instant.now())
            .build();
    }

    public AgentExecutionResult completedFromTurn(RuntimeExecuteCommand command, Turn turn, Message assistantMessage) {
        AssistantMessage resultMessage = toAssistantMessage(assistantMessage, turn.getTurnId());
        return AgentExecutionResult.builder()
            .requestId(command.getRequestId())
            .runStatus(RunStatus.COMPLETED)
            .conversationId(turn.getConversationId())
            .sessionId(turn.getSessionId())
            .turnId(turn.getTurnId())
            .userMessageId(turn.getUserMessageId())
            .assistantMessageId(turn.getAssistantMessageId())
            .runId(turn.getRunId())
            .finalAssistantMessage(resultMessage)
            .finishReason(turn.getFinishReason())
            .usage(turn.getUsage())
            .createdAt(Instant.now())
            .build();
    }

    public AgentExecutionResult processing(RuntimeExecuteCommand command, Turn turn) {
        return AgentExecutionResult.builder()
            .requestId(command.getRequestId())
            .runStatus(RunStatus.RUNNING)
            .conversationId(turn.getConversationId())
            .sessionId(turn.getSessionId())
            .turnId(turn.getTurnId())
            .userMessageId(turn.getUserMessageId())
            .assistantMessageId(turn.getAssistantMessageId())
            .runId(turn.getRunId())
            .createdAt(Instant.now())
            .build();
    }

    public AgentExecutionResult failedFromTurn(RuntimeExecuteCommand command, Turn turn) {
        FinishReason finishReason = turn.getFinishReason() == null ? FinishReason.ERROR : turn.getFinishReason();
        return AgentExecutionResult.builder()
            .requestId(command.getRequestId())
            .runStatus(RunStatus.FAILED)
            .conversationId(turn.getConversationId())
            .sessionId(turn.getSessionId())
            .turnId(turn.getTurnId())
            .userMessageId(turn.getUserMessageId())
            .assistantMessageId(turn.getAssistantMessageId())
            .runId(turn.getRunId())
            .finalAssistantMessage(AssistantMessage.create(
                turn.getAssistantMessageId(),
                turn.getConversationId(),
                turn.getSessionId(),
                turn.getTurnId(),
                turn.getRunId(),
                0L,
                "",
                Collections.emptyList(),
                finishReason,
                turn.getUsage()
            ))
            .finishReason(finishReason)
            .usage(turn.getUsage())
            .createdAt(Instant.now())
            .build();
    }

    public AgentExecutionResult cancelledFromTurn(RuntimeExecuteCommand command, Turn turn) {
        return AgentExecutionResult.builder()
            .requestId(command.getRequestId())
            .runStatus(RunStatus.CANCELLED)
            .conversationId(turn.getConversationId())
            .sessionId(turn.getSessionId())
            .turnId(turn.getTurnId())
            .userMessageId(turn.getUserMessageId())
            .assistantMessageId(turn.getAssistantMessageId())
            .runId(turn.getRunId())
            .finishReason(FinishReason.CANCELLED)
            .createdAt(Instant.now())
            .build();
    }

    private AssistantMessage toAssistantMessage(Message message, String turnId) {
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage;
        }
        if (message == null) {
            return AssistantMessage.create(
                null,
                null,
                null,
                turnId,
                null,
                0L,
                "",
                Collections.emptyList(),
                null,
                null
            );
        }
        return AssistantMessage.create(
            message.getMessageId(),
            message.getConversationId(),
            message.getSessionId(),
            message.getTurnId(),
            message.getRunId(),
            message.getSequenceNo(),
            message.getContent(),
            Collections.emptyList(),
            null,
            null
        );
    }
}
