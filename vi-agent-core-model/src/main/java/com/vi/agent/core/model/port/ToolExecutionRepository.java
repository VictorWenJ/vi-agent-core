package com.vi.agent.core.model.port;

import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;

/**
 * Tool execution record repository port.
 */
public interface ToolExecutionRepository {

    void saveToolCall(ToolCallRecord toolCallRecord);

    void saveToolResult(ToolResultRecord toolResultRecord);
}
