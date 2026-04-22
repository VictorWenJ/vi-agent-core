package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.tool.ToolGateway;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentRunContext 构建工厂。
 */
@Component
public class AgentRunContextFactory {

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    @Resource
    private ToolGateway toolGateway;

    public AgentRunContext create(RuntimeExecutionContext context) {
        List<Message> historyMessages = persistenceCoordinator.load(context.conversationId(), context.sessionId());
        List<Message> workingMessages = new ArrayList<>();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            workingMessages.addAll(historyMessages);
        }
        if (context.getUserMessage() != null) {
            workingMessages.add(context.getUserMessage());
        }

        return AgentRunContext.builder()
            .runMetadata(context.getRunMetadata())
            .conversation(context.getResolution().getConversation())
            .session(context.getResolution().getSession())
            .turn(context.getTurn())
            .userInput(context.getCommand().getMessage())
            .workingMessages(workingMessages)
            .availableTools(toolGateway.listDefinitions())
            .state(AgentRunState.STARTED)
            .iteration(0)
            .build();
    }
}
