package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.annotation.AgentTool;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool registry.
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    public void register(ToolDefinition definition, ToolExecutor executor) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "tool definition is invalid");
        }
        if (executor == null) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "tool executor is null");
        }
        String toolName = definition.getName();
        if (executors.containsKey(toolName)) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "duplicate tool: " + toolName);
        }
        executors.put(toolName, executor);
        definitions.put(toolName, definition);
    }

    public void registerAnnotatedTools(Object toolBundle) {
        if (toolBundle == null) {
            return;
        }
        for (Method method : toolBundle.getClass().getMethods()) {
            AgentTool agentTool = method.getAnnotation(AgentTool.class);
            if (agentTool == null) {
                continue;
            }
            validateToolMethod(method);
            ToolDefinition definition = ToolDefinition.builder()
                .name(agentTool.name())
                .description(agentTool.description())
                .parametersJson(agentTool.parametersJson())
                .build();
            register(definition, toolCall -> invokeToolMethod(toolBundle, method, toolCall));
        }
    }

    public void registerAnnotatedTools(Collection<?> toolBundles) {
        if (toolBundles == null) {
            return;
        }
        for (Object toolBundle : toolBundles) {
            registerAnnotatedTools(toolBundle);
        }
    }

    public Optional<ToolExecutor> find(String toolName) {
        return Optional.ofNullable(executors.get(toolName));
    }

    public List<ToolDefinition> listDefinitions() {
        List<ToolDefinition> list = new ArrayList<>(definitions.values());
        list.sort(Comparator.comparing(ToolDefinition::getName));
        return list;
    }

    private void validateToolMethod(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "@AgentTool cannot mark static method");
        }
        if (method.getParameterCount() > 1) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "@AgentTool supports at most one parameter");
        }
    }

    private ToolResult invokeToolMethod(Object toolBundle, Method method, ToolCall toolCall) {
        long start = System.currentTimeMillis();
        try {
            method.setAccessible(true);
            Object value;
            if (method.getParameterCount() == 0) {
                value = method.invoke(toolBundle);
            } else {
                Class<?> parameterType = method.getParameterTypes()[0];
                if (String.class.equals(parameterType)) {
                    value = method.invoke(toolBundle, toolCall.getArgumentsJson());
                } else if (ToolCall.class.equals(parameterType)) {
                    value = method.invoke(toolBundle, toolCall);
                } else {
                    throw new AgentRuntimeException(
                        ErrorCode.INVALID_ARGUMENT,
                        "@AgentTool parameter must be String or ToolCall"
                    );
                }
            }
            return ToolResult.builder()
                .toolCallRecordId(toolCall.getToolCallRecordId())
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(true)
                .output(value == null ? "" : String.valueOf(value))
                .durationMs(System.currentTimeMillis() - start)
                .build();
        } catch (Exception e) {
            return ToolResult.builder()
                .toolCallRecordId(toolCall.getToolCallRecordId())
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(false)
                .output("")
                .errorCode(ErrorCode.TOOL_EXECUTION_FAILED.getCode())
                .errorMessage(e.getMessage())
                .durationMs(System.currentTimeMillis() - start)
                .build();
        }
    }
}
