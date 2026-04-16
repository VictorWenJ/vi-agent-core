package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.runtime.annotation.AgentTool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
 * 工具注册表。
 */
@Slf4j
public class ToolRegistry {

    /** 已注册工具执行器映射。 */
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    /** 已注册工具定义映射。 */
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * 注册工具。
     *
     * @param definition 工具定义
     * @param executor 工具执行器
     */
    public void register(ToolDefinition definition, ToolExecutor executor) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "工具定义为空或名称为空");
        }
        if (executor == null) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "工具执行器不能为空");
        }
        String toolName = definition.getName();
        if (executors.containsKey(toolName)) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "重复注册工具: " + toolName);
        }
        executors.put(toolName, executor);
        definitions.put(toolName, definition);
        log.info("ToolRegistry registered toolName={}", toolName);
    }

    /**
     * 基于注解扫描注册工具。
     *
     * @param toolBundle 工具集合对象
     */
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

    /**
     * 批量注册注解工具。
     *
     * @param toolBundles 工具集合对象列表
     */
    public void registerAnnotatedTools(Collection<?> toolBundles) {
        if (toolBundles == null) {
            return;
        }
        for (Object toolBundle : toolBundles) {
            registerAnnotatedTools(toolBundle);
        }
    }

    /**
     * 查询工具执行器。
     *
     * @param toolName 工具名称
     * @return 可选执行器
     */
    public Optional<ToolExecutor> find(String toolName) {
        return Optional.ofNullable(executors.get(toolName));
    }

    /**
     * 获取已注册工具定义列表。
     *
     * @return 工具定义列表
     */
    public List<ToolDefinition> listDefinitions() {
        List<ToolDefinition> list = new ArrayList<>(definitions.values());
        list.sort(Comparator.comparing(ToolDefinition::getName));
        return list;
    }

    private void validateToolMethod(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "@AgentTool 不允许标记 static 方法: " + method.getName());
        }
        if (method.getParameterCount() > 1) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "@AgentTool 方法参数最多 1 个: " + method.getName());
        }
    }

    private ToolResult invokeToolMethod(Object toolBundle, Method method, ToolCall toolCall) {
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
                        "@AgentTool 方法参数类型仅支持 String 或 ToolCall: " + method.getName()
                    );
                }
            }
            return ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(true)
                .output(value == null ? "" : String.valueOf(value))
                .errorMessage("")
                .build();
        } catch (Exception e) {
            log.error("ToolRegistry invoke tool failed toolName={} method={}", toolCall.getToolName(), method.getName(), e);
            return ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(toolCall.getTurnId())
                .success(false)
                .output("")
                .errorMessage(e.getMessage())
                .build();
        }
    }
}
