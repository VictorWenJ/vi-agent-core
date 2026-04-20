package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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

    public TurnDedupResult findAndBuildByRequestId(String requestId) {
        Optional<Turn> existingTurn = turnRepository.findByRequestId(requestId);
        if (existingTurn.isEmpty()) {
            return null;
        }

        Turn turn = existingTurn.get();
        Message userMessage = messageRepository.findByMessageId(turn.getUserMessageId()).orElse(null);
        Message assistantMessage = null;
        if (turn.getAssistantMessageId() != null) {
            assistantMessage = messageRepository.findByMessageId(turn.getAssistantMessageId()).orElse(null);
        }

        TurnReuseStatus status = switch (turn.getStatus()) {
            case COMPLETED -> TurnReuseStatus.COMPLETED;
            case RUNNING -> TurnReuseStatus.RUNNING;
            case FAILED, CANCELLED -> TurnReuseStatus.FAILED;
        };

        return TurnDedupResult.builder()
            .status(status)
            .turn(turn)
            .userMessage(userMessage)
            .assistantMessage(assistantMessage)
            .build();
    }

    public Turn createRunningTurn(String turnId, String conversationId, String sessionId, String requestId, String runId, String userMessageId) {
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
