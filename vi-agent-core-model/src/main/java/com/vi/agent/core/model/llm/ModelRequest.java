package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
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

    /** 本次内部结构化输出使用的业务契约；普通聊天请求为空。 */
    private final StructuredLlmOutputContract structuredOutputContract;

    /** 本次内部结构化输出偏好的 provider 承载模式；为空时由 provider 请求前选择。 */
    private final StructuredLlmOutputMode preferredStructuredOutputMode;

    /** 本次内部结构化输出使用的 provider function name；为空时按 contract key 派生。 */
    private final String structuredOutputFunctionName;
}
