# PROJECT_PLAN.md

> 更新日期：2026-04-15

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

### 3.2 核心任务清单

| 任务 | 产出 | 验收标准 |
| :--- | :--- | :--- |
| 1. 项目初始化 | `pom.xml`、基础包结构、治理文档 | 项目可正常编译，`mvn test` 通过 |
| 2. 实现 `LlmProvider` 抽象 | `OpenAiProvider` 或 `DeepSeekProvider` | 可成功调用 API 并返回非流式响应 |
| 3. 实现 `RuntimeOrchestrator` 基础循环 | 同步版 `execute(String userInput)` | 无工具调用时，可完成单轮问答 |
| 4. 实现 `ToolGateway` 与工具注册 | 自定义 `@Tool` 注解，`ToolRegistry` | 能扫描并注册至少一个工具 |
| 5. 工具调用闭环 | `RuntimeOrchestrator` 能识别 `tool_calls` 并执行 | 调用 `get_weather` 或等价只读工具并返回结果 |
| 6. 流式输出支持 | `StreamingChatService` + SSE `Flux` | 前端可逐段接收模型输出 |
| 7. 会话持久化（基础版） | `TranscriptRepository` / Transcript 最小实现 | 重启服务后，同 `sessionId` 可恢复最小对话历史（至少包含用户消息、助手回复、工具调用记录） |
| 8. 运行时标识与日志 | `traceId`、`conversationId`、`sessionId`、`turnId`、`messageId`、`runId`、`toolCallId` | 任意一次请求可定位基础日志链路 |
| 9. 单元测试与集成测试 | 覆盖 Loop 终止条件、工具路由、异常路径等 | 覆盖率 > 60% |

### 3.3 本阶段明确不做

- 子代理委派（Delegation）
- Skill 动态配置
- RAG 知识库检索
- 复杂多模态解析
- 前端页面开发
- 长期记忆与摘要压缩（Phase 2）
- 写操作工具与审批（Phase 4）

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

### 3.5 Phase 1 设计意图

- **借鉴 Claude Code**：先把 Agent Loop + Tool Use 最小闭环跑通，而不是先做复杂多代理。
- **借鉴 OpenClaw**：先建立入口、运行时、状态记录三者的清晰主链路。
- **借鉴旧项目经验**：先建立“中心编排 + 扩展点注册 + 运行时监控”的骨架，再逐步补功能，不把复杂逻辑堆进入口层。

### 3.6 Phase 1 验收口径

必须同时满足以下条件，Phase 1 才算完成：

- 同步对话和流式对话都能走通。
- 至少一个只读工具能够被模型调用并回填结果。
- 运行时主链路以 `RuntimeOrchestrator` 为唯一编排中心。
- 具备最小 Transcript（用户消息、助手回复、工具调用记录）和最小运行时标识体系（`conversationId`、`sessionId`、`turnId`、`messageId`、`runId`、`toolCallId`、`traceId`）。
- 关键核心模块测试通过且覆盖率达标。
- 未偷跑 Phase 2+ 的完整实现。

---

## 4. Phase 2：上下文工程 + 状态记忆（预览）

### 4.1 阶段概要

在 Phase 1 稳定主链路的基础上，实现 Token 计数与滑动窗口裁剪能力，将 Transcript（完整历史）与 Working Context（模型输入）彻底分离。同时引入 `MemoryService` 的真实实现，支持 Agent 通过工具主动读写长期偏好，并注入到 System Prompt 中。

本阶段结束时，系统应具备可控的上下文预算管理和跨会话记忆能力。

### 4.2 核心任务预览

- 实现 `ContextAssembler` 的 Token 计数与裁剪逻辑。
- 分离 `TranscriptStoreService` 与 Working Context 构建。
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

`PROJECT_PLAN.md` 当前聚焦于 Phase 1：用 2-3 周时间，在 Java 生态下打造一个具备 Agent Loop + Tool Calling + 最小状态底座的健壮核心运行时，并为后续阶段预留清晰的扩展接口。
