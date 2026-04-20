package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.message.AssistantMessage;

/**
 * Callback for assistant message streaming lifecycle events.
 */
public interface AssistantStreamListener {

    void onMessageStarted(String assistantMessageId);

    void onMessageDelta(String assistantMessageId, String delta);

    void onMessageCompleted(AssistantMessage assistantMessage, FinishReason finishReason);
}

