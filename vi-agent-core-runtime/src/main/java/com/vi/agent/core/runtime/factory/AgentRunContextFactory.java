package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingContextBuildResult;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoadCommand;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoader;
import com.vi.agent.core.runtime.context.mode.AgentModeResolver;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.tool.ToolGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AgentRunContext 构建工厂。
 */
@Slf4j
@Component
public class AgentRunContextFactory {

    @Resource
    private AgentModeResolver agentModeResolver;

    @Resource
    private WorkingContextLoader workingContextLoader;

    @Resource
    private ToolGateway toolGateway;

    public AgentRunContext create(RuntimeExecutionContext context) {
        AgentMode agentMode = agentModeResolver.resolve(context.getCommand());

        WorkingContextBuildResult workingContextBuildResult = workingContextLoader.loadForMainAgent(WorkingContextLoadCommand.builder()
            .conversationId(context.conversationId())
            .sessionId(context.sessionId())
            .turnId(context.turnId())
            .runId(context.runId())
            .currentUserMessage(context.getUserMessage())
            .agentMode(agentMode)
            .contextViewType(ContextViewType.MAIN_AGENT)
            .checkpointTrigger(CheckpointTrigger.BEFORE_FIRST_MODEL_CALL)
            .modelCallSequenceNo(1)
            .build());
        log.info("AgentRunContextFactory create workingContextBuildResult:{}", JsonUtils.toJson(workingContextBuildResult));

        List<Message> workingMessages = workingContextBuildResult.getProjection() == null
            ? List.of()
            : workingContextBuildResult.getProjection().getModelMessages();

        return AgentRunContext.builder()
            .runMetadata(context.getRunMetadata())
            .conversation(context.getResolution().getConversation())
            .session(context.getResolution().getSession())
            .turn(context.getTurn())
            .userInput(context.getCommand().getMessage())
            .agentMode(agentMode)
            .workingContextBuildResult(workingContextBuildResult)
            .workingMessages(workingMessages)
            .availableTools(toolGateway.listDefinitions())
            .state(AgentRunState.STARTED)
            .iteration(0)
            .build();
    }
}
