# vi-agent-core Message / Tool Runtime 存储与协议投影系统详细设计 v2

> 文档用途：指导 Codex / 实现代理对 `vi-agent-core` 的 message、tool call、tool execution、Redis session context、provider message projection 进行基座级重构。  
> 设计目标：建立长期可扩展、可恢复、可审计、可支持后续 summary / plan / subagent / memory 的标准 Agent Runtime message 基座。  
> 执行原则：当前阶段只解决纯文本 + tool calling + runtime context 的正确存储与协议投影，不引入多模态 content parts，不做历史数据迁移和回滚兼容。  
> 本版修订重点：消除 Codex Review 提出的 P0/P1 歧义，包括 FAILED 幂等返回、`ToolCallMessage` 物理删除、SessionStateLoader 加载范围、provider 前校验失败收口、tool_call_id 唯一性边界、DB/Redis 一致性、Redis snapshot 契约、本轮必做范围边界。

---

## 0. 使用说明

### 0.1 模板适用范围

适用：

- Runtime message 模型重构
- Tool calling 存储与恢复
- `agent_message` / `agent_message_tool_call` / `agent_tool_execution` 表设计
- Redis session context snapshot 重构
- Provider request projection 重构
- `MessageTypeHandlerRegistry` 装配体系建设
- 工具调用历史上下文恢复错误修复
- 后续 summary / checkpoint / plan / subagent 基座预留

不适用：

- 前端 UI 改造
- 多模态 ordered content parts
- 文件 / 图片 / 音频 / 视频输入完整方案
- LlmRouter
- 长期记忆 `agent_memory`
- 完整 plan-and-execute
- subagent 调度
- 生产数据迁移和回滚兼容

### 0.2 填写规则

- 本文档是本轮 message/tool runtime 基座重构的执行依据。
- 若当前代码、旧文档、旧测试与本文档冲突，以本文档为准。
- 本轮处于开发阶段，旧数据无用，允许手动清空 MySQL 和 Redis。
- 不得为了兼容旧 `ToolCallMessage` 设计保留过时逻辑。
- 不得引入与本文档无关的额外架构扩展。

---

## 1. 文档元信息

- 文档名称：`vi-agent-core Message / Tool Runtime 存储与协议投影系统详细设计`
- 变更主题：`重构 Message / Tool Call / Tool Execution 存储模型，物理删除 ToolCallMessage 作为 Message 的错误建模，建立标准 provider-compatible transcript 基座`
- 目标分支 / 迭代：`message-tool-runtime-foundation-refactor`
- 文档版本：`v2.0`
- 状态：`Review`
- 作者：`ChatGPT`
- 评审人：`Victor / Codex Review Agent`
- 关联文档：
  - `AGENTS.md`
  - `ARCHITECTURE.md`
  - `CODE_REVIEW.md`
  - `PROJECT_PLAN.md`
  - `agent-runtime-session-refactor-design-v2.md`
  - `PROJECT_DESIGN_TEMPLATE.md`
  - 本文档参考基线：
    - AI SDK 的 `ModelMessage / tool call / tool result / content parts` 思路
    - Claude Code 的 agent loop / hooks / subagent / context management 思路
    - OpenClaw 的 gateway / tools / skills / self-hosted assistant runtime 思路
    - Hermes Agent 的 persistent agent / profiles / memory / skills 思路

---

## 2. 变更摘要（Executive Summary）

### 2.1 一句话摘要

本次重构将当前 `ToolCallMessage extends Message` 导致的 tool call 元数据丢失、上下文恢复不合法、provider 请求报错等问题，改造成标准 AI Agent Runtime message 基座：`agent_message` 保存真正的 transcript message，`agent_message_tool_call` 保存 assistant message 的 `tool_calls`，`agent_tool_execution` 保存工具执行事实，Redis hash 保存可恢复的 session context snapshot，provider 侧通过 `OpenAICompatibleMessageProjector` 统一投影成 OpenAI-compatible 请求。

### 2.2 本次范围

1. 重构领域模型：
   - 保留 `Message` 接口。
   - 保留 `UserMessage`。
   - 改造 `AssistantMessage`，使其持有 `List<AssistantToolCall>`。
   - 新增 `ToolMessage`，表示 `role=TOOL` 的工具结果消息。
   - 新增 `SystemMessage` 与 `SummaryMessage` 基础模型和 handler。
   - 新增 `AssistantToolCall`。
   - 新增 `ToolExecution`。
   - 物理删除 `ToolCallMessage extends Message`。

2. 重构 MySQL 存储：
   - 修改 `agent_message`。
   - 新增 `agent_message_tool_call`。
   - 新增 `agent_tool_execution`。
   - 新增 `agent_run_event`。
   - 本轮不创建 `agent_checkpoint`，仅在架构规划中保留。

3. 重构 Redis 存储：
   - Redis 全部使用 hash 数据结构。
   - key 统一为 `前缀 + id`。
   - TTL 统一从 yml 读取，单位秒。
   - enum 值统一存 `enum.name()`。
   - session context hash 中必须能恢复 `AssistantMessage.toolCalls` 与 `ToolMessage.toolCallId/toolName`。
   - Redis snapshot 使用稳定 DTO，不直接序列化 Java domain class。

4. 重构消息装配：
   - 新增 `MessageTypeHandler<T extends Message>`。
   - 新增 `MessageTypeHandlerRegistry`。
   - 根据 `role + messageType` 定位 handler。
   - handler 负责 `DB aggregate rows -> domain Message` 与 `domain Message -> DB write plan`。

5. 重构 provider 投影：
   - 新增 `OpenAICompatibleMessageProjector`。
   - `UserMessage -> role=user`。
   - `AssistantMessage -> role=assistant + tool_calls`。
   - `ToolMessage -> role=tool`。
   - `SystemMessage / SummaryMessage -> role=system`。
   - provider 投影前必须校验 tool chain。

6. 重构 runtime loop：
   - 模型返回 tool call 时生成 `AssistantMessage(toolCalls)`。
   - 工具执行完成后生成 `ToolMessage`。
   - 不再生成 `ToolCallMessage`。
   - `PersistenceCoordinator` 保存 message、assistant tool call、tool execution、run event 和 Redis context。

7. 统一 enum 规范：
   - 所有 enum 必须有 `value` 和 `desc` 字段。
   - MySQL 存储 `enum.name()`。
   - Entity 字段使用 enum 类型。
   - Redis 存储 `enum.name()`。
   - provider API 投影使用 `enum.getValue()`。

### 2.3 明确不做

1. 当前阶段不引入 `agent_message_content_part`。
2. 当前阶段不做多模态 ordered content blocks。
3. 当前阶段不做图片 / 文件 / 音频 / 视频输入。
4. 当前阶段不做前端 messageParts 协议。
5. 当前阶段不做历史数据迁移。
6. 当前阶段不做回滚方案。
7. 当前阶段不实现 summary 自动生成。
8. 当前阶段不创建 `agent_checkpoint` 表。
9. 当前阶段不实现完整 plan-and-execute。
10. 当前阶段不实现 subagent 调度。
11. 当前阶段不实现长期记忆表 `agent_memory`。
12. 当前阶段不新增 LlmRouter。
13. 当前阶段不保留 `ToolCallMessage` 作为主链路 message 类型。
14. 当前阶段不通过 `buildCompleteData` 一类逻辑反复查库补字段。
15. 当前阶段不实现 Redis outbox / 异步补偿队列 / 缓存版本对账。

---

## 3. 背景与问题定义

### 3.1 当前现状（仅写与本次直接相关）

- 当前项目中的 `AssistantMessage / ToolCallMessage / ToolResultMessage / UserMessage` 都以 `Message` 子类形式参与 runtime 流转。
- 当前 `agent_message` 主要保存 `AbstractMessage` 公共字段，例如：
  - `message_id`
  - `conversation_id`
  - `session_id`
  - `turn_id`
  - `role`
  - `message_type`
  - `sequence_no`
  - `content`
  - `created_at`
- `ToolCallMessage.toolCallId / toolName / argumentsJson` 等子类字段无法可靠落入 `agent_message`。
- `AssistantMessage.toolCalls` 在 MySQL / Redis 恢复后可能变成空列表。
- `ToolResultMessage.toolCallId / toolName` 在恢复时可能被错误兜底为 `messageId / "tool"`。
- provider 请求中出现过两类错误：
  - 多出一条重复的 `assistant(tool_calls)`。
  - 出现孤立 `tool`，即 `tool` 前面没有 `assistant(tool_calls)`。
- DeepSeek / OpenAI-compatible provider 报错：
  - `Messages with role 'tool' must be a response to a preceding message with 'tool_calls'`
  - `An assistant message with 'tool_calls' must be followed by tool messages responding to each 'tool_call_id'`

### 3.2 已确认问题

1. **错误建模：`ToolCallMessage` 不应作为 transcript message。**  
   标准 OpenAI-compatible transcript 是：
   ```text
   user
   assistant(tool_calls)
   tool
   assistant
   ```
   不是：
   ```text
   user
   assistant
   tool_call
   tool
   assistant
   ```

2. **`agent_message` 只存公共字段，导致 message 类型关键字段丢失。**  
   例如：
   - assistant 的 `toolCalls` 丢失。
   - tool result 的 `toolCallId/toolName` 丢失。
   - provider 投影无法恢复合法 tool chain。

3. **Redis session context 如果只保存残缺 message，会持续传播错误上下文。**  
   即使 MySQL 能补查，Redis hit 后也可能恢复出错误结构：
   ```text
   assistant
   tool
   ```

4. **主流程补查数据库不是标准解决方案。**  
   `buildCompleteData` 之类补救逻辑会导致：
   - 高频 DB 查询。
   - Redis 缓存价值下降。
   - 主链路复杂化。
   - 未来新增 message 类型时继续补丁化。

5. **provider 投影职责不清。**  
   `OpenAICompatibleChatProvider` 不应该依赖脏 message，也不应该用旧 `ToolCallMessage` 推导协议。

6. **缺少统一 MessageTypeHandler 机制。**  
   当前 message 装配和拆解没有按 `role + messageType` 显式分发，容易形成大 switch 或散落逻辑。

7. **enum 使用不统一。**  
   后续所有 enum 必须统一 `value + desc`，MySQL/Redis 存 `enum.name()`，provider 投影使用 `value`。

8. **幂等失败语义存在歧义。**  
   `requestId` 命中 FAILED turn 时必须固定为返回 `runStatus=FAILED` 的幂等结果，不允许一处返回失败响应、一处抛业务错误。

9. **tool_call_id 唯一性边界需要收敛。**  
   Provider 返回的 `tool_call_id` 不应假设全局唯一，必须引入内部全局唯一 `tool_call_record_id`。

10. **DB / Redis 一致性边界需要收敛。**  
    MySQL 多表写入必须事务；Redis commit 后 best-effort，不回滚 DB。

### 3.3 不改风险

如果不重构：

- 工具调用历史上下文会持续出错。
- DeepSeek / OpenAI-compatible provider 会继续返回 400。
- Redis 命中时可能比 MySQL reload 更容易恢复残缺数据。
- 后续 summary / plan / subagent / memory 都会建立在错误 message 模型上。
- Codex 后续开发会继续围绕 `ToolCallMessage` 补丁式修复。
- Java 领域对象、MySQL 事实表、Redis snapshot、provider request 四者会长期不一致。

---

## 4. 项目固定基线（不可绕开）

### 4.1 模块与依赖方向（项目固定基线）

- `vi-agent-core-common`
- `vi-agent-core-model -> common`
- `vi-agent-core-runtime -> model + common`
- `vi-agent-core-infra -> model + common`
- `vi-agent-core-app -> runtime + infra + model + common`

### 4.2 分层职责（项目固定基线）

- `app`：controller / application service / Spring 装配 / 协议适配
- `runtime`：主链路编排、loop、上下文装配、工具协调
- `infra`：provider、DB/Redis repository、外部适配实现
- `model`：领域模型、值对象、port 契约
- `common`：异常、ID、通用无状态工具

### 4.3 代码治理红线（项目固定基线）

- 禁止 `runtime -> infra` 反向依赖。
- 禁止 mapper / dao / repository 混在同一文件。
- 禁止 `model.port` 放 DTO / Command / Record。
- 禁止为兼容旧测试保留已确认过时逻辑。
- 禁止继续把 `ToolCallMessage` 作为标准 `Message`。
- 禁止在 runtime 中拼 provider request。
- 禁止在 provider 中访问 repository。
- 禁止主链路通过二次查库补字段来维持 message 正确性。
- 禁止 Redis snapshot 保存残缺 message。
- 禁止对外 DTO 返回 `traceId`。

---

## 5. 术语、语义与标识

### 5.1 术语表（按需裁剪）

| 术语 | 定义 | 本次是否涉及 |
|---|---|---|
| `conversation` | 前端会话窗口 | Y |
| `session` | 会话窗口下的一段 runtime 生命周期 | Y |
| `turn` | 一次请求执行单元 | Y |
| `run` | 一次 runtime 执行实例 | Y |
| `message` | transcript 中真正进入对话协议的消息，包括 SYSTEM / USER / ASSISTANT / TOOL / SUMMARY | Y |
| `assistant_tool_call` | assistant message 上的 tool_calls 子结构，不是 message | Y |
| `tool_message` | `role=TOOL` 的工具结果消息 | Y |
| `tool_execution` | 工具执行事实，包含入参、输出、耗时、错误、状态 | Y |
| `run_event` | runtime 时间线事件 | Y |
| `checkpoint` | summary / context / plan / subagent / memory 状态快照；本轮不建表 | N |
| `content_part` | 多模态内容块；当前阶段不实现 | N |

### 5.2 标识符定义

| 标识符 | 用途 | 生成方 | 是否对外返回 |
|---|---|---|---|
| `requestId` | 幂等键 | 前端 | 是 |
| `conversationId` | 会话窗口标识 | 后端 | 是 |
| `sessionId` | runtime 生命周期标识 | 后端 | 是 |
| `turnId` | 单次执行标识 | 后端 | 是 |
| `runId` | 单次 runtime 执行标识 | 后端 | 是 |
| `messageId` | transcript message 标识 | 后端 | 是 |
| `toolCallRecordId` | 系统内部工具调用记录 ID，全局唯一 | 后端 | 否 |
| `toolCallId` | provider tool_call_id；不保证全局唯一 | provider / 后端兜底 | 事件中可返回 |
| `toolExecutionId` | 工具执行记录 ID | 后端 | 否 |
| `eventId` | runtime event ID | 后端 | 否 |
| `checkpointId` | checkpoint ID；本轮不使用 | 后端 | 否 |
| `traceId` | 内部观测链路 | 后端 | 否，仅日志/MDC |

### 5.3 显式语义枚举（统一规则）

#### 5.3.1 enum 统一结构

所有 enum 必须遵守以下结构：

```java
@Getter
@AllArgsConstructor
public enum XxxEnum {

    SOME_VALUE("some_value", "中文描述");

    /** 对外协议、日志展示或非数据库场景使用的值。 */
    private final String value;

    /** 枚举含义说明。 */
    private final String desc;
}
```

当前阶段：

```text
value = enum.name().toLowerCase(Locale.ROOT)
desc = 中文描述
```

#### 5.3.2 MySQL enum 存储规则

- MySQL 表字段使用 `VARCHAR`。
- Entity 字段类型使用 enum，不使用 String。
- MySQL 存储 `enum.name()`。
- 不使用 `@EnumValue` 将 `value` 写入数据库。
- MyBatis / MyBatis-Plus 使用 `EnumTypeHandler`。

推荐配置：

```yaml
mybatis-plus:
  configuration:
    default-enum-type-handler: org.apache.ibatis.type.EnumTypeHandler
```

示例：

```java
private MessageRole role;
private MessageType messageType;
private MessageStatus status;
```

数据库值：

```text
role = ASSISTANT
message_type = ASSISTANT_OUTPUT
status = COMPLETED
```

#### 5.3.3 Redis enum 存储规则

- Redis hash 中 enum 也统一存 `enum.name()`。
- Redis 是内部缓存，不是 provider 协议。
- provider 投影时才使用 `enum.getValue()`。

### 5.4 本次核心枚举

#### `MessageRole`

```java
@Getter
@AllArgsConstructor
public enum MessageRole {

    /** 终端用户输入消息。 */
    USER("user", "终端用户输入消息"),

    /** 模型生成的助手输出消息。 */
    ASSISTANT("assistant", "模型生成的助手输出消息"),

    /** 工具侧输出消息。 */
    TOOL("tool", "工具侧输出消息"),

    /** 系统指令消息。 */
    SYSTEM("system", "系统指令消息"),

    /** 记忆压缩摘要消息；对外协议按 system 角色发送。 */
    SUMMARY("summary", "用于记忆压缩的摘要消息");

    private final String value;
    private final String desc;
}
```

#### `MessageType`

```java
@Getter
@AllArgsConstructor
public enum MessageType {

    /** 用户输入消息。 */
    USER_INPUT("user_input", "用户输入消息"),

    /** 助手输出消息。 */
    ASSISTANT_OUTPUT("assistant_output", "助手输出消息"),

    /** 工具结果消息。 */
    TOOL_RESULT("tool_result", "工具结果消息"),

    /** 系统提示消息。 */
    SYSTEM_PROMPT("system_prompt", "系统提示消息"),

    /** 上下文压缩摘要消息。 */
    SUMMARY_CONTEXT("summary_context", "上下文压缩摘要消息");

    private final String value;
    private final String desc;
}
```

禁止：

```java
TOOL_CALL
```

tool call 不再是 message type。

#### `MessageStatus`

```java
@Getter
@AllArgsConstructor
public enum MessageStatus {

    /** 消息已创建。 */
    CREATED("created", "消息已创建"),

    /** 消息流式生成中。 */
    STREAMING("streaming", "消息流式生成中"),

    /** 消息已完成。 */
    COMPLETED("completed", "消息已完成"),

    /** 消息失败。 */
    FAILED("failed", "消息失败");

    private final String value;
    private final String desc;
}
```

#### `ToolCallStatus`

```java
@Getter
@AllArgsConstructor
public enum ToolCallStatus {

    /** 工具调用已创建。 */
    CREATED("created", "工具调用已创建"),

    /** 工具调用执行中。 */
    RUNNING("running", "工具调用执行中"),

    /** 工具调用成功。 */
    SUCCEEDED("succeeded", "工具调用成功"),

    /** 工具调用失败。 */
    FAILED("failed", "工具调用失败");

    private final String value;
    private final String desc;
}
```

#### `ToolExecutionStatus`

```java
@Getter
@AllArgsConstructor
public enum ToolExecutionStatus {

    /** 工具执行中。 */
    RUNNING("running", "工具执行中"),

    /** 工具执行成功。 */
    SUCCEEDED("succeeded", "工具执行成功"),

    /** 工具执行失败。 */
    FAILED("failed", "工具执行失败");

    private final String value;
    private final String desc;
}
```

#### `RunEventType`

```java
@Getter
@AllArgsConstructor
public enum RunEventType {

    RUN_STARTED("run_started", "运行开始"),
    MESSAGE_STARTED("message_started", "消息开始生成"),
    MESSAGE_DELTA("message_delta", "消息增量输出"),
    MESSAGE_COMPLETED("message_completed", "消息生成完成"),
    TOOL_CALL_CREATED("tool_call_created", "工具调用已创建"),
    TOOL_STARTED("tool_started", "工具执行开始"),
    TOOL_COMPLETED("tool_completed", "工具执行完成"),
    RUN_COMPLETED("run_completed", "运行完成"),
    RUN_FAILED("run_failed", "运行失败");

    private final String value;
    private final String desc;
}
```

#### `CheckpointType`

```java
@Getter
@AllArgsConstructor
public enum CheckpointType {

    CONTEXT_WINDOW("context_window", "上下文窗口快照"),
    CONTEXT_SUMMARY("context_summary", "上下文压缩摘要"),
    PLAN_STATE("plan_state", "计划状态快照"),
    SUBAGENT_STATE("subagent_state", "子 Agent 状态快照"),
    MEMORY_STATE("memory_state", "记忆状态快照");

    private final String value;
    private final String desc;
}
```

#### 其他已有 enum

项目中已有的所有 enum，例如：

- `SessionMode`
- `RunStatus`
- `TurnStatus`
- `SessionStatus`
- `ConversationStatus`
- `StreamEventType`
- `FinishReason`
- `ErrorType`
- `ProviderType`

都必须按同一规则补齐：

```java
private final String value;
private final String desc;
```

---

## 6. 对外协议设计（API / Stream）

### 6.1 请求契约

本次不修改 `/api/chat` 的对外字段结构。

```java
class ChatRequest {
    String requestId;
    String conversationId;
    String sessionId;
    SessionMode sessionMode;
    String message;
}
```

字段表：

| 字段 | 类型 | 必填 | 含义 | 校验规则 |
|---|---|---|---|---|
| `requestId` | `String` | Y | 前端幂等 ID | 非空 |
| `conversationId` | `String` | 按 `SessionMode` | 会话窗口 ID | 由 `SessionResolutionService` 校验 |
| `sessionId` | `String` | 按 `SessionMode` | runtime session ID | 由 `SessionResolutionService` 校验 |
| `sessionMode` | `SessionMode` | Y | 会话操作模式 | 非空 |
| `message` | `String` | Y | 当前文本输入 | 非空 |

### 6.2 同步响应契约

本次不修改 `ChatResponse` 对外字段结构。

```java
class ChatResponse {
    String requestId;
    String conversationId;
    String sessionId;
    String turnId;
    String userMessageId;
    String assistantMessageId;
    String runId;
    RunStatus runStatus;
    String content;
    FinishReason finishReason;
    UsageInfo usage;
    Instant createdAt;
}
```

`requestId` 命中 FAILED turn 时：

```text
固定返回 ChatResponse，runStatus=FAILED。
不抛业务错误。
不重新执行。
content 为空字符串或历史失败结果内容；当前阶段统一为空字符串。
finishReason 使用 ERROR / FAILED，如 enum 支持；否则为 null。
```

### 6.3 流式事件契约（按需）

本次不修改 `ChatStreamEvent` 对外字段结构。

```java
class ChatStreamEvent {
    StreamEventType eventType;
    String requestId;
    String conversationId;
    String sessionId;
    String turnId;
    String runId;
    RunStatus runStatus;
    String messageId;
    String delta;
    String content;
    FinishReason finishReason;
    UsageInfo usage;
    ToolCallPayload toolCall;
    ToolResultPayload toolResult;
    ErrorPayload error;
}
```

`requestId` 命中 FAILED turn 时：

```text
发送 RUN_FAILED 事件。
event.runStatus = FAILED。
event.error 从 agent_turn 错误字段恢复。
发送后结束流。
不重新执行。
```

### 6.4 协议红线

- `traceId` 不得出现在对外 DTO。
- 请求语义不得依赖 `conversationId/sessionId` 是否为空进行隐式推断，必须以 `SessionMode` 为准。
- `requestId` 命中幂等时，同步与流式语义必须一致。
- `ToolCallMessage` 不得作为 API DTO 或 transcript message 出现。
- 流式 `TOOL_CALL` 事件返回的是 event payload，不是 `Message`。
- `MESSAGE_DELTA.messageId` 必须是 assistant message ID。
- provider request 必须满足：
  - `assistant(tool_calls)` 后跟对应 `tool`。
  - `tool.tool_call_id` 必须匹配前面 assistant `tool_calls[].id`。
- Summary 若进入 provider 请求，对外角色按 `system` 投影。

---

## 7. 领域模型设计

### 7.1 领域对象清单

| 对象 | 职责 | 所在模块/包 | 本次变更类型 |
|---|---|---|---|
| `Message` | transcript message 接口 | `vi-agent-core-model/.../message` | 修改 |
| `UserMessage` | 用户输入消息 | `model.message` | 修改 |
| `AssistantMessage` | 模型输出消息，持有 `List<AssistantToolCall>` | `model.message` | 修改 |
| `ToolMessage` | 工具结果消息，role=TOOL | `model.message` | 新增 |
| `SystemMessage` | 系统指令消息 | `model.message` | 新增 |
| `SummaryMessage` | 上下文摘要消息，provider 投影为 system | `model.message` | 新增 |
| `AssistantToolCall` | assistant message 的 tool_calls 子结构 | `model.message` | 新增 |
| `ToolExecution` | 工具执行事实 | `model.tool` | 新增 |
| `ToolCallMessage` | 旧错误模型 | `model.message` | 物理删除 |
| `MessageTypeHandler` | 根据 role + messageType 装配 / 拆解 message | `infra.persistence.message` | 新增 |
| `MessageTypeHandlerRegistry` | message handler 注册表 | `infra.persistence.message` | 新增 |
| `MessageAggregateRows` | MySQL 聚合行对象 | `infra.persistence.mysql.message` | 新增 |
| `MessageWritePlan` | MySQL 写入计划 | `infra.persistence.mysql.message` | 新增 |
| `OpenAICompatibleMessageProjector` | domain message -> OpenAI-compatible protocol | `infra.provider.openai` | 新增 |

### 7.2 关键领域对象

#### `Message`

```java
public interface Message {

    String getMessageId();

    String getConversationId();

    String getSessionId();

    String getTurnId();

    String getRunId();

    MessageRole getRole();

    MessageType getMessageType();

    Long getSequenceNo();

    MessageStatus getStatus();

    String getContentText();

    Instant getCreatedAt();
}
```

#### `UserMessage`

```java
@Getter
@Builder
public class UserMessage implements Message {

    private final String messageId;
    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;
    private final Long sequenceNo;
    private final MessageStatus status;
    private final String contentText;
    private final Instant createdAt;

    @Override
    public MessageRole getRole() {
        return MessageRole.USER;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.USER_INPUT;
    }
}
```

#### `AssistantMessage`

```java
@Getter
@Builder
public class AssistantMessage implements Message {

    private final String messageId;
    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;
    private final Long sequenceNo;
    private final MessageStatus status;
    private final String contentText;

    private final List<AssistantToolCall> toolCalls;

    private final FinishReason finishReason;
    private final UsageInfo usage;

    private final Instant createdAt;

    @Override
    public MessageRole getRole() {
        return MessageRole.ASSISTANT;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.ASSISTANT_OUTPUT;
    }
}
```

#### `AssistantToolCall`

```java
@Getter
@Builder
public class AssistantToolCall {

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String assistantMessageId;

    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;

    private final String toolName;

    private final String argumentsJson;

    private final Integer callIndex;

    private final ToolCallStatus status;

    private final Instant createdAt;
}
```

#### `ToolMessage`

```java
@Getter
@Builder
public class ToolMessage implements Message {

    private final String messageId;
    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;
    private final Long sequenceNo;
    private final MessageStatus status;

    private final String contentText;

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String toolName;

    private final ToolExecutionStatus executionStatus;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;

    private final Instant createdAt;

    @Override
    public MessageRole getRole() {
        return MessageRole.TOOL;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.TOOL_RESULT;
    }
}
```

#### `SystemMessage`

```java
@Getter
@Builder
public class SystemMessage implements Message {

    private final String messageId;
    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;
    private final Long sequenceNo;
    private final MessageStatus status;
    private final String contentText;
    private final Instant createdAt;

    @Override
    public MessageRole getRole() {
        return MessageRole.SYSTEM;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SYSTEM_PROMPT;
    }
}
```

#### `SummaryMessage`

```java
@Getter
@Builder
public class SummaryMessage implements Message {

    private final String messageId;
    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;
    private final Long sequenceNo;
    private final MessageStatus status;
    private final String contentText;
    private final Long coveredFromSequenceNo;
    private final Long coveredToSequenceNo;
    private final Instant createdAt;

    @Override
    public MessageRole getRole() {
        return MessageRole.SUMMARY;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SUMMARY_CONTEXT;
    }
}
```

#### `ToolExecution`

```java
@Getter
@Builder
public class ToolExecution {

    private final String toolExecutionId;

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String toolResultMessageId;

    private final String conversationId;
    private final String sessionId;
    private final String turnId;
    private final String runId;

    private final String toolName;

    private final String argumentsJson;

    private final String outputText;

    private final String outputJson;

    private final ToolExecutionStatus status;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;

    private final Instant startedAt;

    private final Instant completedAt;

    private final Instant createdAt;
}
```

### 7.3 领域规则与状态流转

1. `ToolCallMessage` 必须物理删除。
2. tool call 是 `AssistantMessage.toolCalls`。
3. tool result 是 `ToolMessage`。
4. `agent_message` 只保存真正的 transcript message：
   - `SYSTEM`
   - `USER`
   - `ASSISTANT`
   - `TOOL`
   - `SUMMARY`
5. `AssistantToolCall.toolCallRecordId` 是系统内部全局唯一 ID。
6. `AssistantToolCall.toolCallId` 是 provider tool call ID，不保证全局唯一。
7. `AssistantToolCall.toolCallId` 在同一个 `assistantMessageId` 下唯一。
8. `ToolMessage.toolCallRecordId` 必须匹配一个 `AssistantToolCall.toolCallRecordId`。
9. `ToolMessage.toolCallId` 必须匹配对应 `AssistantToolCall.toolCallId`。
10. 一个 `AssistantMessage` 可以有多个 `AssistantToolCall`。
11. 一个 `AssistantToolCall` 至多对应一个 `ToolExecution`。
12. 一个 `ToolExecution` 应关联一个 `ToolMessage`。
13. provider 投影前必须校验 tool chain。
14. 当前阶段 `Message` 不包含 `MessagePart`。
15. 当前阶段纯文本内容统一放 `contentText`。
16. 当前阶段不处理附件和多模态。
17. Summary 当前只做基础模型和 handler，不自动生成。

---

## 8. 持久化与数据设计

### 8.1 事实源与缓存边界

- 事实源：MySQL。
- 缓存层职责：Redis hash 保存 session context snapshot、requestId 幂等辅助、session lock。
- Redis 不是完整审计事实源。
- MySQL 保存完整事实：
  - message
  - assistant tool call
  - tool execution
  - run event
- miss 回填策略：
  1. `SessionStateLoader` 先读 Redis hash。
  2. Redis hit：解析 `messagesJson` 恢复 domain messages。
  3. Redis miss：从 MySQL 批量读取 `agent_message + agent_message_tool_call + agent_tool_execution`。
  4. 使用 `MessageTypeHandlerRegistry` 组装 `List<Message>`。
  5. 写回 Redis hash 并设置 TTL。

当前开发阶段：

```text
不做旧数据迁移。
不做回滚策略。
允许手动清库。
旧数据无用。
```

### 8.2 关系型存储设计

| 表名 | 用途 | 主键/唯一约束 | 关键索引 | 本次动作 |
|---|---|---|---|---|
| `agent_message` | transcript message 主表 | `uk_agent_message_message_id` | `(session_id, sequence_no)` | 修改 |
| `agent_message_tool_call` | assistant message 的 tool_calls 子表 | `uk_tool_call_record_id`, `uk_assistant_tool_call` | `assistant_message_id`, `turn_id`, `session_id` | 新增 |
| `agent_tool_execution` | 工具执行事实表 | `uk_agent_tool_execution_id` | `tool_call_record_id`, `tool_call_id`, `tool_result_message_id`, `turn_id` | 新增 |
| `agent_run_event` | runtime 时间线事件表 | `uk_agent_run_event_id`, `(run_id,event_index)` | `(turn_id,event_index)`, `(session_id,created_at)` | 新增 |
| `agent_checkpoint` | checkpoint 表 | N/A | N/A | 本轮不创建 |
| 旧 `agent_tool_call` | 旧工具调用表 | N/A | N/A | 废弃 |
| 旧 `agent_tool_result` | 旧工具结果表 | N/A | N/A | 废弃 |

### 8.2.1 `agent_message`

```sql
CREATE TABLE agent_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',

  message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '会话窗口ID',
  session_id VARCHAR(64) NOT NULL COMMENT 'runtime session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn ID',
  run_id VARCHAR(64) DEFAULT NULL COMMENT 'run ID',

  role VARCHAR(32) NOT NULL COMMENT '枚举名：MessageRole.name()',
  message_type VARCHAR(64) NOT NULL COMMENT '枚举名：MessageType.name()',
  sequence_no BIGINT NOT NULL COMMENT 'session内消息顺序',
  status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '枚举名：MessageStatus.name()',

  content_text MEDIUMTEXT DEFAULT NULL COMMENT '消息文本内容',

  tool_call_record_id VARCHAR(64) DEFAULT NULL COMMENT 'role=TOOL时对应系统内部tool_call_record_id',
  tool_call_id VARCHAR(128) DEFAULT NULL COMMENT 'role=TOOL时对应provider tool_call_id',
  tool_name VARCHAR(128) DEFAULT NULL COMMENT 'role=TOOL时工具名',

  provider VARCHAR(64) DEFAULT NULL COMMENT 'provider',
  model VARCHAR(128) DEFAULT NULL COMMENT 'model',
  finish_reason VARCHAR(64) DEFAULT NULL COMMENT '枚举名：FinishReason.name()',
  metadata_json MEDIUMTEXT DEFAULT NULL COMMENT '扩展元数据JSON',

  created_at DATETIME(6) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(6) NOT NULL COMMENT '更新时间',

  UNIQUE KEY uk_agent_message_message_id (message_id),
  UNIQUE KEY uk_agent_message_session_sequence (session_id, sequence_no),
  KEY idx_agent_message_turn_id (turn_id),
  KEY idx_agent_message_session_id (session_id),
  KEY idx_agent_message_tool_call_record_id (tool_call_record_id),
  KEY idx_agent_message_tool_call_id (tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent transcript message主表';
```

Entity：

```java
@TableName("agent_message")
@Getter
@Setter
public class AgentMessageEntity {

    private Long id;

    private String messageId;
    private String conversationId;
    private String sessionId;
    private String turnId;
    private String runId;

    private MessageRole role;
    private MessageType messageType;
    private Long sequenceNo;
    private MessageStatus status;

    private String contentText;

    private String toolCallRecordId;
    private String toolCallId;
    private String toolName;

    private String provider;
    private String model;
    private FinishReason finishReason;
    private String metadataJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 8.2.2 `agent_message_tool_call`

```sql
CREATE TABLE agent_message_tool_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',

  tool_call_record_id VARCHAR(64) NOT NULL COMMENT '系统内部工具调用记录ID，全局唯一',
  tool_call_id VARCHAR(128) NOT NULL COMMENT 'provider tool_call_id',
  assistant_message_id VARCHAR(64) NOT NULL COMMENT '触发该tool_call的assistant message_id',

  conversation_id VARCHAR(64) NOT NULL COMMENT '会话窗口ID',
  session_id VARCHAR(64) NOT NULL COMMENT 'session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn ID',
  run_id VARCHAR(64) DEFAULT NULL COMMENT 'run ID',

  tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
  arguments_json MEDIUMTEXT NOT NULL COMMENT '工具调用参数JSON',
  call_index INT NOT NULL DEFAULT 0 COMMENT '同一assistant message下第几个tool call',
  status VARCHAR(32) NOT NULL COMMENT '枚举名：ToolCallStatus.name()',

  created_at DATETIME(6) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(6) NOT NULL COMMENT '更新时间',

  UNIQUE KEY uk_tool_call_record_id (tool_call_record_id),
  UNIQUE KEY uk_assistant_tool_call (assistant_message_id, tool_call_id),
  KEY idx_agent_message_tool_call_assistant (assistant_message_id),
  KEY idx_agent_message_tool_call_turn (turn_id),
  KEY idx_agent_message_tool_call_session (session_id, created_at),
  KEY idx_agent_message_tool_call_provider_id (tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Assistant message tool_calls子表';
```

Entity：

```java
@TableName("agent_message_tool_call")
@Getter
@Setter
public class AgentMessageToolCallEntity {

    private Long id;

    private String toolCallRecordId;
    private String toolCallId;
    private String assistantMessageId;

    private String conversationId;
    private String sessionId;
    private String turnId;
    private String runId;

    private String toolName;
    private String argumentsJson;
    private Integer callIndex;
    private ToolCallStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 8.2.3 `agent_tool_execution`

```sql
CREATE TABLE agent_tool_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',

  tool_execution_id VARCHAR(64) NOT NULL COMMENT '工具执行记录ID',
  tool_call_record_id VARCHAR(64) NOT NULL COMMENT '系统内部工具调用记录ID',
  tool_call_id VARCHAR(128) NOT NULL COMMENT 'provider tool_call_id',
  tool_result_message_id VARCHAR(64) DEFAULT NULL COMMENT '对应role=TOOL的message_id',

  conversation_id VARCHAR(64) NOT NULL COMMENT '会话窗口ID',
  session_id VARCHAR(64) NOT NULL COMMENT 'session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn ID',
  run_id VARCHAR(64) DEFAULT NULL COMMENT 'run ID',

  tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
  arguments_json MEDIUMTEXT DEFAULT NULL COMMENT '工具入参JSON',
  output_text MEDIUMTEXT DEFAULT NULL COMMENT '工具输出文本',
  output_json MEDIUMTEXT DEFAULT NULL COMMENT '工具输出JSON',

  status VARCHAR(32) NOT NULL COMMENT '枚举名：ToolExecutionStatus.name()',
  error_code VARCHAR(128) DEFAULT NULL COMMENT '错误码',
  error_message TEXT DEFAULT NULL COMMENT '错误信息',
  duration_ms BIGINT DEFAULT NULL COMMENT '执行耗时',

  started_at DATETIME(6) DEFAULT NULL COMMENT '开始时间',
  completed_at DATETIME(6) DEFAULT NULL COMMENT '完成时间',
  created_at DATETIME(6) NOT NULL COMMENT '创建时间',

  UNIQUE KEY uk_agent_tool_execution_id (tool_execution_id),
  KEY idx_agent_tool_execution_record (tool_call_record_id),
  KEY idx_agent_tool_execution_call (tool_call_id),
  KEY idx_agent_tool_execution_result_message (tool_result_message_id),
  KEY idx_agent_tool_execution_turn (turn_id),
  KEY idx_agent_tool_execution_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具执行事实表';
```

Entity：

```java
@TableName("agent_tool_execution")
@Getter
@Setter
public class AgentToolExecutionEntity {

    private Long id;

    private String toolExecutionId;
    private String toolCallRecordId;
    private String toolCallId;
    private String toolResultMessageId;

    private String conversationId;
    private String sessionId;
    private String turnId;
    private String runId;

    private String toolName;
    private String argumentsJson;
    private String outputText;
    private String outputJson;

    private ToolExecutionStatus status;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
```

### 8.2.4 `agent_run_event`

```sql
CREATE TABLE agent_run_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',

  event_id VARCHAR(64) NOT NULL COMMENT '事件ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '会话窗口ID',
  session_id VARCHAR(64) NOT NULL COMMENT 'session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn ID',
  run_id VARCHAR(64) NOT NULL COMMENT 'run ID',

  event_index BIGINT NOT NULL COMMENT 'run内事件顺序',
  event_type VARCHAR(64) NOT NULL COMMENT '枚举名：RunEventType.name()',
  actor_type VARCHAR(64) DEFAULT NULL COMMENT '枚举名：ActorType.name()',
  actor_id VARCHAR(128) DEFAULT NULL COMMENT 'actor标识',

  payload_json MEDIUMTEXT DEFAULT NULL COMMENT '事件payload JSON',
  created_at DATETIME(6) NOT NULL COMMENT '创建时间',

  UNIQUE KEY uk_agent_run_event_id (event_id),
  UNIQUE KEY uk_agent_run_event_run_index (run_id, event_index),
  KEY idx_agent_run_event_turn_index (turn_id, event_index),
  KEY idx_agent_run_event_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent runtime事件时间线表';
```

### 8.3 Redis / Hash 结构

### 8.3.1 Redis 总规则

```text
1. 所有 Redis 数据类型均使用 hash。
2. key 格式为：前缀 + id。
3. hash field 为字段名。
4. hash value 为字符串。
5. 复杂对象存 JSON 字符串。
6. enum 存 enum.name()。
7. TTL 统一在 yml 配置，单位秒。
8. Redis 不直接序列化 Java domain class，不保存 Java class name。
9. Redis session context 使用稳定 snapshot DTO。
```

### 8.3.2 Redis 配置

```yaml
vi:
  agent:
    redis:
      ttl:
        session-context-seconds: 604800
        request-cache-seconds: 86400
        session-lock-seconds: 60
```

### 8.3.3 `agent:session:context:{sessionId}`

用途：

```text
缓存 session 的 provider-compatible domain context snapshot。
```

Redis hash key：

```text
agent:session:context:{sessionId}
```

fields：

| field | value |
|---|---|
| `sessionId` | `Sess-xxx` |
| `conversationId` | `conv-xxx` |
| `fromSequenceNo` | `1` |
| `toSequenceNo` | `120` |
| `messageCount` | `120` |
| `snapshotVersion` | `1` |
| `messagesJson` | `SessionContextMessageSnapshot[]` JSON |
| `updatedAtEpochMs` | `1776769871111` |

当前只支持：

```text
snapshotVersion = 1
```

#### `SessionContextMessageSnapshot`

公共字段：

```json
{
  "messageId": "msg_xxx",
  "conversationId": "conv_xxx",
  "sessionId": "Sess_xxx",
  "turnId": "turn_xxx",
  "runId": "run_xxx",
  "role": "ASSISTANT",
  "messageType": "ASSISTANT_OUTPUT",
  "sequenceNo": 12,
  "status": "COMPLETED",
  "contentText": "我来查询时间。",
  "createdAtEpochMs": 1776769871111
}
```

USER 必填：

```text
公共字段
contentText
```

ASSISTANT 必填：

```text
公共字段
toolCalls
```

`toolCalls` 必须存在，没工具调用时为空数组。

```json
{
  "toolCallRecordId": "tcr_xxx",
  "toolCallId": "call_xxx",
  "assistantMessageId": "msg_assistant_xxx",
  "toolName": "get_time",
  "argumentsJson": "{}",
  "callIndex": 0,
  "status": "SUCCEEDED"
}
```

TOOL 必填：

```text
公共字段
toolCallRecordId
toolCallId
toolName
executionStatus
```

可选：

```text
errorCode
errorMessage
durationMs
```

SYSTEM / SUMMARY 必填：

```text
公共字段
contentText
```

#### Redis 版本处理

如果 Redis 中 `snapshotVersion != 1`：

```text
1. 删除当前 Redis key。
2. 从 MySQL 重新加载。
3. 重建 snapshotVersion=1。
```

不做 Redis 内部版本迁移。

#### Redis 反序列化失败处理

如果出现：

- `messagesJson` 解析失败
- 必填字段缺失
- enum 无法恢复
- tool chain 校验失败
- snapshotVersion 不支持

则执行：

```text
1. evict 当前 Redis key
2. 从 MySQL reload 一次
3. reload 成功：重写 Redis 并返回
4. reload 失败：当前 turn 标记 FAILED
5. 写 RUN_FAILED run_event
6. session 保持 ACTIVE
7. 返回/发送 INVALID_SESSION_CONTEXT 失败结果
```

### 8.3.4 `agent:request:{requestId}`

用途：

```text
requestId 幂等辅助缓存。
```

Redis hash key：

```text
agent:request:{requestId}
```

fields：

| field | value |
|---|---|
| `requestId` | `R_001` |
| `conversationId` | `conv-xxx` |
| `sessionId` | `Sess-xxx` |
| `turnId` | `turn-xxx` |
| `runId` | `run-xxx` |
| `runStatus` | `RUNNING` |
| `createdAtEpochMs` | `1776769871111` |

说明：

```text
Redis request cache 只是辅助。
最终幂等事实以 MySQL agent_turn 为准。
```

### 8.3.5 `agent:session:lock:{sessionId}`

用途：

```text
session 并发锁。
```

Redis hash key：

```text
agent:session:lock:{sessionId}
```

fields：

| field | value |
|---|---|
| `sessionId` | `Sess-xxx` |
| `runId` | `run-xxx` |
| `lockedAtEpochMs` | `1776769871111` |

加锁使用 Lua 保证原子性：

```lua
if redis.call('EXISTS', KEYS[1]) == 0 then
  redis.call('HSET', KEYS[1],
    'sessionId', ARGV[1],
    'runId', ARGV[2],
    'lockedAtEpochMs', ARGV[3]
  )
  redis.call('EXPIRE', KEYS[1], ARGV[4])
  return 1
else
  return 0
end
```

释放锁使用 Lua 校验 `runId`：

```lua
local runId = redis.call('HGET', KEYS[1], 'runId')
if runId == ARGV[1] then
  redis.call('DEL', KEYS[1])
  return 1
else
  return 0
end
```

### 8.4 DB / Redis 一致性

#### 8.4.1 MySQL 成功路径事务

成功路径必须在一个 MySQL 事务中完成：

```text
1. agent_turn 状态更新为 COMPLETED
2. agent_message 批量写入
3. agent_message_tool_call 批量写入
4. agent_tool_execution 批量写入
5. agent_run_event 批量写入
6. agent_session touch / last_turn 更新
7. agent_conversation last_message_at 更新，如当前已有该逻辑
```

如果事务中任一步失败：

```text
事务整体回滚。
turn 不得标记为 COMPLETED。
```

#### 8.4.2 MySQL 失败路径事务

失败路径必须在一个 MySQL 事务中完成：

```text
1. agent_turn 状态更新为 FAILED
2. agent_run_event 写 RUN_FAILED
3. 必要的失败 message / error metadata 写入
4. agent_session 保持 ACTIVE，只 touch 更新时间
```

#### 8.4.3 Redis 写入策略

Redis 不进入 MySQL 事务。

成功路径：

```text
MySQL commit 成功
-> 尝试刷新 agent:session:context:{sessionId}
-> 设置 TTL
```

如果 Redis save 失败：

```text
1. 记录 WARN 日志。
2. 尝试删除 agent:session:context:{sessionId}。
3. 不回滚 MySQL。
4. 不改变本次响应结果。
```

如果删除也失败：

```text
记录 WARN。
等待 TTL 过期。
```

失败路径：

```text
MySQL commit 成功
-> evict Redis session context
```

如果 evict 失败：

```text
记录 WARN。
不回滚 DB。
```

当前阶段不做：

```text
Redis 补偿队列
Outbox
异步重试
缓存版本对账
```

### 8.5 迁移策略

当前开发阶段：

```text
迁移机制：N/A
脚本命名：N/A
回滚策略：N/A
```

执行原则：

```text
1. 不做旧数据迁移。
2. 不做回滚兼容。
3. 开发环境允许手动清库。
4. 旧数据无用。
5. 可直接重建表结构。
```

---

## 9. Runtime 主链路设计

### 9.1 角色职责分配

| 组件 | 职责 | 本次变更 |
|---|---|---|
| `RuntimeOrchestrator` | run-level orchestration | 不改主职责，只使用新 Message 模型 |
| `AgentLoopEngine` | llm-tool loop owner | 改为生成 `AssistantMessage(toolCalls)` 与 `ToolMessage`，不再生成 `ToolCallMessage` |
| `SessionStateLoader` | 加载 session context | 只加载 COMPLETED turn；Redis hash hit 或 MySQL batch load |
| `PersistenceCoordinator` | 持久化协调 | 保存 message/toolCall/toolExecution/runEvent/Redis hash |
| `MessageTypeHandlerRegistry` | 根据 role + messageType 管理 message 装配/拆解 | 新增 |
| `OpenAICompatibleMessageProjector` | provider 协议投影 | 新增 |
| `RedisSessionContextRepository` | Redis hash 读写 | 修改 |
| `RedisSessionLockRepository` | Redis hash 锁 | 修改 |
| `RedisRequestCacheRepository` | requestId hash 缓存 | 新增/按需 |

### 9.2 SessionStateLoader 加载范围

最终规则：

```text
SessionStateLoader 只加载 COMPLETED turn 的消息进入上下文。
```

排除：

```text
RUNNING
FAILED
CANCELLED
```

当前 turn 的 user message 不由 `SessionStateLoader` 读取，而是由 `AgentRunContextFactory` 追加。

流程：

```text
SessionStateLoader.load(sessionId)
-> 返回历史 COMPLETED turns 的 messages

AgentRunContextFactory
-> 追加当前 turn 的 user message
```

Redis hit 与 Redis miss 都必须遵守 COMPLETED turn 过滤规则。

### 9.3 主链路时序（同步）

```text
ChatController
-> ChatApplicationService
-> RuntimeOrchestrator
   -> SessionResolutionService
   -> TurnInitializationService
   -> AgentRunContextFactory
      -> SessionStateLoader
         -> RedisSessionContextRepository.get(sessionId)
         -> if hit:
              parse SessionContextMessageSnapshot[]
              validate snapshotVersion/tool chain
         -> if miss:
              MessageRepository.findCompletedContextBySessionId(...)
              MessageTypeHandlerRegistry.assemble(...)
              RedisSessionContextRepository.save(...)
   -> AgentLoopEngine
      -> LlmGateway.generate(...)
         -> OpenAICompatibleMessageProjector.project(...)
      -> ModelResponse(content, toolCalls, usage, finishReason)
      -> if toolCalls not empty:
           create AssistantMessage(toolCalls)
           for each toolCall:
              ToolGateway.execute(...)
              create ToolMessage
           call LLM again with user + assistant(toolCalls) + tool
      -> create AssistantMessage(final)
   -> PersistenceCoordinator.persistSuccess(...)
      -> MySQL transaction:
           MessageTypeHandlerRegistry.decompose(...)
           save agent_message
           save agent_message_tool_call
           save agent_tool_execution
           save agent_run_event
           update turn/session/conversation
      -> after commit:
           RedisSessionContextRepository.save(...)
-> ChatResponseAssembler
-> ChatResponse
```

### 9.4 主链路时序（流式，按需）

```text
ChatStreamController
-> ChatStreamApplicationService
-> RuntimeOrchestrator
   -> AgentLoopEngine.runStreaming(...)
      -> MESSAGE_STARTED
      -> MESSAGE_DELTA
      -> TOOL_CALL event
      -> TOOL_RESULT event
      -> MESSAGE_COMPLETED
   -> PersistenceCoordinator.persistSuccess(...)
      -> MySQL transaction
      -> Redis hash refresh after commit
-> SSE Event Stream
```

### 9.5 ReAct 工具调用示例

用户：

```text
现在是什么时间？
```

第一次请求模型：

```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "user",
      "content": "现在是什么时间？"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_time",
        "description": "获取当前系统时间",
        "parameters": {
          "type": "object",
          "properties": {}
        }
      }
    }
  ],
  "tool_choice": "auto",
  "stream": false
}
```

模型返回：

```json
{
  "role": "assistant",
  "content": "我来查询当前时间。",
  "tool_calls": [
    {
      "id": "call_time_001",
      "type": "function",
      "function": {
        "name": "get_time",
        "arguments": "{}"
      }
    }
  ]
}
```

系统执行工具，生成：

```java
AssistantMessage(
    messageId = "msg_assistant_001",
    contentText = "我来查询当前时间。",
    toolCalls = [
        AssistantToolCall(
            toolCallRecordId = "tcr_001",
            toolCallId = "call_time_001",
            toolName = "get_time",
            argumentsJson = "{}"
        )
    ]
)

ToolMessage(
    messageId = "msg_tool_001",
    contentText = "2026-04-22T10:20:30+02:00",
    toolCallRecordId = "tcr_001",
    toolCallId = "call_time_001",
    toolName = "get_time"
)
```

第二次请求模型：

```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "user",
      "content": "现在是什么时间？"
    },
    {
      "role": "assistant",
      "content": "我来查询当前时间。",
      "tool_calls": [
        {
          "id": "call_time_001",
          "type": "function",
          "function": {
            "name": "get_time",
            "arguments": "{}"
          }
        }
      ]
    },
    {
      "role": "tool",
      "content": "2026-04-22T10:20:30+02:00",
      "tool_call_id": "call_time_001",
      "name": "get_time"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_time",
        "description": "获取当前系统时间",
        "parameters": {
          "type": "object",
          "properties": {}
        }
      }
    }
  ],
  "tool_choice": "auto",
  "stream": false
}
```

最终 assistant 返回：

```text
当前时间是 2026-04-22 10:20:30。
```

最终数据库：

`agent_message`

| sequence_no | message_id | role | message_type | content_text | tool_call_record_id | tool_call_id | tool_name |
|---:|---|---|---|---|---|---|---|
| 1 | msg_user_001 | USER | USER_INPUT | 现在是什么时间？ | NULL | NULL | NULL |
| 2 | msg_assistant_001 | ASSISTANT | ASSISTANT_OUTPUT | 我来查询当前时间。 | NULL | NULL | NULL |
| 3 | msg_tool_001 | TOOL | TOOL_RESULT | 2026-04-22T10:20:30+02:00 | tcr_001 | call_time_001 | get_time |
| 4 | msg_assistant_002 | ASSISTANT | ASSISTANT_OUTPUT | 当前时间是 2026-04-22 10:20:30。 | NULL | NULL | NULL |

`agent_message_tool_call`

| tool_call_record_id | tool_call_id | assistant_message_id | tool_name | arguments_json | call_index | status |
|---|---|---|---|---|---:|---|
| tcr_001 | call_time_001 | msg_assistant_001 | get_time | `{}` | 0 | SUCCEEDED |

`agent_tool_execution`

| tool_execution_id | tool_call_record_id | tool_call_id | tool_result_message_id | tool_name | output_text | status | duration_ms |
|---|---|---|---|---|---|---|---:|
| exec_001 | tcr_001 | call_time_001 | msg_tool_001 | get_time | 2026-04-22T10:20:30+02:00 | SUCCEEDED | 8 |

---

## 10. 幂等、并发与状态机（按需）

### 10.1 幂等策略

| 命中场景 | 处理策略 | 返回语义 |
|---|---|---|
| `COMPLETED` | 根据 MySQL `agent_turn` 和 final assistant message 返回历史结果 | `runStatus=COMPLETED` |
| `RUNNING` | 不重复执行，返回处理中 | `runStatus=RUNNING` |
| `FAILED` | 不重新执行，不抛业务错误，返回历史失败结果 | `runStatus=FAILED` |

FAILED 返回细则：

```text
同步接口：
返回 ChatResponse，runStatus=FAILED，content=""，usage 如已记录则返回，否则 null。

流式接口：
发送 RUN_FAILED 事件，runStatus=FAILED，error 从 agent_turn 错误字段恢复，然后结束流。
```

### 10.2 并发策略

- 并发范围：按 `sessionId`。
- 控制策略：直接拒绝并发，不排队。
- 同步与流式一致性：同步和流式都必须走同一 session lock。
- Redis lock 使用 hash + Lua 原子操作。
- MySQL `existsRunningTurn(sessionId)` 仍作为事实校验。

### 10.3 状态机

```text
Turn:
RUNNING -> COMPLETED
RUNNING -> FAILED
RUNNING -> CANCELLED

Message:
CREATED -> STREAMING -> COMPLETED
CREATED -> COMPLETED
CREATED -> FAILED

ToolCall:
CREATED -> RUNNING -> SUCCEEDED
CREATED -> RUNNING -> FAILED

ToolExecution:
RUNNING -> SUCCEEDED
RUNNING -> FAILED

Session:
ACTIVE -> ARCHIVED
ACTIVE -> FAILED，仅会话级不可恢复错误
```

普通 turn 失败：

```text
turn -> FAILED
session 保持 ACTIVE
evict Redis session context
下一轮从 MySQL completed turn 重建
```

Provider 前工具链校验失败：

```text
1. 不调用 provider
2. 当前 turn 标记 FAILED
3. session 保持 ACTIVE
4. 写 RUN_FAILED run_event
5. evict 当前 session Redis context
6. 同步返回 runStatus=FAILED；流式发送 RUN_FAILED
```

---

## 11. Provider 与 Tool 设计

### 11.1 模型调用端口

- 统一端口：`LlmGateway`
- 返回对象：`ModelResponse`

Provider-neutral 字段：

```java
class ModelResponse {
    String content;
    List<ModelToolCall> toolCalls;
    FinishReason finishReason;
    UsageInfo usage;
    String provider;
    String model;
}
```

### 11.2 Provider 选择策略

- 当前默认 provider：DeepSeek。
- 备选 provider：OpenAI / Doubao 保留。
- 路由策略：本次不做 LlmRouter。
- 默认 provider 仍通过 yml 配置。

### 11.3 Tool 协议

#### tool call 记录策略

模型返回 tool call 后：

```text
ModelToolCall
-> AssistantToolCall
-> AssistantMessage.toolCalls
-> agent_message_tool_call
-> TOOL_CALL_CREATED run event
```

不再生成：

```text
ToolCallMessage
```

#### tool result 记录策略

工具执行完成后：

```text
ToolResult
-> ToolMessage
-> agent_message(role=TOOL)
-> agent_tool_execution
-> TOOL_COMPLETED run event
```

#### 异常工具处理

工具失败时仍然生成 `ToolMessage`：

```text
role = TOOL
content_text = {"success":false,"errorCode":"...","errorMessage":"..."}
tool_call_record_id = tcr_xxx
tool_call_id = call_xxx
tool_name = xxx
```

同时：

```text
agent_tool_execution.status = FAILED
agent_tool_execution.error_code = ...
agent_tool_execution.error_message = ...
```

### 11.4 `OpenAICompatibleMessageProjector`

位置：

```text
vi-agent-core-infra/.../provider/openai/OpenAICompatibleMessageProjector.java
```

职责：

```text
domain Message -> OpenAI-compatible ChatCompletionsMessage
```

接口：

```java
public class OpenAICompatibleMessageProjector {

    public List<ChatCompletionsMessage> project(List<Message> messages) {
        // validate and map
    }
}
```

投影规则：

| Domain Message | Provider Message |
|---|---|
| `UserMessage` | `role=user`, `content=contentText` |
| `AssistantMessage` | `role=assistant`, `content=contentText`, `tool_calls=toolCalls` |
| `ToolMessage` | `role=tool`, `content=contentText`, `tool_call_id=toolCallId`, `name=toolName` |
| `SystemMessage` | `role=system`, `content=contentText` |
| `SummaryMessage` | `role=system`, `content=contentText` |

校验规则：

1. 不允许出现 `ToolCallMessage`。
2. `ToolMessage` 前必须存在未匹配的 `AssistantToolCall`。
3. `ToolMessage.toolCallId` 必须匹配前序 assistant `toolCalls[].toolCallId`。
4. `ToolMessage.toolCallRecordId` 必须匹配前序 assistant `toolCalls[].toolCallRecordId`。
5. 一个 assistant 带多个 tool calls 时，后续 tool messages 必须覆盖这些 tool call，或按模型协议要求完整匹配。
6. 出现非法上下文时，在系统内部抛 `INVALID_MODEL_CONTEXT`，并由 runtime failure flow 标记 turn FAILED。

---

## 12. 配置设计（按需）

| 配置项 | 默认值 | 是否必填 | 说明 |
|---|---|---|---|
| `vi.agent.redis.ttl.session-context-seconds` | `604800` | Y | session context hash TTL，默认 7 天 |
| `vi.agent.redis.ttl.request-cache-seconds` | `86400` | Y | request cache TTL，默认 1 天 |
| `vi.agent.redis.ttl.session-lock-seconds` | `60` | Y | session lock TTL，默认 60 秒 |
| `vi.agent.runtime.session-context.max-messages` | `200` | Y | session context 最大消息数 |
| `vi.agent.runtime.message.validate-tool-chain` | `true` | Y | provider 投影前校验 tool chain |
| `mybatis-plus.configuration.default-enum-type-handler` | `org.apache.ibatis.type.EnumTypeHandler` | Y | enum 存 `name()` |

配置示例：

```yaml
vi:
  agent:
    redis:
      ttl:
        session-context-seconds: 604800
        request-cache-seconds: 86400
        session-lock-seconds: 60
    runtime:
      session-context:
        max-messages: 200
      message:
        validate-tool-chain: true

mybatis-plus:
  configuration:
    default-enum-type-handler: org.apache.ibatis.type.EnumTypeHandler
```

配置校验规则：

- 所有 TTL 必须大于 0。
- `session-lock-seconds` 不得超过合理范围，默认 60 秒。
- `session-context.max-messages` 必须大于 0。
- `validate-tool-chain` 默认 true，不建议关闭。
- enum 不允许使用 `@EnumValue` 存 `value`。

---

## 13. 可测试性与测试计划

### 13.1 必测场景

1. 所有 enum 都有 `value + desc`。
2. MySQL Entity enum 字段使用 enum 类型。
3. MySQL 保存 enum 时值为 `enum.name()`。
4. Redis hash 保存 enum 时值为 `enum.name()`。
5. Redis session context 使用 hash。
6. Redis request cache 使用 hash。
7. Redis session lock 使用 hash + Lua。
8. `AssistantMessage` 能保存并恢复 `toolCalls`。
9. `ToolMessage` 能保存并恢复 `toolCallRecordId/toolCallId/toolName/status`。
10. `ToolCallMessage` 类物理删除，主链路无引用。
11. `agent_message` 不再出现 `message_type=TOOL_CALL`。
12. `OpenAICompatibleMessageProjector` 输出合法 tool chain。
13. 不再出现孤立 `role=tool`。
14. 不再出现重复 `assistant(tool_calls)`。
15. Redis hit 和 Redis miss 恢复出的上下文一致。
16. 普通文本 chat 正常。
17. 工具调用 chat 正常。
18. 流式工具调用正常。
19. requestId 幂等 COMPLETED 正常。
20. requestId 幂等 RUNNING 正常。
21. requestId 幂等 FAILED 返回 `runStatus=FAILED`。
22. session 并发锁正常。
23. provider 前校验失败时 turn FAILED、session ACTIVE、Redis evict、RUN_FAILED event。

### 13.2 测试分层

单元测试：

- `EnumContractTest`
- `MessageTypeHandlerRegistryTest`
- `UserMessageTypeHandlerTest`
- `AssistantMessageTypeHandlerTest`
- `ToolMessageTypeHandlerTest`
- `SystemMessageTypeHandlerTest`
- `SummaryMessageTypeHandlerTest`
- `OpenAICompatibleMessageProjectorTest`
- `RedisSessionContextMapperTest`
- `RedisSessionLockRepositoryTest`
- `RedisRequestCacheRepositoryTest`

集成测试：

- `MessageRepositoryIntegrationTest`
- `SessionStateLoaderIntegrationTest`
- `PersistenceCoordinatorMessageToolIntegrationTest`
- `AgentLoopEngineToolCallIntegrationTest`
- `OpenAICompatibleChatProviderProjectionIntegrationTest`

回归测试：

- `/api/chat` 普通文本
- `/api/chat` 工具调用
- `/api/chat/stream` 工具调用
- requestId `COMPLETED`
- requestId `RUNNING`
- requestId `FAILED`
- session 并发拒绝

### 13.3 与旧测试冲突处理

- 若旧测试断言 `ToolCallMessage` 是 `Message`，删除或重写。
- 若旧测试断言 `message_type=TOOL_CALL`，删除或重写。
- 若旧测试验证外部行为，例如工具调用成功，必须迁移到新模型并继续通过。
- 不允许为了兼容旧测试保留过时主链路。

---

## 14. 分阶段实施计划

| 阶段 | 目标 | 改动范围 | 完成标准 | 回滚点 |
|---|---|---|---|---|
| 阶段 1 | 统一 enum 规范 | 全模块 enum | 所有 enum 有 `value + desc`，DB/Redis 存 name | 开发阶段不设计回滚 |
| 阶段 2 | 重构领域模型 | `vi-agent-core-model` | 新增 `AssistantToolCall / ToolMessage / ToolExecution / SystemMessage / SummaryMessage`，物理删除 `ToolCallMessage` | 清库重做 |
| 阶段 3 | 重构 MySQL schema/entity | `infra/mysql` | 新表可创建，Entity enum 字段为 enum 类型，含 `tool_call_record_id` | 清库重做 |
| 阶段 4 | 实现 MessageTypeHandlerRegistry | `infra/persistence/message` | role+messageType 能正确装配/拆解 Message | 清库重做 |
| 阶段 5 | 重构 MessageRepository | `infra/mysql/repository` | 批量读写 message/toolCall/toolExecution | 清库重做 |
| 阶段 6 | 重构 Redis hash 存储 | `infra/redis` | session/request/lock 全部 hash，TTL yml 秒级，snapshot DTO 可恢复 | 清 Redis |
| 阶段 7 | 重构 AgentLoopEngine | `runtime/engine` | 不生成 ToolCallMessage，生成 AssistantMessage(toolCalls)+ToolMessage | 回滚代码 |
| 阶段 8 | 新增 Provider Projector | `infra/provider/openai` | DeepSeek request 合法，无孤立 tool，无重复 tool_calls | 回滚代码 |
| 阶段 9 | 重构 PersistenceCoordinator | `runtime/persistence` | MySQL 事务 + Redis commit 后 best-effort | 回滚代码 |
| 阶段 10 | 测试与清理 | 全模块 | 删除旧逻辑，测试通过 | N/A |

执行顺序不可跳。Codex 必须按阶段落地。

---

## 15. 风险与回滚

### 15.1 风险清单

| 风险 | 影响 | 概率 | 应对方案 | Owner |
|---|---|---|---|---|
| enum 存储方式配置错误 | MySQL 读写失败 | 中 | 增加 `EnumContractTest`，配置 EnumTypeHandler | Codex |
| provider 投影错误 | DeepSeek 400 | 高 | projector 单测覆盖 tool chain | Codex |
| Redis hash 恢复残缺 | 上下文错误 | 中 | Redis mapper 测试覆盖 assistant toolCalls / tool message | Codex |
| 旧 ToolCallMessage 残留 | 主链路混乱 | 高 | 物理删除并用测试禁止 | Codex |
| tool_execution 与 tool_message 不一致 | 审计错误 | 中 | 同事务保存并测试 | Codex |
| session lock hash Lua 错误 | 并发控制失效 | 中 | Redis lock 单测 | Codex |
| 旧数据不兼容 | 开发数据失效 | 高 | 当前阶段允许清库 | Victor |
| Redis 写入失败 | 缓存失效 | 中 | Redis best-effort，失败 evict + WARN | Codex |
| checkpoint 范围膨胀 | 延误主任务 | 中 | 本轮不创建 agent_checkpoint | Codex |

### 15.2 回滚策略

- 协议层回滚：N/A，本次不改对外 API。
- 数据层回滚：N/A，当前开发阶段允许清库重建。
- 主链路回滚：N/A，当前阶段不设计回滚；若失败，回滚代码并清库。
- Redis 回滚：清理所有相关 Redis key。

---

## 16. 验收标准

### 16.1 功能验收

- 普通文本 chat 正常。
- 工具调用 chat 正常。
- 历史上下文中工具调用可恢复。
- 不再出现：
  - `Messages with role 'tool' must be a response to a preceding message with 'tool_calls'`
- 不再出现：
  - `assistant message with tool_calls must be followed by tool messages`
- `ToolMessage.toolCallId` 与 `AssistantMessage.toolCalls[].toolCallId` 匹配。
- `ToolMessage.toolCallRecordId` 与 `AssistantMessage.toolCalls[].toolCallRecordId` 匹配。
- `agent_message` 中不再有 `message_type=TOOL_CALL`。
- `agent_message_tool_call` 能恢复 assistant `tool_calls`。
- `agent_tool_execution` 能记录工具执行结果。
- Redis hit / miss 下上下文恢复一致。
- 流式工具事件正常输出。
- requestId 命中 FAILED 返回 `runStatus=FAILED`，不报错。

### 16.2 架构与分层验收

- 不破坏模块依赖方向。
- `model` 只放领域对象和值对象。
- `infra/mysql` 放 Entity / Mapper / Repository / Handler。
- `infra/redis` 放 hash repository。
- `infra/provider` 放 `OpenAICompatibleMessageProjector`。
- `runtime` 不拼 provider request。
- `runtime` 不直接访问 infra。
- 不再通过主流程二次查库补 message 字段。
- `ToolCallMessage` 物理删除。
- `agent_checkpoint` 本轮不创建。

### 16.3 协议与数据验收

- 对外 DTO 不变。
- MySQL enum 存 `enum.name()`。
- Redis enum 存 `enum.name()`。
- Provider API 使用 `enum.value`。
- Redis 全部使用 hash。
- Redis TTL 全部从 yml 秒级配置读取。
- Redis snapshot 使用稳定 DTO。
- Redis snapshotVersion 当前为 1。
- 当前开发阶段不验迁移和回滚。
- MySQL 表字段与 Entity 对齐。
- `OpenAICompatibleMessageProjector` 输出合法 OpenAI-compatible request。

### 16.4 测试验收

- 必测场景全部通过。
- 旧 `ToolCallMessage` 相关主链路测试已删除或重写。
- provider projector 单测覆盖：
  - no tool
  - single tool call
  - multiple tool calls
  - orphan tool error
  - mismatched toolCallId error
  - summary as system
- Redis hash 单测覆盖：
  - save/load session context
  - snapshotVersion 不匹配
  - messagesJson 解析失败
  - request cache
  - session lock acquire/release
- Message repository 集成测试覆盖：
  - assistant toolCalls 保存/恢复
  - tool message 保存/恢复
  - tool execution 保存/恢复
- PersistenceCoordinator 测试覆盖：
  - MySQL 事务成功
  - MySQL 事务失败回滚
  - Redis save 失败不影响响应
  - failure path evict Redis

---

## 17. 最终交付清单

1. 代码改造清单：
   - 所有 enum 增加 `value + desc`
   - `Message`
   - `UserMessage`
   - `AssistantMessage`
   - `ToolMessage`
   - `SystemMessage`
   - `SummaryMessage`
   - `AssistantToolCall`
   - `ToolExecution`
   - 物理删除 `ToolCallMessage`
   - `MessageTypeHandler`
   - `MessageTypeHandlerRegistry`
   - `UserMessageTypeHandler`
   - `AssistantMessageTypeHandler`
   - `ToolMessageTypeHandler`
   - `SystemMessageTypeHandler`
   - `SummaryMessageTypeHandler`
   - `MessageAggregateRows`
   - `MessageWritePlan`
   - `OpenAICompatibleMessageProjector`
   - `SessionContextMessageSnapshot`
   - `SessionContextSnapshotDocument`
   - `RedisSessionContextRepository`
   - `RedisRequestCacheRepository`
   - `RedisSessionLockRepository`
   - `SessionStateLoader`
   - `PersistenceCoordinator`
   - `AgentLoopEngine`

2. 配置改造清单：
   - `vi.agent.redis.ttl.session-context-seconds`
   - `vi.agent.redis.ttl.request-cache-seconds`
   - `vi.agent.redis.ttl.session-lock-seconds`
   - `vi.agent.runtime.session-context.max-messages`
   - `vi.agent.runtime.message.validate-tool-chain`
   - `mybatis-plus.configuration.default-enum-type-handler`

3. 数据脚本清单：
   - 开发阶段重建表 SQL
   - 不提供历史迁移
   - 不提供回滚

4. 测试新增/修改/删除清单：
   - 新增 enum contract 测试
   - 新增 message handler 测试
   - 新增 message repository 集成测试
   - 新增 Redis hash repository 测试
   - 新增 provider projector 测试
   - 新增 persistence transaction 测试
   - 删除或重写旧 ToolCallMessage 测试

5. 变更总结：
   - Tool call 不再是 message。
   - Assistant message 持有 toolCalls。
   - Tool result 是 ToolMessage。
   - Provider projector 负责 OpenAI-compatible 投影。
   - Redis 使用 hash 并保存完整 context snapshot。
   - MySQL 保存完整事实。
   - enum 统一 value + desc，DB/Redis 存 name。
   - 本轮不创建 agent_checkpoint。
   - 本轮不做 agent_message_content_part。

---

## 18. 给实现代理的执行指令模板（可直接复用）

```text
你现在是本仓库实现代理。先完整阅读并严格遵守：
1) 根目录 AGENTS.md
2) 根目录 PROJECT_PLAN.md
3) 根目录 ARCHITECTURE.md
4) 根目录 CODE_REVIEW.md
5) 本设计文档

本次任务是 vi-agent-core message / tool runtime 基座重构。

执行要求：
1. 严格按本文档“分阶段实施计划”执行，不跳阶段。
2. 不修改对外 ChatRequest / ChatResponse / ChatStreamEvent 字段。
3. 不引入 agent_message_content_part。
4. 不创建 agent_checkpoint。
5. 不做多模态 content parts。
6. 不做历史数据迁移。
7. 不做回滚兼容。
8. 开发环境旧数据无用，允许手动清库。
9. 所有 enum 统一 value + desc。
10. MySQL enum 字段 Entity 使用 enum 类型。
11. MySQL 存 enum.name()，不存 enum.value。
12. Redis 全部使用 hash。
13. Redis key 格式为 前缀 + id。
14. Redis hash field 存字段名，value 存字符串。
15. Redis enum 值存 enum.name()。
16. Redis TTL 从 yml 配置读取，单位秒。
17. Redis session context 使用稳定 snapshot DTO。
18. snapshotVersion 当前只支持 1。
19. 不再让 ToolCallMessage 作为 Message。
20. 物理删除 ToolCallMessage。
21. AssistantMessage 持有 AssistantToolCall。
22. Tool result 使用 ToolMessage。
23. Provider 使用 OpenAICompatibleMessageProjector。
24. Message 装配使用 MessageTypeHandlerRegistry。
25. requestId 命中 FAILED 固定返回 runStatus=FAILED。
26. SessionStateLoader 只加载 COMPLETED turn。
27. provider 前工具链校验失败必须标记 turn FAILED、session ACTIVE、写 RUN_FAILED、evict Redis。
28. tool_call_id 不做全局唯一，使用内部 tool_call_record_id 全局唯一。
29. MySQL 多表写入必须事务。
30. Redis commit 后 best-effort，失败不回滚 DB。
31. 若旧测试与新语义冲突，直接更新或删除。
32. 不做与本任务无关的额外架构扩展。

最终交付：
- 代码
- 配置
- 开发表结构 SQL
- 测试
- 变更总结
```

### 18.1 给 Codex Review Agent 的评审指令

```text
你现在是本仓库的评审代理。请评审本文档是否足以指导 Codex 完成 message / tool runtime 基座重构。

评审标准：
1. 是否明确 requestId 命中 FAILED 的唯一返回语义。
2. 是否明确 ToolCallMessage 物理删除。
3. 是否明确 SessionStateLoader 只加载 COMPLETED turn。
4. 是否明确 provider 前工具链校验失败后的收口。
5. 是否明确 tool_call_id 唯一性边界与 tool_call_record_id。
6. 是否明确 MySQL 事务边界与 Redis best-effort 策略。
7. 是否明确 Redis snapshot DTO、版本和失败恢复策略。
8. 是否明确本轮必做与未来占位。
9. 是否明确 AssistantMessage.toolCalls 与 ToolMessage 的职责。
10. 是否明确 MySQL 表结构。
11. 是否明确 enum 存储规则。
12. 是否明确 Redis hash 规则。
13. 是否明确 MessageTypeHandlerRegistry。
14. 是否明确 OpenAICompatibleMessageProjector。
15. 是否明确 Runtime 主链路变化。
16. 是否明确测试与验收标准。
17. 是否存在会导致 Codex 模糊实现的空白点。
18. 是否存在违反模块依赖方向的问题。
19. 是否存在过度设计或本次不应实现的内容。
20. 是否足以通过该文档生成可执行实现 prompt。

评审输出：
- PASS / FAIL
- 主要问题
- 必须补充的内容
- 可选优化建议
```

---

## 19. 文档维护规则

- 本文档是 `vi-agent-core` message / tool runtime 基座重构的专题设计文档。
- 采用增量更新，不做无理由整体改写。
- 当前阶段不做 `agent_message_content_part`。
- 当前阶段不创建 `agent_checkpoint`。
- 后续多模态阶段再单独设计 content parts。
- 后续 context engineering 阶段再引入 `agent_checkpoint`。
- 后续 plan 阶段基于 `agent_run_event + agent_checkpoint` 扩展。
- 后续 subagent 阶段基于 `agent_run_event.actor_type=SUBAGENT` 与 `agent_checkpoint.checkpoint_type=SUBAGENT_STATE` 扩展。
- 后续 memory 阶段单独设计 `agent_memory`，不混入 checkpoint。
- 新增 message 类型必须新增对应 `MessageTypeHandler`。
- 新增 provider 必须通过 provider-specific projector，不能污染 domain message。
- 新设计点必须同步落到：术语、契约、领域、存储、主链路、测试、阶段计划。
