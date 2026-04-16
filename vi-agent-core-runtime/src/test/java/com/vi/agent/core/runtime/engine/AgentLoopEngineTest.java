package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunState;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.port.LlmGateway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class AgentLoopEngineTest {

    @Test
    void runShouldDelegateToLlmGateway() {
        LlmGateway gateway = runContext -> new AssistantMessage("from-llm");
        DefaultAgentLoopEngine engine = new DefaultAgentLoopEngine(gateway);

        AgentRunContext context = buildContext();
        context.setIteration(1);

        AssistantMessage result = engine.run(context);
        Assertions.assertEquals("from-llm", result.getContent());
    }

    @Test
    void runStreamingShouldDelegateToStreamingGateway() {
        LlmGateway gateway = new LlmGateway() {
            @Override
            public AssistantMessage generate(AgentRunContext runContext) {
                return new AssistantMessage("fallback");
            }

            @Override
            public AssistantMessage generateStreaming(AgentRunContext runContext, java.util.function.Consumer<String> chunkConsumer) {
                chunkConsumer.accept("a");
                chunkConsumer.accept("b");
                return new AssistantMessage("ab");
            }
        };

        DefaultAgentLoopEngine engine = new DefaultAgentLoopEngine(gateway);
        AgentRunContext context = buildContext();
        context.setIteration(1);

        List<String> chunks = new ArrayList<>();
        AssistantMessage result = engine.runStreaming(context, chunks::add);

        Assertions.assertEquals(List.of("a", "b"), chunks);
        Assertions.assertEquals("ab", result.getContent());
    }

    private AgentRunContext buildContext() {
        ConversationTranscript transcript = new ConversationTranscript("session-1", "conv-1");
        UserMessage userMessage = new UserMessage("msg-1", "hello");
        transcript.appendMessage(userMessage);

        return new AgentRunContext(
            "trace-1",
            "run-1",
            "session-1",
            "conv-1",
            "turn-1",
            "hello",
            List.of(userMessage),
            List.of(),
            transcript,
            RunState.STARTED
        );
    }
}
