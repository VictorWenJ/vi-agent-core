# ARCHITECTURE.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core` 的总体架构、分层职责、依赖方向与核心调用关系。

本文件只负责回答：

- 系统总体分层是什么
- 各层分别负责什么
- 各层之间如何依赖
- 主链路如何调用
- 结构拆分与模块归位原则是什么
- 长期架构能力层如何演进

本文件不负责：

- 仓库级协作规则与通用开发约束（见 `AGENTS.md`）
- 阶段详细设计、阶段开发计划、阶段专项测试验收、Codex prompt、阶段收口记录（见 `execution-phase/{phase-name}/`）
- 具体包结构、类职责与局部规则（见各模块 `AGENTS.md`）
- 代码审查细节、拒绝项与测试门禁（见 `CODE_REVIEW.md`）
- 阶段状态、当前阶段索引与高层路线图（见 `PROJECT_PLAN.md`）

根目录 `ARCHITECTURE.md` 只承载长期稳定的架构原则。  
阶段性详细设计必须下沉到：

```text
execution-phase/{phase-name}/design.md
```

---

## 2. 架构定位

`vi-agent-core` 旨在构建一个**逻辑分布式、职责可分、单体先行落地**的轻量 Agent Runtime Framework / Agent Runtime Kernel。

系统当前仍然是单体应用，但内部必须按稳定的逻辑边界设计，不能退化为“大 chat service”。

核心借鉴思想：

- **Claude Code / Agent SDK**：Agent Loop、Tool Use、Runtime Kernel、受控扩展点
- **OpenClaw**：Gateway、Context Engine、Session / Streaming / Provider 边界
- **传统企业后端经验**：中心编排、依赖倒置、注册表扩展、配置驱动、可测试性优先

长期目标能力层包括：

- Model Provider Layer
- Context Engineering
- Prompt Engineering
- Memory Engineering
- Tool Runtime
- Workflow / Graph Orchestration
- Storage / Cache
- Safety / Permission
- Observability / Audit
- Evaluation / Testing
- RAG / Knowledge
- Checkpoint / Resume

当前项目原则：

- 先以单体工程落地稳定边界；
- 再逐步把能力抽象为可注册、可替换、可审计的 runtime kernel 组件；
- 不提前把项目做成完整 LangGraph clone；
- 不为未来能力牺牲当前阶段的清晰边界；
- 新能力必须按阶段文档进入 `execution-phase/{phase-name}/`，不能直接污染根目录架构文档。

技术基线如下：

- 主模型提供方当前以 DeepSeek API 形态为主要开发验证路径；
- Provider 能力统一通过 `model.port` 中的 gateway / port 契约进入 runtime；
- 工具能力以 `@AgentTool`、Tool Registry、Tool Gateway 逐步收口；
- 会话热态缓存以 Redis 为 snapshot cache；
- 会话、状态、摘要、证据等长期事实源以 MySQL 为准；
- Prompt Engineering 以可版本化、可注册、可渲染、可审计的治理结构为长期方向；
- Prompt Template 的具体存储形态属于阶段设计内容，不直接写死在根架构文档中；
- 流式输出采用 provider streaming → runtime event → app SSE 的分层边界。

---

## 3. Maven 模块落地视图

代码按 Maven 多模块落地：

- `vi-agent-core-app`：应用入口与装配层，唯一可运行模块
- `vi-agent-core-runtime`：核心运行时与主链路编排层
- `vi-agent-core-infra`：基础设施实现层
- `vi-agent-core-model`：内部运行时模型层与跨层共享契约层
- `vi-agent-core-common`：轻量公共能力层

标准依赖方向固定为：

```text
common
model -> common
runtime -> model + common
infra -> model + common
app -> runtime + infra + model + common
```

架构约束如下：

- `runtime` 与 `infra` 都只依赖 `model/common`；
- `infra` 不得反向依赖 `runtime`；
- `runtime` 不得直接依赖 `infra`；
- `app` 是唯一允许装配 runtime 与 infra 的上层模块；
- `model.port` 只保留真正跨层接口；
- `model.provider`、`model.context`、`model.memory`、`model.prompt`、`model.runtime` 等按真实对象语义拆分；
- `runtime` 承载运行时编排、上下文工程、提示词治理、记忆协调、工具协调、运行时事件与指标抽象；
- `infra` 承载 provider、persistence、cache、external repository、mock integration 等具体实现；
- `common` 只承载异常、错误码、ID、无状态工具等轻量基础能力。

---

## 4. 总体分层（逻辑视图）

### 4.1 接入层（`app/api`）

职责：

- 暴露 REST API；
- 承接 SSE 流式连接；
- 做请求绑定；
- 做响应协议输出；
- 将 HTTP / SSE 协议语义与 runtime 内部事件语义隔离。

当前典型代码落点：

- `ChatController`
- `ChatStreamController`
- `HealthController`
- `GlobalExceptionHandler`

约束：

- 只做接入、参数绑定、协议适配与异常出口协作；
- 不承载 Runtime 主循环；
- 不直接处理 provider delta / tool fragment 解析；
- 不直接访问 MySQL / Redis repository；
- 不把同步阻塞逻辑直接跑在 WebFlux 事件线程上；
- 不把 debug / audit / internal task 信息混入主 chat response 或 stream event。

---

### 4.2 应用入口层（`app/application` + `app/config`）

职责：

- 作为 Facade 与顶层装配层；
- 连接 WebFlux 与 Runtime / Infra；
- 管理顶层 Bean 装配；
- 做必要的应用层轻量映射与调度隔离。

当前典型代码落点：

- `ChatApplicationService`
- `ChatStreamApplicationService`
- `RuntimeCoreConfig`
- `ProviderConfig`
- `PersistenceConfig`
- `ToolConfig`

约束：

- Application 层只做转发、轻量映射、调度隔离、SSE 适配；
- 配置层只做 Bean 装配，不做业务编排；
- 预算参数、Provider 配置、持久化配置、工具配置统一在 `app/config` 加载；
- 不在 app 层承载 runtime 领域规则；
- 不在 app 层新增阶段性 debug API，除非当前阶段文档明确要求。

---

### 4.3 核心运行时层（`runtime`）

职责：

- 统一 run 生命周期；
- 组织 Working Context；
- 执行 Agent Loop；
- 协调 Tool 调用；
- 协调 post-turn memory update；
- 产生 Runtime Event；
- 承载 Prompt Engineering 的运行时治理能力；
- 承载 Observability / Audit 的运行时抽象能力。

当前核心能力方向：

- `RuntimeOrchestrator`
- Agent Loop
- Working Context 构建与投影
- Prompt Registry / Prompt Renderer / Prompt Governance
- Tool Gateway / Tool Registry
- Runtime Event / Runtime Event Emitter
- Session Memory Coordinator
- Internal LLM Task 协调
- Runtime Metrics / Audit 抽象

关键边界：

- `RuntimeOrchestrator` 是唯一主链路编排中心，但只做 run-level orchestration（运行级编排）；
- Agent Loop 是真正的 loop owner（循环拥有者）；
- Provider 调用只能通过 `model.port` 中的 gateway / port 契约完成；
- runtime 只依赖 `model/common`，不得直接依赖 `infra`；
- runtime 不直接操作 Redis / MySQL repository；
- runtime 不直接解析厂商协议对象；
- runtime 只产生内部事件，不负责 SSE / Reactor 协议适配；
- prompt governance 只负责模板注册、版本、变量、渲染、输出 schema、审计元信息等运行时治理，不直接承担 provider 调用；
- post-turn memory update 失败不能反向影响主聊天成功返回，除非阶段文档明确改变该规则。

---

### 4.4 基础设施层（`infra`）

职责：

- 提供 Provider、Persistence、Cache、External Repository、Mock Integration 的具体实现；
- 处理外部系统协议差异；
- 处理 MySQL / Redis / 文件 / 外部 API 的具体读写；
- 将外部对象映射为 `model` 中的跨层契约对象。

当前典型代码落点：

- provider
- persistence
- cache
- integration mock
- repository implementation

关键边界：

- infra 只做实现，不做主导；
- provider 负责厂商协议请求、响应、流解析；
- persistence 负责 Redis / MySQL 读写与映射；
- infra 不反向依赖 runtime；
- infra 不接管 run 生命周期；
- infra 不编排 Agent Loop；
- infra 不决定 memory update 业务策略；
- infra 中的 repository implementation 必须服务于 `model.port` 或清晰的基础设施边界。

---

### 4.5 模型层（`model`）

职责：

- 承载内部运行时模型；
- 承载跨层共享契约；
- 承载领域值对象、枚举、command、result、snapshot、patch、ref 等稳定对象；
- 承载 `model.port` 跨层接口。

关键边界：

- `model.port` 只放接口；
- `message / tool / transcript / runtime / memory / context / prompt / provider` 按对象语义分层；
- 核心对象优先不可变；
- 对外集合优先只读；
- 不依赖 `runtime` / `infra` / `app`；
- 不承载具体持久化实现；
- 不承载 provider 厂商协议实现；
- 不承载 Spring 装配逻辑。

---

### 4.6 公共层（`common`）

职责：

- 异常；
- 错误码；
- ID；
- 通用无状态工具；
- 少量全局基础常量。

关键边界：

- 不承载强业务语义契约；
- 不承载运行时编排；
- 不承载领域对象；
- 不成为“无处安放代码”的垃圾场；
- 只提供最小、稳定、跨模块可复用的基础能力。

---

## 5. Agent Runtime Kernel 能力分层

`vi-agent-core` 的长期目标是 Agent Runtime Kernel，不是单一 chat backend。

长期能力分层如下：

### 5.1 Model Provider Layer

职责：

- 屏蔽不同模型厂商协议差异；
- 提供统一 chat / stream / tool-call 入口；
- 返回统一 `model.provider` 契约对象；
- 不向 runtime / app 泄漏厂商协议对象。

边界：

- provider 实现属于 `infra`；
- provider 契约属于 `model.port` 与 `model.provider`；
- runtime 只调用抽象 gateway / port。

---

### 5.2 Context Engineering

职责：

- 从 transcript、state、summary、runtime instruction、current user message 等来源构建 Working Context；
- 执行预算控制、上下文裁剪、block 分类、projection；
- 生成 provider 调用所需的模型消息；
- 防止 synthetic context message 回写 transcript。

边界：

- Working Context 不等于 Transcript；
- projection 只用于模型调用；
- synthetic runtime/state/summary message 不持久化为 raw transcript；
- `SessionWorkingSet` 只能来自 MySQL completed raw transcript，不得从 projection / workingMessages 回刷。

---

### 5.3 Prompt Engineering

职责：

- 管理 prompt template；
- 管理 prompt version；
- 管理 prompt variable；
- 管理 prompt output schema；
- 执行 prompt render；
- 维护 prompt schema 与 parser allowlist 的一致性；
- 提供 prompt audit metadata。

边界：

- Prompt Engineering 不是 Provider 调用层；
- Prompt Engineering 不直接写 MySQL / Redis；
- Prompt Engineering 不污染主 chat response / stream event；
- Prompt Engineering 的阶段详细设计进入对应 `execution-phase/{phase-name}/design.md`；
- 新增 prompt governance 对象必须先在阶段设计文档中声明契约，再实现代码。

---

### 5.4 Memory Engineering

职责：

- 维护会话级状态快照；
- 维护对话摘要；
- 维护 working set；
- 协调 post-turn memory update；
- 记录 internal memory task；
- 绑定 evidence；
- 支持未来 long-term memory、RAG、checkpoint 扩展。

边界：

- Memory 不等于 Message；
- state / summary / evidence 是 post-turn 派生 memory；
- memory update 失败不能影响主聊天成功返回；
- internal task / audit / evidence 不暴露到主 chat response；
- MySQL 是事实源，Redis 是 snapshot cache。

---

### 5.5 Tool Runtime

职责：

- 管理工具注册；
- 管理工具参数 schema；
- 管理工具执行；
- 管理工具结果；
- 为未来 Web / Browser / Document / Computer / Mobile tools 打基础。

边界：

- ToolExecution 不等于 ToolMessage；
- tool message 只是模型协议的一种表达；
- 工具执行记录、审计、权限、结果摘要必须有独立模型；
- 当前阶段不得因 Tool Runtime 未来规划而提前扩展主协议。

---

### 5.6 Workflow / Graph Orchestration

职责：

- 在未来阶段将现有 runtime 能力包装为 node / step / route；
- 支持更复杂的 agent workflow；
- 支持后续工具链、记忆链、RAG 链、审批链组合。

边界：

- Graph Workflow 是中层编排，不是底层能力；
- Graph Workflow 不替代 Provider、Context、Prompt、Memory、Tool、Storage 等能力层；
- 当前 P2 不提前推进 Graph Workflow；
- 未来 P3 先做项目专用 graph workflow kernel，不做完整 LangGraph clone。

---

### 5.7 Storage / Cache

职责：

- 管理 MySQL 事实源；
- 管理 Redis snapshot cache；
- 支持 transcript、state、summary、evidence、internal task 等对象的读写；
- 支持未来 checkpoint、long-term memory、RAG metadata 等扩展。

边界：

- MySQL 是事实源；
- Redis 是 snapshot cache；
- cache miss 不能改变事实源语义；
- repository 对外单实体查询契约统一优先使用 `Optional<T>`；
- persistence 不做 runtime 主流程编排。

---

### 5.8 Safety / Permission

职责：

- 管理工具权限；
- 管理高风险操作审批；
- 管理 future human approval；
- 管理模型输出和工具执行的安全边界。

边界：

- 当前阶段不做大规模权限系统；
- 未来能力必须以 node / tool / policy / service 方式接入；
- 不允许用散落 if/else 在主链路中堆安全策略。

---

### 5.9 Observability / Audit

职责：

- 管理 traceId / runId / sessionId / conversationId / turnId 等链路标识；
- 管理 runtime event；
- 管理 internal task audit；
- 管理 evidence audit；
- 支持未来 debug 查询接口。

边界：

- audit / debug 不污染主 chat response；
- internal task / evidence 不通过主协议暴露；
- debug API 必须走独立查询接口；
- 当前阶段未明确要求时，不提前新增 debug API。

---

### 5.10 Evaluation / Testing

职责：

- 管理单元测试；
- 管理契约测试；
- 管理回归测试；
- 管理 prompt contract tests；
- 管理未来 RAG eval / tool eval / workflow eval。

边界：

- 旧测试与新架构冲突时，更新或删除旧测试；
- 不为了旧测试保留过时逻辑；
- 阶段专用测试标准写入对应 `execution-phase/{phase-name}/test.md`；
- 通用测试门禁写入 `CODE_REVIEW.md`。

---

### 5.11 RAG / Knowledge

职责：

- 管理文档摄取；
- 管理 chunk；
- 管理 embedding；
- 管理 vector store；
- 管理 retrieval；
- 管理 reranking；
- 管理 knowledge audit。

边界：

- 当前 P2 不推进 RAG；
- RAG / Knowledge 是独立能力层，不应混入 Context Engineering 当前主链路；
- 未来进入 RAG 阶段时必须单独建立阶段设计文档。

---

### 5.12 Checkpoint / Resume

职责：

- 管理 run checkpoint；
- 管理 workflow resume；
- 管理失败恢复；
- 管理 human approval 后续恢复点。

边界：

- 当前 P2 不做 checkpoint / resume；
- 未来应以 workflow state / node state / task state 方式扩展；
- 不应提前污染当前 runtime 主链路。

---

## 6. `RuntimeOrchestrator` 结构边界

`RuntimeOrchestrator` 继续保留为唯一主编排中心，但要保持 run-level orchestration（运行级编排）边界。

### 6.1 `RuntimeOrchestrator` 保留的职责

- 打开 / 关闭一次 run 生命周期；
- 协调 transcript、working context、loop、持久化、事件发射的顺序；
- 收敛成功 / 失败出口；
- 保证同步链路与流式链路共享统一 runtime 语义；
- 保证 post-turn memory update 的失败边界不破坏主聊天成功返回。

### 6.2 `RuntimeOrchestrator` 不应承担的职责

- 不直接拼接 provider messages；
- 不直接访问 MySQL / Redis repository；
- 不直接解析厂商协议；
- 不直接执行工具反射调用；
- 不直接承担 prompt 模板加载细节；
- 不直接承担 parser allowlist 细节；
- 不直接把 debug / audit 信息塞入主协议；
- 不把所有子流程继续堆成一个超大方法。

### 6.3 运行时协作者职责原则

运行时协作者应按职责拆分，包括但不限于：

- run context 创建；
- transcript 生命周期管理；
- working context 构建；
- prompt 渲染；
- agent loop 执行；
- tool 执行协调；
- turn artifact 持久化协调；
- post-turn memory update 协调；
- runtime event 发射；
- MDC / trace 上下文管理。

协作者拆分的目标不是制造类数量，而是控制主编排复杂度，保持每个类职责可测试、可替换、可审计。

---

## 7. Provider、Registry、Persistence 的职责收口

### 7.1 Provider 收口

- Provider 统一通过 `model.port` 中的 gateway / port 契约进入 runtime；
- provider 实现放在 `infra`；
- provider 负责厂商协议差异，不把协议对象泄漏到 runtime / app；
- provider request mapper、response mapper、stream parser、tool call assembler 应按职责拆分；
- provider 不做 Runtime 主流程编排；
- provider 不做 transcript / memory / audit 持久化。

---

### 7.2 Tool Registry 收口

- `ToolRegistry` 负责注册与查找，不应长期同时承担扫描、校验、反射执行等过宽职责；
- `ToolGateway.execute(...)` 作为统一工具执行边界；
- 反射调用、注册扫描、schema 校验、权限校验应逐步下沉为独立协作者；
- 工具执行与模型协议中的 tool message 必须保持语义分离。

---

### 7.3 Persistence 收口

- Transcript / State / Summary / Evidence / Internal Task 都应遵循 MySQL 事实源 + Redis snapshot cache 的双层治理；
- transcript Redis 相关实现统一进入 `infra/persistence/cache/transcript/*`；
- Repository 对外单实体查询契约统一为 `Optional<T>`，由 repository 边界表达缺失语义，runtime / app 调用方不依赖 `null`；
- MySQL 持久化默认采用 MyBatis-Plus Lambda Wrapper 链式函数写法表达查询、更新、删除条件（`Wrappers.lambdaQuery(...)` / `Wrappers.lambdaUpdate(...)`）；
- `insert(entity)` 作为标准新增写法允许保留；
- 只有 SQL 复杂到链式写法明显不可读时，才允许 XML / 字符串 SQL / 注解 SQL；
- `MemoryJsonMapper` 应按对象类型逐步拆分，避免成为泛化 JSON 大杂烩；
- 命名带 `Mysql`、`Redis`、`File` 等实现细节的 repository 必须与其真实职责一致，不得保留误导性仓储类。

---

## 8. 主调用链

### 8.1 同步链路

标准同步链路：

```text
Controller
-> ApplicationService
-> RuntimeOrchestrator
-> WorkingContext 构建 / Prompt 渲染
-> AgentLoopEngine
-> LlmGateway / ToolGateway
-> TranscriptLifecycle / TurnArtifactPersistence
-> Post-turn Memory Update
-> Response
```

约束：

- provider 调用前必须先形成合法 Working Context；
- Working Context Projection 只用于 provider 调用；
- provider 响应后由 runtime 构建正式 assistant message；
- transcript 写入只能写 raw user / assistant / tool 事实消息；
- state / summary / evidence 通过 post-turn memory update 派生；
- memory update 失败不能反向影响主聊天成功返回。

---

### 8.2 流式链路

标准流式链路：

```text
Controller
-> StreamApplicationService
-> RuntimeOrchestrator
-> WorkingContext 构建 / Prompt 渲染
-> AgentLoopEngine(stream)
-> RuntimeEventEmitter
-> app SSE Adapter
-> Response
```

约束：

- runtime 只产生内部 event；
- app 负责 SSE 协议适配；
- provider stream delta 不直接泄漏到 app；
- tool fragment / provider fragment 的解析不放在 app 层；
- debug / audit / internal task 信息不进入主 stream event。

---

### 8.3 Transcript / State / Summary / Evidence 治理口径

- Transcript：原始事实记录；
- State：当前会话结构化快照；
- Summary：老历史压缩结果；
- Evidence：state / summary 等 memory 结论的证据绑定；
- Internal Task：post-turn memory update 等内部任务记录；
- MySQL：事实源；
- Redis：snapshot cache。

关键约束：

- WorkingContext 不等于 Transcript；
- Memory 不等于 Message；
- ToolExecution 不等于 ToolMessage；
- `SessionWorkingSet` 只能来自 MySQL completed raw transcript；
- `WorkingContextProjection.modelMessages` 只用于 provider 调用；
- synthetic runtime/state/summary message 不能持久化为 raw transcript；
- internal task / audit / evidence 不暴露到主 chat response。

---

## 9. 阶段架构设计下沉规则

从 `execution-phase/` 建立后，阶段性架构设计不再长期堆叠到本文件中。

本文件只记录长期稳定架构原则。  
某一阶段的详细架构设计、领域对象、包结构、流程细节、契约片段、测试要求，必须进入：

```text
execution-phase/{phase-name}/
```

典型分工如下：

| 内容 | 归属 |
|---|---|
| 长期模块边界 | 根目录 `ARCHITECTURE.md` |
| 长期依赖方向 | 根目录 `ARCHITECTURE.md` |
| 长期能力层划分 | 根目录 `ARCHITECTURE.md` |
| 当前阶段详细系统设计 | `execution-phase/{phase-name}/design.md` |
| 当前阶段开发计划 | `execution-phase/{phase-name}/plan.md` |
| 当前阶段专项测试验收 | `execution-phase/{phase-name}/test.md` |
| 当前阶段 Codex prompt | `execution-phase/{phase-name}/prompts.md` |
| 当前阶段完成收口 | `execution-phase/{phase-name}/closure.md` |

如果某个阶段产出的架构规则已经成为长期稳定规则，才允许从阶段文档晋升到本文件。  
晋升时必须只抽取长期规则，不得把阶段执行细节整体搬入本文件。

---

## 10. 架构约束

- `runtime` 与 `infra` 只依赖 `model` / `common`。
- `model.port` 只保留真正接口。
- `RuntimeOrchestrator` 是唯一主链路编排中心，但应通过协作者拆分控制复杂度。
- provider 只做协议请求、响应与流解析。
- persistence 只做存储实现。
- app 只做接入与装配。
- transcript、state、summary、evidence、internal task 等数据对象必须按职责分层治理。
- MySQL 是事实源，Redis 是 snapshot cache。
- `WorkingContextProjection.modelMessages` 只用于 provider 调用，不得写回 transcript。
- synthetic runtime/state/summary message 不得持久化为 raw transcript。
- debug / audit / internal task / evidence 不得污染主 chat response / stream event。
- 新阶段详细架构设计必须优先写入 `execution-phase/{phase-name}/design.md`。
- 旧测试与新架构冲突时，更新或删除旧测试，不为了旧测试保留过时逻辑。

---

## 11. 一句话总结

`ARCHITECTURE.md` 的职责，是把“系统长期如何分层、模块应该如何依赖、对象应该放在哪、运行时应该如何拆、Transcript / State / Summary / Evidence 应该如何治理、阶段架构设计应该如何下沉”这些结构性问题定死。

它不承载阶段详细设计、阶段执行计划、阶段专项测试清单或 Codex prompt。
