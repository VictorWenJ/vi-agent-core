package com.vi.agent.core.runtime.context.builder;

import com.vi.agent.core.common.id.ContextBlockIdGenerator;
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
import com.vi.agent.core.runtime.context.prompt.ContextBlockPromptVariablesFactory;
import com.vi.agent.core.runtime.context.render.SessionStateBlockRenderer;
import com.vi.agent.core.runtime.prompt.PromptRenderRequest;
import com.vi.agent.core.runtime.prompt.PromptRenderResult;
import com.vi.agent.core.runtime.prompt.PromptRenderer;
import com.vi.agent.core.runtime.prompt.TextPromptRenderResult;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds canonical context blocks from raw memory inputs.
 */
@Component
public class ContextBlockFactory {

    private final ContextBudgetCalculator contextBudgetCalculator;

    private final SessionStateBlockRenderer sessionStateBlockRenderer;

    private final ContextBlockIdGenerator contextBlockIdGenerator;

    /** 系统 prompt 渲染器。 */
    private final PromptRenderer promptRenderer;

    /** context block prompt 变量工厂。 */
    private final ContextBlockPromptVariablesFactory promptVariablesFactory;

    @Autowired
    public ContextBlockFactory(
        ContextBudgetCalculator contextBudgetCalculator,
        SessionStateBlockRenderer sessionStateBlockRenderer,
        ContextBlockIdGenerator contextBlockIdGenerator,
        PromptRenderer promptRenderer,
        ContextBlockPromptVariablesFactory promptVariablesFactory
    ) {
        this.contextBudgetCalculator = Objects.requireNonNull(contextBudgetCalculator, "contextBudgetCalculator must not be null");
        this.sessionStateBlockRenderer = Objects.requireNonNull(sessionStateBlockRenderer, "sessionStateBlockRenderer must not be null");
        this.contextBlockIdGenerator = Objects.requireNonNull(contextBlockIdGenerator, "contextBlockIdGenerator must not be null");
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer must not be null");
        this.promptVariablesFactory = Objects.requireNonNull(promptVariablesFactory, "promptVariablesFactory must not be null");
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
        TextPromptRenderResult renderResult = renderTextPrompt(
            SystemPromptKey.RUNTIME_INSTRUCTION_RENDER,
            promptVariablesFactory.runtimeInstructionVariables(agentMode, bundle.getLatestState())
        );
        String renderedText = renderResult.getRenderedText();
        return RuntimeInstructionBlock.builder()
            .blockId(nextBlockId())
            .priority(ContextPriority.MANDATORY)
            .required(true)
            .tokenEstimate(contextBudgetCalculator.estimateText(renderedText))
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of(ContextSourceRef.builder()
                .sourceType(ContextSourceType.RUNTIME_INSTRUCTION)
                .sourceId(renderResult.getPromptKey().getValue())
                .sourceVersion(renderResult.getMetadata().getCatalogRevision())
                .fieldPath("renderedText")
                .build()))
            .evidenceIds(List.of())
            .promptTemplateKey(renderResult.getPromptKey().getValue())
            .promptTemplateVersion(renderResult.getMetadata().getCatalogRevision())
            .renderedText(renderedText)
            .build();
    }

    private SessionStateBlock buildSessionStateBlock(SessionStateSnapshot stateSnapshot) {
        String sessionStateText = sessionStateBlockRenderer.render(stateSnapshot);
        TextPromptRenderResult renderResult = renderTextPrompt(
            SystemPromptKey.SESSION_STATE_RENDER,
            promptVariablesFactory.sessionStateVariables(stateSnapshot, sessionStateText)
        );
        String renderedText = renderResult.getRenderedText();
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
            .promptTemplateKey(renderResult.getPromptKey().getValue())
            .promptTemplateVersion(renderResult.getMetadata().getCatalogRevision())
            .stateSnapshot(stateSnapshot)
            .renderedText(renderedText)
            .build();
    }

    private ConversationSummaryBlock buildConversationSummaryBlock(ConversationSummary summary) {
        TextPromptRenderResult renderResult = renderTextPrompt(
            SystemPromptKey.CONVERSATION_SUMMARY_RENDER,
            promptVariablesFactory.conversationSummaryVariables(summary)
        );
        String renderedText = renderResult.getRenderedText();
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
            .promptTemplateKey(renderResult.getPromptKey().getValue())
            .promptTemplateVersion(renderResult.getMetadata().getCatalogRevision())
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
        return contextBlockIdGenerator.nextId();
    }

    /**
     * 渲染 TEXT prompt 并校验返回类型。
     */
    private TextPromptRenderResult renderTextPrompt(SystemPromptKey promptKey, java.util.Map<String, String> variables) {
        PromptRenderResult result = promptRenderer.render(PromptRenderRequest.builder()
            .promptKey(promptKey)
            .variables(variables)
            .build());
        if (!(result instanceof TextPromptRenderResult textPromptRenderResult)) {
            throw new IllegalStateException("context block prompt 必须渲染为 TEXT: " + promptKey.getValue());
        }
        return textPromptRenderResult;
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
