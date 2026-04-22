package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.loop.LoopStreamObserver;
import org.springframework.stereotype.Component;

/**
 * LoopStreamObserver 工厂。
 */
@Component
public class LoopStreamObserverFactory {

    public LoopStreamObserver create(RuntimeEventSink eventSink) {
        return new LoopStreamObserver() {
            @Override
            public void onMessageStarted(String assistantMessageId) {
                eventSink.messageStarted(assistantMessageId);
            }

            @Override
            public void onMessageDelta(String assistantMessageId, String delta) {
                eventSink.messageDelta(assistantMessageId, delta);
            }

            @Override
            public void onMessageCompleted(AssistantMessage assistantMessage, FinishReason finishReason) {
                eventSink.messageCompleted(assistantMessage, finishReason, null);
            }

            @Override
            public void onToolCall(AssistantToolCall toolCall) {
                eventSink.toolCall(toolCall);
            }

            @Override
            public void onToolResult(ToolExecution toolExecution) {
                eventSink.toolResult(toolExecution);
            }
        };
    }
}
