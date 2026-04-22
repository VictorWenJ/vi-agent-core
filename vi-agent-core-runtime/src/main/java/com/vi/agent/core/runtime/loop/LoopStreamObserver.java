package com.vi.agent.core.runtime.loop;

import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.runtime.engine.AssistantStreamListener;

/**
 * loop 流式观察器。
 */
public interface LoopStreamObserver extends AssistantStreamListener {

    void onToolCall(AssistantToolCall toolCall);

    void onToolResult(ToolExecution toolExecution);
}
