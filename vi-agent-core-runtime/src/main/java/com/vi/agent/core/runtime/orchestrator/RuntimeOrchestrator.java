package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.RunState;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.context.ContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.tool.ToolGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime 核心编排入口（唯一主链路编排中心）。
 */
@Slf4j
@RequiredArgsConstructor
public class RuntimeOrchestrator {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_RUN_ID = "runId";
    private static final String MDC_SESSION_ID = "sessionId";

    /** 上下文装配器。 */
    private final ContextAssembler contextAssembler;

    /** Agent Loop 引擎。 */
    private final AgentLoopEngine agentLoopEngine;

    /** 工具网关。 */
    private final ToolGateway toolGateway;

    /** Transcript 存储接口。 */
    private final TranscriptStore transcriptStore;

    /** traceId 生成器。 */
    private final TraceIdGenerator traceIdGenerator;

    /** runId 生成器。 */
    private final RunIdGenerator runIdGenerator;

    /**
     * 执行一次同步会话。
     *
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return 执行结果
     */
    public RuntimeExecutionResult execute(String sessionId, String userInput) {
        ValidationUtils.requireNonBlank(sessionId, "sessionId");
        ValidationUtils.requireNonBlank(userInput, "userInput");

        String traceId = traceIdGenerator.nextId();
        String runId = runIdGenerator.nextId();
        String previousTraceId = MDC.get(MDC_TRACE_ID);
        String previousRunId = MDC.get(MDC_RUN_ID);
        String previousSessionId = MDC.get(MDC_SESSION_ID);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_RUN_ID, runId);
        MDC.put(MDC_SESSION_ID, sessionId);

        try {
            log.info("RuntimeOrchestrator runtime execute start");

            // 1.加载上下文，没有就新建（新会话）
            ConversationTranscript transcript = transcriptStore.load(sessionId)
                .orElseGet(() -> new ConversationTranscript(sessionId));
            transcript.setTraceId(traceId);
            transcript.setRunId(runId);
            log.info("RuntimeOrchestrator execute transcript:{}", JsonUtils.toJson(transcript));

            // 2.萃集上下文信息（历史信息+用户输入）
            UserMessage userMessage = new UserMessage(userInput);
            List<Message> workingMessages = contextAssembler.assemble(transcript, userMessage);
            log.info("RuntimeOrchestrator execute workingMessages:{}", JsonUtils.toJson(workingMessages));

            // 3.创建运行时上下文信息
            AgentRunContext runContext = new AgentRunContext(
                traceId,
                runId,
                sessionId,
                userInput,
                workingMessages,
                transcript,
                RunState.STARTED
            );

            AssistantMessage assistantMessage = agentLoopEngine.run(runContext);
            transcript.appendMessage(userMessage);
            transcript.appendMessage(assistantMessage);

            List<ToolResult> toolResults = executeToolCalls(assistantMessage.getToolCalls());
            for (ToolResult toolResult : toolResults) {
                transcript.appendToolResult(toolResult);
                transcript.appendToolCall(new ToolCall(
                    toolResult.getToolCallId(),
                    toolResult.getToolName(),
                    "{}"
                ));
                transcript.appendMessage(new ToolExecutionMessage(
                    toolResult.getToolCallId(),
                    toolResult.getToolName(),
                    toolResult.getOutput()
                ));
            }

            runContext.setRunState(RunState.COMPLETED);
            transcriptStore.save(transcript);

            log.info("Runtime execute success traceId={} runId={} sessionId={} messages={} toolCalls={} toolResults={}",
                traceId,
                runId,
                sessionId,
                transcript.getMessages().size(),
                transcript.getToolCalls().size(),
                transcript.getToolResults().size());

            return new RuntimeExecutionResult(traceId, runId, sessionId, assistantMessage);
        } catch (AgentRuntimeException e) {
            log.error("Runtime execute failed traceId={} runId={} sessionId={}", traceId, runId, sessionId, e);
            throw e;
        } catch (Exception e) {
            log.error("Runtime execute failed traceId={} runId={} sessionId={}", traceId, runId, sessionId, e);
            throw new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "运行时执行失败", e);
        } finally {
            restoreMdcValue(MDC_TRACE_ID, previousTraceId);
            restoreMdcValue(MDC_RUN_ID, previousRunId);
            restoreMdcValue(MDC_SESSION_ID, previousSessionId);
        }
    }

    private void restoreMdcValue(String key, String value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

    private List<ToolResult> executeToolCalls(List<ToolCall> toolCalls) {
        List<ToolResult> toolResults = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            ToolResult toolResult = toolGateway.route(toolCall);
            toolResults.add(toolResult);
        }
        return toolResults;
    }
}