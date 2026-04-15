package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunState;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.port.LlmGateway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AgentLoopEngineTest {

    @Test
    void runShouldDelegateToLlmGateway() {
        LlmGateway gateway = runContext -> new AssistantMessage("from-llm");
        DefaultAgentLoopEngine engine = new DefaultAgentLoopEngine(gateway);

        AgentRunContext context = new AgentRunContext(
            "trace-1",
            "run-1",
            "session-1",
            "hello",
            List.of(),
            new ConversationTranscript("session-1"),
            RunState.STARTED
        );

        AssistantMessage result = engine.run(context);
        Assertions.assertEquals("from-llm", result.getContent());
    }
}
