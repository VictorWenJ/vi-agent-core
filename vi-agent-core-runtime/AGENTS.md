# AGENTS.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core-runtime` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：

- `runtime` 模块负责什么
- `runtime` 模块不负责什么
- `runtime` 模块内包结构如何约定
- 在 `runtime` 模块开发时必须遵守哪些局部规则
- `runtime` 模块测试与依赖应如何建设

本文件不负责：

- 仓库级协作规则与通用开发规范（见根目录 `AGENTS.md`）
- 项目高层路线图、阶段状态与当前阶段索引（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准与通用测试门禁（见根目录 `CODE_REVIEW.md`）
- 阶段详细设计、阶段开发计划、阶段专项测试验收、Codex prompt、阶段收口记录（见 `execution-phase/{phase-name}/`）
- `app` / `infra` / `model` / `common` 模块内部职责细节（见对应模块 `AGENTS.md`）

执行 `runtime` 模块相关任务前，必须遵守根目录 `AGENTS.md` 中定义的 canonical 实现任务阅读顺序，并额外阅读本文件。

本文件只维护 `runtime` 模块长期边界，不重复维护全仓库阅读顺序。

---

## 2. 模块定位

`vi-agent-core-runtime` 是整个 `vi-agent-core` 系统的**核心运行时层**，也是 Agent 执行逻辑的唯一主编排中心。

标准依赖链位置：

```text
runtime -> model + common
```

因此，`runtime` 模块是：

- **唯一主链路编排模块**
- **Agent Loop 运行时模块**
- **Context Engineering 运行时模块**
- **Prompt Engineering 运行时治理模块**
- **Tool Runtime 协调模块**
- **Runtime Event 事件模块**
- **Session Memory 协调模块**
- **Observability / Audit 运行时抽象模块**

但 `runtime` 模块不是：

- Web 接入层
- Provider 实现层
- Persistence 实现层
- Redis / MySQL repository 实现层
- Web DTO 层
- 公共工具类模块
- 共享契约定义层
- 阶段详细设计承载层
- 未启用占位类寄存层

`runtime` 模块的核心定位是：

**负责 Agent 运行时主链路、上下文工程、提示词治理、工具协调、事件发射、记忆更新协调与运行时观测边界，不负责 HTTP 协议、厂商协议、存储实现或跨层契约定义。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

`runtime` 模块负责以下内容：

#### 1）主链路编排

- `RuntimeOrchestrator`
- run 生命周期打开与关闭
- 同步链路与流式链路统一编排
- transcript、working context、agent loop、runtime event、post-turn memory update 的顺序协调
- 成功 / 失败出口收敛

约束：

- `RuntimeOrchestrator` 是唯一主链路编排中心。
- `RuntimeOrchestrator` 只做 run-level orchestration（运行级编排）。
- `RuntimeOrchestrator` 不直接承担 provider 协议解析、Redis / MySQL 实现、Web DTO 转换或 Prompt 模板读取实现。

#### 2）Agent Loop 执行

- `AgentLoopEngine`
- `SimpleAgentLoopEngine`
- loop iteration 控制
- 模型调用与工具调用的运行时协调
- assistant message 构建
- 最大迭代次数、工具回填、多轮结束判断

约束：

- `AgentLoopEngine` 是真正的 loop owner（循环拥有者）。
- `LlmGateway` 返回 `ModelResponse`。
- 正式 `AssistantMessage` 由 loop 内部统一构建。
- loop 不直接依赖 provider 实现类。
- loop 不直接处理 WebFlux / SSE 协议。

#### 3）Context Engineering 运行时能力

- Working Context 构建
- Context Budget 计算
- Context Block 组织
- Working Context Projection
- Context Validation
- Working Context snapshot 协调
- provider 调用前模型消息准备

约束：

- WorkingContext 不等于 Transcript。
- WorkingContextProjection 只用于 provider 调用。
- `WorkingContextProjection.modelMessages` 不得写回 transcript。
- synthetic runtime / state / summary message 不得持久化为 raw transcript。
- `SessionWorkingSet` 只能来自 MySQL completed raw transcript，不得从 projection / workingMessages 回刷。

#### 4）Prompt Engineering 运行时治理

- Prompt Registry
- Prompt Renderer
- Prompt render request / result
- Prompt variable 校验
- Prompt output schema 与 parser allowlist 对齐协作
- Prompt audit metadata 运行时组装
- runtime instruction / state render / summary render 的统一治理

约束：

- `runtime.prompt` 负责 prompt 注册、渲染、变量校验、schema 治理协作。
- `runtime.prompt` 不负责读取文件系统、classpath 资源或数据库。
- prompt 外部资源读取实现属于 `infra`。
- prompt 契约对象属于 `model.prompt`。
- prompt repository port 属于 `model.port`。
- Prompt Renderer 不直接调用模型。
- Prompt Registry 不直接写 MySQL / Redis。
- prompt audit metadata 不进入主 chat response / stream event。
- P2-E 或后续阶段新增 Prompt Governance 细节，必须以当前阶段 `design.md` 为准。

#### 5）Tool Runtime 协调

- `ToolGateway`
- `ToolRegistry`
- tool execution 协调
- tool schema / tool call / tool result 的运行时边界
- mock 工具统一进入注册与执行链路

约束：

- `ToolGateway` 统一暴露 `execute(...)`。
- `ToolRegistry` 负责注册与查找，不长期承担扫描、校验、反射执行等全部职责。
- mock 工具也必须通过统一注册与统一执行链路进入 runtime。
- ToolExecution 不等于 ToolMessage。
- 工具执行记录、工具结果、模型协议 tool message 必须保持语义分离。

#### 6）Runtime Event 与流式事件

- `RuntimeEvent`
- `RuntimeEventEmitter`
- run started / completed / failed
- tool started / finished / failed
- stream delta 内部事件
- runtime event 到 app SSE adapter 的事件边界

约束：

- runtime 只产生内部事件。
- app 负责 SSE 协议适配。
- provider stream delta 不直接泄漏到 app。
- runtime event 必须保持内部语义稳定。
- debug / audit / internal task / evidence 信息不得混入主 stream event。

#### 7）Session Memory 协调

- `SessionMemoryCoordinator`
- post-turn memory update hook
- state extract 协调
- summary extract 协调
- evidence enrich 协调
- internal memory task 协调
- state / summary / evidence 写入后的 cache refresh 协调

约束：

- Memory 不等于 Message。
- state / summary / evidence 是 post-turn 派生 memory。
- post-turn memory update 失败不能影响主聊天成功返回。
- internal task / audit / evidence 不暴露到主 chat response。
- evidence 保存失败只能 degraded，不回滚已经成功写入的 state / summary。
- runtime 只调用 `model.port` 中的 repository / store 抽象，不依赖 infra 具体实现。

#### 8）Observability / Audit 运行时抽象

- runtime metrics 抽象
- MDC / trace scope 管理
- traceId / runId / sessionId / conversationId / turnId / requestId 运行时链路关联
- internal task audit 运行时协作

约束：

- traceId 只用于 runtime 内部日志、MDC 与事件链路，不向外部 DTO 透出。
- audit / debug 不污染主 chat response。
- internal task / evidence 不通过主协议暴露。
- debug API 必须走独立查询接口，当前阶段未明确要求时不得提前实现。

---

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `runtime` 模块：

- Controller / HTTP / SSE 协议处理
- Web 请求 / 响应 DTO
- DeepSeek / OpenAI / Doubao 等 Provider 实现
- Provider HTTP 调用与协议解析
- Provider request mapper / response mapper / stream parser
- Redis / MySQL repository / mapper / entity 实现
- 文件系统 / classpath prompt template 读取实现
- Spring Boot 顶层配置装配
- 通用 JSON / 校验 / ID 工具
- 共享领域模型与 port 接口定义
- 未启用的 Skill / Delegation / Subagent 主类
- 阶段详细设计或阶段专项测试规则

如果一段代码需要：

- 暴露 HTTP / SSE 协议；
- 解析厂商协议对象；
- 操作 Redis / MySQL 具体 client、mapper、entity；
- 读取 prompt 模板文件或 classpath 资源；
- 定义 runtime 与 infra 共同依赖的接口；
- 定义 model value object；
- 处理 Web DTO 映射；
- 依赖 `infra` 或 `app` 具体类；

那它就不属于 `runtime` 模块。

---

## 4. 模块内包结构治理

`runtime` 模块包结构不应机械等同于源码目录快照。

本文件采用包结构治理口径，将 runtime 包分为：

1. 核心长期包；
2. 合法运行时协作者包；
3. P2.5 待复核包；
4. 禁止保留的历史预留包。

新增 runtime 包时，必须先判断其属于哪一类。  
不得因为当前代码中存在某个包，就自动把它合法化为长期包。  
不得因为旧文档没有列出某个包，就在未评估的情况下直接删除真实主链路协作者包。

---

### 4.1 核心长期包

以下包属于 `runtime` 模块长期核心包：

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

#### `context/`

职责：

- Working Context 构建；
- Context Budget 计算；
- Context Planner / Policy；
- Context Block Factory；
- Working Context Projection；
- Working Context Validation；
- Working Context Snapshot 协调。

约束：

- 不直接访问 Redis / MySQL 具体实现。
- 不直接拼接 raw transcript 持久化对象。
- 不把 projection / workingMessages 写回 transcript。
- 不把 synthetic runtime / state / summary message 当作 raw message。
- 不直接调用 Provider。
- 不承载 Prompt Registry / Renderer 的实现细节。

#### `engine/`

职责：

- Agent Loop 执行；
- 模型调用协调；
- 工具调用协调；
- loop iteration 控制；
- assistant message 构建；
- loop 结果输出。

约束：

- `AgentLoopEngine` 是 loop owner。
- engine 只通过 `model.port.LlmGateway` 调用模型。
- engine 只通过 `ToolGateway` 调用工具。
- engine 不直接依赖 provider 实现。
- engine 不直接写 transcript / state / summary / evidence。
- engine 不直接处理 SSE 协议。

#### `event/`

职责：

- runtime event 定义；
- runtime event 发射；
- 同步 / 流式链路共享的内部事件表达。

约束：

- event 是 runtime 内部事件，不是 app 层 SSE DTO。
- event 不泄漏 provider 原始协议对象。
- event 不承载 Web DTO。
- event 中的 debug / audit 信息必须受阶段文档约束，不得污染主协议。
- stream delta 必须稳定携带同一 `messageId`。

#### `memory/`

职责：

- post-turn memory update 协调；
- internal memory task 协调；
- state extract / summary extract / evidence enrich 调度；
- state / summary / evidence 写入后 cache refresh 协调；
- memory failure policy 协调。

约束：

- memory 运行时逻辑不等于 message 逻辑。
- memory update 不得写入 raw transcript。
- state / summary / evidence 不暴露到主 response。
- memory 只依赖 `model.port` 抽象，不依赖 infra 实现。
- parser allowlist 与 prompt output schema 的一致性规则，以当前阶段 `design.md / test.md` 为准。
- evidence deterministic audit 不得被误改为 LLM prompt。

#### `observability/`

职责：

- runtime metrics 抽象；
- MDC / trace scope；
- run / turn / session / conversation / request 链路标识协作；
- runtime audit metadata 的抽象协作。

约束：

- 不直接写 MySQL / Redis。
- 不直接暴露 debug API。
- 不把 traceId 放入 app DTO。
- 不把 audit / internal task / evidence 明细放入主协议。
- 只承载运行时观测抽象，不做业务编排。

#### `orchestrator/`

职责：

- 主链路总控；
- run lifecycle 协调；
- session resolution 协调；
- transcript lifecycle 协调；
- working context、agent loop、event、memory update 的顺序组织；
- 成功 / 失败出口统一收敛。

约束：

- `RuntimeOrchestrator` 保持唯一主链路编排中心。
- orchestrator 不直接拼 provider messages。
- orchestrator 不直接解析 provider 协议。
- orchestrator 不直接访问 infra 具体 repository。
- orchestrator 不直接执行工具反射调用。
- orchestrator 不直接承担 prompt 文件读取。
- orchestrator 不把 debug / audit / evidence 塞入主协议。
- orchestrator 不把所有逻辑堆进一个超大方法。

#### `prompt/`

职责：

- Prompt Registry；
- Prompt Renderer；
- Prompt render request / result；
- Prompt variable 校验；
- Prompt output schema 治理协作；
- prompt audit metadata 协作；
- runtime instruction / state render / summary render 的运行时治理。

约束：

- `runtime.prompt` 不读取文件系统或 classpath 资源。
- `runtime.prompt` 不实现 prompt repository 的基础设施细节。
- `runtime.prompt` 不直接调用 provider。
- `runtime.prompt` 不写 MySQL / Redis。
- `runtime.prompt` 不污染 `ChatResponse / ChatStreamEvent`。
- 新增 Prompt Governance 规则必须先进入当前阶段文档。
- prompt schema 与 parser allowlist 不得双轨漂移。

#### `result/`

职责：

- runtime 执行结果对象；
- agent execution result；
- 主链路内部 result / status / failure reason 相关对象。

约束：

- result 不等于 app response DTO。
- result 不直接暴露 provider 原始对象。
- result 不承载 debug / audit / internal task / evidence 明细。
- result 应保持可测试、可映射、语义清晰。

#### `tool/`

职责：

- tool registry；
- tool gateway；
- tool executor；
- tool schema / invocation / result 的运行时协调；
- mock tool 的统一接入链路。

约束：

- `ToolGateway` 是工具执行统一边界。
- `ToolRegistry` 不长期承担扫描、校验、反射执行等全部职责。
- tool execution 不直接写 transcript。
- tool result 与 tool message 保持语义分离。
- tool 运行时不得绕过权限、审计和未来 safety policy 边界。
- 当前阶段未要求时，不提前扩展 Web / Browser / Document / Computer / Mobile tools。

---

### 4.2 合法运行时协作者包

以下包允许作为 `runtime` 主链路的协作者包存在：

```text
command/
completion/
dedup/
execution/
factory/
failure/
lifecycle/
loop/
mdc/
persistence/
scope/
session/
```

这些包不是新的能力层，也不是独立子系统。  
它们只允许服务 runtime 主链路拆分，不能越界承载 infra 实现、app 协议、model 契约或后续阶段平台能力。

#### `command/`

职责：

- 承载 runtime 内部 command / input object；
- 降低长参数方法；
- 表达运行时动作输入语义。

约束：

- 不放 Web request DTO。
- 不放 provider request 协议对象。
- 不放持久化 entity。
- 不替代 `model` 中的跨层 command 契约。

#### `completion/`

职责：

- 承载 assistant completion 结果收口；
- 协助 loop / orchestrator 处理模型完成结果；
- 收敛 finish reason、usage、assistant 输出等运行时语义。

约束：

- 不解析厂商协议。
- 不直接调用 provider。
- 不直接写 transcript。
- 不承担 Web response DTO 组装。

#### `dedup/`

职责：

- 承载 runtime 内部幂等 / 去重 / 防重复写入协作逻辑；
- 支持 run / turn / event / persistence coordinator 的重复保护。

约束：

- 不直接访问 MySQL / Redis 具体实现。
- 不把去重规则写成跨层事实源。
- 不替代 repository 的唯一约束或幂等写入实现。

#### `execution/`

职责：

- 承载 run execution 过程中的内部状态、步骤协作或执行上下文辅助能力；
- 支持 orchestrator 分解主链路执行步骤。

约束：

- 不替代 `engine/` 的 Agent Loop 职责。
- 不接管 `RuntimeOrchestrator` 主编排中心地位。
- 不放 provider / persistence 实现。

#### `factory/`

职责：

- 承载 runtime 对象创建逻辑；
- 例如 run context、runtime command、内部结果对象等构造协作。

约束：

- factory 只负责构造，不负责业务编排。
- 不访问 MySQL / Redis / Provider。
- 不手写业务 ID，必须使用对应语义的 IdGenerator。
- 不通过 factory 隐藏复杂业务决策。

#### `failure/`

职责：

- 承载 runtime 内部失败分类、失败结果转换、失败出口辅助逻辑；
- 支持 orchestrator / engine / memory 对失败进行统一语义收口。

约束：

- 不做 HTTP status 映射。
- 不替代 app 层 `GlobalExceptionHandler`。
- 不吞异常后伪造成功。
- 不把所有异常粗暴归为同一种失败。

#### `lifecycle/`

职责：

- 承载 run / turn / transcript / artifact 等生命周期协作；
- 支持 orchestrator 将生命周期步骤从主类中拆出。

约束：

- 不接管主编排中心。
- 不直接调用 infra 具体 repository。
- 不把生命周期协调和存储实现混在一起。

#### `loop/`

职责：

- 承载 Agent Loop 的辅助对象或细分协作者；
- 支持 `engine/` 内部 loop 逻辑拆分。

约束：

- `AgentLoopEngine` 仍是 loop owner。
- `loop/` 不独立成为第二套 engine。
- `loop/` 与 `engine/` 的边界在 P2.5 阶段需要复核，避免长期重复表达。

#### `mdc/`

职责：

- 承载 runtime MDC / trace scope 辅助逻辑；
- 管理 traceId、runId、sessionId、conversationId、turnId 等日志上下文字段的写入与清理。

约束：

- 不做业务编排。
- 不暴露 traceId 到 app DTO。
- 不写 MySQL / Redis。
- `mdc/` 与 `observability/` 的边界在 P2.5 阶段需要复核。

#### `persistence/`

职责：

- 承载 runtime 层的持久化协调逻辑，例如 turn artifact persistence coordinator；
- 只负责调用 `model.port` 抽象完成运行时持久化流程协调。

约束：

- 不放 MySQL / Redis repository 实现。
- 不放 mapper / entity。
- 不直接依赖 infra。
- 不执行 SQL。
- 不成为第二个 infra persistence。
- `persistence/` 与 `memory/`、`orchestrator/` 的边界在 P2.5 阶段需要复核。

#### `scope/`

职责：

- 承载 runtime scope / run scope / resource scope 等生命周期范围对象；
- 支持主链路资源打开、关闭和上下文传递。

约束：

- 不持有不受控全局状态。
- 不做存储实现。
- 不承担业务规则判断。

#### `session/`

职责：

- 承载 session resolution；
- 处理 conversationId + sessionId 的运行时解析；
- 协调 session metadata port；
- 保证 transcript lifecycle 使用解析后的 session 信息。

约束：

- 不在 app controller / application 中散落 session 解析。
- 不从 transcript / projection 反向推断 session metadata。
- 不直接访问 infra repository 实现。
- 不替代 `model.port.SessionMetadataStore` 契约。

---

### 4.3 P2.5 待复核包

以下包或包边界当前允许存在，但不视为最终长期冻结结构：

```text
record/
loop/
mdc/
persistence/
```

P2.5 架构治理阶段必须复核：

- 是否存在包名语义偏弱；
- 是否与核心长期包职责重叠；
- 是否应归并到 `observability/`、`engine/`、`orchestrator/`、`memory/`；
- 是否存在历史残留类；
- 是否存在仅为旧测试保留的兼容逻辑；
- 是否需要重命名或删除。

当前阶段不为了清理包名而大规模迁移真实主链路代码。  
除非某个类已经无引用、无文档依据、无主链路职责，否则不在 P2-E 前置治理中强制迁移。

---

### 4.4 禁止保留的历史预留包

以下包不允许作为当前 runtime 正式包继续保留：

```text
skill/
delegation/
subagent/
```

规则：

- 当前阶段不保留 Skill / Delegation / Subagent 主类占位代码。
- 当前阶段不提前实现 Claude Code 风格 subagent。
- 当前阶段不提前实现 delegation coordinator。
- 当前阶段不提前实现 skill runtime。
- 相关能力未来如需恢复，必须在对应阶段 `execution-phase/{phase-name}/design.md` 中重新定义目标、边界、对象和测试要求。
- 未经阶段文档确认，不得恢复这些包或同义预留类。

---

### 4.5 新增 runtime 包规则

新增 runtime 包必须满足以下要求：

1. 当前阶段文档明确允许；
2. 包名能准确表达职责；
3. 不与现有核心包职责重复；
4. 不承载 infra 实现；
5. 不承载 app 协议；
6. 不承载 model 契约定义；
7. 不提前实现后续阶段能力；
8. 不作为历史残留或预留能力寄存地；
9. 有对应测试或验收项支撑；
10. 必要时同步更新本文件。

---

## 5. RuntimeOrchestrator 边界

### 5.1 `RuntimeOrchestrator` 保留的职责

`RuntimeOrchestrator` 负责：

- 打开 / 关闭一次 run 生命周期；
- 协调 session resolution；
- 协调 transcript lifecycle；
- 协调 working context 构建；
- 协调 agent loop 执行；
- 协调 runtime event 发射；
- 协调 turn artifact 持久化抽象；
- 协调 post-turn memory update；
- 收敛成功 / 失败出口；
- 保证同步链路与流式链路共享统一 runtime 语义。

### 5.2 `RuntimeOrchestrator` 不应承担的职责

`RuntimeOrchestrator` 不应承担：

- provider 协议解析；
- provider request / response mapping；
- Redis / MySQL 具体实现调用；
- prompt 文件读取；
- prompt output schema 详细校验逻辑；
- tool 反射执行细节；
- Web DTO 组装；
- SSE 协议输出；
- memory parser 细节；
- evidence repository 实现；
- debug API 实现。

### 5.3 推荐协作者

`RuntimeOrchestrator` 应通过协作者保持边界清晰，包括但不限于：

- `SessionResolutionService`
- `RunContextFactory`
- `TranscriptLifecycleManager`
- `WorkingContextBuilder`
- `WorkingContextProjector`
- `WorkingContextValidator`
- `WorkingContextSnapshotService`
- `AgentLoopEngine`
- `RuntimeEventEmitter`
- `SessionMemoryCoordinator`
- `MdcScope` / `RuntimeMdcManager`
- `TurnArtifactPersistenceCoordinator`

协作者拆分的目标不是制造类数量，而是控制主编排复杂度，保持每个类职责可测试、可替换、可审计。

### 5.4 `persistTurnArtifacts(...)` 规则

`persistTurnArtifacts(...)` 可以保留为总流程入口，但必须拆成清晰步骤，而不是继续膨胀为大方法。

推荐拆分为：

- `upsertSessionMetadata(...)`
- `persistTranscript(...)`
- `persistSummaryIfPresent(...)`
- `persistStateIfPresent(...)`
- `persistEvidenceIfPresent(...)`
- `refreshSnapshotCacheIfNeeded(...)`
- `recordInternalTaskIfNeeded(...)`

如果当前架构已将部分逻辑拆入独立 coordinator，应保持职责一致，不得再回退为单个大方法。

---

## 6. 会话解析与链路标识规则

### 6.1 会话解析规则

- Runtime 入口先做 `conversationId + sessionId` 解析，再创建 `runId / turnId / traceId` 与 `AgentRunContext`。
- 会话解析由 `SessionResolutionService` 或等价 runtime 协作者统一承担。
- 会话解析不允许散落在 controller / app service 中。
- `TranscriptLifecycleManager` 按解析后的 session 加载 transcript。
- `TranscriptLifecycleManager` 必须确保 conversationId 与 session resolution 结果一致。
- conversation-session 事实源由 session metadata 负责，不从 transcript / projection 反向推断。

### 6.2 链路标识规则

- `RuntimeEvent` 必须携带 `requestId`。
- stream delta 必须稳定携带同一 `messageId`。
- `traceId` 仅用于 runtime 内部日志、MDC 与事件链路。
- `traceId` 不向外部 DTO 透出。
- `runId / turnId / sessionId / conversationId / requestId / messageId` 必须保持语义清晰。
- 业务 ID / 审计 ID / projection ID / snapshot ID / block ID / internal task ID 禁止手写 `"prefix-" + UUID.randomUUID()`，必须使用对应语义的 IdGenerator。

---

## 7. 模块局部开发约束

### 7.1 依赖与 POM 规则

- `runtime` 只允许依赖 `model` + `common`。
- 严禁 `runtime -> infra`。
- 严禁 `runtime -> app`。
- 模块 POM 只保留直接依赖。
- 不重复写受父 POM 管理的版本。
- 测试依赖只能用于测试作用域。
- 不引入与当前阶段无关的大型框架、中间件或平台依赖。
- 不得通过新增依赖绕开模块边界。
- runtime 中需要的外部能力必须通过 `model.port` 抽象获得。

### 7.2 类设计与方法设计规则

- 超过 300 行或承担 4 类以上职责的类要主动评估拆分。
- 单方法参数超过 5 个时，必须评估 Command / Builder。
- 静态工厂超过 6 个参数时，默认改 Builder。
- 总流程方法可以保留，但内部必须步骤化。
- 编排类不做 mapper 细节。
- Registry 不承担注册以外的宽职责。
- Coordinator 不直接接管基础设施实现。
- Parser 不承担业务 merge。
- Renderer 不承担 provider 调用。
- 类名、包位置、字段集合、方法集合必须保持语义一致。

### 7.3 可变性规则

- `AgentRunContext` 禁止继续使用类级别 `@Setter`。
- 对外暴露的集合必须返回只读视图。
- 运行时关键对象的状态变化优先通过显式意图方法控制。
- 值对象优先不可变。
- 核心对象构造完成后应处于合法、自洽状态。
- 不允许半初始化对象进入主链路。

### 7.4 工具优先规则

- 字符串判空优先使用 `StringUtils`。
- 集合判空优先使用 `CollectionUtils`。
- Map 判空保持统一风格。
- 对象判空优先使用 JDK `Objects` 相关 API。
- 避免在 runtime 中重复手写判空模板逻辑。
- JSON 处理优先复用项目统一工具或 mapper，不在 runtime 中散落 `ObjectMapper` 样板逻辑。

### 7.5 Lombok 规则

- Spring / runtime 协作者优先使用 `@RequiredArgsConstructor`。
- 需要日志的类优先使用 `@Slf4j`。
- 核心领域语义对象不滥用 `@Data`。
- 可由 Lombok `@Builder` 完整替代的手写 builder，不新增重复实现。
- 使用 Lombok 后不得弱化构造约束、默认值来源和对象合法性。

### 7.6 占位类规则

- 当前不保留 Skill / Delegation / Subagent 主类占位代码。
- 当前不提前新增 Graph Workflow、RAG、Long-term Memory、Checkpoint、Computer / Mobile Control 主类。
- 预留能力如确需保留，必须进入专门预留包并有文档依据。
- 无文档、无调用、无明确归属的占位类必须删除。
- 不得为了未来能力提前污染当前 runtime 主链路。

### 7.7 注释规则

- 新增领域类、命令类、结果类、配置类、DTO、record-like value object 必须添加中文类注释。
- 新增字段必须添加中文字段注释。
- 公共接口、公共方法、关键协作者与复杂规则点必须补充正式说明。
- 注释必须解释边界、约束或风险，不得机械重复代码字面含义。
- 注释必须与代码同步维护。
- 失败策略、降级策略、线程安全假设、存储一致性假设必须说明清楚。

---

## 8. 专项边界规则

### 8.1 Context 边界规则

- WorkingContext 不等于 Transcript。
- `SessionWorkingSet` 只能来自 MySQL completed raw transcript。
- `WorkingContextProjection.modelMessages` 只用于 provider 调用。
- synthetic runtime / state / summary message 不能持久化为 raw transcript。
- `AgentRunContext.workingMessages` 不得回退为由 `AgentRunContextFactory` 手工拼接 history + current user。
- Context debug / audit 信息不得进入主聊天协议。

### 8.2 Prompt 边界规则

- P2-E 之后，不允许新增散落 inline prompt。
- 新增 LLM prompt 必须进入统一 Prompt Governance 结构。
- 新增 prompt 必须具备 template key、version、variables、output schema。
- prompt output schema 必须与 parser allowlist 保持一致。
- deterministic audit 不得伪装为 LLM prompt。
- prompt audit metadata 不得进入 `ChatResponse / ChatStreamEvent`。
- runtime prompt 层不读取文件系统，不做 infra repository 实现。
- P2-E 详细设计与验收标准以当前阶段文档为准。

### 8.3 Memory 边界规则

- Memory 不等于 Message。
- StateDelta 必须保持领域补丁风格，不能回退到 `upsert/remove`。
- completed raw transcript -> STATE_EXTRACT / SUMMARY_EXTRACT 是 post-turn memory 派生链路。
- state / summary 写入成功后可以刷新 Redis snapshot cache。
- evidence binding 保存失败只 degraded，不回滚 state / summary。
- memory update 失败不能影响主聊天成功返回。
- internal task / audit / evidence 不暴露到主 chat response。
- parser 顶层与嵌套字段 allowlist 必须强校验。
- 涉及 `StateDelta`、`SessionStateSnapshot` 等强契约对象时，必须递归检查嵌套对象契约。

### 8.4 Tool 边界规则

- ToolExecution 不等于 ToolMessage。
- `ToolGateway.execute(...)` 是工具执行统一入口。
- `ToolRegistry` 不长期承担扫描、校验、反射执行、权限判断等全部职责。
- 工具结果摘要与工具消息必须保持独立语义。
- 当前阶段未明确要求时，不提前扩展 Web / Browser / Document / Computer / Mobile tools。

### 8.5 主协议防污染规则

- `ChatResponse` 不得承载 debug / audit / internal task / evidence。
- `ChatStreamEvent` 不得承载 debug / audit / internal task / evidence。
- `traceId` 不得透出到 app DTO。
- runtime event 不等于 app SSE DTO。
- provider delta 不得直接泄漏到 app。
- debug / audit 后续必须走独立查询接口。
- 当前阶段未明确要求时，不新增 debug API。

---

## 9. 测试要求

### 9.1 必须覆盖的测试场景

| 测试目标 | 测试要求 |
|---|---|
| `RuntimeOrchestrator` | 覆盖单轮成功、失败出口、session resolution、post-turn memory update 降级 |
| `AgentLoopEngine` | 覆盖单轮、工具回填、多轮结束、最大迭代超限、assistant message 构建 |
| 流式 runtime event | 覆盖 run event、delta event、messageId 稳定性、失败事件 |
| Working Context | 覆盖 block 构建、预算裁剪、projection、synthetic message 不回写 transcript |
| Prompt Registry / Renderer | 覆盖 template 注册、version、变量必填校验、render 结果、output schema 元信息 |
| Prompt schema / parser 对齐 | 覆盖 output schema 与 parser allowlist 一致性 |
| ToolRegistry / ToolGateway | 覆盖注册、查找、执行、缺失工具、异常工具 |
| SessionMemoryCoordinator | 覆盖 state extract、summary extract、evidence enrich、失败降级 |
| State / Summary / Evidence 协调 | 覆盖保存成功、cache refresh、evidence degraded、不污染主协议 |
| Internal Task | 覆盖任务创建、状态更新、失败记录、audit metadata |
| SessionResolutionService | 覆盖 conversationId + sessionId 解析、缺失场景、冲突场景 |
| TranscriptLifecycleManager | 覆盖 transcript 加载、追加、conversationId 一致性 |
| IdGenerator 使用路径 | 覆盖 runtime 不手写 UUID，调用对应语义生成器 |
| 主协议防污染 | 覆盖 response / stream event 不包含 debug、audit、internal task、evidence |
| runtime 包结构治理 | 覆盖禁止恢复 `skill/`、`delegation/`、`subagent/` 预留包；P2.5 待复核包不得承载 infra / app / model 职责 |

### 9.2 测试约束

- runtime 测试不依赖真实 Provider API。
- runtime 测试不直接连接真实 Redis / MySQL，除非是明确的集成测试。
- runtime 测试应通过 mock `model.port` 契约隔离 infra。
- Prompt Renderer 测试不测试文件读取实现。
- Tool 测试必须经过统一 registry / gateway。
- Memory 测试必须覆盖失败不影响主聊天成功返回。
- JSON / parser / contract 测试必须覆盖未知字段、缺失字段和旧语义防回退。
- 旧测试与新架构冲突时，更新或删除旧测试，不为了旧测试保留过时逻辑。
- 包结构治理测试不应为了保留旧预留包而修改文档规则。

---

## 10. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 文档治理规则约束。
2. 当 `runtime` 模块核心长期包、合法运行时协作者包或禁止预留包发生长期变化时，必须同步更新本文件。
3. 阶段性详细设计、阶段开发计划、阶段专项验收、Codex prompt 不写入本文件，应写入当前阶段 `execution-phase/{phase-name}/`。
4. 若根目录文档中出现了本模块过细实现内容，应回收到本文件。
5. 若阶段文档中产生了长期稳定的 runtime 模块边界，完成阶段收口后可以晋升到本文件。
6. 未经确认，不得改变本文件的布局、标题层级与章节顺序。
7. 不得把 `app` / `infra` / `model` / `common` 的实现细节长期写成本模块文档。
8. 不得把某个阶段的临时实现细节固化为 runtime 模块长期规则。
9. 历史阶段补充规则如果已经成为长期边界，应归并到对应章节，不应继续放在“一句话总结”之后。
10. 本文件不机械镜像源码目录；源码包与文档包结构治理口径不一致时，必须先分类判断，再决定文档承认、代码归并或删除残留。

---

## 11. 一句话总结

`runtime` 模块的核心要求是：保持它作为唯一运行时编排层的纯度。

它负责 RuntimeOrchestrator、Agent Loop、Working Context、Prompt Governance、Tool Runtime、Runtime Event、Session Memory、Observability / Audit 的运行时协调；合法运行时协作者包可以服务主链路拆分，但不得演化成第二编排层、infra 实现层、app 协议层或未来能力预留区。

`skill/`、`delegation/`、`subagent/` 等历史预留包不得在当前阶段恢复。
