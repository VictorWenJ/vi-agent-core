package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.llm.*;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.tool.ToolGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Default loop engine with llm-tool iterations.
 */
@Slf4j
@Component
public class SimpleAgentLoopEngine implements AgentLoopEngine {

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private ToolGateway toolGateway;

    @Resource
    private MessageFactory messageFactory;

    @Value("${vi.agent.runtime.max-iterations:6}")
    private int maxIterations;

    @Override
    public LoopExecutionResult run(AgentRunContext runContext) {
        return execute(runContext, null);
    }

    @Override
    public LoopExecutionResult runStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        return execute(runContext, chunkConsumer);
    }

    private LoopExecutionResult execute(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        List<Message> appendedMessages = new ArrayList<>();
        List<ToolCallRecord> toolCallRecords = new ArrayList<>();
        List<ToolResultRecord> toolResultRecords = new ArrayList<>();
        UsageInfo totalUsage = UsageInfo.empty();

        AssistantMessage finalAssistant = null;
        FinishReason finalFinishReason = FinishReason.ERROR;

        for (int i = 0; i < maxIterations; i++) {
            log.info("SimpleAgentLoopEngine execute start iteration={}", i);

            runContext.nextIteration();
            ModelRequest modelRequest = ModelRequest.builder()
                .runId(runContext.getRunMetadata().getRunId())
                .conversationId(runContext.getConversation().getConversationId())
                .sessionId(runContext.getSession().getSessionId())
                .turnId(runContext.getTurn().getTurnId())
                .messages(runContext.getWorkingMessages())
                .tools(runContext.getAvailableTools())
                .build();
            log.info("SimpleAgentLoopEngine execute modelRequest={}", JsonUtils.toJson(modelRequest));

            ModelResponse modelResponse = chunkConsumer == null
                ? llmGateway.generate(modelRequest)
                : llmGateway.generateStreaming(modelRequest, chunkConsumer);
            log.info("SimpleAgentLoopEngine execute modelResponse={}", JsonUtils.toJson(modelResponse));

            if (modelResponse == null) {
                throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "model response is null");
            }
            totalUsage = UsageInfo.sum(totalUsage, modelResponse.getUsage());

            List<ModelToolCall> modelToolCalls = modelResponse.getToolCalls() == null ? List.of() : modelResponse.getToolCalls();
            AssistantMessage assistantMessage = messageFactory.createAssistantMessage(
                runContext.getSession().getSessionId(),
                runContext.getTurn().getTurnId(),
                modelResponse.getContent(),
                modelToolCalls
            );
            runContext.appendWorkingMessage(assistantMessage);
            appendedMessages.add(assistantMessage);
            finalAssistant = assistantMessage;
            finalFinishReason = modelResponse.getFinishReason() == null ? FinishReason.STOP : modelResponse.getFinishReason();

            if (modelToolCalls.isEmpty()) {
                break;
            }

            int toolCallSequence = 1;
            for (ModelToolCall modelToolCall : modelToolCalls) {
                log.info("SimpleAgentLoopEngine execute toolCall start iteration={} toolCallSequence={} current modelToolCall={}",
                    i, toolCallSequence, JsonUtils.toJson(modelToolCall));

                String toolCallId = messageFactory.resolveToolCallId(modelToolCall);
                ToolCallMessage toolCallMessage = messageFactory.createToolCallMessage(
                    runContext.getSession().getSessionId(),
                    runContext.getTurn().getTurnId(),
                    toolCallId,
                    modelToolCall.getToolName(),
                    modelToolCall.getArgumentsJson()
                );

                runContext.appendWorkingMessage(toolCallMessage);
                appendedMessages.add(toolCallMessage);

                ToolCallRecord toolCallRecord = messageFactory.createToolCallRecord(
                    runContext.getConversation().getConversationId(),
                    runContext.getSession().getSessionId(),
                    runContext.getTurn().getTurnId(),
                    toolCallMessage,
                    toolCallSequence++
                );

                toolCallRecords.add(toolCallRecord);

                ToolCall toolCall = messageFactory.toToolCall(runContext.getTurn().getTurnId(), toolCallId, modelToolCall);
                ToolResult toolResult = toolGateway.execute(toolCall);
                log.info("SimpleAgentLoopEngine execute toolCall start iteration={} toolCallSequence={} current toolCall ={} toolResult={}",
                    i, toolCallSequence, JsonUtils.toJson(toolCall), JsonUtils.toJson(toolResult));

                ToolResultMessage toolResultMessage = messageFactory.createToolResultMessage(
                    runContext.getSession().getSessionId(),
                    runContext.getTurn().getTurnId(),
                    toolResult
                );
                runContext.appendWorkingMessage(toolResultMessage);
                appendedMessages.add(toolResultMessage);

                ToolResultRecord toolResultRecord = messageFactory.createToolResultRecord(
                    runContext.getConversation().getConversationId(),
                    runContext.getSession().getSessionId(),
                    runContext.getTurn().getTurnId(),
                    toolResultMessage
                );
                toolResultRecords.add(toolResultRecord);
            }
        }

        if (Objects.isNull(finalAssistant)) {
            throw new AgentRuntimeException(ErrorCode.RUNTIME_MAX_ITERATIONS_EXCEEDED, "max iterations exceeded: " + maxIterations);
        }

        return LoopExecutionResult.builder()
            .assistantMessage(finalAssistant)
            .appendedMessages(appendedMessages)
            .toolCalls(toolCallRecords)
            .toolResults(toolResultRecords)
            .finishReason(finalFinishReason)
            .usage(totalUsage)
            .build();
    }
}
