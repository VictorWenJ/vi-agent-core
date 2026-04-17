# ARCHITECTURE.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core` 的总体架构、分层职责、依赖方向与核心调用关系。

本文件只负责回答：
- 系统总体分层是什么
- 各层分别负责什么
- 各层之间如何依赖
- 当前阶段主链路如何调用

本文件不负责：
- 仓库级协作规则与开发约束（见 `AGENTS.md`）
- 阶段路线图与任务状态（见 `PROJECT_PLAN.md`）
- 具体包结构、类职责与局部规则（见各模块 `AGENTS.md`）
- 代码审查细节（见 `CODE_REVIEW.md`）

---

## 2. 架构定位

`vi-agent-core` 旨在构建一个**逻辑分布式、职责可分、单体先行落地**的 Agent Runtime Framework。
当前阶段（Phase 1）仍然是单体应用，但内部必须按稳定的逻辑边界设计，不能退化为“大 chat service”。

核心借鉴思想：
- **Claude Code / Agent SDK**：Agent Loop、Tool Use、Runtime Kernel、受控扩展点
- **OpenClaw**：Gateway、Context Engine、Session / Streaming / Provider 边界
- **传统企业后端经验**：中心编排、依赖倒置、注册表扩展、配置驱动、可测试性优先

当前 Phase 1 的技术收口为：
- 主模型提供方以 **DeepSeek** 为准
- 工具能力以 **`@AgentTool` + `ToolRegistry` + mock 工具** 跑通闭环
- 会话短期状态以 **Redis Hash Transcript** 为准
- 流式输出采用 **provider streaming → runtime event → app SSE** 的分层边界
- 本轮同时要修正 **依赖方向** 与 **POM 表达能力**

### 2.1 Maven 模块落地视图（Phase 1）

当前代码已按 Maven 多模块落地：

- `vi-agent-core-app`：应用入口与装配层（唯一运行模块）
- `vi-agent-core-runtime`：核心运行时与主链路编排
- `vi-agent-core-infra`：基础设施实现层
- `vi-agent-core-model`：内部运行时模型层
- `vi-agent-core-common`：轻量公共能力层

当前代码现实与标准目标已完成本轮关键治理收口：
- `infra -> runtime` 历史反向依赖已移除
- 共享契约 `LlmGateway`、`TranscriptStore`、`@AgentTool`、`ToolBundle` 已下沉到 `model`
- 当前依赖方向已回归到：`runtime/infra -> model/common`

包结构、类职责、模块内依赖规则已下沉到各模块 `AGENTS.md`；本文件不再重复展开类级别明细。

---

## 3. 总体分层（逻辑视图）

### 3.1 接入层（`app/api`）
- **职责**：暴露 REST API、承接 SSE 流式连接、做请求绑定与响应协议输出
- **当前代码落点**：`ChatController`、`ChatStreamController`、`HealthController`、`GlobalExceptionHandler`
- **约束**：
  - 只做接入、参数绑定、协议适配与异常出口
  - 不承载 Runtime 主循环
  - 不直接处理 provider delta / tool fragment 解析
  - 不把同步阻塞逻辑直接跑在 WebFlux 事件线程上

### 3.2 应用入口层（`app/application` + `app/config`）
- **职责**：作为 Facade 与顶层装配层，连接 WebFlux 与 Runtime / Infra
- **当前代码落点**：`ChatApplicationService`、`ChatStreamApplicationService`、`RuntimeCoreConfig`、`ProviderConfig`、`PersistenceConfig`、`ToolConfig`
- **约束**：
  - Application 层只做转发、轻量映射、调度隔离、SSE 适配
  - 配置层只做 Bean 装配，不做业务编排
  - 不把 provider / Redis / tool registry 的实现细节写进 controller

### 3.3 核心运行时层（`runtime`）
- **职责**：统一 run 生命周期、Context 组装、Agent Loop、Tool 协调、Runtime Event 产生
- **当前代码落点**：`RuntimeOrchestrator`、`AgentLoopEngine`、`SimpleAgentLoopEngine`、`ContextAssembler`、`ToolGateway`、`ToolRegistry`、`RuntimeEvent`
- **约束**：
  - `RuntimeOrchestrator` 是唯一主链路编排中心
  - sync / stream 必须共享同一套 Runtime 语义
  - runtime 只产生内部事件，不负责 SSE/Reactor 协议适配
  - 跨层共享契约不应长期挂在 runtime；本轮需向 `model` / `common` 下沉

### 3.4 基础设施层（`infra`）
- **职责**：提供 Provider、Persistence、Observability、Mock Integration 的具体实现
- **当前代码落点**：
  - provider：`DeepSeekChatProvider`、`OpenAICompatibleChatProvider` 等
  - persistence：`TranscriptStoreAdapter`、`RedisTranscriptRepository`、`RedisTranscriptMapper`
  - integration：`MockReadOnlyTools`
  - observability：`TraceContext`、`RuntimeMetricsCollector`
- **约束**：
  - 只做实现与适配，不做主流程编排
  - provider streaming 解析属于 infra，不上移到 app
  - persistence 只管 Redis / MySQL / repository / mapper，不参与 Working Context 组装
  - 本轮必须移除对 runtime 的历史反向依赖

### 3.5 数据模型层（`model`）
- **职责**：定义内部运行时模型与跨层共享契约
- **当前代码落点**：`AbstractMessage`、`UserMessage`、`AssistantMessage`、`ToolExecutionMessage`、`ToolCall`、`ToolResult`、`ConversationTranscript`、`AgentRunContext`
- **本轮重点**：
  - 让主消息模型承接 `turnId`
  - 让 Transcript 记录具备按 turn 追踪能力
  - 逐步承接 `runtime` 与 `infra` 共享的契约

### 3.6 公共能力层（`common`）
- **职责**：承载异常、ID、校验、JSON 等轻量公共能力
- **当前代码落点**：`AgentRuntimeException`、`ErrorCode`、各类 `IdGenerator`、`JsonUtils`、`ValidationUtils`
- **约束**：
  - 只放纯基础能力
  - 不放运行时语义强、需要被 provider / runtime 协作编排的 SPI
  - 不放 Spring Bean、Repository、Provider 调用逻辑

---

## 4. 依赖方向

### 4.1 标准目标依赖方向
```text
common
  ↑
model
  ↑        ↑
runtime   infra
   \      /
      app
```

说明：
- `common` 是最底层
- `model` 在 `common` 之上，承载内部模型与共享契约
- `runtime` 与 `infra` 都依赖 `model` / `common`
- `app` 作为唯一运行入口，装配 `runtime` + `infra`

### 4.2 当前代码中的历史例外
历史例外已在本轮治理完成：
- `infra/pom.xml` 不再依赖 `runtime`
- 以下共享契约已从 `runtime` 下沉到 `model`：
  - `model.port.LlmGateway`
  - `model.port.TranscriptStore`
  - `model.annotation.AgentTool`
  - `model.tool.ToolBundle`

**当前治理结果**：
- `infra` 仅依赖 `model` / `common`
- `runtime` 仅依赖 `model` / `common`
- POM 依赖方向与架构边界保持一致

---

## 5. 核心调用链路

### 5.1 同步对话链路
`POST /api/chat`
→ `ChatController`
→ `ChatApplicationService`
→ `RuntimeOrchestrator.execute(...)`
→ `TranscriptStore` 加载 Transcript
→ `ContextAssembler` 组装 working messages
→ `AgentLoopEngine` 调用 LLM
→ 如识别到 `tool_calls`，经 `ToolGateway` / `ToolRegistry` 执行工具并回填
→ 再次调用 LLM
→ `RuntimeOrchestrator` 保存 Transcript
→ 返回 JSON 响应

当前本轮要求补齐：
- 同步链路中的阻塞调用必须从 WebFlux 事件线程隔离出去
- 错误码与 HTTP 状态映射要分层清晰

### 5.2 流式对话链路
`POST /api/chat/stream`
→ `ChatStreamController`
→ `ChatStreamApplicationService`
→ `RuntimeOrchestrator.executeStreaming(...)`
→ provider streaming
→ runtime 内部产生 token / tool / complete / error 等事件
→ `ChatStreamApplicationService` 适配为 `Flux<ServerSentEvent<...>>`
→ 前端逐段接收

当前本轮要求补齐：
- streaming 入口不能再只是“套一层事件回调后仍走同步 generate”
- runtime 必须真正产出 token/delta 级别事件
- SSE / Reactor 类型继续留在 `app` 层，不侵入 `runtime`

### 5.3 工具调用链路
`@AgentTool` 标记的方法
→ `ToolRegistry` 统一注册
→ `RuntimeOrchestrator` / `AgentLoopEngine` 识别模型提出的 `tool_calls`
→ `ToolGateway` 路由执行
→ `ToolResult` / `ToolExecutionMessage` 回填到 working messages 与 Transcript
→ 再次推理

说明：
- 当前工具以 mock 只读工具为主
- mock 工具也必须经过统一注册、统一路由、统一回填
- 工具闭环属于 Runtime 责任，不允许被 app / infra 旁路实现

### 5.4 状态落盘链路（Phase 1 最小版）
`RuntimeOrchestrator`
→ `TranscriptStore`
→ `TranscriptStoreAdapter`
→ `RedisTranscriptRepository`
→ Redis Hash

当前建议保持的 Redis Hash 口径：
- key：`transcript:{sessionId}`
- field：
  - `conversationId`
  - `traceId`
  - `runId`
  - `messages`
  - `toolCalls`
  - `toolResults`
  - `updatedAt`

说明：
- Redis 当前负责最小恢复，不负责长期治理与审计
- Redis 裁剪 / TTL / MySQL 长期持久化不属于本轮
- 映射与序列化细节属于 `infra.persistence`，不属于 `model`

### 5.5 未来委派链路（预留）
本轮仍只保留 `DelegationCoordinator` 等最小扩展位，不实现完整多代理执行。
相关包结构与接口边界见 `vi-agent-core-runtime/AGENTS.md`。

---

## 6. 关键模块边界说明

| 模块 | 核心职责 | 关键约束 |
| :--- | :--- | :--- |
| `app` | 唯一运行入口、WebFlux 接入、SSE 适配、配置装配 | 不编排 Runtime，不解析 provider streaming 协议 |
| `runtime` | 唯一主链路编排、Loop、Tool 协调、Runtime Event | 不依赖 Web 协议对象，不长期承载共享契约 |
| `infra` | Provider / Persistence / Observability / Mock 实现 | 不主导流程，不长期依赖 runtime |
| `model` | Message / Tool / Transcript / Run 模型与共享契约 | 不承载持久化实现和业务编排 |
| `common` | 异常、ID、JSON、校验等轻量基础能力 | 不膨胀为业务工具箱或 SPI 杂糅层 |

更细的包结构、类职责、模块内依赖规则统一见各模块 `AGENTS.md`。

---

## 7. 当前阶段架构约束（Phase 1）
- 单体部署、逻辑分层
- `RuntimeOrchestrator` 是唯一主链路编排中心
- streaming 必须经过 provider streaming → runtime event → app SSE 适配三段式链路
- 同步 chat 的阻塞路径必须隔离出 WebFlux 事件线程
- `turnId` 要从 run context 继续下沉到消息模型与 transcript
- `infra -> runtime` 历史反向依赖必须在本轮移除
- Redis Transcript 继续作为当前最小正式实现
- 日志脱敏、Redis 裁剪、MySQL 升级不属于本轮阻塞项
- Skill / Delegation / RAG / Approval / Replay / Evaluation 继续后置

---

## 8. 文档模板冻结规则
与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束。
当根目录架构文档出现职责过细时，允许将实现细节下沉到模块 `AGENTS.md`，但不得打乱整体章节结构。

---

## 9. 一句话总结

`ARCHITECTURE.md` 当前只做一件事：把 `app / runtime / infra / model / common` 的边界、依赖方向和主调用链路定死，并明确指出本轮必须修掉 `infra -> runtime` 反向依赖和“假流式”这两个结构性问题。
