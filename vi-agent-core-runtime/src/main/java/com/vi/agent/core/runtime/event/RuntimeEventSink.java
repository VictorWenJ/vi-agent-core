package com.vi.agent.core.runtime.event;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import org.springframework.util.ObjectUtils;

import java.util.function.Consumer;

/**
 * Runtime 事件发送门面，封装空消费者判断。
 */
public class RuntimeEventSink {

    private final RuntimeExecutionContext context;

    private final RuntimeEventFactory runtimeEventFactory;

    private final Consumer<RuntimeEvent> eventConsumer;

    public RuntimeEventSink(RuntimeExecutionContext context, RuntimeEventFactory runtimeEventFactory, Consumer<RuntimeEvent> eventConsumer) {
        this.context = context;
        this.runtimeEventFactory = runtimeEventFactory;
        this.eventConsumer = eventConsumer;
    }

    public void runStarted() {
        emit(runtimeEventFactory.runStarted(context));
    }

    public void messageStarted(String messageId) {
        emit(runtimeEventFactory.messageStarted(context, messageId));
    }

    public void messageDelta(String messageId, String delta) {
        emit(runtimeEventFactory.messageDelta(context, messageId, delta));
    }

    public void messageCompleted(AssistantMessage assistantMessage, FinishReason finishReason, UsageInfo usage) {
        emit(runtimeEventFactory.messageCompleted(context, assistantMessage, finishReason, usage));
    }

    public void toolCall(AssistantToolCall toolCall) {
        emit(runtimeEventFactory.toolCall(context, toolCall));
    }

    public void toolResult(ToolExecution toolExecution) {
        emit(runtimeEventFactory.toolResult(context, toolExecution));
    }

    public void runCompleted(LoopExecutionResult loopExecutionResult) {
        emit(runtimeEventFactory.runCompleted(context, loopExecutionResult));
    }

    public void runFailed(String errorCode, String errorMessage, String errorType, boolean retryable) {
        emit(runtimeEventFactory.runFailed(context, errorCode, errorMessage, errorType, retryable));
    }

    private void emit(RuntimeEvent event) {
        if (ObjectUtils.isEmpty(eventConsumer) || event == null) {
            return;
        }
        eventConsumer.accept(event);
    }
}
