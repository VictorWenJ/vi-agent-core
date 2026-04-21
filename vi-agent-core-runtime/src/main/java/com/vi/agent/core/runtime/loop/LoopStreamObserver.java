package com.vi.agent.core.runtime.loop;

import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.runtime.engine.AssistantStreamListener;

/**
 * loop 流式观察器。
 */
public interface LoopStreamObserver extends AssistantStreamListener {

    void onToolCall(ToolCallRecord toolCallRecord);

    void onToolResult(ToolResultRecord toolResultRecord);
}

