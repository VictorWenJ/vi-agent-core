package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.Message;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the inline P2-D-2 state extraction prompt.
 */
@Component
public class StateDeltaExtractionPromptBuilder {

    public static final String PROMPT_TEMPLATE_KEY = "state_extract_inline";

    public static final String PROMPT_TEMPLATE_VERSION = "p2-d-2-v1";

    public String buildPrompt(StateDeltaExtractionCommand command) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an internal state extraction task for vi-agent-core.\n");
        prompt.append("Only output StateDelta JSON. Do not include markdown, prose, summary, evidence, or user-visible reply.\n");
        prompt.append("Allowed top-level fields: taskGoalOverride, confirmedFactsAppend, constraintsAppend, ");
        prompt.append("userPreferencesPatch, decisionsAppend, openLoopsAppend, openLoopIdsToClose, ");
        prompt.append("recentToolOutcomesAppend, workingModeOverride, phaseStatePatch, sourceCandidateIds.\n");
        prompt.append("Do not output upsert. Do not output remove. Do not output patches, operations, memory, or messages.\n");
        prompt.append("If there is no durable state change, output {\"sourceCandidateIds\":[]}.\n\n");
        appendRunMetadata(prompt, command);
        appendCurrentState(prompt, command == null ? null : command.getCurrentState());
        appendConversationSummary(prompt, command == null ? null : command.getConversationSummary());
        appendTurnMessages(prompt, command == null ? null : command.getTurnMessages());
        return prompt.toString();
    }

    private void appendRunMetadata(StringBuilder prompt, StateDeltaExtractionCommand command) {
        prompt.append("Metadata:\n");
        prompt.append("- conversationId: ").append(command == null ? null : command.getConversationId()).append('\n');
        prompt.append("- sessionId: ").append(command == null ? null : command.getSessionId()).append('\n');
        prompt.append("- turnId: ").append(command == null ? null : command.getTurnId()).append('\n');
        prompt.append("- runId: ").append(command == null ? null : command.getRunId()).append('\n');
        prompt.append("- traceId: ").append(command == null ? null : command.getTraceId()).append('\n');
        prompt.append("- agentMode: ").append(command == null || command.getAgentMode() == null ? null : command.getAgentMode().name()).append('\n');
        prompt.append("- workingContextSnapshotId: ").append(command == null ? null : command.getWorkingContextSnapshotId()).append('\n');
    }

    private void appendCurrentState(StringBuilder prompt, SessionStateSnapshot currentState) {
        prompt.append("- currentStateVersion: ").append(currentState == null ? null : currentState.getStateVersion()).append("\n\n");
        prompt.append("Current state JSON:\n");
        prompt.append(currentState == null ? "{}" : JsonUtils.toJson(currentState)).append("\n\n");
    }

    private void appendConversationSummary(StringBuilder prompt, ConversationSummary conversationSummary) {
        prompt.append("Latest conversation summary:\n");
        if (conversationSummary == null) {
            prompt.append("(none)\n\n");
            return;
        }
        prompt.append("- summaryVersion: ").append(conversationSummary.getSummaryVersion()).append('\n');
        prompt.append("- summaryText: ").append(conversationSummary.getSummaryText()).append("\n\n");
    }

    private void appendTurnMessages(StringBuilder prompt, List<Message> turnMessages) {
        prompt.append("Completed turn transcript messages:\n");
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
