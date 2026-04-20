package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolDefinition;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Provider neutral model request.
 */
@Getter
@Builder
public class ModelRequest {

    private final String runId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final List<Message> messages;

    private final List<ToolDefinition> tools;
}
