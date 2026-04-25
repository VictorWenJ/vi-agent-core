package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.id.InternalTaskMessageIdGenerator;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 LLM 的会话摘要抽取器。
 */
@Slf4j
@Component
public class LlmConversationSummaryExtractor implements ConversationSummaryExtractor {

    /** LLM 调用网关。 */
    @Resource
    private LlmGateway llmGateway;

    /** 摘要抽取 prompt 构建器。 */
    @Resource
    private ConversationSummaryExtractionPromptBuilder promptBuilder;

    /** 摘要抽取输出解析器。 */
    @Resource
    private ConversationSummaryExtractionOutputParser outputParser;

    /** 内部任务消息 ID 生成器。 */
    @Resource
    private InternalTaskMessageIdGenerator internalTaskMessageIdGenerator;

    public LlmConversationSummaryExtractor() {
    }

    public LlmConversationSummaryExtractor(
        LlmGateway llmGateway,
        ConversationSummaryExtractionPromptBuilder promptBuilder,
        ConversationSummaryExtractionOutputParser outputParser,
        InternalTaskMessageIdGenerator internalTaskMessageIdGenerator
    ) {
        this.llmGateway = llmGateway;
        this.promptBuilder = promptBuilder;
        this.outputParser = outputParser;
        this.internalTaskMessageIdGenerator = internalTaskMessageIdGenerator;
    }

    @Override
    public ConversationSummaryExtractionResult extract(ConversationSummaryExtractionCommand command) {
        try {
            if (llmGateway == null) {
                return degraded("summary extraction LLM gateway is not configured");
            }
            String prompt = promptBuilder.buildPrompt(command);
            ModelResponse response = llmGateway.generate(ModelRequest.builder()
                .conversationId(command == null ? null : command.getConversationId())
                .sessionId(command == null ? null : command.getSessionId())
                .turnId(command == null ? null : command.getTurnId())
                .runId(command == null ? null : command.getRunId())
                .messages(buildPromptMessages(command, prompt))
                .tools(List.of())
                .build());
            String rawOutput = response == null ? null : response.getContent();
            ConversationSummaryExtractionResult parsed = outputParser.parse(rawOutput);
            return parsed.toBuilder()
                .generatorProvider(response == null ? null : response.getProvider())
                .generatorModel(response == null ? null : response.getModel())
                .build();
        } catch (Exception ex) {
            log.warn("Conversation summary extraction LLM call failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return degraded("summary extraction llm call failed: " + ex.getMessage());
        }
    }

    private List<Message> buildPromptMessages(ConversationSummaryExtractionCommand command, String prompt) {
        return List.of(
            SystemMessage.create(
                nextInternalMessageId("system"),
                command == null ? null : command.getConversationId(),
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                command == null ? null : command.getRunId(),
                -2L,
                "You are an internal memory summary worker. Return only strict ConversationSummary JSON."
            ),
            UserMessage.create(
                nextInternalMessageId("user"),
                command == null ? null : command.getConversationId(),
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                command == null ? null : command.getRunId(),
                -1L,
                prompt
            )
        );
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
}
