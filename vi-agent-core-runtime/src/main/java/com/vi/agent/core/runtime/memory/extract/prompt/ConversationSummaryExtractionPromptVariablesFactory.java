package com.vi.agent.core.runtime.memory.extract.prompt;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionCommand;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话摘要抽取 prompt 变量工厂。
 */
@Component
public class ConversationSummaryExtractionPromptVariablesFactory {

    /**
     * 将会话摘要抽取命令转换为 prompt renderer 变量。
     */
    public Map<String, String> variables(ConversationSummaryExtractionCommand command) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("conversationId", command == null ? "" : StringUtils.defaultString(command.getConversationId()));
        variables.put("sessionId", command == null ? "" : StringUtils.defaultString(command.getSessionId()));
        variables.put("turnId", command == null ? "" : StringUtils.defaultString(command.getTurnId()));
        variables.put("runId", command == null ? "" : StringUtils.defaultString(command.getRunId()));
        variables.put("traceId", command == null ? "" : StringUtils.defaultString(command.getTraceId()));
        variables.put("agentMode", agentModeValue(command == null ? null : command.getAgentMode()));
        variables.put("workingContextSnapshotId", command == null ? "" : StringUtils.defaultString(command.getWorkingContextSnapshotId()));
        variables.put("previousSummaryText", command == null || command.getLatestSummary() == null
            ? ""
            : StringUtils.defaultString(command.getLatestSummary().getSummaryText()));
        variables.put("latestStateJson", command == null || command.getLatestState() == null ? "{}" : JsonUtils.toJson(command.getLatestState()));
        variables.put("turnMessagesText", renderTurnMessages(command));
        return variables;
    }

    /**
     * 将当前回合 transcript 转为稳定文本。
     */
    private String renderTurnMessages(ConversationSummaryExtractionCommand command) {
        if (command == null || CollectionUtils.isEmpty(command.getTurnMessages())) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Message message : command.getTurnMessages()) {
            if (message == null) {
                continue;
            }
            builder.append("- messageId: ").append(StringUtils.defaultString(message.getMessageId()))
                .append(", role: ").append(message.getRole() == null ? "" : message.getRole().getValue())
                .append(", sequenceNo: ").append(message.getSequenceNo())
                .append(", content: ").append(StringUtils.defaultString(message.getContentText()))
                .append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * 读取 AgentMode 稳定 value。
     */
    private String agentModeValue(AgentMode agentMode) {
        return agentMode == null ? AgentMode.GENERAL.getValue() : agentMode.getValue();
    }
}
