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

`vi-agent-core` 旨在构建一个**逻辑分布式、职责可分**的 Agent Runtime Framework。当前阶段（Phase 1）为单体应用，但内部严格按逻辑服务边界设计，为未来物理拆分预留接缝。

核心借鉴思想：
- **Claude Code / Agent SDK**：Agent Loop、Tool Use、Subagents 思想。
- **OpenClaw**：Gateway、Context Engine、Memory 与 Session 管理。

---

## 3. 总体分层（逻辑视图）

### 3.1 接入层（`controller/`）
- **职责**：暴露 REST API，处理 SSE 流式连接。
- **关键组件**：`ChatController`、`StreamController`。
- **约束**：不包含业务逻辑，只做请求解析与响应序列化。

### 3.2 应用入口层（`service/`）
- **职责**：作为 Facade，承接请求并委托给核心运行时。
- **关键组件**：`ChatService`、`StreamingChatService`。
- **约束**：不直接调用模型或工具，保持轻薄。

### 3.3 核心运行时层（`core/`）
系统的心脏，包含以下子模块：

- **`runtime/`**：Agent Loop 执行引擎，负责“推理-工具-再推理”循环。
- **`context/`**：上下文装配与管理，控制 Token 预算与历史裁剪（Phase 2 重点）。
- **`tool/`**：统一工具网关，负责工具注册、Schema 暴露、执行与结果标准化。
- **`memory/`**：短期记忆与长期偏好存储（Phase 2 重点）。
- **`delegation/`**：受控子代理委派（Phase 3 重点）。

### 3.4 基础设施层（`infrastructure/`）
- **`provider/`**：LLM、Embedding、Rerank 等模型能力统一抽象。
- **`persistence/`**：数据库、Redis 访问。
- **`observability/`**：日志、Trace、Metrics 收口（Phase 4 重点）。

### 3.5 数据模型层（`model/`）
- **职责**：定义跨层共享的 POJO/Record 契约（如 `Message`、`ToolCall`、`AgentRun`）。

---

## 4. 依赖方向

**正向依赖原则：**

`controller` → `service` → `core/runtime` → `core/context` / `core/tool` / `infrastructure/provider` → `model`

**关键约束：**
- `core` 层不依赖 `controller` 或 `service`。
- `infrastructure` 层只向外暴露接口，不反向依赖业务层。
- `tool` 模块必须通过 `ToolGateway` 访问，不允许跨层直接调用 `ToolExecutor`。

---

## 5. 核心调用链路

### 5.1 同步对话链路
`POST /api/chat` → `ChatService` → `AgentLoopEngine` → `ContextAssembler` → `ToolGateway` (如果需要) → `LlmProvider` → 返回 JSON。

### 5.2 流式对话链路
`POST /api/chat/stream` → `StreamingChatService` → `AgentLoopEngine` → 循环迭代 → `Flux<ServerSentEvent>` 流式输出。

### 5.3 工具调用链路
`AgentLoopEngine` 识别 `tool_calls` → `ToolGateway.route()` → `ToolExecutor.execute()` → 结果回填至 `AgentLoopEngine`。

---

## 6. 关键模块边界说明

| 模块 | 核心职责 | 边界约束 |
| :--- | :--- | :--- |
| **AgentLoopEngine** | 执行 ReAct 循环，决定何时停止 | 不直接操作数据库，不处理 HTTP 协议 |
| **ContextAssembler** | 构建每一轮的 working context | 不调用模型，只做数据裁剪与拼装 |
| **ToolGateway** | 统一工具入口与标准化 | 工具实现必须通过此网关注册，不得绕过 |
| **MemoryService** | 管理长期偏好与摘要 | 与 Transcript 严格分离，不是聊天记录的简单复制 |

---

## 7. 当前阶段架构约束（Phase 1）

- **单体应用，逻辑分层**：所有模块在同一进程中，但代码边界必须清晰。
- **单 Agent 模式**：先实现主 Agent 完整闭环，暂不做子代理委派。
- **同步与流式共用核心**：`AgentLoopEngine` 同时支撑 `/chat` 和 `/chat/stream`。
- **Tool Calling 为 Phase 1 核心**：必须实现可扩展的工具注册与调用机制。
- **为后续阶段预留接口**：`ContextAssembler`、`MemoryService`、`DelegationService` 等接口需提前定义，但 Phase 1 可提供简单实现或空实现。

---

## 8. 文档模板冻结规则

与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：
- 只做增量更新，不改变整体风格与章节结构。
- 任何架构变更必须先更新本文档，再修改代码。

---

## 9. 一句话总结

`ARCHITECTURE.md` 定义了 `vi-agent-core` 的逻辑分层与模块边界，确保系统在从 Phase 1 到 Phase 4 的演进过程中，不会因职责不清而陷入混乱。