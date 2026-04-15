package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.model.message.AssistantMessage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime 核心编排入口（唯一主链路编排中心）。
 */
public class RuntimeOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RuntimeOrchestrator.class);

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

    public RuntimeOrchestrator(
        ContextAssembler contextAssembler,
        AgentLoopEngine agentLoopEngine,
        ToolGateway toolGateway,
        TranscriptStore transcriptStore,
        TraceIdGenerator traceIdGenerator,
        RunIdGenerator runIdGenerator
    ) {
        this.contextAssembler = contextAssembler;
        this.agentLoopEngine = agentLoopEngine;
        this.toolGateway = toolGateway;
        this.transcriptStore = transcriptStore;
        this.traceIdGenerator = traceIdGenerator;
        this.runIdGenerator = runIdGenerator;
    }

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

        try {
            ConversationTranscript transcript = transcriptStore.load(sessionId)
                .orElseGet(() -> new ConversationTranscript(sessionId));
            transcript.setTraceId(traceId);
            transcript.setRunId(runId);

            UserMessage userMessage = new UserMessage(userInput);
            List<com.vi.agent.core.model.message.Message> workingMessages =
                contextAssembler.assemble(transcript, userMessage);

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

            log.info("Runtime execute success traceId={} runId={} sessionId={} toolCalls={}",
                traceId,
                runId,
                sessionId,
                assistantMessage.getToolCalls().size());

            return new RuntimeExecutionResult(traceId, runId, sessionId, assistantMessage);
        } catch (AgentRuntimeException e) {
            log.error("Runtime execute failed traceId={} runId={} sessionId={}", traceId, runId, sessionId, e);
            throw e;
        } catch (Exception e) {
            log.error("Runtime execute failed traceId={} runId={} sessionId={}", traceId, runId, sessionId, e);
            throw new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "运行时执行失败", e);
        }
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
