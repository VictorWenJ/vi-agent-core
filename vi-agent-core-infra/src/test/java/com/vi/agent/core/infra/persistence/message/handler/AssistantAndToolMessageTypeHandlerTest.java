package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.common.id.ToolExecutionIdGenerator;
import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistantAndToolMessageTypeHandlerTest {

    @Test
    void assistantHandlerShouldRoundTripToolCalls() {
        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();

        AssistantMessage input = AssistantMessage.create(
            "msg-assistant-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "thinking",
            List.of(toolCall),
            FinishReason.TOOL_CALL,
            UsageInfo.empty()
        );

        AssistantMessageTypeHandler handler = new AssistantMessageTypeHandler();
        MessageWritePlan plan = handler.decompose(input);
        AssistantMessage restored = handler.assemble(MessageAggregateRows.builder()
            .message(plan.getMessage())
            .toolCalls(plan.getToolCalls())
            .build());

        assertEquals(1, restored.getToolCalls().size());
        assertEquals("tcr-1", restored.getToolCalls().get(0).getToolCallRecordId());
        assertEquals("call-1", restored.getToolCalls().get(0).getToolCallId());
    }

    @Test
    void toolHandlerShouldRoundTripExecutionFields() {
        ToolMessage input = ToolMessage.create(
            "msg-tool-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            3L,
            "2026-04-23T00:00:00+08:00",
            "tcr-1",
            "call-1",
            "get_time",
            ToolExecutionStatus.SUCCEEDED,
            null,
            null,
            8L,
            "{}"
        );

        ToolMessageTypeHandler handler = new ToolMessageTypeHandler(new FixedToolExecutionIdGenerator());
        MessageWritePlan plan = handler.decompose(input);
        ToolMessage restored = handler.assemble(MessageAggregateRows.builder()
            .message(plan.getMessage())
            .toolExecution(plan.getToolExecution())
            .build());

        assertEquals("tex-fixed", plan.getToolExecution().getToolExecutionId());
        assertEquals("tcr-1", restored.getToolCallRecordId());
        assertEquals("call-1", restored.getToolCallId());
        assertEquals(ToolExecutionStatus.SUCCEEDED, restored.getExecutionStatus());
    }

    private static final class FixedToolExecutionIdGenerator extends ToolExecutionIdGenerator {

        @Override
        public String nextId() {
            return "tex-fixed";
        }
    }
}
