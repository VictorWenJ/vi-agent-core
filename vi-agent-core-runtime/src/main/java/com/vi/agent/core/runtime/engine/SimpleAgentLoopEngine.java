package com.vi.agent.core.runtime.engine;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.tool.ToolGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 默认 LLM-Tool 循环引擎。
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
    public LoopExecutionResult runStreaming(AgentRunContext runContext, AssistantStreamListener streamListener) {
        return execute(runContext, streamListener);
    }

    private LoopExecutionResult execute(AgentRunContext runContext, AssistantStreamListener streamListener) {
        List<Message> appendedMessages = new ArrayList<>();
        List<AssistantToolCall> toolCalls = new ArrayList<>();
        List<ToolExecution> toolExecutions = new ArrayList<>();
        UsageInfo totalUsage = UsageInfo.empty();

        AssistantMessage finalAssistant = null;
        FinishReason finalFinishReason = FinishReason.ERROR;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            runContext.nextIteration();

            ModelRequest modelRequest = ModelRequest.builder()
                .runId(runContext.getRunMetadata().getRunId())
                .conversationId(runContext.getConversation().getConversationId())
                .sessionId(runContext.getSession().getSessionId())
                .turnId(runContext.getTurn().getTurnId())
                .messages(runContext.getWorkingMessages().stream().filter(Objects::nonNull).toList())
                .tools(runContext.getAvailableTools())
                .build();
            log.info("SimpleAgentLoopEngine execute iteration={} modelRequest={}", iteration, JsonUtils.toJson(modelRequest));

            String assistantMessageId = messageFactory.nextAssistantMessageId();
            if (streamListener != null) {
                streamListener.onMessageStarted(assistantMessageId);
            }

            ModelResponse modelResponse = streamListener == null
                ? llmGateway.generate(modelRequest)
                : llmGateway.generateStreaming(modelRequest, delta -> streamListener.onMessageDelta(assistantMessageId, delta));
            log.info("SimpleAgentLoopEngine execute iteration={} modelResponse={}", iteration, JsonUtils.toJson(modelResponse));
            if (modelResponse == null) {
                throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "model response is null");
            }

            totalUsage = UsageInfo.sum(totalUsage, modelResponse.getUsage());
            FinishReason currentFinishReason = modelResponse.getFinishReason() == null ? FinishReason.STOP : modelResponse.getFinishReason();
            List<ModelToolCall> modelToolCalls = modelResponse.getToolCalls() == null ? List.of() : modelResponse.getToolCalls();

            List<AssistantToolCall> assistantToolCalls = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(modelToolCalls)) {
                int callIndex = 0;
                for (ModelToolCall modelToolCall : modelToolCalls) {
                    if (modelToolCall == null) {
                        continue;
                    }
                    AssistantToolCall assistantToolCall = messageFactory.createAssistantToolCall(
                        runContext.getConversation().getConversationId(),
                        runContext.getSession().getSessionId(),
                        runContext.getTurn().getTurnId(),
                        runContext.getRunMetadata().getRunId(),
                        assistantMessageId,
                        modelToolCall,
                        callIndex++
                    );
                    assistantToolCalls.add(assistantToolCall);
                }
            }

            AssistantMessage assistantMessage = messageFactory.createAssistantMessage(
                runContext.getConversation().getConversationId(),
                runContext.getSession().getSessionId(),
                runContext.getTurn().getTurnId(),
                runContext.getRunMetadata().getRunId(),
                assistantMessageId,
                modelResponse.getContent(),
                assistantToolCalls,
                currentFinishReason,
                modelResponse.getUsage()
            );

            runContext.appendWorkingMessage(assistantMessage);
            appendedMessages.add(assistantMessage);
            finalAssistant = assistantMessage;
            finalFinishReason = currentFinishReason;

            if (streamListener != null) {
                streamListener.onMessageCompleted(assistantMessage, currentFinishReason);
            }

            if (CollectionUtils.isEmpty(assistantToolCalls)) {
                break;
            }

            for (AssistantToolCall assistantToolCall : assistantToolCalls) {
                log.info("SimpleAgentLoopEngine execute tool call start iteration={} assistantToolCall={}", iteration, JsonUtils.toJson(assistantToolCall));
                toolCalls.add(assistantToolCall);
                runContext.appendToolCall(assistantToolCall);

                ToolCall toolCall = messageFactory.toToolCall(runContext.getTurn().getTurnId(), assistantToolCall);
                log.info("SimpleAgentLoopEngine execute tool call start iteration={} toolCall={}", iteration, JsonUtils.toJson(toolCall));

                Instant startedAt = Instant.now();
                ToolResult normalizedToolResult;
                Throwable toolExecutionThrowable = null;
                try {
                    ToolResult toolResult = toolGateway.execute(toolCall);
                    log.info("SimpleAgentLoopEngine execute tool call start iteration={} toolResult={}", iteration, JsonUtils.toJson(toolResult));
                    normalizedToolResult = normalizeToolResult(toolResult, assistantToolCall, runContext.getTurn().getTurnId());
                } catch (Throwable throwable) {
                    toolExecutionThrowable = throwable;
                    normalizedToolResult = normalizeToolResultFromThrowable(
                        assistantToolCall,
                        runContext.getTurn().getTurnId(),
                        startedAt,
                        throwable
                    );
                }

                Instant completedAt = Instant.now();
                if (!normalizedToolResult.isSuccess()) {
                    ToolExecution failedToolExecution = messageFactory.createToolExecution(
                        runContext.getConversation().getConversationId(),
                        runContext.getSession().getSessionId(),
                        runContext.getTurn().getTurnId(),
                        runContext.getRunMetadata().getRunId(),
                        normalizedToolResult,
                        null,
                        assistantToolCall.getArgumentsJson(),
                        startedAt,
                        completedAt
                    );
                    toolExecutions.add(failedToolExecution);
                    runContext.appendToolExecution(failedToolExecution);
                    throw buildToolExecutionFailedException(assistantToolCall, normalizedToolResult, toolExecutionThrowable);
                }

                ToolMessage toolMessage = messageFactory.createToolMessage(
                    runContext.getConversation().getConversationId(),
                    runContext.getSession().getSessionId(),
                    runContext.getTurn().getTurnId(),
                    runContext.getRunMetadata().getRunId(),
                    normalizedToolResult,
                    assistantToolCall.getArgumentsJson()
                );
                runContext.appendWorkingMessage(toolMessage);
                appendedMessages.add(toolMessage);
                ToolExecution toolExecution = messageFactory.createToolExecution(
                    runContext.getConversation().getConversationId(),
                    runContext.getSession().getSessionId(),
                    runContext.getTurn().getTurnId(),
                    runContext.getRunMetadata().getRunId(),
                    normalizedToolResult,
                    toolMessage,
                    assistantToolCall.getArgumentsJson(),
                    startedAt,
                    completedAt
                );
                toolExecutions.add(toolExecution);
                runContext.appendToolExecution(toolExecution);
            }
        }
        log.info("SimpleAgentLoopEngine execute assistantToolCall={}", JsonUtils.toJson(toolExecutions));

        if (Objects.isNull(finalAssistant)) {
            throw new AgentRuntimeException(ErrorCode.RUNTIME_MAX_ITERATIONS_EXCEEDED, "max iterations exceeded: " + maxIterations);
        }

        return LoopExecutionResult.builder()
            .assistantMessage(finalAssistant)
            .appendedMessages(appendedMessages)
            .toolCalls(toolCalls)
            .toolExecutions(toolExecutions)
            .finishReason(finalFinishReason)
            .usage(totalUsage)
            .build();
    }

    private ToolResult normalizeToolResult(ToolResult toolResult, AssistantToolCall assistantToolCall, String turnId) {
        if (toolResult == null) {
            return ToolResult.builder()
                .toolCallRecordId(assistantToolCall.getToolCallRecordId())
                .toolCallId(assistantToolCall.getToolCallId())
                .toolName(assistantToolCall.getToolName())
                .turnId(turnId)
                .success(false)
                .output("")
                .errorCode(ErrorCode.TOOL_EXECUTION_FAILED.getCode())
                .errorMessage("tool result is null")
                .durationMs(0L)
                .build();
        }
        boolean success = toolResult.isSuccess();
        String errorCode = success ? null : StringUtils.defaultIfBlank(toolResult.getErrorCode(), ErrorCode.TOOL_EXECUTION_FAILED.getCode());
        String errorMessage = success ? null : StringUtils.defaultIfBlank(toolResult.getErrorMessage(), "tool execution failed");
        return ToolResult.builder()
            .toolCallRecordId(assistantToolCall.getToolCallRecordId())
            .toolCallId(assistantToolCall.getToolCallId())
            .toolName(assistantToolCall.getToolName())
            .turnId(turnId)
            .success(success)
            .output(toolResult.getOutput())
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .durationMs(toolResult.getDurationMs() == null ? 0L : Math.max(toolResult.getDurationMs(), 0L))
            .build();
    }

    private ToolResult normalizeToolResultFromThrowable(
        AssistantToolCall assistantToolCall,
        String turnId,
        Instant startedAt,
        Throwable throwable
    ) {
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        return ToolResult.builder()
            .toolCallRecordId(assistantToolCall.getToolCallRecordId())
            .toolCallId(assistantToolCall.getToolCallId())
            .toolName(assistantToolCall.getToolName())
            .turnId(turnId)
            .success(false)
            .output("")
            .errorCode(ErrorCode.TOOL_EXECUTION_FAILED.getCode())
            .errorMessage(StringUtils.defaultIfBlank(throwable == null ? null : throwable.getMessage(), "tool execution failed"))
            .durationMs(Math.max(durationMs, 0L))
            .build();
    }

    private AgentRuntimeException buildToolExecutionFailedException(
        AssistantToolCall assistantToolCall,
        ToolResult normalizedToolResult,
        Throwable throwable
    ) {
        String message = String.format(
            "tool execution failed, toolName=%s, toolCallId=%s, error=%s",
            assistantToolCall == null ? null : assistantToolCall.getToolName(),
            assistantToolCall == null ? null : assistantToolCall.getToolCallId(),
            normalizedToolResult == null ? null : normalizedToolResult.getErrorMessage()
        );
        if (throwable == null) {
            return new AgentRuntimeException(ErrorCode.TOOL_EXECUTION_FAILED, message);
        }
        return new AgentRuntimeException(ErrorCode.TOOL_EXECUTION_FAILED, message, throwable);
    }
}
