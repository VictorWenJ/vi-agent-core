package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContextMessageFilterTest {

    @Test
    void filterShouldExcludeToolCallMessageOnly() {
        ModelContextMessageFilter filter = new ModelContextMessageFilter();

        UserMessage userMessage = UserMessage.create("msg-user", "turn-1", 1L, "现在是什么时间");
        AssistantMessage assistantWithToolCall = AssistantMessage.create(
            "msg-assistant-tool",
            "turn-1",
            2L,
            "我来查询时间",
            List.of(ModelToolCall.builder()
                .toolCallId("call-1")
                .toolName("get_time")
                .argumentsJson("{}")
                .build())
        );
        ToolCallMessage toolCallMessage = ToolCallMessage.create(
            "msg-tool-call",
            "turn-1",
            3L,
            "call-1",
            "get_time",
            "{}"
        );
        ToolResultMessage toolResultMessage = ToolResultMessage.create(
            "msg-tool-result",
            "turn-1",
            4L,
            "call-1",
            "get_time",
            true,
            "2026-04-21T16:00:00+08:00",
            null,
            null,
            1L
        );
        AssistantMessage finalAssistant = AssistantMessage.create(
            "msg-assistant-final",
            "turn-1",
            5L,
            "现在是下午 4 点",
            List.of()
        );

        List<Message> filtered = filter.filter(List.of(
            userMessage,
            assistantWithToolCall,
            toolCallMessage,
            toolResultMessage,
            finalAssistant
        ));

        assertEquals(4, filtered.size());
        assertEquals("msg-user", filtered.get(0).getMessageId());
        assertEquals("msg-assistant-tool", filtered.get(1).getMessageId());
        assertEquals("msg-tool-result", filtered.get(2).getMessageId());
        assertEquals("msg-assistant-final", filtered.get(3).getMessageId());
        assertTrue(filtered.stream().noneMatch(message -> message instanceof ToolCallMessage));
    }
}

