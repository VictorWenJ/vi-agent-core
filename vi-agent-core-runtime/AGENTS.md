# AGENTS.md

> 更新日期：2026-04-19

## 1. 文档定位

本文件定义 `vi-agent-core-runtime` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `runtime` 模块负责什么
- `runtime` 模块不负责什么
- `runtime` 模块内包结构如何约定
- 在 `runtime` 模块开发时必须遵守哪些局部规则
- `runtime` 模块测试与依赖应如何建设

执行 `runtime` 模块相关任务前，必须先读根目录四文档与本文件。

---

## 2. 模块定位

`vi-agent-core-runtime` 是整个 `vi-agent-core` 系统的**核心运行时层**，也是 Agent 执行逻辑的唯一编排中心。

标准依赖链位置：
`runtime -> model + common`

因此，`runtime` 模块是：
- **唯一主链路编排模块**
- **Agent Loop 运行时模块**
- **Context / Tool / Event 协调中心**
- **运行时记忆接口层**
- **运行时指标抽象层**

但 `runtime` 模块不是：
- Web 接入层
- Provider 实现层
- Persistence 实现层
- 公共工具类模块
- 未启用占位类寄存层

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容
- 主链路编排：`RuntimeOrchestrator` 与其协作者
- Agent Loop 执行：`AgentLoopEngine`、`SimpleAgentLoopEngine`
- Prompt 工程：`PromptManager`、`PromptRenderer`、`PromptTemplateRepository`
- 上下文装配：`ContextAssembler`、`WorkingContextLoader`
- 工具运行时边界：`ToolGateway`、`ToolRegistry`、`ToolExecutor`
- 流式事件：`RuntimeEvent`、`RuntimeEventEmitter`
- 运行时记忆接口：`ConversationMemoryService`、`InternalLlmTaskService*`
- 运行时指标抽象：`RuntimeMetricsCollector`
- 运行结果对象：`AgentExecutionResult`

### 3.2 本模块明确不负责的内容
以下内容禁止写入 `runtime` 模块：
- Controller / HTTP / SSE 协议处理
- DeepSeek / OpenAI / Redis / MySQL 等基础设施实现
- Redis / MySQL repository / mapper / entity
- Web DTO
- 通用 JSON / 校验工具
- 未启用的 Skill / Delegation / Subagent 主类

### 3.3 `RuntimeOrchestrator` 协作者拆分
`RuntimeOrchestrator` 仍然是唯一主链路编排中心，但必须回到 run-level orchestration（运行级编排）边界。建议并要求当前按以下协作者拆分：
- `RunContextFactory`
- `TranscriptLifecycleManager`
- `WorkingContextLoader`
- `RuntimeEventEmitter`
- `MdcScope` / `RuntimeMdcManager`
- `TurnArtifactPersistenceCoordinator`

### 3.4 `persistTurnArtifacts(...)` 规则
`persistTurnArtifacts(...)` 可以保留为总流程入口，但必须拆成清晰步骤，而不是继续膨胀为大方法。最少拆分为：
- `upsertSessionMetadata(...)`
- `persistTranscript(...)`
- `persistSummaryIfPresent(...)`
- `persistStateIfPresent(...)`
- `persistEvidenceIfPresent(...)`

---

## 4. 模块内包结构约定

当前 `runtime` 模块包结构固定为：

```text
com.vi.agent.core.runtime
├── context/
├── engine/
├── event/
├── memory/
├── observability/
├── orchestrator/
├── prompt/
├── result/
└── tool/
```

### 4.1 包规则
- 包名必须全小写。
- 包位置必须表达真实职责。
- `runtime.memory` 承载内部 LLM 任务相关接口。
- `runtime.observability` 承载运行时指标抽象。
- `runtime.prompt` 承载 Prompt 抽象与渲染，不允许把 prompt 拼装逻辑散落到 orchestrator / app。
- 不允许把 provider 协议对象、Redis/MySQL 访问实现、Web DTO 留在 `runtime`。

### 4.2 工具边界规则
- `ToolGateway` 统一暴露 `execute(...)`；不再使用 `route(...)`。
- `ToolRegistry` 不应长期同时承担注册、扫描、校验、反射执行等全部职责。
- mock 工具仍必须通过统一注册与统一执行链路进入 runtime。

### 4.3 Loop 边界规则
- `AgentLoopEngine` 是真正的 loop owner（循环拥有者）。
- `LlmGateway` 返回 `ModelResponse`；`AssistantMessage` 由 loop 内部统一构建。
- `WorkingContextLoader` 必须先渲染 S0（`runtime_instruction` + `response_guardrails`），再交给 `ContextAssembler`。
- runtime 不负责 provider 协议解析，不负责 SSE 协议输出。

---

## 5. 模块局部开发约束

### 5.1 依赖与 POM 规则
- `runtime` 只允许依赖 `model` + `common`。
- 严禁 `runtime -> infra`。
- 模块 POM 只保留直接依赖，不重复写受父 POM 管理的版本。

### 5.2 类设计与方法设计规则
- 超过 300 行或承担 4 类以上职责的类要主动评估拆分。
- 单方法参数超过 5 个时，必须评估 Command / Builder。
- 静态工厂超过 6 个参数时，默认改 Builder。
- 总流程方法保留可以，但内部必须步骤化。

### 5.3 可变性规则
- `AgentRunContext` 禁止继续使用类级别 `@Setter`。
- 对外暴露的集合必须返回只读视图。
- 运行时关键对象的状态变化优先通过显式意图方法控制。

### 5.4 工具优先规则
- 字符串判空优先 `StringUtils`。
- 集合判空优先 `CollectionUtils`。
- 避免在 runtime 中重复手写判空模板逻辑。

### 5.5 占位类规则
- 当前不保留 Skill / Delegation / Subagent 主类占位代码。
- 预留能力如确需保留，必须进入专门预留包并有文档依据。

---

## 6. 测试要求
- `RuntimeOrchestrator` / `AgentLoopEngine` 改动必须覆盖单轮、工具回填、多轮结束、最大迭代超限、流式事件行为。
- `ToolRegistry` / `ToolGateway` 改动必须覆盖注册、查找、执行、异常路径。
- `persistTurnArtifacts(...)` 与 transcript 双层治理改动必须覆盖行为测试。

---

## 7. 一句话总结

`runtime` 模块的核心要求是：保持它作为唯一运行时编排层的纯度，同时通过协作者拆分把 orchestrator 收回到“总控而不杂糅”的边界。

## 8. 会话解析边界补充

- Runtime 入口先做 `conversationId + sessionId` 解析，再创建 `runId/turnId/traceId` 与 `AgentRunContext`。
- 会话解析由 `SessionResolutionService` 统一承担，不允许散落在 controller/app service。
- `TranscriptLifecycleManager` 按解析后的 session 加载 transcript，并确保 conversationId 与解析结果一致。
- `RuntimeEvent` 必须携带 `requestId`；stream delta 必须稳定携带同一 `messageId`。
- `traceId` 仅用于 runtime 内部日志、MDC 与事件链路，不向外部 DTO 透出。
