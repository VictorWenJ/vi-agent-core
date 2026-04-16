# AGENTS.md

> 更新日期：2026-04-16

## 1. 文档定位

本文件定义 `vi-agent-core-model` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `model` 模块负责什么
- `model` 模块不负责什么
- `model` 模块内包结构如何约定
- 在 `model` 模块开发时必须遵守哪些局部规则
- `model` 模块测试应如何建设

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

> **模块**：`vi-agent-core-model` — 内部运行时模型模块  
> **模块定位**：承载消息模型、工具模型、Transcript 模型、运行状态模型、Artifact 引用模型  
> **当前目标**：服务于 Phase 1 主链路对象补齐阶段  
> **核心约束**：只承载内部模型与少量模型自身行为；不承载 Web DTO、不承载持久化实现、不承载 Runtime 编排  
> **包结构重点**：`message`、`tool`、`transcript`、`runtime`、`artifact`

---

## 2. 模块定位

`vi-agent-core-model` 是整个 `vi-agent-core` 系统的**内部运行时模型层**，负责定义在 `app`、`runtime`、`infra` 等模块间共享的、与具体实现技术无关的内部数据契约。

模块目标：
- 提供跨模块统一的数据结构定义，包括消息（`Message`）、工具调用（`ToolCall` / `ToolResult`）、运行状态（`AgentRunContext` / `RunState`）、会话转录（`ConversationTranscript`）、大对象引用（`ArtifactRef`）等；
- 保持核心运行时模型与 API 层模型解耦：`model` 模块不包含任何 Web 层注解，也不应直接充当 HTTP 请求或响应体；
- 保持模型的纯粹性与可序列化性，模型类应尽量不可变，或至少在封装边界上保持可控；
- 不包含业务编排、持久化实现、依赖注入等，只承载数据结构与少量模型自身行为；
- 为当前 Phase 1 的 message / tool / transcript / run 语义提供稳定基础，并为后续 Context / Memory / Delegation / Replay 等阶段演进预留空间。

模块在整体依赖链中的位置：

`model` 依赖 `common`，被 `runtime`、`infra`、`app` 共同使用，位于依赖树的较低层。

因此，`model` 模块是：
- **内部统一模型层**
- **Runtime 运行时对象层**
- **消息与状态表达层**

但 `model` 模块不是：
- Web DTO 模块
- 持久化 Entity / Record 模块
- Runtime 编排模块
- Tool 执行模块
- Provider 适配模块
- 公共工具类模块

换句话说：

**`model` 模块负责“表达对象”，不负责“执行业务”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）消息模型
- `Message`
- `BaseMessage`
- `UserMessage`
- `AssistantMessage`
- `SystemMessage`
- `ToolExecutionMessage`
- `MessageRole`

用于表达运行时对话链路中的内部消息对象。

当前 Phase 1 需要明确支持：
- `messageId`
- `role`
- `content`
- assistant message 上的 `toolCalls`
- tool execution message 的标准表达

#### 2）工具模型
- `ToolDefinition`
- `ToolCall`
- `ToolResult`
- `ToolCallStatus`

用于表达工具定义、模型提出的工具调用请求，以及工具执行后的标准化结果。

当前 Phase 1 要求：
- mock 工具与未来真实工具共享统一内部模型
- `ToolDefinition` 继续作为统一注册、暴露和调用的最小契约
- `ToolCall` / `ToolResult` 必须携带后续日志与 transcript 所需最小字段

#### 3）Transcript 模型
- `ConversationTranscript`
- `TranscriptEntry`
- `EntryType`

用于表达一次 session 下的最小完整会话转录与运行时记录聚合对象。

当前 Phase 1 要求：
- 至少记录用户消息、助手回复、工具调用、工具结果
- 允许最小聚合行为，如 append / replace
- transcript 语义独立于具体 Redis / MySQL 存储实现

#### 4）运行状态模型
- `AgentRunContext`
- `AgentRun`
- `ExecutionContext`
- `RunState`
- `TokenUsage`

用于表达当前 run 的上下文、状态和最小运行元信息。

当前 Phase 1 要求：
- 支撑 `traceId`、`runId`、`conversationId`、`sessionId`、`turnId`
- 为 sync / stream 共用运行时语义提供最小对象表达

#### 5）Artifact 引用模型
- `Artifact`
- `ArtifactRef`

用于表达运行时引用的文件、草稿、附件、中间产物的最小引用对象。

---

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `model` 模块：

- HTTP request / response DTO
- 持久化 Entity / Record / Repository
- RuntimeOrchestrator 主编排逻辑
- Agent Loop 循环逻辑
- Tool 选择与路由逻辑
- Provider 调用逻辑
- JSON 工具、字符串工具、校验工具
- 复杂业务流程方法
- Spring Bean、配置类、Controller、Service

如果一个类需要：
- 依赖 Spring 生命周期
- 直接操作数据库
- 决定运行流程
- 直接发起工具调用或模型调用

那它就**不属于 `model` 模块**。

---

## 4. 模块内包结构约定

当前 `model` 模块包结构固定为：

```text
com.vi.agent.core.model
├── artifact/
├── message/
├── runtime/
├── tool/
└── transcript/
```

推荐类分布示例：

```text
com.vi.agent.core.model/
├── artifact/
│   ├── Artifact.java
│   └── ArtifactRef.java
├── message/
│   ├── Message.java
│   ├── BaseMessage.java
│   ├── UserMessage.java
│   ├── AssistantMessage.java
│   ├── SystemMessage.java
│   ├── ToolExecutionMessage.java
│   └── MessageRole.java
├── runtime/
│   ├── AgentRun.java
│   ├── AgentRunContext.java
│   ├── ExecutionContext.java
│   ├── RunState.java
│   └── TokenUsage.java
├── tool/
│   ├── ToolDefinition.java
│   ├── ToolCall.java
│   ├── ToolResult.java
│   └── ToolCallStatus.java
└── transcript/
    ├── ConversationTranscript.java
    ├── TranscriptEntry.java
    └── EntryType.java
```

### 4.1 `artifact/`
职责：
- 表达外部附件、生成草稿、中间产物等的引用对象
- 为运行时传递 artifact 元信息提供统一结构

约束：
- 这里只放“引用模型”，不放 Artifact 存储逻辑
- 不放文件上传、下载、序列化、解析逻辑
- `Artifact` / `ArtifactRef` 应保持轻量

### 4.2 `message/`
职责：
- 表达运行时消息模型
- 统一描述 user / assistant / system / tool execution 等消息类型

约束：
- 消息对象只表达数据与少量模型自身行为
- 不写编排逻辑
- 不写 provider / tool gateway 调用逻辑
- 不混入 Web 层 request/response DTO

建议：
- `Message` 作为顶层接口或抽象契约
- `BaseMessage` 承载公共字段
- 具体子类表达不同角色消息
- 当前阶段应为 `messageId`、tool calls、最小可恢复 transcript 提供稳定字段

### 4.3 `runtime/`
职责：
- 表达一次 run 相关的最小状态对象
- 为运行时链路传递上下文、状态枚举、Token 消耗等提供统一结构

约束：
- 只表达 run 信息，不主导 run 流程
- 不要把 RuntimeOrchestrator 的流程控制塞进这里
- 不要把 TraceContext、MetricsCollector 等 observability 实现对象混进这里

### 4.4 `tool/`
职责：
- 表达工具定义、工具调用请求和执行结果
- 为 runtime 和 infra/provider 之间传递标准化工具对象

约束：
- `ToolDefinition` / `ToolCall` / `ToolResult` 是运行时内部对象
- 不要混入 tool registry、tool executor、schema 路由逻辑
- 不要把 Web DTO 和 Tool 模型混用

`ToolDefinition` 要求：
- 至少包含 `name`、`description`、`parameters`
- `parameters` 应为可扩展的 Schema 表达
- Phase 1 可简化，但必须预留向更标准化 Schema 演进的空间
- mock 工具与真实工具在模型层保持统一表达，不做双套模型

### 4.5 `transcript/`
职责：
- 表达最小完整 session 转录对象
- 承载消息列表、工具调用列表、工具结果列表、系统事件等运行时会话聚合信息

约束：
- `ConversationTranscript` 可以有少量聚合型行为方法，如：
    - `appendMessage`
    - `appendToolCall`
    - `appendToolResult`
    - `replaceLastAssistantMessage`
- 但不得演变成业务编排器
- 不得承担数据库映射职责
- 不得承担 ContextAssembler 或 MemoryService 职责
- Transcript 与 Redis Hash 的映射关系属于 `infra.persistence`，不属于 `model`

---

## 5. 模块局部开发约束

### 5.1 模型层只表达，不编排
`model` 模块的对象可以有**少量与自身封装相关的行为**，但不能承担编排职责。

允许的行为：
- 字段合法性保护
- 集合追加
- defensive copy
- 简单状态替换
- 轻量对象构造便利方法

不允许的行为：
- Agent Loop 控制
- Tool 路由
- Provider 调用
- Repository 存取
- Runtime 主流程判断

### 5.2 API 模型与内部模型严格分离
`model` 层对象不是 Web 协议对象。

严格要求：
- `ChatRequest` / `ChatResponse` / `ApiErrorResponse` 等只属于 `app.controller.dto`
- `Message` / `ToolCall` / `ConversationTranscript` 等只属于 `model`
- 不允许图省事直接把 `model` 对象暴露成 Web 层 DTO
- 不允许把 Web DTO 直接当内部运行时对象传到 `runtime`

### 5.3 Persistence 模型与领域模型严格分离
`model` 层对象不是持久化实体。

严格要求：
- `ConversationTranscript` ≠ `ConversationTranscriptEntity`
- `Message` ≠ `MessageRecord`
- `ToolCall` ≠ `ToolCallRecord`

如果 `infra.persistence` 需要落盘：
- 应该通过映射或转换完成
- 不允许把 JPA / MyBatis / Redis 注解直接写进 `model` 层

### 5.4 Record、不可变性与普通类选择规则
`model` 模块优先追求**清晰、稳定、可序列化**，而不是机械统一。

原则：
- 对纯数据承载、天然不可变、没有复杂封装需求的对象，优先考虑 Java `record`
- 对带有集合封装、append / replace 行为、自定义 getter、defensive copy 的聚合对象，优先使用普通类
- 不要为了“全部 record 化”而牺牲封装边界
- 也不要为了“全部普通类化”而保留大量无意义样板代码

### 5.5 Lombok 使用规则
`model` 模块允许并鼓励使用 Lombok，但必须按对象类型区别处理。

#### 适合优先使用 Lombok 的对象
- 轻量值对象
- 简单运行时状态对象
- 构造语义清晰、字段稳定的模型

#### 必须采用“选择性 Lombok”的对象
- `ConversationTranscript`
- `AgentRunContext`
- 任何带集合封装、append / replace、自定义 getter、防御性拷贝语义的对象

### 5.6 字段与注释规则
- 所有 `record` 组件或普通 POJO 字段必须有中文注释
- 注释至少说明：
    - 字段含义
    - 是否可空
    - 关键约束（若有）
- 若当前阶段部分旧代码尚未完全补齐，后续新增与修改时必须按该规则补全

### 5.7 集合与可变性规则
对含集合字段的模型对象，必须谨慎处理。

要求：
- 不直接暴露可变内部集合
- 如有必要，对外返回只读视图或 defensive copy
- 追加、替换等操作尽量通过显式方法完成
- 不允许外部随意通过 setter 把内部聚合状态改坏

特别是：
- `ConversationTranscript`
- `AgentRunContext`（若后续扩展出集合字段）

这些对象必须优先保证封装性。

### 5.8 构造器与对象创建规则
- 对简单对象，优先使用 Lombok 或 record 替代样板构造器
- 对有强约束的对象，可保留手写构造器
- 若对象创建需要明显表达语义，优先考虑 `@Builder` 或显式工厂方法
- 不要为了减少几行代码，把对象构造语义做模糊

### 5.9 序列化与校验规则
- 所有模型类必须可被 Jackson 正常序列化 / 反序列化
- 不需要额外绑定复杂框架注解才能工作
- 构造器或紧凑构造器内允许做基础校验，如非空、空字符串、非法枚举值
- 复杂业务校验不应放在模型层
- 模型类内部不记录业务日志，校验失败应抛出标准异常或 `IllegalArgumentException`

### 5.10 日志与工具类规则
`model` 模块一般**不应该成为日志重点模块**。

原则：
- 模型对象本身不承载日志职责
- 一般不要在模型类中引入 logger
- 若极少数场景确需日志，必须非常克制，并明确说明原因

同时：
- `model` 模块不得创建 `JsonUtils`、`StringUtils`、`ValidationUtils`
- 需要公共工具时，统一使用 `common.util`
- 不要在 model 对象中写复杂 JSON 转换逻辑
- 不要在 model 对象中写“为了方便序列化”的业务外工具方法

---

## 6. 当前阶段下的 `model` 模块约束
当前阶段内，`model` 模块允许存在：
- 最小消息模型
- 最小工具模型
- 最小 Transcript 模型
- 最小运行状态模型
- 最小 Artifact 引用模型

但不得提前引入：
- 长期记忆模型体系
- RAG 文档治理模型体系
- 子代理复杂协议模型
- Evaluation / Replay 重型模型体系
- Product Profile 配置模型

当前阶段内，`model` 模块的唯一目标是：

**为 Runtime Core 和基础设施层提供最小、稳定、清晰的内部运行时对象表达。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| 消息模型序列化/反序列化 | 单元测试 | 验证主要 Message 实现类能正确序列化为 JSON 并反序列化 |
| `ToolDefinition` / `ToolCall` / `ToolResult` | 单元测试 | 验证字段语义、最小 Schema 结构与构造行为 |
| `ConversationTranscript` | 单元测试 | 覆盖消息追加、工具调用追加、工具结果追加、替换行为、集合封装边界 |
| `AgentRunContext` / `AgentRun` / `ExecutionContext` / `RunState` | 单元测试 | 覆盖最小状态表达与基本行为 |
| Record / 不可变对象 | 单元测试 | 验证不可变性与基本构造约束 |

### 7.2 当前阶段测试目标
- `model` 模块核心对象测试可通过
- `mvn test` 可通过
- 不要求机械追求高覆盖率，但必须覆盖：
    - 聚合对象行为
    - 集合封装边界
    - 关键构造逻辑
    - 基本序列化能力
    - 与 Phase 1 主链路相关的 message / tool / transcript 语义

### 7.3 测试约束
- 优先写单元测试
- 不需要引入 Spring 上下文
- 不要为简单 getter / setter 机械补测试
- 重点测试有行为的模型对象，而不是所有 POJO 平铺测试
- 序列化测试应使用 Jackson `ObjectMapper` 实际验证

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `runtime` / `infra` / `app` / `common` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-model` 的职责是为整个 Agent Runtime 系统提供**最小、稳定、清晰的内部运行时对象表达**；它负责消息、工具、Transcript、运行状态和 Artifact 引用等模型定义，但绝不承担 Runtime 编排、持久化实现或 Web 协议职责。
