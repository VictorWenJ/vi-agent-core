package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ToolGatewayTest {

    @Test
    void routeShouldReturnToolResultWhenToolExists() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
            ToolDefinition.builder().name("echo").description("echo").parametersJson("{}").build(),
            toolCall -> ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName("echo")
                .turnId(toolCall.getTurnId())
                .success(true)
                .output(toolCall.getArgumentsJson())
                .errorMessage("")
                .build()
        );

        DefaultToolGateway gateway = new DefaultToolGateway(registry);
        ToolResult result = gateway.route(ToolCall.builder()
            .toolCallId("tc-1")
            .toolName("echo")
            .argumentsJson("{\"msg\":\"hi\"}")
            .turnId("turn-1")
            .build());

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("echo", result.getToolName());
    }

    @Test
    void routeShouldThrowWhenToolMissing() {
        DefaultToolGateway gateway = new DefaultToolGateway(new ToolRegistry());
        Assertions.assertThrows(AgentRuntimeException.class,
            () -> gateway.route(ToolCall.builder()
                .toolCallId("tc-2")
                .toolName("missing")
                .argumentsJson("{}")
                .turnId("turn-1")
                .build()));
    }

    @Test
    void listDefinitionsShouldExposeRegisteredTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
            ToolDefinition.builder().name("tool-a").description("a").parametersJson("{}").build(),
            toolCall -> ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(true)
                .output("ok")
                .errorMessage("")
                .build()
        );

        DefaultToolGateway gateway = new DefaultToolGateway(registry);
        List<ToolDefinition> definitions = gateway.listDefinitions();

        Assertions.assertEquals(1, definitions.size());
        Assertions.assertEquals("tool-a", definitions.get(0).getName());
    }
}
