# PROJECT_PLAN.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core` 的项目阶段规划、当前阶段任务、里程碑与验收口径。

本文件只负责回答：
- 项目长期目标是什么
- 当前处于哪个阶段
- 本阶段做什么、不做什么
- 当前里程碑还差什么
- 如何验收

本文件不负责：
- 架构细节与分层职责（见 `ARCHITECTURE.md`）
- 仓库级协作与开发约束（见 `AGENTS.md`）
- 具体包结构、类职责与模块规则（见各模块 `AGENTS.md`）
- 代码审查门禁（见 `CODE_REVIEW.md`）

---

## 2. 项目长期路线图

| 阶段 | 名称 | 核心目标 | 预计时长 |
| :--- | :--- | :--- | :--- |
| **Phase 1** | 核心闭环 + 最小状态底座 | 跑通单 Agent 主链路，补齐 Provider、Tool、Transcript、Streaming、最小状态标识 | 2-3 周 |
| **Phase 2** | 上下文工程 + 状态记忆 | 建立 Working Context、裁剪、短期记忆与摘要能力 | 2 周 |
| **Phase 3** | Skill System + 多代理受控委派 | 引入 Skill 装配与受控子代理边界 | 2-3 周 |
| **Phase 4** | 企业级治理、执行闭环与产品化 | 补齐策略、审计、评估、回放、产品装配与治理闭环 | 2-3 周 |

阶段衔接原则：
- **Phase 1** 先把 Runtime Core 主闭环做真
- **Phase 2** 再处理 Context / Transcript / Memory 的精细化治理
- **Phase 3** 再做 Skill / Delegation / Subagent
- **Phase 4** 再做 Approval / Replay / Evaluation / Product Profile 等治理与产品化能力

---

## 3. 当前阶段：Phase 1 - 核心闭环 + 最小状态底座

### 3.1 阶段目标
跑通单 Agent 的完整执行链路，实现“用户输入 → 上下文组装 → Agent Loop → 工具调用 → 结果输出 → 最小状态落盘”的闭环。

基于 2026-04-17 最新快照，当前 Phase 1 的主目标不是继续扩模块，而是在既有五模块边界内完成以下收口：
- 把 `/api/chat/stream` 从“阶段事件流”升级为“真实模型流式输出链路”
- 让同步 `/api/chat` 不再把阻塞调用跑在 WebFlux 事件线程上
- 把错误码到 HTTP 状态映射做细，不再把所有运行时异常统一打成 400
- 让 `turnId` 真正贯穿 message / transcript 主消息模型
- 移除 `infra -> runtime` 历史反向依赖
- 完成 Maven POM 标准化
- 清理根目录文档与模块文档的职责边界，使文档与代码结构一一映射

### 3.2 核心任务清单
| 任务 | 当前状态 | 本轮产出 | 验收标准 |
| :--- | :--- | :--- | :--- |
| 1. 项目初始化与五模块骨架 | 已完成 | 保持现有模块结构与唯一运行模块方案 | 聚合构建结构稳定，模块边界不新增第六模块 |
| 2. DeepSeek 主 Provider 接入 | 已部分完成 | 保留 `DeepSeekChatProvider` 为主实现，继续收口普通回复与 streaming 解析 | 同步与流式路径都能通过 DeepSeek 返回正确结果 |
| 3. Runtime 主闭环 | 已部分完成 | 保持 `RuntimeOrchestrator` 唯一编排入口，补齐 sync/stream 语义一致性 | 真正实现“推理 → 工具 → 再推理 → 最终回答” |
| 4. 统一工具注册与 mock 工具闭环 | 已部分完成 | 保持 `@AgentTool` + `ToolRegistry` + `ToolGateway` 统一方案 | mock 工具经统一注册、统一路由、统一回填闭环工作 |
| 5. 真正流式输出能力 | 关键缺口 | provider streaming → runtime token event → SSE 适配 | `/api/chat/stream` 可逐段输出模型内容，而不是只输出阶段事件 |
| 6. WebFlux 阻塞隔离 | 未完成 | `/api/chat` 的同步阻塞路径切到合适调度器 | 不直接在 WebFlux 事件线程执行 Redis / Provider / Tool 阻塞调用 |
| 7. 会话持久化（Redis Hash） | 已部分完成 | 保持 Redis Hash Transcript 最小实现 | 可按 `sessionId` 恢复最小会话历史 |
| 8. 运行时标识与消息建模 | 已部分完成 | `turnId` 下沉到主消息模型与 transcript 恢复链路 | 能按 turn 追踪用户消息、助手消息、工具调用与工具结果 |
| 9. 错误码与 HTTP 状态映射 | 未完成 | `ErrorCode -> HttpStatus` 分层映射 | 不再把所有运行时异常统一映射为 400 |
| 10. 依赖治理：移除 `infra -> runtime` | 已完成 | 将跨层共享契约下沉到 `model`（少量基础能力保留在 `common`） | `infra/pom.xml` 不再依赖 `runtime` |
| 11. Maven POM 标准化 | 已完成 | 根 POM 统一管理版本、插件、内部模块依赖；模块 POM 按职责精简 | POM 可清晰表达模块边界与依赖方向 |
| 12. 测试补齐 | 进行中 | 补齐 runtime / infra / persistence / tool / app 关键路径测试 | 不再只有 controller 测试，关键路径测试可支撑本阶段验收 |
| 13. 文档职责重整 | 进行中 | 根目录四文档去细节、模块文档承接包结构与类职责 | 文档与代码结构一一映射，Codex 执行无歧义 |

当前推荐执行顺序：
1. 依赖治理与 POM 标准化
2. 真正流式输出能力
3. 同步 `/chat` 阻塞隔离
4. 错误码与 HTTP 状态映射
5. `turnId` 贯穿主消息模型
6. 测试补齐
7. 文档与代码现实对齐收口

### 3.3 本阶段明确不做
- Redis TTL / trim / 近期上下文裁剪治理
- MySQL Transcript 长期持久化实现
- 日志脱敏与日志瘦身收口
- 长期记忆与摘要压缩
- RAG 检索 / 索引 / 离线构建
- 子代理委派（Delegation）完整实现
- Skill 动态配置与发布
- Approval / Replay / Evaluation / Policy
- 复杂真实外部工具系统
- 前端页面与工作台产品化开发

### 3.4 关键接口与跨层共享契约（为本轮与后续阶段准备）
本阶段仍然保留最小扩展位，但根目录文档不再承载接口代码片段，接口与共享契约的细节统一下沉到模块文档中：

- Runtime 主链路相关：见 `vi-agent-core-runtime/AGENTS.md`
- Provider / Persistence 适配相关：见 `vi-agent-core-infra/AGENTS.md`
- Message / Tool / Transcript / Shared Contract 建模：见 `vi-agent-core-model/AGENTS.md`
- Exception / ID / 公共工具：见 `vi-agent-core-common/AGENTS.md`

当前阶段对“共享契约”的明确要求是：
- 若契约需要被 `runtime` 与 `infra` 同时依赖，应下沉到 `model`
- 只有纯基础、与运行时语义无关的能力才留在 `common`
- 不再让 `runtime` 长期充当跨层 SPI 的寄存地

### 3.5 Phase 1 设计意图
- **借鉴 Claude Code**：先把 Runtime Loop + Tool Use + 流式输出做真，而不是先做多代理或复杂治理
- **借鉴 OpenClaw**：先把 Access / Runtime / State 之间的主路径定死，再逐步补治理与产品化
- **借鉴传统后端工程经验**：先修依赖方向、POM、文档职责边界，再放量开发，避免后面越做越乱
- **当前决策收口**：
  - 主 Provider 以 `DeepSeekChatProvider` 为准
  - 工具以 `@AgentTool` + mock 只读工具验证闭环
  - Transcript 以 Redis Hash 为当前正式实现
  - 文档职责边界一次性收紧，减少 Codex 执行歧义

### 3.6 Phase 1 验收口径
必须同时满足以下条件，Phase 1 才算完成：

- `RuntimeOrchestrator` 仍然是唯一主链路编排中心
- `/api/chat` 与 `/api/chat/stream` 都能跑通
- `/api/chat/stream` 是真正的模型流式输出链路，而不是只有阶段事件
- 同步 `/api/chat` 的阻塞路径已做线程隔离
- 至少一个 `DeepSeekChatProvider` 真实可调用，并能覆盖同步与流式两条路径
- 至少一个 mock 工具能够被统一注册、统一路由、执行并回填结果
- `ToolRegistry` 不再依赖长期手工写死工具表
- Transcript 以 Redis Hash 落地，可按 `sessionId` 恢复最小上下文
- `turnId` 已进入主消息模型与 transcript 恢复链路
- `ErrorCode -> HttpStatus` 已做分类映射
- `infra -> runtime` 反向依赖已被移除
- Maven POM 已完成标准化，能够清楚表达模块职责与依赖方向
- 关键路径测试已补齐并通过
- 未偷跑 Phase 2+ 的完整实现

### 3.7 Phase 1 最新落地快照（2026-04-17）
基于最新快照，当前代码现实如下：

- **app**
  - 已有 `ViAgentCoreApplication`
  - 已有 `ChatController`、`ChatStreamController`、`HealthController`
  - 已有 `ChatApplicationService`、`ChatStreamApplicationService`
  - 已有 `GlobalExceptionHandler`
- **runtime**
  - 已有 `RuntimeOrchestrator`
  - 已有 `AgentLoopEngine`、`SimpleAgentLoopEngine`
  - 已有 `RuntimeEvent`、`RuntimeEventType`
  - 已有 `ToolRegistry`、`ToolGateway`
- **infra**
  - 已有 `DeepSeekChatProvider`、`OpenAIChatProvider`、`DoubaoChatProvider`
  - 已有 `OpenAICompatibleChatProvider`
  - 已有 `TranscriptStoreAdapter`、`RedisTranscriptRepository`、`RedisTranscriptMapper`
  - 已有 `MockReadOnlyTools`
- **model**
  - 已有 `AbstractMessage`、`UserMessage`、`AssistantMessage`、`ToolExecutionMessage`
  - 已有 `ConversationTranscript`、`ToolCall`、`ToolResult`、`AgentRunContext`
  - 已承接共享契约：`LlmGateway`、`TranscriptStore`、`ToolBundle`、`@AgentTool`
- **common**
  - 已有 `AgentRuntimeException`、`ErrorCode`
  - 已有 `TraceIdGenerator`、`RunIdGenerator`、`ConversationIdGenerator`、`TurnIdGenerator`、`MessageIdGenerator`、`ToolCallIdGenerator`
  - 已有 `JsonUtils`、`ValidationUtils`

当前最突出的缺口是：
- streaming 入口最终仍未形成真正的 provider token 输出链路
- 同步 chat 仍存在阻塞隔离问题
- 错误码到 HTTP 状态映射过粗
- `turnId` 还未完整进入主消息模型
- 测试当前只有 controller 级别，远未满足阶段验收要求
- 部分根目录文档仍承载了过细内容，需继续下沉到模块文档

---

## 4. Phase 2：上下文工程 + 状态记忆（预览）

### 4.1 阶段概要
在 Phase 1 稳定主链路的基础上，实现 Working Context、Token 计数、滑动窗口裁剪、短期摘要与长期记忆边界。

### 4.2 核心任务预览
- 实现真正的上下文预算与裁剪
- 分离完整 Transcript 与模型输入上下文
- 引入短期记忆与长期偏好召回
- 推进 Redis / MySQL 分层与状态治理

---

## 5. Phase 3：Skill System + 多代理受控委派（预览）

### 5.1 阶段概要
在上下文与状态边界稳定后，引入 Skill 装配与 Delegation 机制，实现受控子任务拆分。

### 5.2 核心任务预览
- Skill Registry 与技能装配
- 受控子代理与工具白名单
- 子任务结构化返回
- 多代理可观测基础

---

## 6. Phase 4：企业级治理、执行闭环与产品化（预览）

### 6.1 阶段概要
在核心能力稳定后，补齐 Approval、Replay、Evaluation、Policy、Audit 与 Product Profile 等治理和产品化能力。

### 6.2 核心任务预览
- 全链路可观测与成本统计
- 策略与写操作审批
- 运行回放与评估
- 产品装配层与多形态工作台

---

## 7. 文档模板冻结规则
与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：

- 只做增量更新，不改变整体风格与章节结构
- 当阶段目标变化时，优先在现有章节内补充或重整内容
- 当根目录文档职责过载时，允许将细节下沉到模块 `AGENTS.md`

---

## 8. 一句话总结

`PROJECT_PLAN.md` 当前只做一件事：把 Phase 1 还缺的关键收口项说清楚，让实现工作围绕真正流式输出、依赖治理、POM 标准化、状态链路补齐和文档职责收口推进，而不是继续扩阶段和扩范围。
