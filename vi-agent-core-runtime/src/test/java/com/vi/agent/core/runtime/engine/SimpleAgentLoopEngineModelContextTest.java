package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.context.ModelContextMessageFilter;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleAgentLoopEngineModelContextTest {

    @Test
    void runShouldKeepToolCallOutOfWorkingMessagesButPersistInAppendedMessages() throws Exception {
        ModelToolCall modelToolCall = ModelToolCall.builder()
            .toolCallId("call-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .build();
        CapturingLlmGateway llmGateway = new CapturingLlmGateway(List.of(
            ModelResponse.builder()
                .content("我来查时间")
                .toolCalls(List.of(modelToolCall))
                .finishReason(FinishReason.TOOL_CALL)
                .usage(UsageInfo.empty())
                .provider("deepseek")
                .model("deepseek-chat")
                .build(),
            ModelResponse.builder()
                .content("现在是 16:00")
                .toolCalls(List.of())
                .finishReason(FinishReason.STOP)
                .usage(UsageInfo.empty())
                .provider("deepseek")
                .model("deepseek-chat")
                .build()
        ));
        StubToolGateway toolGateway = new StubToolGateway();
        StubMessageFactory messageFactory = new StubMessageFactory();

        SimpleAgentLoopEngine engine = new SimpleAgentLoopEngine();
        setField(engine, "llmGateway", llmGateway);
        setField(engine, "toolGateway", toolGateway);
        setField(engine, "messageFactory", messageFactory);
        setField(engine, "modelContextMessageFilter", new ModelContextMessageFilter());
        setField(engine, "maxIterations", 3);

        AgentRunContext runContext = buildRunContext();
        LoopExecutionResult loopExecutionResult = engine.run(runContext);

        assertTrue(runContext.getWorkingMessages().stream()
            .noneMatch(message -> message instanceof ToolCallMessage));
        assertTrue(runContext.getWorkingMessages().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .anyMatch(message -> !message.getToolCalls().isEmpty()));
        assertTrue(runContext.getWorkingMessages().stream()
            .anyMatch(message -> message instanceof ToolResultMessage));

        assertTrue(loopExecutionResult.getAppendedMessages().stream()
            .anyMatch(message -> message instanceof ToolCallMessage));
        assertTrue(loopExecutionResult.getAppendedMessages().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .anyMatch(message -> !message.getToolCalls().isEmpty()));
        assertTrue(loopExecutionResult.getAppendedMessages().stream()
            .anyMatch(message -> message instanceof ToolResultMessage));
    }

    @Test
    void modelRequestShouldNotContainToolCallMessageAcrossToolLoop() throws Exception {
        ModelToolCall modelToolCall = ModelToolCall.builder()
            .toolCallId("call-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .build();
        CapturingLlmGateway llmGateway = new CapturingLlmGateway(List.of(
            ModelResponse.builder()
                .content("我来查时间")
                .toolCalls(List.of(modelToolCall))
                .finishReason(FinishReason.TOOL_CALL)
                .usage(UsageInfo.empty())
                .provider("deepseek")
                .model("deepseek-chat")
                .build(),
            ModelResponse.builder()
                .content("现在是 16:00")
                .toolCalls(List.of())
                .finishReason(FinishReason.STOP)
                .usage(UsageInfo.empty())
                .provider("deepseek")
                .model("deepseek-chat")
                .build()
        ));
        StubToolGateway toolGateway = new StubToolGateway();
        StubMessageFactory messageFactory = new StubMessageFactory();

        SimpleAgentLoopEngine engine = new SimpleAgentLoopEngine();
        setField(engine, "llmGateway", llmGateway);
        setField(engine, "toolGateway", toolGateway);
        setField(engine, "messageFactory", messageFactory);
        setField(engine, "modelContextMessageFilter", new ModelContextMessageFilter());
        setField(engine, "maxIterations", 3);

        engine.run(buildRunContext());

        assertEquals(2, llmGateway.capturedRequests.size());
        assertTrue(llmGateway.capturedRequests.stream()
            .allMatch(request -> request.getMessages().stream().noneMatch(ToolCallMessage.class::isInstance)));

        ModelRequest secondRequest = llmGateway.capturedRequests.get(1);
        assertEquals(3, secondRequest.getMessages().size());
        assertTrue(secondRequest.getMessages().get(0) instanceof UserMessage);
        assertTrue(secondRequest.getMessages().get(1) instanceof AssistantMessage);
        assertTrue(secondRequest.getMessages().get(2) instanceof ToolResultMessage);

        long assistantToolCallMessageCount = secondRequest.getMessages().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .filter(message -> !message.getToolCalls().isEmpty())
            .count();
        assertEquals(1, assistantToolCallMessageCount);
        assertFalse(secondRequest.getMessages().stream()
            .anyMatch(message -> message instanceof ToolCallMessage));
    }

    private static AgentRunContext buildRunContext() {
        UserMessage userMessage = UserMessage.create("msg-user-1", "turn-1", 1L, "现在是什么时间");
        return AgentRunContext.builder()
            .runMetadata(RunMetadata.builder()
                .traceId("trace-1")
                .runId("run-1")
                .turnId("turn-1")
                .build())
            .conversation(Conversation.builder()
                .conversationId("conv-1")
                .title("test")
                .status(ConversationStatus.ACTIVE)
                .activeSessionId("sess-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build())
            .session(Session.builder()
                .sessionId("sess-1")
                .conversationId("conv-1")
                .status(SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
            .turn(Turn.builder()
                .turnId("turn-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .requestId("req-1")
                .runId("run-1")
                .status(TurnStatus.RUNNING)
                .userMessageId("msg-user-1")
                .createdAt(Instant.now())
                .build())
            .userInput("现在是什么时间")
            .workingMessages(new ArrayList<>(List.of(userMessage)))
            .availableTools(List.of(ToolDefinition.builder()
                .name("get_time")
                .description("获取当前时间")
                .parametersJson("{}")
                .build()))
            .state(AgentRunState.STARTED)
            .iteration(0)
            .build();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SimpleAgentLoopEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CapturingLlmGateway implements LlmGateway {
        private final List<ModelResponse> responses;
        private final List<ModelRequest> capturedRequests = new ArrayList<>();
        private int index = 0;

        private CapturingLlmGateway(List<ModelResponse> responses) {
            this.responses = responses;
        }

        @Override
        public ModelResponse generate(ModelRequest modelRequest) {
            capturedRequests.add(modelRequest);
            return responses.get(index++);
        }

        @Override
        public ModelResponse generateStreaming(ModelRequest modelRequest, java.util.function.Consumer<String> chunkConsumer) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }

    private static final class StubToolGateway implements ToolGateway {

        @Override
        public ToolResult execute(ToolCall toolCall) {
            return ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(true)
                .output("2026-04-21T16:00:00+08:00")
                .durationMs(1L)
                .build();
        }

        @Override
        public List<ToolDefinition> listDefinitions() {
            return List.of();
        }
    }

    private static final class StubMessageFactory extends MessageFactory {
        private long sequence = 1L;
        private long messageCounter = 0L;

        @Override
        public String nextAssistantMessageId() {
            return "msg-assistant-" + (++messageCounter);
        }

        @Override
        public AssistantMessage createAssistantMessage(
            String sessionId,
            String turnId,
            String assistantMessageId,
            String content,
            List<ModelToolCall> toolCalls
        ) {
            return AssistantMessage.create(assistantMessageId, turnId, ++sequence, content, toolCalls);
        }

        @Override
        public String resolveToolCallId(ModelToolCall modelToolCall) {
            return modelToolCall.getToolCallId() == null ? "generated-tool-call-id" : modelToolCall.getToolCallId();
        }

        @Override
        public ToolCallMessage createToolCallMessage(
            String sessionId,
            String turnId,
            String toolCallId,
            String toolName,
            String argumentsJson
        ) {
            return ToolCallMessage.create("msg-tool-call-" + (++messageCounter), turnId, ++sequence, toolCallId, toolName, argumentsJson);
        }

        @Override
        public ToolCallRecord createToolCallRecord(
            String conversationId,
            String sessionId,
            String turnId,
            ToolCallMessage message,
            int sequenceNo
        ) {
            return ToolCallRecord.builder()
                .toolCallId(message.getToolCallId())
                .conversationId(conversationId)
                .sessionId(sessionId)
                .turnId(turnId)
                .messageId(message.getMessageId())
                .toolName(message.getToolName())
                .argumentsJson(message.getArgumentsJson())
                .sequenceNo(sequenceNo)
                .status("REQUESTED")
                .createdAt(Instant.now())
                .build();
        }

        @Override
        public ToolCall toToolCall(String turnId, String toolCallId, ModelToolCall modelToolCall) {
            return ToolCall.builder()
                .toolCallId(toolCallId)
                .toolName(modelToolCall.getToolName())
                .argumentsJson(modelToolCall.getArgumentsJson())
                .turnId(turnId)
                .build();
        }

        @Override
        public ToolResultMessage createToolResultMessage(String sessionId, String turnId, ToolResult toolResult) {
            return ToolResultMessage.create(
                "msg-tool-result-" + (++messageCounter),
                turnId,
                ++sequence,
                toolResult.getToolCallId(),
                toolResult.getToolName(),
                toolResult.isSuccess(),
                toolResult.getOutput(),
                toolResult.getErrorCode(),
                toolResult.getErrorMessage(),
                toolResult.getDurationMs()
            );
        }

        @Override
        public ToolResultRecord createToolResultRecord(
            String conversationId,
            String sessionId,
            String turnId,
            ToolResultMessage message
        ) {
            return ToolResultRecord.builder()
                .toolCallId(message.getToolCallId())
                .conversationId(conversationId)
                .sessionId(sessionId)
                .turnId(turnId)
                .messageId(message.getMessageId())
                .toolName(message.getToolName())
                .success(message.isSuccess())
                .outputJson(message.getContent())
                .errorCode(message.getErrorCode())
                .errorMessage(message.getErrorMessage())
                .durationMs(message.getDurationMs())
                .createdAt(Instant.now())
                .build();
        }
    }
}

