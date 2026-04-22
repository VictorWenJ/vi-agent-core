package com.vi.agent.core.runtime.lifecycle;

import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * turn 启动初始化服务。
 */
@Service
public class TurnInitializationService {

    @Resource
    private MessageFactory messageFactory;

    @Resource
    private TurnLifecycleService turnLifecycleService;

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    public TurnStartResult start(RuntimeExecutionContext context) {
        UserMessage userMessage = messageFactory.createUserMessage(
            context.conversationId(),
            context.sessionId(),
            context.getRunMetadata().getTurnId(),
            context.runId(),
            context.getCommand().getMessage()
        );

        Turn turn = turnLifecycleService.createRunningTurn(
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
}
