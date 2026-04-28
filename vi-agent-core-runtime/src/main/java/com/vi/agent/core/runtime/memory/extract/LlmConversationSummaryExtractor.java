package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.id.InternalTaskMessageIdGenerator;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import com.vi.agent.core.runtime.memory.extract.prompt.ConversationSummaryExtractionPromptVariablesFactory;
import com.vi.agent.core.runtime.prompt.ChatMessagesPromptRenderResult;
import com.vi.agent.core.runtime.prompt.PromptRenderRequest;
import com.vi.agent.core.runtime.prompt.PromptRenderResult;
import com.vi.agent.core.runtime.prompt.PromptRenderedMessage;
import com.vi.agent.core.runtime.prompt.PromptRenderer;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 基于 LLM 的会话摘要抽取器。
 */
@Slf4j
@Component
public class LlmConversationSummaryExtractor implements ConversationSummaryExtractor {

    /** LLM 调用网关。 */
    private final LlmGateway llmGateway;

    /** 系统 prompt 渲染器。 */
    private final PromptRenderer promptRenderer;

    /** 系统 prompt 只读注册表。 */
    private final SystemPromptRegistry systemPromptRegistry;

    /** 会话摘要抽取变量工厂。 */
    private final ConversationSummaryExtractionPromptVariablesFactory promptVariablesFactory;

    /** 摘要抽取输出解析器。 */
    private final ConversationSummaryExtractionOutputParser outputParser;

    /** 内部任务消息 ID 生成器。 */
    private final InternalTaskMessageIdGenerator internalTaskMessageIdGenerator;

    public LlmConversationSummaryExtractor(
        @Qualifier("llmGateway") LlmGateway llmGateway,
        PromptRenderer promptRenderer,
        SystemPromptRegistry systemPromptRegistry,
        ConversationSummaryExtractionPromptVariablesFactory promptVariablesFactory,
        ConversationSummaryExtractionOutputParser outputParser,
        InternalTaskMessageIdGenerator internalTaskMessageIdGenerator
    ) {
        this.llmGateway = llmGateway;
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer must not be null");
        this.systemPromptRegistry = Objects.requireNonNull(systemPromptRegistry, "systemPromptRegistry must not be null");
        this.promptVariablesFactory = Objects.requireNonNull(promptVariablesFactory, "promptVariablesFactory must not be null");
        this.outputParser = Objects.requireNonNull(outputParser, "outputParser must not be null");
        this.internalTaskMessageIdGenerator = Objects.requireNonNull(internalTaskMessageIdGenerator, "internalTaskMessageIdGenerator must not be null");
    }

    @Override
    public ConversationSummaryExtractionResult extract(ConversationSummaryExtractionCommand command) {
        try {
            if (llmGateway == null) {
                return degraded("summary extraction LLM gateway is not configured");
            }
            ChatMessagesPromptRenderResult renderResult = renderPrompt(command);
            StructuredLlmOutputContract contract = systemPromptRegistry.getStructuredLlmOutputContract(
                StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT
            );
            ModelResponse response = llmGateway.generate(ModelRequest.builder()
                .conversationId(command == null ? null : command.getConversationId())
                .sessionId(command == null ? null : command.getSessionId())
                .turnId(command == null ? null : command.getTurnId())
                .runId(command == null ? null : command.getRunId())
                .messages(buildPromptMessages(command, renderResult.getRenderedMessages()))
                .tools(List.of())
                .structuredOutputContract(contract)
                .structuredOutputFunctionName("emit_conversation_summary")
                .build());
            StructuredOutputChannelResult channelResult = response == null ? null : response.getStructuredOutputChannelResult();
            if (channelResult == null || !Boolean.TRUE.equals(channelResult.getSuccess())) {
                return degraded(
                    renderResult,
                    channelResult,
                    channelFailureReason(channelResult)
                );
            }
            NormalizedStructuredLlmOutput output = channelResult.getOutput();
            ConversationSummaryExtractionResult parsed = outputParser.parse(output);
            return parsed.toBuilder()
                .generatorProvider(response == null ? null : response.getProvider())
                .generatorModel(response == null ? null : response.getModel())
                .promptRenderMetadata(renderResult.getMetadata())
                .structuredOutputChannelResult(channelResult)
                .build();
        } catch (Exception ex) {
            log.warn("Conversation summary extraction LLM call failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return degraded("summary extraction llm call failed: " + ex.getMessage());
        }
    }

    /**
     * 渲染会话摘要抽取 prompt。
     */
    private ChatMessagesPromptRenderResult renderPrompt(ConversationSummaryExtractionCommand command) {
        PromptRenderResult result = promptRenderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT)
            .variables(promptVariablesFactory.variables(command))
            .build());
        if (!(result instanceof ChatMessagesPromptRenderResult chatMessagesPromptRenderResult)) {
            throw new IllegalStateException("CONVERSATION_SUMMARY_EXTRACT 必须渲染为 CHAT_MESSAGES");
        }
        return chatMessagesPromptRenderResult;
    }

    /**
     * 将渲染后的 prompt 消息转换为 provider-neutral Message。
     */
    private List<Message> buildPromptMessages(
        ConversationSummaryExtractionCommand command,
        List<PromptRenderedMessage> renderedMessages
    ) {
        return renderedMessages.stream()
            .map(message -> toInternalMessage(command, message))
            .toList();
    }

    /**
     * 构造内部 worker 消息，仅允许 SYSTEM 与 USER。
     */
    private Message toInternalMessage(ConversationSummaryExtractionCommand command, PromptRenderedMessage renderedMessage) {
        MessageRole role = renderedMessage.getRole();
        long sequenceNo = renderedMessage.getOrder() == null ? -1L : renderedMessage.getOrder();
        if (role == MessageRole.SYSTEM) {
            return SystemMessage.create(
                nextInternalMessageId("system"),
                command == null ? null : command.getConversationId(),
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                command == null ? null : command.getRunId(),
                sequenceNo,
                renderedMessage.getRenderedContent()
            );
        }
        if (role == MessageRole.USER) {
            return UserMessage.create(
                nextInternalMessageId("user"),
                command == null ? null : command.getConversationId(),
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                command == null ? null : command.getRunId(),
                sequenceNo,
                renderedMessage.getRenderedContent()
            );
        }
        throw new IllegalStateException("CONVERSATION_SUMMARY_EXTRACT 只允许 SYSTEM/USER 消息: " + role);
    }

    private String nextInternalMessageId(String role) {
        return internalTaskMessageIdGenerator.nextId(role);
    }

    private ConversationSummaryExtractionResult degraded(String failureReason) {
        return ConversationSummaryExtractionResult.builder()
            .success(false)
            .degraded(true)
            .failureReason(failureReason)
            .build();
    }

    /**
     * 构造携带 prompt/channel audit 元数据的降级结果。
     */
    private ConversationSummaryExtractionResult degraded(
        ChatMessagesPromptRenderResult renderResult,
        StructuredOutputChannelResult channelResult,
        String failureReason
    ) {
        return ConversationSummaryExtractionResult.builder()
            .success(false)
            .degraded(true)
            .failureReason(failureReason)
            .promptRenderMetadata(renderResult.getMetadata())
            .structuredOutputChannelResult(channelResult)
            .build();
    }

    /**
     * 解析 provider 结构化输出通道失败原因。
     */
    private String channelFailureReason(StructuredOutputChannelResult channelResult) {
        if (channelResult == null) {
            return "summary structured output channel result is missing";
        }
        return channelResult.getFailureReason() == null || channelResult.getFailureReason().isBlank()
            ? "summary structured output channel failed"
            : channelResult.getFailureReason();
    }
}
