package com.vi.agent.core.model.port;

import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;

import java.util.List;

/**
 * Tool execution record repository port.
 */
public interface ToolExecutionRepository {

    void saveToolCall(ToolCallRecord toolCallRecord);

    void saveToolResult(ToolResultRecord toolResultRecord);

    ToolCallRecord findToolCallByMessageId(String messageId);

    ToolResultRecord findToolResultByMessageId(String messageId);

    List<ToolCallRecord> findToolCallsByTurnId(String turnId);
}
