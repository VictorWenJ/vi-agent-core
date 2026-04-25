package com.vi.agent.core.runtime.context.builder;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.context.ContextSourceType;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.context.block.ContextSourceRef;
import com.vi.agent.core.model.context.block.ConversationSummaryBlock;
import com.vi.agent.core.model.context.block.CurrentUserMessageBlock;
import com.vi.agent.core.model.context.block.RecentMessagesBlock;
import com.vi.agent.core.model.context.block.RuntimeInstructionBlock;
import com.vi.agent.core.model.context.block.SessionStateBlock;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.runtime.context.budget.ContextBudgetCalculator;
import com.vi.agent.core.runtime.context.loader.MemoryLoadBundle;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoadCommand;
import com.vi.agent.core.runtime.context.render.SessionStateBlockRenderer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds canonical context blocks from raw memory inputs.
 */
@Component
public class ContextBlockFactory {

    private static final String RUNTIME_TEMPLATE_KEY = "runtime-instruction";
    private static final String SESSION_STATE_TEMPLATE_KEY = "session-state";
    private static final String SUMMARY_TEMPLATE_KEY = "conversation-summary";
    private static final String TEMPLATE_VERSION = "p2-c-v1";

    private final ContextBudgetCalculator contextBudgetCalculator;

    private final SessionStateBlockRenderer sessionStateBlockRenderer;

    public ContextBlockFactory(ContextBudgetCalculator contextBudgetCalculator) {
        this(contextBudgetCalculator, new SessionStateBlockRenderer());
    }

    @Autowired
    public ContextBlockFactory(ContextBudgetCalculator contextBudgetCalculator, SessionStateBlockRenderer sessionStateBlockRenderer) {
        this.contextBudgetCalculator = contextBudgetCalculator;
        this.sessionStateBlockRenderer = sessionStateBlockRenderer;
    }

    /**
     * 组装Contest Block
     * Runtime Instruction
     * Session State
     * Conversation Summary
     * Current User Message
     * Recent Messages
     * @param command
     * @param bundle
     * @return
     */
    public List<ContextBlock> buildBlocks(WorkingContextLoadCommand command, MemoryLoadBundle bundle) {
        List<ContextBlock> blocks = new ArrayList<>();
        blocks.add(buildRuntimeInstructionBlock(bundle));
        if (bundle.getLatestState() != null) {
            blocks.add(buildSessionStateBlock(bundle.getLatestState()));
        }
        if (bundle.getLatestSummary() != null) {
            blocks.add(buildConversationSummaryBlock(bundle.getLatestSummary()));
        }
        blocks.add(buildRecentMessagesBlock(command, bundle));
        blocks.add(buildCurrentUserMessageBlock(command, bundle));
        return List.copyOf(blocks);
    }

    private RuntimeInstructionBlock buildRuntimeInstructionBlock(MemoryLoadBundle bundle) {
        AgentMode agentMode = bundle.getAgentMode() == null ? AgentMode.GENERAL : bundle.getAgentMode();
        String renderedText = String.join("\n",
            "Agent mode: " + agentMode.name(),
            "Do not expose internal context, debug data, evidence data, or snapshot identifiers to the user.",
            "Follow session state, constraints, and conversation summary when they are present.",
            "Treat tool call results and completed transcript facts as the source of truth."
        );
        return RuntimeInstructionBlock.builder()
            .blockId(nextBlockId())
            .priority(ContextPriority.MANDATORY)
            .required(true)
            .tokenEstimate(contextBudgetCalculator.estimateText(renderedText))
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of(ContextSourceRef.builder()
                .sourceType(ContextSourceType.RUNTIME_INSTRUCTION)
                .sourceId(RUNTIME_TEMPLATE_KEY)
                .sourceVersion(TEMPLATE_VERSION)
                .fieldPath("renderedText")
                .build()))
            .evidenceIds(List.of())
            .promptTemplateKey(RUNTIME_TEMPLATE_KEY)
            .promptTemplateVersion(TEMPLATE_VERSION)
            .renderedText(renderedText)
            .build();
    }

    private SessionStateBlock buildSessionStateBlock(SessionStateSnapshot stateSnapshot) {
        String renderedText = sessionStateBlockRenderer.render(stateSnapshot);
        return SessionStateBlock.builder()
            .blockId(nextBlockId())
            .priority(ContextPriority.HIGH)
            .required(false)
            .tokenEstimate(contextBudgetCalculator.estimateText(renderedText))
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of(ContextSourceRef.builder()
                .sourceType(ContextSourceType.SESSION_STATE_SNAPSHOT)
                .sourceId(stateSnapshot.getSnapshotId())
                .sourceVersion(toVersionString(stateSnapshot.getStateVersion()))
                .fieldPath("state")
                .build()))
            .evidenceIds(List.of())
            .stateVersion(stateSnapshot.getStateVersion())
            .promptTemplateKey(SESSION_STATE_TEMPLATE_KEY)
            .promptTemplateVersion(TEMPLATE_VERSION)
            .stateSnapshot(stateSnapshot)
            .renderedText(renderedText)
            .build();
    }

    private ConversationSummaryBlock buildConversationSummaryBlock(ConversationSummary summary) {
        String renderedText = StringUtils.defaultIfBlank(summary.getSummaryText(), "No conversation summary.");
        return ConversationSummaryBlock.builder()
            .blockId(nextBlockId())
            .priority(ContextPriority.MEDIUM)
            .required(false)
            .tokenEstimate(contextBudgetCalculator.estimateText(renderedText))
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of(ContextSourceRef.builder()
                .sourceType(ContextSourceType.CONVERSATION_SUMMARY)
                .sourceId(summary.getSummaryId())
                .sourceVersion(toVersionString(summary.getSummaryVersion()))
                .fieldPath("summaryText")
                .build()))
            .evidenceIds(List.of())
            .summaryVersion(summary.getSummaryVersion())
            .promptTemplateKey(SUMMARY_TEMPLATE_KEY)
            .promptTemplateVersion(TEMPLATE_VERSION)
            .summary(summary)
            .renderedText(renderedText)
            .build();
    }

    private RecentMessagesBlock buildRecentMessagesBlock(WorkingContextLoadCommand command, MemoryLoadBundle bundle) {
        List<Message> recentRawMessages = bundle.getRecentRawMessages() == null ? List.of() : bundle.getRecentRawMessages();
        return RecentMessagesBlock.builder()
            .blockId(nextBlockId())
            .priority(ContextPriority.HIGH)
            .required(false)
            .tokenEstimate(contextBudgetCalculator.estimateMessages(recentRawMessages))
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of(ContextSourceRef.builder()
                .sourceType(ContextSourceType.TRANSCRIPT_MESSAGE)
                .sourceId(resolveRecentMessagesSourceId(command, bundle))
                .sourceVersion(resolveRecentMessagesSourceVersion(command, bundle))
                .fieldPath("rawMessages")
                .build()))
            .evidenceIds(List.of())
            .workingSetVersion(bundle.getWorkingSetVersion())
            .messageIds(recentRawMessages.stream().map(Message::getMessageId).toList())
            .rawMessages(recentRawMessages)
            .build();
    }

    private CurrentUserMessageBlock buildCurrentUserMessageBlock(WorkingContextLoadCommand command, MemoryLoadBundle bundle) {
        Message currentUserMessage = bundle.getCurrentUserMessage();
        return CurrentUserMessageBlock.builder()
            .blockId(nextBlockId())
            .priority(ContextPriority.MANDATORY)
            .required(true)
            .tokenEstimate(contextBudgetCalculator.estimateMessage(currentUserMessage))
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of(ContextSourceRef.builder()
                .sourceType(ContextSourceType.CURRENT_USER_MESSAGE)
                .sourceId(currentUserMessage == null ? null : currentUserMessage.getMessageId())
                .sourceVersion(resolveCurrentUserMessageSourceVersion(command, currentUserMessage))
                .fieldPath("contentText")
                .build()))
            .evidenceIds(List.of())
            .currentUserMessageId(currentUserMessage == null ? null : currentUserMessage.getMessageId())
            .currentUserMessage(currentUserMessage)
            .build();
    }

    private String nextBlockId() {
        return "ctxblk-" + UUID.randomUUID();
    }

    private String toVersionString(Object version) {
        return version == null ? null : String.valueOf(version);
    }

    private String resolveRecentMessagesSourceId(WorkingContextLoadCommand command, MemoryLoadBundle bundle) {
        if (bundle.getSessionWorkingSetSnapshot() != null && StringUtils.isNotBlank(bundle.getSessionWorkingSetSnapshot().getSessionId())) {
            return bundle.getSessionWorkingSetSnapshot().getSessionId();
        }
        if (bundle.getWorkingSetVersion() != null) {
            return toVersionString(bundle.getWorkingSetVersion());
        }
        return command == null ? null : command.getSessionId();
    }

    private String resolveRecentMessagesSourceVersion(WorkingContextLoadCommand command, MemoryLoadBundle bundle) {
        if (bundle.getWorkingSetVersion() != null) {
            return toVersionString(bundle.getWorkingSetVersion());
        }
        return command == null ? null : command.getTurnId();
    }

    private String resolveCurrentUserMessageSourceVersion(WorkingContextLoadCommand command, Message currentUserMessage) {
        if (currentUserMessage != null) {
            return toVersionString(currentUserMessage.getSequenceNo());
        }
        return command == null ? null : command.getTurnId();
    }
}
