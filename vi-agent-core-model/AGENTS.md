# AGENTS.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core-model` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `model` 模块负责什么
- `model` 模块不负责什么
- `model` 模块内包结构如何约定
- 在 `model` 模块开发时必须遵守哪些局部规则
- `model` 模块测试与依赖应如何建设

本文件不负责：
- 仓库级协作规则（见根目录 `AGENTS.md`）
- 项目阶段规划（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准（见根目录 `CODE_REVIEW.md`）

执行 `model` 模块相关任务前，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 本文件 `vi-agent-core-model/AGENTS.md`

---

## 1.1 AI 代理必读速览

> **模块**：`vi-agent-core-model` — 内部运行时模型与共享契约模块  
> **模块定位**：承载 Message、Tool、Transcript、RunState、ArtifactRef 等内部对象，并逐步承接跨层共享契约  
> **当前目标**：服务于 Phase 1 收口阶段的消息链路补齐与共享契约下沉  
> **本轮重点**：主消息模型补齐 `turnId`；Transcript 支持按 turn 追踪；为移除 `infra -> runtime` 承接共享契约  
> **核心约束**：只表达对象和少量对象自身行为；不承载 Web DTO、持久化实现、Runtime 编排

---

## 2. 模块定位

`vi-agent-core-model` 是整个 `vi-agent-core` 系统的**内部运行时模型层**。

模块目标：
- 提供统一的 Message / Tool / Transcript / Run 对象表达；
- 为 `runtime`、`infra`、`app` 提供共享的内部数据契约；
- 保持内部模型与 Web DTO、持久化 Entity 解耦；
- 在本轮承接原本错误滞留在 `runtime` 的跨层共享契约。

模块在整体依赖链中的位置：

`model` → `common`

因此，`model` 模块是：
- **内部运行时模型层**
- **消息与状态表达层**
- **跨层共享契约承载层**

但 `model` 模块不是：
- Web DTO 模块
- 持久化 Entity 模块
- Runtime 编排模块
- Provider 实现模块
- 公共工具类模块

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）消息模型
- `Message`
- `AbstractMessage`
- `UserMessage`
- `AssistantMessage`
- `ToolExecutionMessage`

当前 Phase 1 本轮要求：
- 主消息模型补齐 `turnId`
- 保持 `messageId`、`role`、`content`、`createdAt` 稳定
- assistant message 继续承载 `toolCalls`

#### 2）工具模型
- `ToolDefinition`
- `ToolCall`
- `ToolResult`

当前 Phase 1 要求：
- mock 工具与未来真实工具共享统一模型
- `ToolCall` / `ToolResult` 保持最小日志与 transcript 所需字段
- 工具结果表达与执行逻辑分离

#### 3）Transcript 模型
- `ConversationTranscript`

当前 Phase 1 要求：
- 至少记录用户消息、助手回复、工具调用、工具结果
- 支持 append / replace 等聚合行为
- Transcript 记录要支持按 turn 追踪

#### 4）运行状态模型
- `AgentRunContext`
- `AgentRunState`

当前 Phase 1 要求：
- 支撑 `traceId`、`runId`、`conversationId`、`sessionId`、`turnId`
- 为 sync / stream 共用 Runtime 语义提供对象表达

#### 5）Artifact 引用模型
- `ArtifactRef`

#### 6）跨层共享契约（本轮治理目标）
本轮已下沉到 `model` 的共享契约包括：
- `LlmGateway`
- `TranscriptStore`
- `@AgentTool`
- `ToolBundle`

这些类型本质上是跨层共享契约，而不是 Runtime 主循环实现的一部分。

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `model` 模块：

- Web request / response DTO
- Redis / MySQL Entity / Record / Repository
- `RuntimeOrchestrator` 主编排逻辑
- ToolRegistry / ToolGateway / ToolExecutor 路由逻辑
- Provider HTTP 调用逻辑
- JSON / String / Validation 通用工具
- Spring Bean、配置类、Controller、Application Service

---

## 4. 模块内包结构约定

当前 `model` 模块包结构固定为：

```text
com.vi.agent.core.model
├── annotation/
├── artifact/
├── message/
├── port/
├── runtime/
├── tool/
└── transcript/
```

推荐类分布示例与当前代码现实一致：

```text
com.vi.agent.core.model/
├── annotation/
│   └── AgentTool.java
├── artifact/
│   └── ArtifactRef.java
├── message/
│   ├── Message.java
│   ├── AbstractMessage.java
│   ├── UserMessage.java
│   ├── AssistantMessage.java
│   └── ToolExecutionMessage.java
├── port/
│   ├── LlmGateway.java
│   └── TranscriptStore.java
├── runtime/
│   ├── AgentRunContext.java
│   └── AgentRunState.java
├── tool/
│   ├── ToolDefinition.java
│   ├── ToolCall.java
│   ├── ToolResult.java
│   └── ToolBundle.java
└── transcript/
    └── ConversationTranscript.java
```

当前 `model` 已承接跨层共享契约：
- `port/`（`LlmGateway`、`TranscriptStore`）
- `annotation/`（`AgentTool`）
- `tool/ToolBundle`

### 4.1 `artifact/`
职责：
- 表达外部附件、中间结果、草稿等的最小引用对象

约束：
- 这里只放引用模型，不放存储逻辑
- 不放上传、下载、解析逻辑

### 4.2 `message/`
职责：
- 表达运行时消息模型

约束：
- 消息对象只表达数据与少量模型自身行为
- 不写编排逻辑
- 不混入 Web DTO
- 本轮要把 `turnId` 加入主消息模型

### 4.3 `runtime/`
职责：
- 表达一次 run 所需的上下文与状态对象

约束：
- 只表达 run 信息，不主导 run 流程
- 不把 TraceContext、MetricsCollector 这类实现对象混进这里

### 4.4 `tool/`
职责：
- 表达工具定义、工具调用请求与执行结果

约束：
- 不混入 registry、executor、routing 逻辑
- 工具模型要能服务于 Transcript、Provider 与 Runtime 三方协作

### 4.5 `transcript/`
职责：
- 表达会话 Transcript 聚合对象

约束：
- 只放聚合行为，不放 Redis / MySQL 映射实现
- `turnId` 等可追踪字段应在模型层表达，而不是只留给运行时上下文

---

## 5. 模块局部开发约束

### 5.1 模型层只表达，不编排
- `model` 只表达对象和少量对象自身行为
- 不承载流程控制
- 不承载远程调用

### 5.2 API 模型与内部模型严格分离
- `app/api/dto` 是 Web 协议模型
- `model` 是 Runtime 内部模型
- 二者不互相替代

### 5.3 Persistence 模型与领域模型严格分离
- Redis / MySQL entity、record、mapper 不进入 `model`
- `model` 保持存储实现无关

### 5.4 Record、不可变性与普通类选择规则
- 轻量值对象可以考虑 record
- 当前已有模型若继续使用普通类，应保持封装边界清晰
- 对集合字段必须做好 defensive copy 或只读视图

### 5.5 Lombok 使用规则
#### 适合优先使用 Lombok 的对象
- 简单模型、结果对象、配置对象

#### 必须采用“选择性 Lombok”的对象
- `ConversationTranscript`
- `AssistantMessage`
- 带集合保护与 append/replace 语义的对象

### 5.6 字段与注释规则
- 关键字段必须有中文注释
- 本轮新增的 `turnId`、共享契约字段等也必须有清晰注释

### 5.7 集合与可变性规则
- 集合字段优先返回只读视图
- append / replace 行为应集中定义，不让外部随意改内部集合

### 5.8 构造器与对象创建规则
- `message`、`tool`、`transcript`、`runtime state` 等核心运行时模型，禁止通过持续新增重载构造器来适配字段扩展
- 每个核心模型最多保留一个完整主构造器，覆盖全部字段
- 对外创建优先使用语义化静态工厂方法，如 `ofText(...)`、`ofToolPlanning(...)`、`restore(...)`
- 运行时新建与持久化恢复必须使用不同命名入口，不得通过多个近似签名构造器混合表达语义
- 当对象存在多个相同类型参数（如多个 `String`）时，禁止继续增加公开重载构造器
- 不得通过 `null` 位置参数对外表达业务语义

### 5.9 序列化与校验规则
- 模型需要能被 infra 做 JSON 存储与恢复
- 不在模型层直接绑定持久化框架注解

### 5.10 日志与工具类规则
- `model` 通常不应该有业务日志
- 不在模型层引入通用工具类流程代码

### 5.11 依赖管理规则
- `model` 只依赖 `common`
- 不依赖 `runtime` / `infra` / `app`
- 本轮新增共享契约时，也必须满足 `model` 可被 `runtime` 与 `infra` 同时依赖而不产生反向依赖

---

## 6. 当前阶段下的 `model` 模块约束

当前阶段内，`model` 模块允许存在：
- Message / Tool / Transcript / Run / Artifact 等内部对象
- 本轮需要承接的共享契约

但不得提前引入：
- 持久化实现
- Web 协议 DTO
- Runtime 编排逻辑
- 复杂评估 / 回放 / 审批模型

当前阶段内，`model` 模块的唯一目标是：

**把运行时对象表达做稳定，把共享契约放到正确位置，并为后续阶段保留清晰可演进的模型边界。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| Message 模型 | 单元测试 | `messageId`、`turnId`、只读集合等行为 |
| `AssistantMessage` | 单元测试 | `toolCalls` 防御性拷贝与只读暴露 |
| `ConversationTranscript` | 单元测试 | append / replace / 按 turn 记录行为 |
| Tool 模型 | 单元测试 | `ToolCall`、`ToolResult` 字段完整性 |
| 共享契约（本轮新增后） | 单元测试 | 基础反射、类型可用性、无额外依赖污染 |

### 7.2 当前阶段测试目标
- 消息模型补齐 `turnId` 后可测
- Transcript 聚合行为可测
- 共享契约迁移后不引入模块依赖污染

### 7.3 测试约束
- 优先纯单元测试
- 不引入 Spring 上下文
- 不为了测试方便把持久化逻辑塞进模型

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 冻结规则约束。
2. 当 `message/`、`tool/`、`transcript/`、`runtime/`、共享契约承载方式发生变化时，必须同步更新本文件。
3. 若根目录文档中出现了本模块过细模型细节，应回收到本文件。
4. 未经确认，不得改变本文件的布局与章节顺序。
5. 不得把 Web DTO、Redis Entity、Runtime 编排细节长期写进本模块文档。

---

## 9. 一句话总结

`vi-agent-core-model` 的职责，是把 Message / Tool / Transcript / Run 等内部对象表达清楚，并在本轮把跨层共享契约收回到正确位置，避免继续让 `runtime` 充当 SPI 暂存层。
