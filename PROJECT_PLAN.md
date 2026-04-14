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
| **Phase 1** | 核心闭环：Agent Loop + 工具调用 | 跑通“推理-工具-再推理”的最小闭环 | 2-3 周 |
| **Phase 2** | 上下文工程与会话记忆 | 实现多轮对话的 Token 管理与短期记忆 | 2 周 |
| **Phase 3** | 多代理与受控委派 | 引入子代理，实现任务的受控拆分与委托 | 2-3 周 |
| **Phase 4** | 企业级治理与可观测性 | 补齐评估、审计、策略控制与产品化能力 | 2-3 周 |

---

## 3. 当前阶段：Phase 1 - 核心闭环

### 3.1 阶段目标
跑通单 Agent 的完整执行链路，实现“用户输入 → 上下文组装 → Agent Loop → 工具调用 → 结果输出 → 状态落盘”的闭环。

### 3.2 核心任务清单
| 任务 | 产出 | 验收标准 |
| :--- | :--- | :--- |
| 1. 项目初始化 | `pom.xml`、基础包结构、治理文档 | 项目可正常编译，`mvn test` 通过 |
| 2. 实现 `LlmProvider` 抽象 | `OpenAiProvider` 或 `DeepSeekProvider` | 可成功调用 API 并返回非流式响应 |
| 3. 实现 `AgentLoopEngine` 基础循环 | 同步版 `execute(String userInput)` | 无工具调用时，可完成单轮问答 |
| 4. 实现 `ToolGateway` 与工具注册 | 自定义 `@Tool` 注解，`ToolRegistry` | 能扫描并注册至少一个工具 |
| 5. 工具调用闭环 | `AgentLoopEngine` 能识别 `tool_calls` 并执行 | 调用 `get_weather` 工具并返回结果 |
| 6. 流式输出支持 | `StreamingChatService` + SSE `Flux` | 前端可逐字接收模型输出 |
| 7. 会话持久化（基础版） | `ConversationRepository`（MySQL/H2） | 重启服务后，同 sessionId 可恢复对话历史 |
| 8. 单元测试与集成测试 | 覆盖 Loop 终止条件、工具路由等 | 覆盖率 > 60% |

### 3.3 本阶段明确不做
- 子代理委派（Delegation）
- RAG 知识库检索
- 复杂多模态解析
- 前端页面开发
- 长期记忆与摘要（Phase 2）

### 3.4 关键接口预留（为 Phase 2-4 准备）
```java
// 预留上下文装配接口，Phase 1 可先简单返回全量历史
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