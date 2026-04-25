package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.Message;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P2-D-3 内联会话摘要抽取 prompt 构建器。
 */
@Component
public class ConversationSummaryExtractionPromptBuilder {

    /** SUMMARY_EXTRACT 内联 prompt key。 */
    public static final String PROMPT_TEMPLATE_KEY = "summary_extract_inline";

    /** SUMMARY_EXTRACT 内联 prompt 版本。 */
    public static final String PROMPT_TEMPLATE_VERSION = "p2-d-3-v1";

    public String buildPrompt(ConversationSummaryExtractionCommand command) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an internal summary update task for vi-agent-core.\n");
        prompt.append("Do not generate a user-visible reply. Do not modify transcript. Do not generate StateDelta. Do not generate evidence. Do not generate debug data.\n");
        prompt.append("Return only strict JSON. Allowed fields are only: summaryText, skipped, reason.\n");
        prompt.append("If there is no meaningful durable summary update, output {\"skipped\":true,\"reason\":\"no meaningful update\"}.\n");
        prompt.append("Do not output system fields: summaryId, sessionId, conversationId, summaryVersion, coveredFromSequenceNo, coveredToSequenceNo, summaryTemplateKey, summaryTemplateVersion, generatorProvider, generatorModel, createdAt.\n");
        prompt.append("Do not output memory, messages, stateDelta, evidence, debug, upsert, remove, or any field outside the allowed schema.\n\n");
        appendRunMetadata(prompt, command);
        appendLatestSummary(prompt, command == null ? null : command.getLatestSummary());
        appendLatestState(prompt, command == null ? null : command.getLatestState());
        appendTurnMessages(prompt, command == null ? null : command.getTurnMessages());
        return prompt.toString();
    }

    private void appendRunMetadata(StringBuilder prompt, ConversationSummaryExtractionCommand command) {
        prompt.append("Metadata:\n");
        prompt.append("- conversationId: ").append(command == null ? null : command.getConversationId()).append('\n');
        prompt.append("- sessionId: ").append(command == null ? null : command.getSessionId()).append('\n');
        prompt.append("- turnId: ").append(command == null ? null : command.getTurnId()).append('\n');
        prompt.append("- runId: ").append(command == null ? null : command.getRunId()).append('\n');
        prompt.append("- traceId: ").append(command == null ? null : command.getTraceId()).append('\n');
        prompt.append("- agentMode: ").append(command == null || command.getAgentMode() == null ? null : command.getAgentMode().name()).append('\n');
        prompt.append("- workingContextSnapshotId: ").append(command == null ? null : command.getWorkingContextSnapshotId()).append("\n\n");
    }

    private void appendLatestSummary(StringBuilder prompt, ConversationSummary latestSummary) {
        prompt.append("Previous conversation summary:\n");
        if (latestSummary == null) {
            prompt.append("(none)\n\n");
            return;
        }
        prompt.append("- summaryVersion: ").append(latestSummary.getSummaryVersion()).append('\n');
        prompt.append("- coveredFromSequenceNo: ").append(latestSummary.getCoveredFromSequenceNo()).append('\n');
        prompt.append("- coveredToSequenceNo: ").append(latestSummary.getCoveredToSequenceNo()).append('\n');
        prompt.append("- summaryText: ").append(latestSummary.getSummaryText()).append("\n\n");
    }

    private void appendLatestState(StringBuilder prompt, SessionStateSnapshot latestState) {
        prompt.append("Latest session state reference:\n");
        prompt.append("- state-version: ").append(latestState == null ? null : latestState.getStateVersion()).append('\n');
        prompt.append(latestState == null ? "{}" : JsonUtils.toJson(latestState)).append("\n\n");
    }

    private void appendTurnMessages(StringBuilder prompt, List<Message> turnMessages) {
        prompt.append("Completed raw turn transcript:\n");
        if (CollectionUtils.isEmpty(turnMessages)) {
            prompt.append("(none)\n");
            return;
        }
        for (Message message : turnMessages) {
            if (message == null) {
                continue;
            }
            prompt.append("- messageId: ").append(message.getMessageId())
                .append(", role: ").append(message.getRole())
                .append(", sequenceNo: ").append(message.getSequenceNo())
                .append(", content: ").append(message.getContentText())
                .append('\n');
        }
    }
}
