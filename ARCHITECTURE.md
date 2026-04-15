# ARCHITECTURE.md

> 更新日期：2026-04-15

## 1. 文档定位

本文件定义 `vi-agent-core` 的总体架构、分层职责、依赖方向与核心调用关系。

本文件只负责回答：
- 系统总体分层是什么
- 各层分别负责什么
- 各层之间如何依赖
- 核心链路的调用流程

本文件不负责：
- 仓库级协作规则（见 `AGENTS.md`）
- 阶段路线图（见 `PROJECT_PLAN.md`）
- 代码审查细节（见 `CODE_REVIEW.md`）

---

## 2. 架构定位

`vi-agent-core` 旨在构建一个**逻辑分布式、职责可分**的 Agent Runtime Framework。
当前阶段（Phase 1）为单体应用，但内部严格按逻辑服务边界设计，为未来物理拆分预留接缝。

核心借鉴思想：
- **Claude Code / Agent SDK**：Agent Loop、Tool Use、Skills、Subagents、Runtime Kernel。
- **OpenClaw**：Gateway、Context Engine、Memory、Session、Streaming、Authoritative Runtime Path。
- **旧项目工程经验**：多模块分层、中心编排 + 子处理器/子策略、注册表扩展、同步与异步链路分离、配置驱动、运行时监控。

因此，本项目的架构原则不是“Controller + Service + Provider”三层聊天服务，而是：
- 用 **Runtime Core** 统一编排一次用户请求的完整生命周期；
- 用 **Context / Tool / Memory / Provider / Persistence** 作为可替换、可扩展的能力模块；
- 用 **Session / Transcript / Artifact / Observability** 承接状态、审计与回放基础；
- 用 **逻辑服务边界** 为后续的物理分布式拆分预留演进路径。

### 2.1 Maven 模块落地视图（Phase 1）

当前代码已按 Maven 多模块落地：

- `vi-agent-core-app`：应用入口与装配层（唯一运行模块）
- `vi-agent-core-runtime`：核心运行时与主链路编排
- `vi-agent-core-infra`：基础设施实现层
- `vi-agent-core-model`：内部运行时模型层
- `vi-agent-core-common`：轻量公共能力层

其中，LangChain4j 在当前阶段仅放置于 `infra` 侧作为基础设施依赖，不主导 `runtime` 的核心抽象设计。

当前公共能力层已落地 `JsonUtils`、`ValidationUtils`、ID 生成器与公共异常等基础能力；后续新增可复用公共逻辑时，应继续沿用“统一沉淀到 `common` 模块、由上层复用”的方式演进。

---

## 3. 总体分层（逻辑视图）

### 3.1 接入层（`controller/`）
- **职责**：暴露 REST API，处理 SSE 流式连接。
- **关键组件**：`ChatController`、`StreamController`。
- **约束**：不包含业务逻辑，只做请求解析与响应序列化。
- **借鉴来源**：
  - OpenClaw 的 Gateway / Session Routing：入口层负责接入与路由，而不是承担运行时核心。
  - 旧项目的 `BlacklistServiceImpl`：入口层保持轻薄，复杂流程全部交给内部编排服务。

### 3.2 应用入口层（`service/`）
- **职责**：作为 Facade，承接请求并委托给核心运行时。
- **关键组件**：`ChatService`、`StreamingChatService`。
- **约束**：不直接调用模型或工具，保持轻薄。
- **借鉴来源**：
  - 旧项目中“Provider / Service 只转发、编排下沉”的经验。
  - OpenClaw 的入口与 Runtime 解耦思路。

### 3.3 核心运行时层（`core/`）
系统的心脏，包含以下子模块：

- **`runtime/`**：`RuntimeOrchestrator` 与其内部 Agent Loop 执行机制，负责“推理-工具-再推理”循环，是系统唯一执行总线。
- **`context/`**：上下文装配与管理，控制 Token 预算、历史裁剪、Prompt Block 组装、Working Context 构建（Phase 2 重点）。
- **`tool/`**：统一工具网关，负责工具注册、Schema 暴露、执行与结果标准化。
- **`memory/`**：短期记忆、摘要与长期偏好存储（Phase 2 重点）。
- **`delegation/`**：受控子代理委派与子任务执行边界（Phase 3 重点）。
- **`skill/`**：技能注册、匹配与装配（Phase 3 重点）。

这一层的设计同时吸收：
- Claude Code 的 Agent Loop、Skill、Subagent 思想；
- OpenClaw 的 Context Engine、Session-aware Runtime 思想；
- 旧项目的 `CentralProcessService + Handler / Retrieval / Feature` 这类“中心编排 + 注册扩展器”经验。

### 3.4 基础设施层（`infrastructure/`）
- **`provider/`**：LLM、Embedding、Rerank 等模型能力统一抽象。
- **`persistence/`**：数据库、Redis、Transcript、Artifact 等存储访问。
- **`observability/`**：日志、Trace、Metrics 收口（Phase 4 重点）。当前日志输出统一通过 SLF4J 门面，日志实现与格式策略统一由 `vi-agent-core-app/src/main/resources/log4j2-spring.xml` 管理。
- **`integration/`**：未来外部系统、MCP、第三方服务适配点。

这一层承接旧项目中“远端调用/配置/队列/监控视为架构组成部分”的经验，但在新项目中必须通过标准接口和适配层接入，而不是静态工具类分散调用。

### 3.5 数据模型层（`model/`）
- **职责**：定义跨层共享的 POJO/Record 契约（如 `Message`、`ToolCall`、`AgentRun`、`ConversationTranscript`）。
- **约束**：协议模型、运行时模型、持久化模型应语义分层，不得混用。
- **借鉴来源**：旧项目中 request / response / info / entity / enum 的领域建模习惯，但在新项目中需要更明确地区分协议对象、运行态对象和持久化对象。

### 3.6 公共能力层（`common/`）
- **职责**：承载轻量、无状态、可跨模块复用的基础能力，如公共异常、ID 生成器、校验工具、JSON 工具等。
- **当前落地内容**：`AgentRuntimeException`、`ErrorCode`、`IdGenerator`、`TraceIdGenerator`、`RunIdGenerator`、`JsonUtils`、`ValidationUtils`。
- **边界约束**：`common` 只能提供通用能力，不得承载业务编排、Runtime 调度、外部依赖装配或容器生命周期逻辑。
- **工具类规则**：类似 `JsonUtils` 这类可复用公共逻辑，应统一沉淀在 `common/util` 中供各模块复用，避免在 `runtime`、`infra`、`app` 内重复实现同类逻辑。

---

## 4. 依赖方向

**正向依赖原则：**

`controller` → `service` → `core/runtime` → `core/context` / `core/tool` / `core/memory` / `infrastructure/provider` / `infrastructure/persistence` → `model`

**模块级依赖（已落地）：**

`vi-agent-core-app` → `vi-agent-core-runtime` + `vi-agent-core-infra` + `vi-agent-core-model` + `vi-agent-core-common`  
`vi-agent-core-infra` → `vi-agent-core-runtime` + `vi-agent-core-model` + `vi-agent-core-common`  
`vi-agent-core-runtime` → `vi-agent-core-model` + `vi-agent-core-common`  
`vi-agent-core-model` → `vi-agent-core-common`

**关键约束：**
- `core` 层不依赖 `controller` 或 `service`。
- `app` 是唯一启动与装配入口，`runtime` 不反向依赖 `app`。
- `infrastructure` 层只向外暴露接口，不反向依赖业务层。
- `tool` 模块必须通过 `ToolGateway` 访问，不允许跨层直接调用 `ToolExecutor`。
- `runtime` 是唯一核心编排入口；`context`、`tool`、`memory`、`skill`、`delegation` 不得互相形成无边界横向调用。
- `provider`、`persistence`、`integration` 必须走适配器/网关，不允许在运行时主链路中直接 `new` 具体外部客户端。
- `transcript`、`memory`、`working context`、`artifact` 必须分层建模，禁止在一个对象里混装多种职责。
- 同步交互链路和异步后台链路必须解耦；未来委派、审批、长任务恢复等能力应沿独立链路演进。

---

## 5. 核心调用链路

### 5.1 同步对话链路
`POST /api/chat` → `ChatService` → `RuntimeOrchestrator` → `ContextAssembler` → `ToolGateway` (如果需要) → `LlmProvider` → 返回 JSON。

### 5.2 流式对话链路
`POST /api/chat/stream` → `StreamingChatService` → `RuntimeOrchestrator` → 内部 Agent Loop 循环 → `Flux<ServerSentEvent>` 流式输出。

### 5.3 工具调用链路
`RuntimeOrchestrator` 内部 Agent Loop 识别 `tool_calls` → `ToolGateway.route()` → `ToolExecutor.execute()` → 结果标准化回填至 Runtime 内部消息列表。

### 5.4 状态落盘链路（Phase 1 最小版）
`RuntimeOrchestrator` → `TranscriptRepository / TranscriptStoreService` → 保存最小会话历史（至少包含用户消息、助手回复、工具调用记录）、运行标识（`traceId` / `runId`）。

### 5.5 未来委派链路（预留）
`RuntimeOrchestrator` → `DelegationService` → 子代理最小上下文执行 → 结构化结果回收 → 主运行时继续汇总。

这条链路借鉴 Claude Code 的 Subagents 和 OpenClaw 的 Multi-Agent 思想，但当前阶段仅预留边界，不实现完整能力。

---

## 6. 关键模块边界说明

| 模块 | 核心职责 | 边界约束 |
| :--- | :--- | :--- |
| **RuntimeOrchestrator** | 执行 ReAct/Agent Loop，统一调度上下文、模型、工具与状态写回 | 不直接操作 HTTP 协议，不直接调用具体数据库客户端，不绕过网关访问外部系统 |
| **ContextAssembler** | 构建每一轮的 working context | 不调用模型，只做数据选择、裁剪、拼装与预算控制。**Phase 1 简单实现：直接返回全量历史消息，不进行 Token 计数或裁剪** |
| **ToolGateway** | 统一工具入口、Schema 暴露、执行标准化 | 工具实现必须通过此网关注册，不得绕过 |
| **MemoryService** | 管理摘要、短期记忆与长期偏好 | 与 Transcript 严格分离，不是聊天记录的简单复制 |
| **Transcript / TranscriptStoreService** | 保存全量历史与运行轨迹 | 不承载上下文裁剪逻辑，不等于 Memory |
| **ArtifactStore** | 保存大对象、中间结果、草稿、附件 | 不直接进入 Working Context，必须通过引用或摘要进入 |
| **AgentRegistry / SkillRegistry** | 维护可扩展 Agent/Skill 定义 | 新增能力优先注册，不允许把扩展逻辑硬编码进 Runtime 核心 |

---

## 7. 当前阶段架构约束（Phase 1）

- **单体应用，逻辑分层**：所有模块在同一进程中，但代码边界必须清晰。
- **单 Agent 模式**：先实现主 Agent 完整闭环，暂不做子代理委派。
- **同步与流式共用核心**：`RuntimeOrchestrator` 同时支撑 `/chat` 和 `/chat/stream`。
- **Tool Calling 为 Phase 1 核心**：必须实现可扩展的工具注册与调用机制。
- **最小状态底座必须到位**：至少具备最小 Transcript（用户消息、助手回复、工具调用记录）、`traceId` / `runId`。
- **为后续阶段预留接口**：`ContextAssembler`（返回全量历史）、`MemoryService`、`DelegationService`、`SkillRegistry` 等接口需提前定义，但仅提供简单或空实现。
- **只读工具优先**：写操作工具、审批、策略控制放后续阶段实现。
- **保持旧项目正确经验，修复旧项目反模式**：继承“中心编排 + 注册扩展 + 运行时监控”，不再使用 static 全局状态和容器外绕路取 Bean 模式。

---

## 8. 文档模板冻结规则

与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：
- 只做增量更新，不改变整体风格与章节结构。
- 任何架构变更必须先更新本文档，再修改代码。

---

## 9. 一句话总结

`ARCHITECTURE.md` 定义了 `vi-agent-core` 的逻辑分层与模块边界，确保系统在从 Phase 1 到 Phase 4 的演进过程中，同时吸收 Claude Code、OpenClaw 与既有工程经验的优点，而不把旧项目反模式带入新的 Agent Runtime 平台。
