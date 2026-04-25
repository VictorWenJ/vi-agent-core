# ARCHITECTURE.md

> 更新日期：2026-04-19

## 1. 文档定位

本文件定义 `vi-agent-core` 的总体架构、分层职责、依赖方向与核心调用关系。

本文件只负责回答：
- 系统总体分层是什么
- 各层分别负责什么
- 各层之间如何依赖
- 主链路如何调用
- 结构拆分与模块归位方案是什么

本文件不负责：
- 仓库级协作规则与开发约束（见 `AGENTS.md`）
- 执行清单类内容（见 `PROJECT_PLAN.md`，当前已清空）
- 具体包结构、类职责与局部规则（见各模块 `AGENTS.md`）
- 代码审查细节（见 `CODE_REVIEW.md`）

---

## 2. 架构定位

`vi-agent-core` 旨在构建一个**逻辑分布式、职责可分、单体先行落地**的 Agent Runtime Framework。系统仍然是单体应用，但内部必须按稳定的逻辑边界设计，不能退化为“大 chat service”。

核心借鉴思想：
- **Claude Code / Agent SDK**：Agent Loop、Tool Use、Runtime Kernel、受控扩展点
- **OpenClaw**：Gateway、Context Engine、Session / Streaming / Provider 边界
- **传统企业后端经验**：中心编排、依赖倒置、注册表扩展、配置驱动、可测试性优先

技术基线如下：
- 主模型提供方以 **DeepSeek** 为准
- 工具能力以 **`@AgentTool` + `ToolRegistry` + mock 工具** 跑通闭环
- 会话热态缓存以 **Redis** 为准，事实源正式引入 **MySQL**
- Prompt Template 以 **Markdown** 作为存储形态，但 Repository 必须抽象
- 流式输出采用 **provider streaming → runtime event → app SSE** 的分层边界

### 2.1 Maven 模块落地视图

代码按 Maven 多模块落地：

- `vi-agent-core-app`：应用入口与装配层（唯一运行模块）
- `vi-agent-core-runtime`：核心运行时与主链路编排层
- `vi-agent-core-infra`：基础设施实现层
- `vi-agent-core-model`：内部运行时模型层与跨层共享契约层
- `vi-agent-core-common`：轻量公共能力层

标准依赖方向固定为：
- `common`
- `model -> common`
- `runtime -> model + common`
- `infra -> model + common`
- `app -> runtime + infra + model + common`

架构约束如下：
- `runtime` 与 `infra` 都只依赖 `model/common`
- `model.port` 只保留真正接口
- `ModelResponse`、`ModelUsage` 归档到 `model.provider`
- `runtime.observability` 承载运行时指标抽象
- `runtime.memory` 承载内部 LLM 任务相关协作接口
- transcript 的 Redis 相关实现统一归档到 `infra/persistence/cache/transcript/*`
- `LlmProvider` 删除，统一收口到 `LlmGateway`

---

## 3. 总体分层（逻辑视图）

### 3.1 接入层（`app/api`）
- **职责**：暴露 REST API、承接 SSE 流式连接、做请求绑定与响应协议输出。
- **当前代码落点**：`ChatController`、`ChatStreamController`、`HealthController`、`GlobalExceptionHandler`。
- **约束**：
  - 只做接入、参数绑定、协议适配与异常出口协作。
  - 不承载 Runtime 主循环。
  - 不直接处理 provider delta / tool fragment 解析。
  - 不把同步阻塞逻辑直接跑在 WebFlux 事件线程上。

### 3.2 应用入口层（`app/application` + `app/config`）
- **职责**：作为 Facade 与顶层装配层，连接 WebFlux 与 Runtime / Infra。
- **当前代码落点**：`ChatApplicationService`、`ChatStreamApplicationService`、`RuntimeCoreConfig`、`ProviderConfig`、`PersistenceConfig`、`ToolConfig`。
- **约束**：
  - Application 层只做转发、轻量映射、调度隔离、SSE 适配。
  - 配置层只做 Bean 装配，不做业务编排。
  - 预算参数、Prompt 配置、持久化配置统一在 `app/config` 加载。

### 3.3 核心运行时层（`runtime`）
- **职责**：统一 run 生命周期、Context 组装、Agent Loop、Tool 协调、Runtime Event 产生。
- **当前代码落点**：
  - `RuntimeOrchestrator`
  - `AgentLoopEngine`、`SimpleAgentLoopEngine`
  - `PromptManager` / `PromptRenderer` / `ContextAssembler` / `WorkingContextLoader`
  - `ToolGateway`、`ToolRegistry`
  - `RuntimeEvent`、`RuntimeEventEmitter`
  - `ConversationMemoryService`、`InternalLlmTaskService*`
  - `RuntimeMetricsCollector`
- **关键边界**：
  - `RuntimeOrchestrator` 是唯一主链路编排中心，但只做 run-level orchestration（运行级编排）。
  - Prompt Engine 在 runtime 内部执行；S0 Runtime Instruction 必须先渲染，再交给 `ContextAssembler` 组装 Working Context。
  - `AgentLoopEngine` 是真正的 loop owner（循环拥有者）。
  - `LlmGateway` 返回 `ModelResponse`，正式 `AssistantMessage` 由 loop 内部统一构建。
  - runtime 只产生内部事件，不负责 SSE / Reactor 协议适配。
  - runtime 不直接操作 Redis / MySQL repository，不直接解析厂商协议对象。

### 3.4 基础设施层（`infra`）
- **职责**：提供 Provider、Persistence、Mock Integration 的具体实现。
- **当前代码落点**：provider、persistence、integration mock。
- **关键边界**：
  - infra 只做实现，不做主导。
  - provider 负责厂商协议请求/响应/流解析。
  - persistence 负责 Redis / MySQL 读写与映射。
  - infra 不反向依赖 runtime，也不接管 run 生命周期。

### 3.5 模型层（`model`）
- **职责**：承载内部运行时模型与跨层共享契约。
- **关键边界**：
  - `model.port` 只放接口。
  - `message / tool / transcript / runtime / memory / context / prompt / provider` 按对象语义分层。
  - 核心对象优先不可变，对外集合优先只读。

### 3.6 公共层（`common`）
- **职责**：异常、错误码、ID、通用无状态工具。
- **关键边界**：
  - 不承载强业务语义契约。
  - 不承载运行时编排。
  - 只提供最小、稳定、跨模块可复用的基础能力。

---

## 4. `RuntimeOrchestrator` 结构拆分方案

`RuntimeOrchestrator` 继续保留为唯一主编排中心，但要回到 run-level orchestration（运行级编排）边界。采用以下拆分：

### 4.1 `RuntimeOrchestrator` 保留的职责
- 打开 / 关闭一次 run 生命周期。
- 协调 transcript、working context、loop、持久化、事件发射的顺序。
- 收敛成功 / 失败出口。

### 4.2 新增协作者与职责

#### `RunContextFactory`
负责：
- 创建 `AgentRunContext`
- 补齐 `runId / turnId / messageId`
- 组装运行时所需基础对象

#### `TranscriptLifecycleManager`
负责：
- 加载 transcript
- 初始化 transcript
- 追加 user / assistant / tool 消息
- 统一协调整个 turn 的 transcript 生命周期

#### `WorkingContextLoader`
负责：
- 读取 transcript / state / summary（按需扩展）
- 调 `ContextAssembler` 构造 working context

#### `RuntimeEventEmitter`
负责：
- 发射 run started / tool started / tool finished / run completed / run failed 等运行时事件
- 为 sync / stream 两条链路提供统一运行时事件出口

#### `MdcScope`（或 `RuntimeMdcManager`）
负责：
- 打开 MDC 上下文
- 写入 traceId / runId / sessionId / turnId 等字段
- 在 run 结束时统一清理

#### `TurnArtifactPersistenceCoordinator`
负责：
- 收口一次 turn 结束后的持久化
- 统一调用 `ConversationMemoryService.persistTurnArtifacts(...)`
- 统一处理 transcript / summary / state / evidence 的持久化异常出口

### 4.3 `persistTurnArtifacts(...)` 的架构边界
`persistTurnArtifacts(...)` 仍然保留为一个总流程入口，但内部必须拆成明确步骤，而不是在一个大方法里继续堆逻辑。

标准结构：
- `upsertSessionMetadata(...)`
- `persistTranscript(...)`
- `persistSummaryIfPresent(...)`
- `persistStateIfPresent(...)`
- `persistEvidenceIfPresent(...)`

这不是把一个流程拆成多个分散 service，而是把总流程入口收成“总控 + 分步”的可维护结构。

---

## 5. Provider、Registry、Persistence 的职责收口

### 5.1 Provider 收口
- `LlmProvider` 删除，统一收口到 `model.port.LlmGateway`。
- `OpenAICompatibleChatProvider` 继续作为公共协议复用基类，但必须拆出：
  - request mapper
  - response mapper
  - stream parser
  - tool call assembler
- provider 负责厂商协议差异，不把协议对象泄漏到 runtime / app。

### 5.2 Tool Registry 收口
- `ToolRegistry` 负责注册与查找，不再长期同时承担扫描、校验、反射执行等过宽职责。
- `ToolGateway.execute(...)` 作为统一工具执行边界。
- 反射调用与注册扫描可继续下沉为协作者。

### 5.3 Persistence 收口
- Transcript / State / Summary 都应遵循 Redis（热态层）+ MySQL（事实源）双层治理。
- transcript Redis 相关实现统一进入 `infra/persistence/cache/transcript/*`。
- Repository 对外单实体查询契约统一为 `Optional<T>`，由 repository 边界表达缺失语义，runtime / app 调用方不依赖 `null`。
- MySQL 持久化默认采用 MyBatis-Plus Lambda Wrapper 链式函数写法表达查询、更新、删除条件（`Wrappers.lambdaQuery(...)` / `Wrappers.lambdaUpdate(...)`）。
- `insert(entity)` 作为标准新增写法允许保留。
- 只有 SQL 复杂到链式写法明显不可读时，才允许 XML / 字符串 SQL / 注解 SQL。
- `MysqlPromptTemplateRepository`等误导性仓储类不保留。
- `MemoryJsonMapper` 要逐步按对象类型拆分，避免成为泛化 JSON 大杂烩。

---

## 6. 主调用链

### 6.1 同步链路
`Controller -> ApplicationService -> RuntimeOrchestrator -> WorkingContextLoader(PromptManager 渲染 S0) -> ContextAssembler -> RunContextFactory -> AgentLoopEngine -> ToolGateway.execute(...) -> TranscriptLifecycleManager -> TurnArtifactPersistenceCoordinator -> Response`

### 6.2 流式链路
`Controller -> StreamApplicationService -> RuntimeOrchestrator -> AgentLoopEngine(stream) -> RuntimeEventEmitter -> app SSE Adapter -> Response`

### 6.3 Transcript / State / Summary 治理口径
- Transcript：原始事实记录。
- State：当前会话结构化快照。
- Summary：老历史压缩结果。
- Redis：热态缓存。
- MySQL：事实源。

---

## 7. 架构约束

- `runtime` 与 `infra` 只依赖 `model` / `common`。
- `model.port` 只保留真正接口。
- `RuntimeOrchestrator` 是唯一主链路编排中心，但应通过协作者拆分控制复杂度。
- provider 只做协议请求、响应与流解析；persistence 只做存储实现；app 只做接入与装配。
- transcript、state、summary 等数据对象必须按职责分层治理。

## 8. 一句话总结

`ARCHITECTURE.md` 当前的职责，是把“模块应该怎么依赖、对象应该放在哪、运行时应该怎么拆、Transcript / State / Summary 应该怎么治理”这些结构性问题定死，而不是去承载仓库级通用规范或当前执行清单。
