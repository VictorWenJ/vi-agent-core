package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Turn lifecycle service.
 */
@Service
public class TurnLifecycleService {

    @Resource
    private TurnRepository turnRepository;

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private MessageFactory messageFactory;

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    @Transactional(rollbackFor = Exception.class)
    public TurnStartResult startTurn(RuntimeExecutionContext context) {
        UserMessage userMessage = messageFactory.createUserMessage(
            context.conversationId(),
            context.sessionId(),
            context.getRunMetadata().getTurnId(),
            context.runId(),
            context.getCommand().getMessage()
        );

        Turn turn = createRunningTurn(
            context.getRunMetadata().getTurnId(),
            context.conversationId(),
            context.sessionId(),
            context.requestId(),
            context.runId(),
            userMessage.getMessageId()
        );

        persistenceCoordinator.persistUserMessage(userMessage);

        return new TurnStartResult(turn, userMessage);
    }

    public TurnDedupResult findAndBuildByRequestId(String requestId) {
        return turnRepository.findByRequestId(requestId)
            .map(turn ->
                TurnDedupResult.builder()
                    .status(turn.getStatus())
                    .turn(turn)
                    .userMessage(Optional.ofNullable(turn.getUserMessageId())
                        .flatMap(userMessageId -> messageRepository.findByMessageId(userMessageId))
                        .orElse(null))
                    .assistantMessage(Optional.ofNullable(turn.getAssistantMessageId())
                        .flatMap(assistantMessageId -> messageRepository.findByMessageId(assistantMessageId))
                        .orElse(null))
                    .build())
            .orElse(null);
    }

    private Turn createRunningTurn(String turnId, String conversationId, String sessionId, String requestId, String runId, String userMessageId) {
        Turn turn = Turn.builder()
            .turnId(turnId)
            .conversationId(conversationId)
            .sessionId(sessionId)
            .requestId(requestId)
            .runId(runId)
            .status(TurnStatus.RUNNING)
            .userMessageId(userMessageId)
            .assistantMessageId(null)
            .createdAt(Instant.now())
            .build();
        turnRepository.save(turn);
        return turn;
    }

    public boolean existsRunningTurn(String sessionId) {
        return turnRepository.existsRunningBySessionId(sessionId);
    }

    public void completeTurn(Turn turn, String assistantMessageId, FinishReason finishReason, UsageInfo usageInfo) {
        turn.markCompleted(finishReason, usageInfo, Instant.now(), assistantMessageId);
        turnRepository.update(turn);
    }

    public void failTurn(Turn turn, String errorCode, String errorMessage) {
        turn.markFailed(errorCode, errorMessage, Instant.now());
        turnRepository.update(turn);
    }
}
