# PROJECT_PLAN.md

> 更新日期：2026-04-16

## 1. 文档定位

本文件定义 `vi-agent-core` 的项目阶段规划、路线图与验收口径。

本文件只负责回答：
- 项目长期目标是什么
- 当前处于哪个阶段
- 本阶段做什么、不做什么
- 如何验收

本文件不负责：
- 架构细节（见 `ARCHITECTURE.md`）
- 代码规范（见 `AGENTS.md`）
- 审查标准（见 `CODE_REVIEW.md`）

---

## 2. 项目长期路线图

| 阶段 | 名称 | 核心目标 | 预计时长 |
| :--- | :--- | :--- | :--- |
| **Phase 1** | 核心闭环 + 最小状态底座 | 跑通“推理-工具-再推理”的最小闭环，并补齐最小 Transcript / Trace 基础 | 2-3 周 |
| **Phase 2** | 上下文工程 + 状态记忆 | 实现多轮对话的 Token 管理与裁剪，建立短期记忆与摘要压缩能力 | 2 周 |
| **Phase 3** | Skill System + 多代理受控委派 | 引入技能与子代理，实现任务的模板化拆分与受控委托 | 2-3 周 |
| **Phase 4** | 企业级治理、执行闭环与产品化 | 补齐评估、审计、策略控制、写操作闭环与产品工作台能力 | 2-3 周 |

阶段衔接原则：
- **Phase 1** 为 Runtime Core 建立唯一主链路。
- **Phase 2** 建立 Context / Transcript / Memory 的清晰边界。
- **Phase 3** 在边界稳定后再引入 Skill 与 Delegation。
- **Phase 4** 再补齐 Approval、Replay、Observability、Workbench 等治理与产品化能力。

---

## 3. 当前阶段：Phase 1 - 核心闭环 + 最小状态底座

### 3.1 阶段目标
跑通单 Agent 的完整执行链路，实现“用户输入 → 上下文组装 → Agent Loop → 工具调用 → 结果输出 → 最小状态落盘”的闭环。

当前 Phase 1 已完成项目初始化与五模块治理骨架，现阶段的主目标不是继续扩模块，而是在既有边界内补齐剩余主链路能力：
- 接入真实 `DeepSeekProvider`
- 让 `RuntimeOrchestrator` / `AgentLoopEngine` 跑通真正的“推理 → 工具 → 再推理”
- 让 `ToolRegistry` 具备统一注册机制
- 用 mock 只读工具打通工具调用闭环
- 用 Redis Hash 承接 Phase 1 的最小 Transcript 短期存储
- 接通真正的流式输出链路
- 贯穿最小运行时标识与关键路径日志
- 补齐关键测试

### 3.2 核心任务清单
| 任务 | 当前状态 | 本轮产出 | 验收标准 |
| :--- | :--- | :--- | :--- |
| 1. 项目初始化 | 已完成 | 保持现有五模块骨架与治理文档一致 | 项目可正常编译，`mvn test` 通过 |
| 2. 实现 `LlmProvider` 抽象 | 部分完成 | `DeepSeekProvider` 真实接入；`OpenAiProvider` 保留扩展位 | 可成功调用 DeepSeek API 并返回非流式响应；OpenAI 不作为本轮主验收目标 |
| 3. 实现 `RuntimeOrchestrator` 基础循环 | 部分完成 | 同步版 while loop 与 run 生命周期编排 | 无工具调用时可完成单轮问答；有工具调用时可回填后再推理 |
| 4. 实现 `ToolGateway` 与工具注册 | 部分完成 | 自定义 `@AgentTool` + 统一 `ToolRegistry` 注册机制 | 能扫描并注册至少一个 mock 工具，不再长期依赖手工硬编码工具表 |
| 5. 工具调用闭环 | 未完成 | mock 只读工具闭环 | `RuntimeOrchestrator` 能识别 `tool_calls`、执行 mock 工具、回填 `ToolResult` 并形成最终回答 |
| 6. 流式输出支持 | 部分完成 | `StreamingChatService` + runtime 流式事件边界 + SSE `Flux` | 前端可逐段接收模型输出；WebFlux/SSE 类型不侵入 runtime 核心 |
| 7. 会话持久化（基础版） | 部分完成 | `RedisTranscriptStore` / Transcript 最小实现 | 同 `sessionId` 可从 Redis Hash 恢复最小对话历史（至少包含用户消息、助手回复、工具调用记录） |
| 8. 运行时标识与日志 | 部分完成 | `traceId`、`conversationId`、`sessionId`、`turnId`、`messageId`、`runId`、`toolCallId` 贯穿 | 任意一次请求可定位基础日志链路、工具调用链路与 transcript 链路 |
| 9. 单元测试与集成测试 | 未完成 | 核心路径测试补齐 | 覆盖 Loop 终止条件、工具路由、异常路径、Redis transcript、WebFlux 入口等关键场景 |

当前推荐执行顺序：
1. `DeepSeekProvider`
2. `RuntimeOrchestrator` / `AgentLoopEngine`
3. `ToolGateway` / `ToolRegistry`
4. mock 工具闭环
5. Redis Transcript
6. 运行时标识与日志
7. 流式输出
8. 测试收口

### 3.3 本阶段明确不做
- 子代理委派（Delegation）
- Skill 动态配置
- RAG 知识库检索
- 复杂多模态解析
- 前端页面开发
- 长期记忆与摘要压缩（Phase 2）
- 写操作工具与审批（Phase 4）
- MySQL Transcript 长期持久化实现
- 复杂真实外部工具系统接入
- 多 Provider 并行完善与复杂路由
- 完整 harness engineering / replay / evaluation / approval / policy

### 3.4 关键接口预留（为 Phase 2-4 准备）
```java
// 预留上下文装配接口，Phase 1 简单实现：直接返回全量历史，不做裁剪
public interface ContextAssembler {
    List<Message> assemble(String sessionId, String currentUserMessage);
}

// 预留记忆服务接口
public interface MemoryService {
    void update(String key, String value);
    String get(String key);
}

// 预留委派服务接口
public interface DelegationService {
    AgentRun delegate(DelegationRequest request);
}

// 预留技能注册接口
public interface SkillRegistry {
    Optional<SkillDefinition> findByIntent(String intent);
}
```

要求：
- 这些接口继续保留，但本轮不扩成完整实现。
- P1 期间不要顺手把这些接口做成 Phase 2+ 的重型系统。
- 预留接口的存在是为了后续阶段演进，不是为了在本轮扩任务范围。

### 3.5 Phase 1 设计意图
- **借鉴 Claude Code**：先把 Agent Loop + Tool Use 最小闭环跑通，而不是先做复杂多代理。
- **借鉴 OpenClaw**：先建立入口、运行时、状态记录三者的清晰主链路。
- **借鉴旧项目经验**：先建立“中心编排 + 扩展点注册 + 运行时监控”的骨架，再逐步补功能，不把复杂逻辑堆进入口层。
- **当前决策收口**：本轮以 DeepSeek 作为主模型接入，以 mock 只读工具验证工具闭环，以 Redis 作为短期 Transcript 存储，以 WebFlux 正确链路承接流式输出。

### 3.6 Phase 1 验收口径
必须同时满足以下条件，Phase 1 才算完成：

- 同步对话和流式对话都能走通。
- 至少一个 `DeepSeekProvider` 真实可调用并能返回普通 assistant 回复。
- 至少一个 mock 只读工具能够被模型调用、统一路由、执行并回填结果。
- `ToolRegistry` 具备统一注册机制，不再依赖长期手工写死注册。
- 运行时主链路以 `RuntimeOrchestrator` 为唯一编排中心，并真正实现“推理 → 工具 → 再推理 → 最终回答”。
- 具备最小 Transcript（用户消息、助手回复、工具调用记录）和最小运行时标识体系（`traceId`、`conversationId`、`sessionId`、`turnId`、`messageId`、`runId`、`toolCallId`）。
- Phase 1 Transcript 短期存储以 Redis Hash 落地；可按 `sessionId` 恢复最小上下文。
- controller 已去除同步式 `try/catch/finally` 包裹 `Mono/Flux` 的错误写法，异常与日志边界符合 WebFlux 规范。
- 关键核心模块测试通过且覆盖关键路径。
- 未偷跑 Phase 2+ 的完整实现。

### 3.7 Phase 1 初始化落地快照（2026-04-15）
当前已完成的初始化产物（与仓库实际结构一致）：

- Maven 多模块结构：`vi-agent-core-app`、`vi-agent-core-runtime`、`vi-agent-core-infra`、`vi-agent-core-model`、`vi-agent-core-common`
- 主链路骨架：`RuntimeOrchestrator`（唯一编排中心）+ `AgentLoopEngine` + `ToolGateway` + `SimpleContextAssembler`
- 基础设施骨架：`LlmProvider/OpenAiProvider`、`TranscriptRepository`、`TranscriptStoreService`、`TraceContext`、`RuntimeMetricsCollector`
- WebFlux 入口骨架：`ChatController`、`StreamController`、`ChatService`、`StreamingChatService`、`GlobalExceptionHandler`
- 测试骨架：`RuntimeOrchestratorTest`、`AgentLoopEngineTest`、`ToolGatewayTest`、`ChatControllerTest`

基于当前快照，本轮明确补齐方向如下：
- Provider：从 stub / placeholder 过渡到 `DeepSeekProvider` 真实接入
- Tool：从手工注册过渡到统一 `ToolRegistry`
- Transcript：从 in-memory / repository 骨架过渡到 Redis Hash 最小存储
- Streaming：从占位或假流式过渡到真正的 runtime + WebFlux/SSE 链路
- Logging / IDs：从局部字段过渡到全链路最小贯穿

---

## 4. Phase 2：上下文工程 + 状态记忆（预览）

### 4.1 阶段概要
在 Phase 1 稳定主链路的基础上，实现 Token 计数与滑动窗口裁剪能力，将 Transcript（完整历史）与 Working Context（模型输入）彻底分离。同时引入 `MemoryService` 的真实实现，支持 Agent 通过工具主动读写长期偏好，并注入到 System Prompt 中。

本阶段结束时，系统应具备可控的上下文预算管理和跨会话记忆能力。

### 4.2 核心任务预览
- 实现 `ContextAssembler` 的 Token 计数与裁剪逻辑。
- 分离 `TranscriptStoreService` 与 Working Context 构建。
- 在保留 Redis 短期上下文价值的前提下，引入长期持久化与摘要策略。
- 实现 `MemoryService` 的键值对存储与召回。
- 实现记忆自动注入 System Prompt。

---

## 5. Phase 3：Skill System + 多代理受控委派（预览）

### 5.1 阶段概要
在上下文与记忆边界稳定后，引入 Skill 与 Delegation 能力。实现 `SkillRegistry` 的配置化技能管理，支持按需将 Skill Prompt 注入上下文。同时实现受控委派模式，主 Agent 可通过 `DelegationService` 将特定子任务（如检索、规划）委托给子代理，子代理运行在受限上下文和工具白名单内，返回结构化结果。

### 5.2 核心任务预览
- 实现 `SkillRegistry` 与技能装配。
- 实现 `DelegationService` 与 `SubAgentExecutor`。
- 实现子代理上下文隔离与工具白名单。
- 实现委派链路可观测。

---

## 6. Phase 4：企业级治理、执行闭环与产品化（预览）

### 6.1 阶段概要
在核心能力稳定后，补齐企业级 Agent 系统所需的治理与产品化能力。包括：统一的 Observability（Trace、Metrics、Cost）、工具分级与审批策略（`PolicyService`）、Run 回放与评估（`EvaluationService`），以及通过 `ProductProfile` 将同一套 Runtime 装配为不同产品形态（如留学顾问 vs 代码助手）。

### 6.2 核心任务预览
- 完善 Observability：全链路 Trace、Token 成本统计。
- 实现 `PolicyService`：工具分级与写操作确认。
- 实现 `EvaluationService`：Run 回放与结果对比。
- 实现 `ProductProfile`：产品化装配层。

---

## 7. 文档模板冻结规则
与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：

- 只做增量更新，不改变整体风格与章节结构。
- 阶段变更时，必须在原有章节内补充或新增章节，不得重写全文。

---

## 8. 一句话总结

`PROJECT_PLAN.md` 当前聚焦于 Phase 1：在既有五模块骨架之上，优先补齐 DeepSeek Provider、统一 ToolRegistry、mock 工具闭环、Redis Transcript、Streaming 与关键测试，把 Java Agent Runtime 的主链路真正做成可运行、可恢复、可审查的最小闭环。
