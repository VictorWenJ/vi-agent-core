package com.vi.agent.core.infra.provider.openai;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsFunction;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsMessage;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.message.UserMessage;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible 消息投影器。
 */
@Component
public class OpenAICompatibleMessageProjector {

    public List<ChatCompletionsMessage> project(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }

        List<ChatCompletionsMessage> projected = new ArrayList<>();
        Map<String, AssistantToolCall> pendingToolCalls = new LinkedHashMap<>();

        for (Message message : messages) {
            if (message == null) {
                continue;
            }

            if (message instanceof UserMessage userMessage) {
                assertNoPendingToolCallsBeforeMessage(pendingToolCalls, message);
                projected.add(toUserMessage(userMessage));
                continue;
            }

            if (message instanceof SystemMessage systemMessage) {
                assertNoPendingToolCallsBeforeMessage(pendingToolCalls, message);
                projected.add(toSystemMessage(systemMessage));
                continue;
            }

            if (message instanceof SummaryMessage summaryMessage) {
                assertNoPendingToolCallsBeforeMessage(pendingToolCalls, message);
                projected.add(toSummaryMessage(summaryMessage));
                continue;
            }

            if (message instanceof AssistantMessage assistantMessage) {
                assertNoPendingToolCallsBeforeMessage(pendingToolCalls, message);
                projected.add(toAssistantMessage(assistantMessage));
                if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                    for (AssistantToolCall toolCall : assistantMessage.getToolCalls()) {
                        if (toolCall == null) {
                            continue;
                        }
                        pendingToolCalls.put(toolCall.getToolCallRecordId(), toolCall);
                    }
                }
                continue;
            }

            if (message instanceof ToolMessage toolMessage) {
                assertToolMessageMatchPendingCalls(pendingToolCalls, toolMessage);
                projected.add(toToolMessage(toolMessage));
                pendingToolCalls.remove(toolMessage.getToolCallRecordId());
                continue;
            }

            throw invalidModelContext("Unsupported message type: " + message.getClass().getName());
        }

        if (!pendingToolCalls.isEmpty()) {
            throw invalidModelContext("Missing tool messages for pending assistant tool_calls");
        }

        return projected;
    }

    private ChatCompletionsMessage toUserMessage(UserMessage userMessage) {
        ChatCompletionsMessage apiMessage = new ChatCompletionsMessage();
        apiMessage.setRole(userMessage.getRole().getValue());
        apiMessage.setContent(userMessage.getContentText());
        return apiMessage;
    }

    private ChatCompletionsMessage toSystemMessage(SystemMessage systemMessage) {
        ChatCompletionsMessage apiMessage = new ChatCompletionsMessage();
        apiMessage.setRole(systemMessage.getRole().getValue());
        apiMessage.setContent(systemMessage.getContentText());
        return apiMessage;
    }

    private ChatCompletionsMessage toSummaryMessage(SummaryMessage summaryMessage) {
        ChatCompletionsMessage apiMessage = new ChatCompletionsMessage();
        apiMessage.setRole("system");
        apiMessage.setContent(summaryMessage.getContentText());
        return apiMessage;
    }

    private ChatCompletionsMessage toAssistantMessage(AssistantMessage assistantMessage) {
        ChatCompletionsMessage apiMessage = new ChatCompletionsMessage();
        apiMessage.setRole(assistantMessage.getRole().getValue());
        apiMessage.setContent(assistantMessage.getContentText());
        if (CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            return apiMessage;
        }
        List<ChatCompletionsToolCall> toolCalls = new ArrayList<>();
        for (AssistantToolCall toolCall : assistantMessage.getToolCalls()) {
            if (toolCall == null) {
                continue;
            }
            if (StringUtils.isAnyBlank(toolCall.getToolCallRecordId(), toolCall.getToolCallId(), toolCall.getToolName())) {
                throw invalidModelContext("assistant tool call fields are incomplete");
            }
            ChatCompletionsToolCall apiToolCall = new ChatCompletionsToolCall();
            apiToolCall.setId(toolCall.getToolCallId());
            apiToolCall.setType("function");
            ChatCompletionsFunction function = new ChatCompletionsFunction();
            function.setName(toolCall.getToolName());
            function.setArguments(StringUtils.defaultIfBlank(toolCall.getArgumentsJson(), "{}"));
            apiToolCall.setFunction(function);
            toolCalls.add(apiToolCall);
        }
        apiMessage.setToolCalls(toolCalls);
        return apiMessage;
    }

    private ChatCompletionsMessage toToolMessage(ToolMessage toolMessage) {
        ChatCompletionsMessage apiMessage = new ChatCompletionsMessage();
        apiMessage.setRole(toolMessage.getRole().getValue());
        apiMessage.setContent(toolMessage.getContentText());
        apiMessage.setToolCallId(toolMessage.getToolCallId());
        apiMessage.setName(toolMessage.getToolName());
        return apiMessage;
    }

    private void assertNoPendingToolCallsBeforeMessage(Map<String, AssistantToolCall> pendingToolCalls, Message currentMessage) {
        if (!pendingToolCalls.isEmpty()) {
            throw invalidModelContext(
                "An assistant message with tool_calls must be followed by matching tool messages before "
                    + currentMessage.getClass().getSimpleName()
            );
        }
    }

    private void assertToolMessageMatchPendingCalls(Map<String, AssistantToolCall> pendingToolCalls, ToolMessage toolMessage) {
        if (pendingToolCalls.isEmpty()) {
            throw invalidModelContext("Tool message appears without preceding assistant tool_calls");
        }
        if (StringUtils.isAnyBlank(toolMessage.getToolCallRecordId(), toolMessage.getToolCallId())) {
            throw invalidModelContext("Tool message toolCallRecordId/toolCallId is blank");
        }
        AssistantToolCall pendingCall = pendingToolCalls.get(toolMessage.getToolCallRecordId());
        if (pendingCall == null) {
            throw invalidModelContext("Tool message toolCallRecordId does not match pending assistant tool_calls");
        }
        if (!StringUtils.equals(pendingCall.getToolCallId(), toolMessage.getToolCallId())) {
            throw invalidModelContext("Tool message toolCallId does not match pending assistant tool_calls");
        }
    }

    private AgentRuntimeException invalidModelContext(String message) {
        return new AgentRuntimeException(ErrorCode.INVALID_MODEL_CONTEXT_MESSAGE, "Invalid model context message chain: " + message);
    }
}
