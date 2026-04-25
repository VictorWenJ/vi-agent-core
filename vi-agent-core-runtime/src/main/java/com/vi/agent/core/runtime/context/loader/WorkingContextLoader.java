package com.vi.agent.core.runtime.context.loader;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ContextPlan;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.ProjectionValidationResult;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextBuildResult;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.runtime.context.audit.WorkingContextSnapshotService;
import com.vi.agent.core.runtime.context.budget.ContextBudgetCalculator;
import com.vi.agent.core.runtime.context.builder.ContextBlockFactory;
import com.vi.agent.core.runtime.context.builder.WorkingContextBuilder;
import com.vi.agent.core.runtime.context.planner.ContextPlanner;
import com.vi.agent.core.runtime.context.projector.WorkingContextProjector;
import com.vi.agent.core.runtime.context.validation.WorkingContextValidator;
import com.vi.agent.core.runtime.persistence.SessionWorkingSetLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Main entry for first-call WorkingContext construction.
 */
@Slf4j
@Component
public class WorkingContextLoader {

    @Resource
    private SessionWorkingSetLoader sessionWorkingSetLoader;

    @Resource
    private SessionWorkingSetRepository sessionWorkingSetRepository;

    @Resource
    private SessionStateRepository sessionStateRepository;

    @Resource
    private SessionSummaryRepository sessionSummaryRepository;

    @Resource
    private ContextBudgetCalculator contextBudgetCalculator;

    @Resource
    private ContextBlockFactory contextBlockFactory;

    @Resource
    private ContextPlanner contextPlanner;

    @Resource
    private WorkingContextBuilder workingContextBuilder;

    @Resource
    private WorkingContextProjector workingContextProjector;

    @Resource
    private WorkingContextValidator workingContextValidator;

    @Resource
    private WorkingContextSnapshotService workingContextSnapshotService;

    public WorkingContextBuildResult loadForMainAgent(WorkingContextLoadCommand command) {
        if (command.getContextViewType() != null && command.getContextViewType() != ContextViewType.MAIN_AGENT) {
            throw new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "only MAIN_AGENT workingContext view is supported in P2-C");
        }

        // 1、 加载各种最新数据
        List<Message> recentRawMessages = sessionWorkingSetLoader.load(command.getConversationId(), command.getSessionId());
        SessionWorkingSetSnapshot latestSessionWorkingSetSnapshot = sessionWorkingSetRepository.findBySessionId(command.getSessionId()).orElse(null);
        Optional<SessionStateSnapshot> latestSessionStateSnapshot = sessionStateRepository.findLatestBySessionId(command.getSessionId());
        Optional<ConversationSummary> latestConversationSummary = sessionSummaryRepository.findLatestBySessionId(command.getSessionId());

        // 2、构建本次操作的全量数据对象捆
        MemoryLoadBundle memoryLoadBundle = MemoryLoadBundle.builder()
            .sessionWorkingSetSnapshot(latestSessionWorkingSetSnapshot)
            .workingSetVersion(latestSessionWorkingSetSnapshot == null ? null : latestSessionWorkingSetSnapshot.getWorkingSetVersion())
            .recentRawMessages(recentRawMessages == null ? List.of() : recentRawMessages)
            .latestState(latestSessionStateSnapshot.orElse(null))
            .latestSummary(latestConversationSummary.orElse(null))
            .currentUserMessage(command.getCurrentUserMessage())
            .agentMode(command.getAgentMode() == null ? AgentMode.GENERAL : command.getAgentMode())
            .contextViewType(ContextViewType.MAIN_AGENT)
            .build();
        log.info("WorkingContextLoader loadForMainAgent memoryLoadBundle:{}", JsonUtils.toJson(memoryLoadBundle));

        // 3、构建本次操作的全量上下文数据块
        List<ContextBlock> contextBlocks = contextBlockFactory.buildBlocks(command, memoryLoadBundle);
        log.info("WorkingContextLoader loadForMainAgent contextBlocks:{}", JsonUtils.toJson(contextBlocks));

        // 4、统计、计算上下文数据块token总量
        ContextBudgetSnapshot contextBudgetSnapshot = contextBudgetCalculator.calculateBlocks(contextBlocks);
        log.info("WorkingContextLoader loadForMainAgent contextBudgetSnapshot:{}", JsonUtils.toJson(contextBudgetSnapshot));

        // 5、计划token分配量
        ContextPlan contextPlan = contextPlanner.plan(contextBlocks, contextBudgetSnapshot, memoryLoadBundle.getAgentMode(), memoryLoadBundle.getContextViewType());
        log.info("WorkingContextLoader loadForMainAgent contextPlan:{}", JsonUtils.toJson(contextPlan));

        // 6、构建work context母版
        WorkingContext workingContext = workingContextBuilder.build(command, memoryLoadBundle, contextPlan);
        log.info("WorkingContextLoader loadForMainAgent workingContext:{}", JsonUtils.toJson(workingContext));

        // 7、基于母版构建work context投影
        WorkingContextProjection workingContextProjection = workingContextProjector.project(workingContext);
        log.info("WorkingContextLoader loadForMainAgent workingContextProjection:{}", JsonUtils.toJson(workingContextProjection));

        // 8、验证投影可用性
        ProjectionValidationResult validationResult = workingContextValidator.validate(workingContext, workingContextProjection);
        log.info("WorkingContextLoader loadForMainAgent validationResult:{}", JsonUtils.toJson(validationResult));

        // 9、保存
        workingContextSnapshotService.save(workingContext, workingContextProjection, contextPlan, validationResult);

        if (!validationResult.isValid()) {
            throw new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "working workingContext validation failed: " + String.join("; ", validationResult.getViolations()));
        }

        return WorkingContextBuildResult.builder()
            .context(workingContext)
            .projection(workingContextProjection)
            .contextPlan(contextPlan)
            .validationResult(validationResult)
            .build();
    }
}
