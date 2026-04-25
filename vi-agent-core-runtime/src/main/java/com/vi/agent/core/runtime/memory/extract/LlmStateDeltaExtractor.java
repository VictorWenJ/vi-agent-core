package com.vi.agent.core.runtime.memory.extract;

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
import java.util.UUID;

/**
 * LLM-backed StateDelta extractor for the internal STATE_EXTRACT task.
 */
@Slf4j
@Component
public class LlmStateDeltaExtractor implements StateDeltaExtractor {

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private StateDeltaExtractionPromptBuilder promptBuilder;

    @Resource
    private StateDeltaExtractionOutputParser outputParser;

    @Override
    public StateDeltaExtractionResult extract(StateDeltaExtractionCommand command) {
        try {
            if (llmGateway == null) {
                return degraded("state delta extraction LLM gateway is not configured");
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
            return outputParser.parse(rawOutput);
        } catch (Exception ex) {
            log.warn("State delta extraction LLM call failed, sessionId={}, turnId={}",
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                ex);
            return degraded("state delta extraction LLM call failed: " + ex.getMessage());
        }
    }

    private List<Message> buildPromptMessages(StateDeltaExtractionCommand command, String prompt) {
        return List.of(
            SystemMessage.create(
                nextInternalMessageId("system"),
                command == null ? null : command.getConversationId(),
                command == null ? null : command.getSessionId(),
                command == null ? null : command.getTurnId(),
                command == null ? null : command.getRunId(),
                -2L,
                "You are an internal memory extraction worker. Return only strict StateDelta JSON."
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
        return "itaskmsg-" + role + "-" + UUID.randomUUID();
    }

    private StateDeltaExtractionResult degraded(String failureReason) {
        return StateDeltaExtractionResult.builder()
            .success(false)
            .degraded(true)
            .failureReason(failureReason)
            .sourceCandidateIds(List.of())
            .build();
    }
}
