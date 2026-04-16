package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.runtime.annotation.AgentTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ToolRegistryAnnotationTest {

    @Test
    void registerAnnotatedToolsShouldExposeDefinitionAndExecutable() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerAnnotatedTools(java.util.List.of(new DemoTools()));

        Assertions.assertEquals(1, registry.listDefinitions().size());
        Assertions.assertTrue(registry.find("demo_echo").isPresent());

        ToolResult result = registry.find("demo_echo").orElseThrow().execute(
            ToolCall.builder()
                .toolCallId("tc-1")
                .toolName("demo_echo")
                .argumentsJson("abc")
                .turnId("turn-1")
                .build()
        );

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("abc", result.getOutput());
    }

    private static class DemoTools implements ToolBundle {

        @AgentTool(name = "demo_echo", description = "demo")
        public String echo(String argumentsJson) {
            return argumentsJson;
        }
    }
}
