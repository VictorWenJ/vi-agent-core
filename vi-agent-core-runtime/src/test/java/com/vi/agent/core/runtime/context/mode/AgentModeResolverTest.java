package com.vi.agent.core.runtime.context.mode;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentModeResolverTest {

    private final AgentModeResolver resolver = new AgentModeResolver();

    @Test
    void missingAgentModeShouldResolveGeneral() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder().metadata(Map.of()).build();

        assertEquals(AgentMode.GENERAL, resolver.resolve(command));
    }

    @Test
    void generalAgentModeShouldResolveGeneral() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .metadata(Map.of("agentMode", "GENERAL"))
            .build();

        assertEquals(AgentMode.GENERAL, resolver.resolve(command));
    }

    @Test
    void invalidAgentModeShouldDowngradeToGeneral() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .metadata(Map.of("agentMode", "bad-mode"))
            .build();

        assertEquals(AgentMode.GENERAL, resolver.resolve(command));
    }
}
