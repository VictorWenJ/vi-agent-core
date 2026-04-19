# AGENTS.md

> 更新日期：2026-04-19

## 1. 文档定位

本文件定义 `vi-agent-core-model` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `model` 模块负责什么
- `model` 模块不负责什么
- `model` 模块内包结构如何约定
- 在 `model` 模块开发时必须遵守哪些局部规则
- `model` 模块测试与依赖应如何建设

---

## 2. 模块定位

`vi-agent-core-model` 是整个 `vi-agent-core` 系统的**内部运行时模型层**，同时也是 `runtime` 与 `infra` 共享契约的正式承载层。

标准依赖链位置：
`model -> common`

但 `model` 模块不是：
- 持久化实现层
- Provider 实现层
- Runtime 编排层
- Web DTO 层
- 未启用占位类寄存层

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容
- 消息模型：`AbstractMessage`、`UserMessage`、`AssistantMessage`、`ToolExecutionMessage`
- 工具模型：`ToolCall`、`ToolResult`、`ToolBundle`
- Transcript 模型：`ConversationTranscript`
- 运行状态模型：`AgentRunContext`
-  Memory / Context / Prompt 模型：`SessionStateSnapshot`、`StateDelta`、`ConversationSummary`、`EvidenceRef`、`WorkingContext`、`ContextBudget`、`PromptRenderContext`、`PromptTemplateRef`
- Provider 结果对象：`ModelResponse`、`ModelUsage`
- 跨层共享契约：`LlmGateway`、`TranscriptStore` 等真正被 `runtime` 与 `infra` 共用的接口

### 3.2 本模块明确不负责的内容
以下内容禁止写入 `model` 模块：
- Redis / MySQL repository / mapper / entity 实现
- Provider HTTP 调用与协议解析
- Runtime 主循环与业务编排
- Web 请求 / 响应 DTO
- 任意动态字段集合式 Session State
- 未启用占位类与未启用主代码

---

## 4. 模块内包结构约定

当前 `model` 模块包结构固定为：

```text
com.vi.agent.core.model
├── annotation/
├── context/
├── memory/
├── message/
├── port/
├── prompt/
├── provider/
├── runtime/
├── tool/
└── transcript/
```

### 4.1 包规则
- `port/` 只放真正接口；值对象、结果对象、DTO、record、builder 不得进入 `port`。
- `provider/` 放 LLM 调用结果对象与配套值对象；不放 provider 实现逻辑。
- `memory/` 放 Summary / State / Evidence 对象；`StateDelta` 必须具备正式序列化 / 反序列化闭环。
- `prompt/` 中 `PromptTemplateType` 与模板 key 语义保持一致（`runtime_instruction`、`response_guardrails`、`summary_extract`、`state_extract`）。
- 包名必须全小写，目录语义必须与对象语义一致。

### 4.2 对象构造规则
- 长参数静态工厂统一向 Builder（或Lombok-@Builder注解，跟Lombok-@Builder注解实现的逻辑一致的，必须使用Lombok-@Builder注解）收口。
- 单个静态工厂超过 6 个参数时，默认视为待整改项。
- 不允许继续通过重载构造器层层堆新字段。

### 4.3 可变性规则
- 领域对象优先不可变。
- 对外集合返回只读视图。
- `AgentRunContext`、`ToolCall`、`ToolResult` 等核心对象必须收紧可变性。
- `ToolCall` / `ToolResult` 的关键字段应在构建时补齐，构建后不再随意改写。

### 4.4 内部类规则
- 生产代码中的 entity / dto / command / event / domain object 默认独立成类。
- 只有纯私有、不可复用、强绑定宿主类的 helper 才允许作为内部类。

---

## 5. 模块局部开发约束

### 5.1 命名规则
- 类名必须是名词，接口名必须体现能力，方法名必须是动词。
- 不允许在模型层保留双重语义或模糊语义命名。

### 5.2 工具优先规则
- 字符串判空优先 `StringUtils`。
- 集合判空优先 `CollectionUtils`。
- Map 判空使用统一风格，不在模型层散落多种写法。
- Lombok 注解、成熟工具类或现成 JDK API 能完整替代手写样板时，必须优先使用，不得新增重复实现。

### 5.3 预留能力规则
- 未启用的模型主类默认删除。
- 预留能力如需保留，必须有明确文档依据与归属。

---

## 6. 测试要求
- `StateDelta`、`SessionStateSnapshot`、`ConversationSummary` 必须有序列化 / 反序列化测试。
- `StateDelta` merge 行为必须有专项测试。
- `ToolCall` / `ToolResult` / `AgentRunContext` 的构造与不可变性变更应有回归测试。

---

## 7. 一句话总结

`model` 模块的核心要求是：让接口只像接口、对象只像对象、结果对象只放在结果对象该在的位置，同时通过 Builder（或Lombok-@Builder注解，跟Lombok-@Builder注解实现的逻辑一致的，必须使用Lombok-@Builder注解） 和不可变性约束把模型层收紧成稳定基线。

## 8. 会话双层模型对象补充

- 新增 `SessionMetadataRecord` 作为 session 元数据读取结果对象，承载 conversation-session 映射信息。
- `SessionMetadataStore` 需提供 pair 查询、session 存在性、创建与归档能力。
- `AgentRunContext` 增加 `requestId` 字段，支持 runtime 事件链路透传请求关联。
- `TranscriptStore` 保持按 `sessionId` 读写，不扩展为双 key 接口。
