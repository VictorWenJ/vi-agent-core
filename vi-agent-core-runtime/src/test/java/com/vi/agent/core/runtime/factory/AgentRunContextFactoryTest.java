package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextBuildResult;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoadCommand;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoader;
import com.vi.agent.core.runtime.context.mode.AgentModeResolver;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentRunContextFactoryTest {

    @Test
    void createShouldInitializeWorkingMessagesFromProjectionModelMessages() {
        Message syntheticRuntime = ContextTestFixtures.runtimeSystemMessage();
        Message currentUser = ContextTestFixtures.currentUserMessage();
        WorkingContext context = ContextTestFixtures.context(List.of(
            ContextTestFixtures.runtimeBlock(),
            ContextTestFixtures.currentUserBlock()
        ), ContextBudgetSnapshot.builder()
            .modelMaxInputTokens(200)
            .inputTokenEstimate(20)
            .reservedOutputTokens(20)
            .reservedToolLoopTokens(20)
            .safetyMarginTokens(10)
            .remainingBudget(130)
            .overBudget(false)
            .build());
        WorkingContextProjection projection = WorkingContextProjection.builder()
            .projectionId("wcp-1")
            .workingContextSnapshotId("wctx-1")
            .contextViewType(context.getMetadata().getContextViewType())
            .modelMessages(List.of(syntheticRuntime, currentUser))
            .inputTokenEstimate(20)
            .build();
        StubWorkingContextLoader loader = new StubWorkingContextLoader(ContextTestFixtures.buildResult(context, projection));
        AgentRunContextFactory factory = new AgentRunContextFactory();
        TestFieldUtils.setField(factory, "agentModeResolver", new AgentModeResolver());
        TestFieldUtils.setField(factory, "workingContextLoader", loader);
        TestFieldUtils.setField(factory, "toolGateway", new StubToolGateway());

        AgentRunContext runContext = factory.create(runtimeExecutionContext());

        assertEquals(AgentMode.GENERAL, runContext.getAgentMode());
        assertNotNull(runContext.getWorkingContextBuildResult());
        assertSame(projection, runContext.getWorkingContextBuildResult().getProjection());
        assertEquals(List.of(syntheticRuntime, currentUser), runContext.getWorkingMessages());
        assertEquals(1, loader.loadCount);
    }

    private RuntimeExecutionContext runtimeExecutionContext() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId(ContextTestFixtures.CONVERSATION_ID)
            .sessionId(ContextTestFixtures.SESSION_ID)
            .message("current user")
            .metadata(Map.of("agentMode", "GENERAL"))
            .build();
        RuntimeExecutionContext context = RuntimeExecutionContext.create(command, null, false);
        context.setResolution(SessionResolutionResult.builder()
            .conversation(Conversation.builder()
                .conversationId(ContextTestFixtures.CONVERSATION_ID)
                .status(ConversationStatus.ACTIVE)
                .activeSessionId(ContextTestFixtures.SESSION_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
            .session(Session.builder()
                .sessionId(ContextTestFixtures.SESSION_ID)
                .conversationId(ContextTestFixtures.CONVERSATION_ID)
                .status(SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
            .build());
        context.setRunMetadata(RunMetadata.builder()
            .traceId("trace-1")
            .runId(ContextTestFixtures.RUN_ID)
            .turnId(ContextTestFixtures.TURN_ID)
            .build());
        context.setTurn(Turn.builder()
            .turnId(ContextTestFixtures.TURN_ID)
            .conversationId(ContextTestFixtures.CONVERSATION_ID)
            .sessionId(ContextTestFixtures.SESSION_ID)
            .requestId("req-1")
            .runId(ContextTestFixtures.RUN_ID)
            .status(TurnStatus.RUNNING)
            .userMessageId("msg-current")
            .createdAt(Instant.now())
            .build());
        context.setUserMessage(ContextTestFixtures.currentUserMessage());
        return context;
    }

    private static final class StubWorkingContextLoader extends WorkingContextLoader {
        private final WorkingContextBuildResult result;
        private int loadCount;

        private StubWorkingContextLoader(WorkingContextBuildResult result) {
            this.result = result;
        }

        @Override
        public WorkingContextBuildResult loadForMainAgent(WorkingContextLoadCommand command) {
            loadCount++;
            return result;
        }
    }

    private static final class StubToolGateway implements ToolGateway {
        @Override
        public ToolResult execute(ToolCall toolCall) {
            return null;
        }

        @Override
        public List<ToolDefinition> listDefinitions() {
            return List.of();
        }
    }
}
