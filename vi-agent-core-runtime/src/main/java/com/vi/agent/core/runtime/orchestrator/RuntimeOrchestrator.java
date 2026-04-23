package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.completion.RuntimeCompletionHandler;
import com.vi.agent.core.runtime.dedup.RuntimeDeduplicationHandler;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventSink;
import com.vi.agent.core.runtime.event.RuntimeEventSinkFactory;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentRunContextFactory;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import com.vi.agent.core.runtime.failure.RuntimeFailureHandler;
import com.vi.agent.core.runtime.lifecycle.TurnInitializationService;
import com.vi.agent.core.runtime.lifecycle.TurnStartResult;
import com.vi.agent.core.runtime.loop.LoopInvocationService;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.scope.RuntimeRunScope;
import com.vi.agent.core.runtime.scope.RuntimeRunScopeManager;
import com.vi.agent.core.runtime.session.SessionResolutionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runtime run orchestrator.
 */
@Slf4j
@Component
public class RuntimeOrchestrator {

    @Resource
    private RuntimeDeduplicationHandler deduplicationHandler;

    @Resource
    private SessionResolutionService sessionResolutionService;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    @Resource
    private RuntimeRunScopeManager runScopeManager;

    @Resource
    private TurnInitializationService turnInitializationService;

    @Resource
    private AgentRunContextFactory agentRunContextFactory;

    @Resource
    private RuntimeEventSinkFactory eventSinkFactory;

    @Resource
    private LoopInvocationService loopInvocationService;

    @Resource
    private RuntimeCompletionHandler completionHandler;

    @Resource
    private RuntimeFailureHandler failureHandler;

    public AgentExecutionResult execute(RuntimeExecuteCommand command) {
        return executeInternal(command, null, false);
    }

    public void executeStreaming(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer) {
        executeInternal(command, eventConsumer, true);
    }

    private AgentExecutionResult executeInternal(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer, boolean streaming) {
        RuntimeExecutionContext context = RuntimeExecutionContext.create(command, eventConsumer, streaming);
        log.info("RuntimeOrchestrator executeInternal context:{}", JsonUtils.toJson(context));

        AgentExecutionResult dedupAgentExecution = deduplicationHandler.checkAndBuildDedupAgentExecution(context);
        log.info("RuntimeOrchestrator executeInternal dedupAgentExecution:{}", JsonUtils.toJson(dedupAgentExecution));
        if (Objects.nonNull(dedupAgentExecution)) {
            return dedupAgentExecution;
        }

        context.setResolution(sessionResolutionService.judgeSessionResolutionMode(command));
        RunMetadata runMetadata = runIdentityFactory.createRunMetadata();
        context.setRunMetadata(runMetadata);

        RuntimeEventSink runtimeEventSink = eventSinkFactory.create(context);
        log.info("RuntimeOrchestrator executeInternal runtimeEventSink:{}", JsonUtils.toJson(runtimeEventSink));

        try (RuntimeRunScope ignored = runScopeManager.open(context)) {
            TurnStartResult turnStartResult = turnInitializationService.start(context);
            log.info("RuntimeOrchestrator executeInternal turnStartResult:{}", JsonUtils.toJson(turnStartResult));

            context.setTurn(turnStartResult.getTurn());
            context.setUserMessage(turnStartResult.getUserMessage());

            runtimeEventSink.runStarted();

            AgentRunContext agentRunContext = agentRunContextFactory.create(context);
            log.info("RuntimeOrchestrator executeInternal agentRunContext:{}", JsonUtils.toJson(agentRunContext));

            context.setRunContext(agentRunContext);

            LoopExecutionResult loopExecutionResult = loopInvocationService.process(context, runtimeEventSink);
            log.info("RuntimeOrchestrator executeInternal loopExecutionResult:{}", JsonUtils.toJson(loopExecutionResult));

            context.setLoopResult(loopExecutionResult);
            log.info("RuntimeOrchestrator executeInternal context:{}", JsonUtils.toJson(context));

            AgentExecutionResult agentExecutionResult = completionHandler.complete(context, runtimeEventSink);
            log.info("RuntimeOrchestrator executeInternal agentExecutionResult:{}", JsonUtils.toJson(agentExecutionResult));
            return agentExecutionResult;
        } catch (Throwable throwable) {
            log.error("RuntimeOrchestrator executeInternal error", throwable);
            return failureHandler.handle(context, throwable, runtimeEventSink);
        }
    }
}
