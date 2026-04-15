package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ToolGatewayTest {

    @Test
    void routeShouldReturnToolResultWhenToolExists() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("echo", toolCall -> new ToolResult(toolCall.getToolCallId(), "echo", true, toolCall.getArgumentsJson()));

        DefaultToolGateway gateway = new DefaultToolGateway(registry);
        ToolResult result = gateway.route(new ToolCall("tc-1", "echo", "{\"msg\":\"hi\"}"));

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("echo", result.getToolName());
    }

    @Test
    void routeShouldThrowWhenToolMissing() {
        DefaultToolGateway gateway = new DefaultToolGateway(new ToolRegistry());
        Assertions.assertThrows(AgentRuntimeException.class,
            () -> gateway.route(new ToolCall("tc-2", "missing", "{}")));
    }
}
