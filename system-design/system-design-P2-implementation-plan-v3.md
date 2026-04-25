# system-design-P2 Implementation Plan v3

> 本计划基于 `system-design-P2-v5.md` 制定，用于把 P2 Context Kernel 设计落实到代码、配置、DDL、测试与文档治理中。
> 本计划是**可执行实施计划**：除阶段目标与约束外，明确每阶段 `Create / Modify / Delete / Test / Acceptance`。
> 当前冻结口径：先落 **通用 Context Kernel**，不在 P2 偷跑 Subagent / Delegation / LongTermMemory 正式实现。

---

## 1. 冻结结论（执行前必须对齐）

1. **主聊天协议不变**
   - 不修改 `ChatRequest / ChatResponse / ChatStreamEvent` 的主业务字段。
   - Working Context / Projection / Budget / Evidence 的调试能力统一通过独立接口查询。

2. **Debug 接口以设计文档 v5 为准**
   - `GET /api/internal/debug/context-debug?runId={runId}`
   - `GET /api/internal/debug/context-debug-snapshot?snapshotId={snapshotId}&includeProjection={true|false}`
   - 若未来要改 RESTful 风格，必须先改设计文档，再改 implementation plan。

3. **WorkingContext 与 Projection 分离**
   - `WorkingContext` = canonical context 母版。
   - `WorkingContextProjection` = provider-ready 派生产物。
   - 返回对象统一使用：

```java
class WorkingContextBuildResult {
    private WorkingContext context;
    private WorkingContextProjection projection;
    private ContextPlan contextPlan;
    private ProjectionValidationResult validationResult;
}
```

4. **ContextBlock 只保留块级模型**
   - 允许的 block：
     - `RuntimeInstructionBlock`
     - `SessionStateBlock`
     - `ConversationSummaryBlock`
     - `RecentMessagesBlock`
     - `CurrentUserMessageBlock`
     - `ContextReferenceBlock`
     - `CompactionNoteBlock`
   - 不允许把 `confirmedFacts / constraints / decisions / openLoops / toolOutcomes` 直接升级成顶级 block。

5. **旧命名必须收敛**
   - 旧 `SessionStateSnapshot`（recent message cache 语义）必须退出。
   - 新语义：
     - `SessionWorkingSetSnapshot`
     - `SessionStateSnapshot`
     - `ConversationSummary`
   - 旧 `SessionContext*` / `RedisSessionContextRepository` 路径不再继续扩展，进入删除或替换清单。

6. **Flyway 只增不改**
   - 新增：`V2__add_context_kernel_tables.sql`
   - 禁止修改历史 `V1__init_*` migration。

7. **Checkpoint rebuild 不可破坏 provider tool chain**
   - rebuild 发生在：`tool result 已成为事实` 之后、`下一次 model call` 之前。
   - rebuild 只影响下一次 projection，不回写旧 transcript/tool facts。
   - 不允许在未闭合 tool chain 中插入 derived message。

8. **Internal task 失败不反向污染主 run 状态**
   - `StateDelta`：post-turn 默认同步短超时，可 `DEGRADED`。
   - `Summary`：默认 background；compaction 必要时允许同步。
   - internal task 失败：
     - 不修改主 `turn/run` 状态
     - 记录 `agent_internal_llm_task.status`
     - 如需 run_event，必须用显式类型，不使用模糊 `warning/error`。

9. **显式 RunEventType（冻结）**
   - `INTERNAL_TASK_STARTED`
   - `INTERNAL_TASK_SUCCEEDED`
   - `INTERNAL_TASK_FAILED`
   - `INTERNAL_TASK_DEGRADED`
   - `INTERNAL_TASK_SKIPPED`

10. **Provider projector 边界冻结**
    - runtime 层：`WorkingContextProjector` 负责 `ContextBlockSet -> WorkingContextProjection`
    - infra 层：继续复用现有 `OpenAICompatibleMessageProjector` 做 provider 协议投影
    - P2 不新增语义重叠的 `ProviderCompatibleMessageProjector`

---

## 2. 交付物总览

### 2.1 代码交付

- `model/context`：Context Kernel 领域模型
- `model/memory`：WorkingSet / State / Summary / Evidence / Delta
- `runtime/context`：Loader / Builder / Planner / Projector / Validator / Policy / Budget / Checkpoint / Compaction
- `runtime/memory`：CandidateHarvester / StateDeltaValidator / StateDeltaMerger / SummaryExtractor / EvidenceBinder / SessionMemoryCoordinator / InternalTaskService
- `runtime/persistence`：Facade + 协作者拆分
- `infra/mysql`：P2 新表 entity / mapper / repository
- `infra/redis`：WorkingSet / State / Summary snapshot DTO / mapper / repository
- `app/internal/debug`：独立 context debug 查询接口

### 2.2 数据交付

新增表：
- `agent_session_state`
- `agent_session_summary`
- `agent_memory_evidence`
- `agent_working_context_snapshot`
- `agent_internal_llm_task`

新增 Redis key：
- `agent:session:working-set:{sessionId}`
- `agent:session:state:{sessionId}`
- `agent:session:summary:{sessionId}`

### 2.3 测试交付

- model 单测
- runtime context / memory 单测
- infra repository / redis 集成测试
- app debug 接口测试
- 全链路回归测试

---

## 3. 阶段 1：命名收敛与领域模型落地

### 3.1 目标

先把错误语义彻底纠正，再建立 P2 Context Kernel 基础模型，避免 runtime / infra 各自重复造对象。

### 3.2 Create

#### model/context
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/AgentMode.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/WorkingMode.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ContextViewType.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ContextBlockType.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ContextPriority.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/CheckpointTrigger.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/CheckpointReason.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/WorkingContext.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/WorkingContextMetadata.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/WorkingContextSource.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ContextBudgetSnapshot.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/WorkingContextProjection.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/WorkingContextBuildResult.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ProjectionValidationResult.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ContextPlan.java`
- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/context/ContextAssemblyDecision.java`

#### model/context/block
- `ContextBlock.java`
- `ContextBlockSet.java`
- `RuntimeInstructionBlock.java`
- `SessionStateBlock.java`
- `ConversationSummaryBlock.java`
- `RecentMessagesBlock.java`
- `CurrentUserMessageBlock.java`
- `ContextReferenceBlock.java`
- `CompactionNoteBlock.java`
- `ContextSourceRef.java`

#### model/memory
- `SessionWorkingSetSnapshot.java`
- `SessionStateSnapshot.java`
- `StateDelta.java`
- `ConversationSummary.java`
- `ConfirmedFactRecord.java`
- `ConstraintRecord.java`
- `DecisionRecord.java`
- `UserPreferenceState.java`
- `AnswerStyle.java`
- `DetailLevel.java`
- `TermFormat.java`
- `OpenLoop.java`
- `OpenLoopKind.java`
- `OpenLoopStatus.java`
- `ToolOutcomeDigest.java`
- `ToolOutcomeFreshnessPolicy.java`
- `PhaseState.java`
- `MemoryWriteMode.java`
- `EvidenceRef.java`
- `EvidenceTarget.java`
- `EvidenceSource.java`
- `EvidenceTargetType.java`
- `EvidenceSourceType.java`
- `ContextReference.java`

### 3.3 Modify

- `vi-agent-core-model/src/test/java/com/vi/agent/core/model/EnumContractTest.java`
- 受影响的 `model` builder / serializer / equals/hashCode 测试

### 3.4 Delete / Replace

- **删除或迁移旧 recent-cache 语义类**：
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/session/SessionStateSnapshot.java`
- 若代码量较大，可分两步：
  1. 先新增 `SessionWorkingSetSnapshot`
  2. 再删除旧 `SessionStateSnapshot`
- 但最终不允许两个类继续并存且承担同一 old recent-cache 语义。

### 3.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-model
```

### 3.6 Acceptance

1. `ContextBlock` 只包含块级模型，不存在 `ConfirmedFactBlock / ConstraintBlock / DecisionBlock / OpenLoopBlock / ToolOutcomeBlock`。  
2. `WorkingContextBuildResult` 同时持有 `context / projection / contextPlan / validationResult`。  
3. `SessionWorkingSetSnapshot` 与 `SessionStateSnapshot` 语义完全分离。  
4. 所有新增 enum 有中文注释与稳定值。  
5. model 模块独立编译通过。  

---

## 4. 阶段 2：DDL、Entity、Mapper、Repository

### 4.1 目标

先落地 P2 新表与 MySQL repository，使 state / summary / evidence / context audit / internal task 有正式事实落点。

### 4.2 Create

#### Flyway
- `vi-agent-core-app/src/main/resources/db/migration/V2__add_context_kernel_tables.sql`

#### infra/mysql/entity
- `AgentSessionStateEntity.java`
- `AgentSessionSummaryEntity.java`
- `AgentMemoryEvidenceEntity.java`
- `AgentWorkingContextSnapshotEntity.java`
- `AgentInternalLlmTaskEntity.java`

#### infra/mysql/mapper
- `AgentSessionStateMapper.java`
- `AgentSessionSummaryMapper.java`
- `AgentMemoryEvidenceMapper.java`
- `AgentWorkingContextSnapshotMapper.java`
- `AgentInternalLlmTaskMapper.java`

#### model/port
- `SessionStateRepository.java`
- `SessionSummaryRepository.java`
- `MemoryEvidenceRepository.java`
- `WorkingContextSnapshotRepository.java`
- `InternalLlmTaskRepository.java`

#### infra/mysql/repository
- `MysqlSessionStateRepository.java`
- `MysqlSessionSummaryRepository.java`
- `MysqlMemoryEvidenceRepository.java`
- `MysqlWorkingContextSnapshotRepository.java`
- `MysqlInternalLlmTaskRepository.java`

### 4.3 Modify

- `vi-agent-core-app/pom.xml`（如需新增 mapper/entity 依赖声明）
- 现有 MyBatis 配置、MapperScan、Entity package 注册

### 4.4 Delete / Replace

- 不删除旧 transcript 事实表相关 entity/mapper/repository
- 不修改历史 `V1__init_*` migration

### 4.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-app -am
```

### 4.6 Acceptance

1. 只新增 `V2__add_context_kernel_tables.sql`，不改历史 migration。  
2. summary 表名固定为 **`agent_session_summary`**。  
3. `agent_internal_llm_task.status` DDL 注释必须包含 `DEGRADED`。  
4. `agent_working_context_snapshot` 必须有：
   - `context_json`
   - `budget_json`
   - `block_set_json`
   - `projection_json`  
5. repository 不做 runtime 决策，只做存取。  

---

## 5. 阶段 3：Redis Snapshot 模型与 Repository

### 5.1 目标

把 Redis 从旧 recent session context 缓存升级为三类稳定 snapshot 缓存：Working Set / Session State / Summary。

P2-A 阶段 `SessionWorkingSetSnapshot.summaryCoveredToSequenceNo` 先完成字段、Redis DTO、Mapper、Loader 链路打通；当前实现允许采用占位策略，不视为 Summary 主链路正式语义。等 `ConversationSummary` 正式接入主链路后，必须改为基于 Summary 实际覆盖区间计算。

### 5.2 Create

#### infra/cache/session/document
- `SessionWorkingSetSnapshotDocument.java`
- `SessionStateSnapshotDocument.java`
- `SessionSummarySnapshotDocument.java`

#### infra/cache/session/mapper
- `SessionWorkingSetRedisMapper.java`
- `SessionStateRedisMapper.java`
- `SessionSummaryRedisMapper.java`

#### infra/cache/session/repository
- `RedisSessionWorkingSetRepository.java`
- `RedisSessionStateRepository.java`
- `RedisSessionSummaryRepository.java`

### 5.3 Modify

- `vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/persistence/cache/session/key/SessionRedisKeyBuilder.java`
- Spring cache / RedisTemplate 配置

### 5.4 Delete / Replace

**删除或退役旧 recent-context 路径：**
- `SessionContextSnapshotDocument.java`
- `SessionContextMessageSnapshot.java`
- `SessionContextRedisMapper.java`
- `RedisSessionContextRepository.java`

> 若当前代码耦合较深，可先标 `@Deprecated` 并完成调用方迁移后删除；但最终 P2 收口时必须移除主链路依赖。

### 5.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-infra -am
```

### 5.6 Acceptance

1. Redis key 固定为：
   - `agent:session:working-set:{sessionId}`
   - `agent:session:state:{sessionId}`
   - `agent:session:summary:{sessionId}`  
2. cache corruption（反序列化失败 / version 不匹配 / required field 缺失）才允许 evict。  
3. 普通 validation failed 不默认 evict Redis。  
4. Redis 只做 snapshot 缓存，MySQL 是事实源。  
5. 旧 `SessionContext*` 不再出现在主链路依赖里。  

---

## 6. 阶段 4：Policy / Budget / Validator / AgentModeResolver

### 6.1 目标

建立 P2 上下文规则层，先把 `GENERAL` 模式的默认策略跑通。

### 6.2 Create

#### runtime/context/policy
- `ContextPolicy.java`
- `DefaultContextPolicy.java`
- `ContextPolicyResolver.java`

#### runtime/context/budget
- `ContextBudgetCalculator.java`
- `ContextBudgetProperties.java`

#### runtime/context/validation
- `WorkingContextValidator.java`
- `WorkingContextValidationResult.java`

#### runtime/context/mode
- `AgentModeResolver.java`

### 6.3 Modify

- `PromptRenderContext`（注入 `agentMode / workingMode`）
- `AgentRunContext`（增加 `agentMode`）

### 6.4 Delete / Replace

- 不新增 Advisor / Workbench 专用策略实现
- 不把 `AgentMode` 留成未接线占位字段

### 6.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-runtime -am
```

### 6.6 Acceptance

1. `AgentModeResolver` 必须接入：
   - `AgentRunContext`
   - `WorkingContextMetadata`
   - `ContextPolicyResolver`
   - `PromptRenderContext`
   - debug/audit 输出  
2. 当前只实现 `GENERAL -> DefaultContextPolicy`。  
3. `ContextBudget` 使用细粒度配置，不回退成粗糙的 `max-working-context-tokens` 单字段设计。  
4. `WorkingContextValidator` 至少校验：
   - tool chain 合法
   - required block 不缺失
   - summary 不得夹进未闭合 tool chain
   - token budget 不超
   - current user message 在末尾  

---

## 7. 阶段 5：WorkingContextLoader / Builder / Projector / Audit

### 7.1 目标

建立 canonical WorkingContext 主入口，并形成 provider-ready projection 与审计快照。

### 7.2 Create

#### runtime/context/loader
- `WorkingContextLoader.java`
- `WorkingContextLoadCommand.java`

#### runtime/context/builder
- `WorkingContextBuilder.java`
- `ContextBlockFactory.java`

#### runtime/context/projector
- `WorkingContextProjector.java`

#### runtime/context/audit
- `WorkingContextSnapshotService.java`

### 7.3 Modify

- `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/persistence/SessionStateLoader.java`
- `vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/openai/OpenAICompatibleMessageProjector.java`

### 7.4 Delete / Replace

- 不新增 `ProviderCompatibleMessageProjector`
- runtime 只负责 `WorkingContextProjector`
- infra 继续复用 `OpenAICompatibleMessageProjector`

### 7.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-runtime -am
```

### 7.6 Acceptance

1. `WorkingContextLoader.loadForMainAgent(...)` 成为主入口。  
2. `SessionStateLoader` 逐步收窄，不再作为 P2 主入口。  
3. `WorkingContextProjector` 输出 `WorkingContextProjection`，不承担 provider 协议细节。  
4. `OpenAICompatibleMessageProjector` 仍只做 provider request 投影。  
5. 每次 build 成功后，持久化：
   - `context_json`
   - `budget_json`
   - `block_set_json`
   - `projection_json`  
6. `EvidenceRef` 不形成默认 `EvidenceBlock`；只通过 `sourceRefs / evidenceIds` 挂在 block 或审计结果上。  

---

## 8. 阶段 6：Runtime 主链路接入

### 8.1 目标

在不改主协议的前提下，把 Context Kernel 接入同步与流式主链路。

### 8.2 Modify

- `RuntimeOrchestrator.java`
- `SimpleAgentLoopEngine.java`
- `AgentRunContextFactory.java`
- `AgentRunContext.java`
- `PersistenceCoordinator.java`

### 8.3 Create

#### runtime/persistence
- `TranscriptPersistenceService.java`
- `RunEventPersistenceService.java`
- `WorkingSetRefreshService.java`
- `SessionMemoryPersistenceService.java`
- `WorkingContextAuditPersistenceService.java`

### 8.4 Delete / Replace

- 不删除 `PersistenceCoordinator`
- 但必须把内部逻辑拆协作者，避免继续膨胀

### 8.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-runtime -am
```

### 8.6 Acceptance

1. provider 调用前必须完成：
   - WorkingContext build
   - projection
   - validation
   - audit snapshot save  
2. validator 失败时：
   - 不调用 provider
   - turn -> FAILED
   - session 保持 ACTIVE
   - 写 `RUN_FAILED`
   - 仅 cache corruption 场景才 evict Redis snapshot  
3. `AgentRunContext` 可以持有 `WorkingContextBuildResult`。  
4. 主协议不新增 debug 字段。  
5. after-commit 刷新 Working Set 时，来源必须是 MySQL completed raw transcript，不得来源于 projection。  

---

## 9. 阶段 7：Internal State / Summary Task 与 Memory Write

### 9.1 目标

落地 post-turn state delta 抽取、summary 生成、evidence 绑定与其写入策略。

### 9.2 Create

#### runtime/memory
- `StateCandidateHarvester.java`
- `StateDeltaValidator.java`
- `StateDeltaMerger.java`
- `SummaryExtractor.java`
- `EvidenceBinder.java`
- `SessionMemoryCoordinator.java`
- `MemoryWritePolicy.java`

#### runtime/internal
- `InternalLlmTaskService.java`
- `InternalStateTaskService.java`
- `InternalSummaryTaskService.java`
- `InternalTaskStatus.java`
- `InternalTaskResultHandler.java`

### 9.3 Modify

- `PersistenceCoordinator.java`
- `RunEventType.java`（新增 internal task 事件类型）

### 9.4 Delete / Replace

- 不允许继续使用“internal task warning/error”这类泛称
- 统一改为显式 `RunEventType`

### 9.5 Test

执行：

```bash
mvn clean test -pl vi-agent-core-runtime -am
```

### 9.6 Acceptance

1. `StateDelta extraction`：post-turn 默认同步短超时，失败可 `DEGRADED`。  
2. `Summary generation`：默认 background，compaction 必要时允许同步。  
3. internal task 失败：
   - 不反向修改主 run / turn 状态
   - 记录 `agent_internal_llm_task.status`
   - 记录显式 `RunEventType`：
     - `INTERNAL_TASK_STARTED`
     - `INTERNAL_TASK_SUCCEEDED`
     - `INTERNAL_TASK_FAILED`
     - `INTERNAL_TASK_DEGRADED`
     - `INTERNAL_TASK_SKIPPED`  
4. `EvidenceBinder` 只负责 grounding/evidence，不生成默认 context block。  

---

## 10. 阶段 8：Summary / Compaction / Checkpoint

### 10.1 目标

实现长会话压缩和 checkpoint rebuild，但不破坏 transcript / tool chain。

### 10.2 Create

- `SessionSummaryService.java`
- `CompactionPolicy.java`
- `CompactionPlan.java`
- `CompactionResult.java`
- `RuntimeCheckpointService.java`
- `CheckpointDecision.java`

### 10.3 Modify

- `SimpleAgentLoopEngine.java`
- `RuntimeOrchestrator.java`
- `WorkingContextLoader.java`

### 10.4 Test

执行：

```bash
mvn clean test -pl vi-agent-core-runtime -am
```

### 10.5 Acceptance

1. rebuild 触发点：
   - `BEFORE_FIRST_MODEL_CALL`
   - `AFTER_TOOL_RESULT_BEFORE_NEXT_MODEL_CALL`
   - `POST_TURN`  
2. rebuild 只影响下一次 projection。  
3. 不回写已完成 transcript / tool facts。  
4. 不向未闭合 tool chain 插入 derived context。  
5. summary **默认** post-turn 刷新，但 **compaction 必要时允许 checkpoint 同步生成**。  
6. 每次 rebuild 生成新的 `contextBuildSeq` 与新的 snapshot，不覆盖旧快照。  

---

## 11. 阶段 9：Debug / Audit 查询接口

### 11.1 目标

提供独立内部接口查询 context debug 数据，不污染主聊天协议。

### 11.2 Create

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/controller/InternalContextDebugController.java`
- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ContextDebugResponse.java`
- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ContextDebugSnapshotResponse.java`
- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/application/service/ContextDebugApplicationService.java`
- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/application/assembler/ContextDebugAssembler.java`

### 11.3 接口（与设计文档 v5 对齐）

```http
GET /api/internal/debug/context-debug?runId={runId}
GET /api/internal/debug/context-debug-snapshot?snapshotId={snapshotId}&includeProjection={true|false}
```

### 11.4 Test

执行：

```bash
mvn clean test -pl vi-agent-core-app -am
```

### 11.5 Acceptance

1. `context-debug` 返回 run 对应 snapshot 列表。  
2. `context-debug-snapshot` 返回详情。  
3. `includeProjection=false` 不返回 `projection_json`。  
4. 查询接口可由配置关闭。  
5. 主聊天协议完全不变。  

---

## 12. 阶段 10：配置接入

### 12.1 Create

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/config/properties/AgentContextProperties.java`
- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/config/properties/InternalTaskProperties.java`

### 12.2 Modify

- `application.yml`
- `application-dev.yml`
- `application-test.yml`
- `application-docker.yml`

### 12.3 配置口径

必须对齐设计文档细粒度配置，不允许回退成粗粒度简化版。至少包括：

- redis ttl
- session-working-set.max-completed-turns
- context.budget.reserved-output-tokens
- context.budget.reserved-tool-loop-tokens
- context.budget.safety-margin-tokens
- context.compaction.trigger-ratio
- context.audit.persist-context-json
- context.audit.persist-projection-json
- context.debug.query-enabled
- internal-task.state-extract.timeout-ms
- internal-task.summary-extract.timeout-ms
- freshness.policies.*

### 12.4 Test

执行：

```bash
mvn clean test -pl vi-agent-core-app -am
```

### 12.5 Acceptance

1. `default-agent-mode=GENERAL` 生效。  
2. 非法 token budget 配置拒绝启动。  
3. 非法 enum 配置拒绝启动。  
4. debug 查询开关可生效。  

---

## 13. 阶段 11：测试总回归

### 13.1 必跑命令

```bash
mvn clean test -pl vi-agent-core-model
mvn clean test -pl vi-agent-core-runtime -am
mvn clean test -pl vi-agent-core-infra -am
mvn clean test -pl vi-agent-core-app -am
mvn clean test
```

### 13.2 必测场景

- 主聊天协议不变
- requestId 幂等语义不回退
- WorkingContext 校验失败不调用 provider
- SessionWorkingSet 只从 MySQL completed raw transcript reload
- Redis corruption 才 evict
- tool call / tool execution 现有状态推进不回退
- OpenAICompatibleMessageProjector 仍只做 provider 侧投影
- context debug 只能通过独立接口查询
- internal task 失败不影响主 run
- checkpoint rebuild 不破坏 tool chain

### 13.3 验收断言

1. `mvn clean test` 全量通过。  
2. sync / stream / tool / transcript 主链路无回退。  
3. context debug 查询可用。  
4. state/summary/evidence/context audit 可持久化并可查询。  

---

## 14. 阶段 12：文档治理更新

### 14.1 Modify

- `ARCHITECTURE.md`
- `CODE_REVIEW.md`
- `vi-agent-core-runtime/AGENTS.md`
- `vi-agent-core-model/AGENTS.md`
- `vi-agent-core-infra/AGENTS.md`
- `vi-agent-core-app/AGENTS.md`

### 14.2 Acceptance

1. Context Kernel 边界写回文档。  
2. runtime / model / infra / app 包结构与治理规则同步更新。  
3. 新旧命名冲突、旧语义类、删除清单同步反映到文档。  

---

## 15. 推荐提交拆分

### Commit 1

```bash
feat(model): add p2 context kernel and memory domain models
```

### Commit 2

```bash
feat(infra): add p2 context kernel mysql persistence
```

### Commit 3

```bash
feat(infra): replace old session context redis cache with working set/state/summary snapshots
```

### Commit 4

```bash
feat(runtime): add context policy budget validator and working context builder
```

### Commit 5

```bash
feat(runtime): wire working context into orchestrator and loop flow
```

### Commit 6

```bash
feat(runtime): add internal state summary tasks and memory coordinator
```

### Commit 7

```bash
feat(runtime): add checkpoint compaction and summary refresh
```

### Commit 8

```bash
feat(app): add internal context debug query api
```

### Commit 9

```bash
test: add p2 context kernel regression coverage
```

### Commit 10

```bash
docs: update p2 context kernel governance
```

---

## 16. 直接交给实现代理的执行口径

1. 严格按阶段顺序执行，不跳阶段。  
2. 每阶段完成后先跑该阶段最小测试命令，再跑上一层聚合测试。  
3. 若旧类被替代，必须同步迁移调用方并删除旧实现，不允许留下双轨主链路。  
4. 若某阶段发现设计与 `system-design-P2-v5.md` 冲突，以设计文档为准，先更新 implementation plan 再改代码。  
5. 所有 P2 新增对象都必须有中文字段注释、枚举说明、必要测试。  
