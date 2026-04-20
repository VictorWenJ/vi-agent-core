# Vi Agent Core 会话分层与 Runtime 主链路重构详细设计

## 1. 文档目的

本文档用于指导 `vi-agent-core` 下一阶段开发，目标是一次性完成以下方向的标准化设计与落地约束：

1. 将 `conversation / session / turn / message / tool` 五层概念彻底拆开。
2. 为前端新建会话、继续会话、同窗口重开 session 提供明确协议。
3. 重构 `RuntimeOrchestrator`，让其只承担 run 级总控职责。
4. 将 loop 逻辑下沉到 `AgentLoopEngine`。
5. 删除 `LlmProvider`，统一收口到 `LlmGateway`，默认使用 DeepSeek。
6. 将 MySQL 作为事实源，Redis 作为热缓存与幂等/锁辅助，不再让 transcript 成为唯一事实源。
7. 规范 DTO、领域模型、持久化层、配置层、事件层、测试层的目录与职责边界。

本文档面向后续 Codex 开发，因此内容按“可直接拆任务执行”的方式组织。

---

## 2. 本阶段硬性结论

### 2.1 会话模型必须双层建模

- `conversation`：前端会话窗口。
- `session`：该窗口下的一段 runtime / memory 生命周期。
- `turn`：一次用户请求触发的一轮执行。
- `message`：append-only 消息事实。
- `tool_call / tool_result`：工具执行事实。

### 2.2 不再使用 `conversationId/sessionId` 的空值组合去猜业务意图

`ChatRequest` 必须新增显式字段 `sessionMode`。标准语义如下：

- `NEW_CONVERSATION`：新建 conversation + 新建 session。
- `CONTINUE_EXACT_SESSION`：继续指定 conversation + session。
- `CONTINUE_ACTIVE_SESSION`：只传 conversation，继续当前 active session。
- `START_NEW_SESSION`：在已有 conversation 下新建 session，并归档旧 active session。

### 2.3 traceId 不返回前端

- `traceId` 只用于日志 / MDC / tracing。
- 对前端返回 `requestId / runId / conversationId / sessionId / turnId / messageId`。
- `ChatResponse`、`ChatStreamEvent`、`AgentExecutionResult` 对 app 暴露口径中都不返回 `traceId`。

### 2.4 LlmRouter 本阶段不做

- 默认 provider/model 使用 DeepSeek。
- 删除 `LlmProvider`，统一保留 `LlmGateway`。
- 若未来需要多模型路由，再新增 `LlmRouter`，但不在本阶段实现。

### 2.5 长会话 summary / snapshot checkpoint 本阶段不实现

- 本阶段不落 `session snapshot / summary checkpoint` 的上下文工程能力。
- 但目录与对象边界要为未来预留，不把 transcript 继续做成永久事实源。

### 2.6 MySQL 与 Redis 必须合理拆层

禁止把 “序列化转换 + SQL/RedisTemplate 操作 + 业务拼装” 全塞到一个类里。

必须拆分为：

- `entity / document`：持久化对象。
- `converter`：领域对象与持久化对象转换器。
- `mapper`：MySQL 侧专指 MyBatis-Plus Mapper；Redis 侧仍用于 document/domain 映射。
- `dao`：直接访问 MySQL/Redis 的数据访问层，封装 Lambda Wrapper 与 Redis 原子操作。
- `repository`：面向 runtime/app 的聚合仓储层。

### 2.7 API 允许破坏性升级

本阶段允许直接升级 API 协议，不保留旧字段兼容层：

- `ChatRequest` 增加 `sessionMode`。
- `ChatResponse`、`ChatStreamEvent` 移除 `traceId`。
- `ChatResponse`、`ChatStreamEvent` 按新字段结构一次性切换。

### 2.8 conversationId / sessionId 由后端统一生成

在以下场景中，由后端统一生成业务主键并返回前端：

- `NEW_CONVERSATION`：后端生成 `conversationId` 与 `sessionId`。
- `START_NEW_SESSION`：后端生成新的 `sessionId`。

前端不参与生成业务主键，只负责传入 `requestId`。

### 2.9 requestId 命中 RUNNING 时返回处理中状态

同一个 `requestId` 再次进入，若命中状态为 `RUNNING`：

- 不抛错。
- 不重复执行。
- 直接返回“处理中”协议结果。

为承载该语义，`ChatResponse` 与 `AgentExecutionResult` 增加 `runStatus` 字段；`ChatStreamEvent` 也带 `runStatus`，用于流式事件对齐。

### 2.10 session lock 本阶段强制实现

- session lock 不是可选项，本阶段必须落地。
- 策略为：**直接拒绝并发请求**，不排队等待。
- 同一 `sessionId` 同时只能存在一个 RUNNING turn。

### 2.11 MySQL 技术栈与 DDL 管理方式拍板

- MySQL 数据访问统一使用 **MyBatis-Plus Lambda Wrapper**。
- DDL 迁移统一使用 **Flyway**。
- 禁止长期用零散手工 SQL 脚本维护主线表结构。

### 2.12 TranscriptStore 本阶段直接切主

当前处于开发阶段，不做双写、不做回退读取窗口：

- MySQL 直接作为唯一事实源。
- Redis 只做热缓存、幂等与锁。
- 旧 `TranscriptStore` 不再承担正式主链路事实存储职责。

### 2.13 provider 保留实现代码，但默认由 yml 固定为 DeepSeek

- 保留 OpenAI / Doubao 等 provider/gateway 代码。
- 本阶段不做动态路由。
- 通过 yml 配置 `default-provider: deepseek`，默认使用 DeepSeek。

---

## 3. 本阶段范围与非范围

## 3.1 本阶段范围

1. 协议升级：`ChatRequest / ChatResponse / ChatStreamEvent`。
2. 领域模型升级：conversation/session/turn/message/tool 分层。
3. 主链路升级：`RuntimeOrchestrator` 瘦身；loop 下沉。
4. 持久化升级：MySQL 事实表 + Redis 热缓存。
5. 幂等：基于 `requestId` 的基础幂等。
6. 会话切换：支持 `START_NEW_SESSION`。
7. 工具事件：流式事件完整透出。
8. Usage 聚合：每个 turn 返回聚合 token 使用量。

## 3.2 非范围

1. 长会话 summary/checkpoint。
2. 多模型路由。
3. 审批、回放、evaluation、subagent。
4. 多租户复杂策略。
5. 前端页面实现。

---

## 4. 目标调用逻辑总览

## 4.1 同步链路

```text
ChatController
→ ChatApplicationService
→ RuntimeOrchestrator.execute(command)
    → SessionResolutionService.resolve(command)
    → RunIdentityFactory.create(...)
    → TurnLifecycleService.startTurn(...)
    → SessionStateRepository.load(...)
    → AgentRunContextFactory.create(...)
    → AgentLoopEngine.run(...)
    → PersistenceCoordinator.persist(...)
    → TurnLifecycleService.completeTurn(...)
    → ChatResponseAssembler.toResponse(...)
→ ChatResponse
```

## 4.2 流式链路

```text
ChatStreamController
→ ChatStreamApplicationService
→ RuntimeOrchestrator.executeStreaming(command, eventConsumer)
    → SessionResolutionService.resolve(command)
    → RunIdentityFactory.create(...)
    → TurnLifecycleService.startTurn(...)
    → SessionStateRepository.load(...)
    → AgentRunContextFactory.create(...)
    → AgentLoopEngine.runStreaming(...)
        → RuntimeEventPublisher.publish(...)
    → PersistenceCoordinator.persist(...)
    → TurnLifecycleService.completeTurn(...)
    → StreamEventAssembler.toApiEvent(...)
→ SSE(ChatStreamEvent)
```

---

## 5. 接口协议设计

## 5.1 ChatRequest

路径：

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/request/ChatRequest.java`

建议字段：

```java
public class ChatRequest {

    /** 客户端请求唯一 ID，前端生成，必填。 */
    private String requestId;

    /** 会话窗口 ID。 */
    private String conversationId;

    /** 运行时 session ID。 */
    private String sessionId;

    /** 会话操作模式，必填。 */
    private SessionMode sessionMode;

    /** 用户当前输入文本，必填。 */
    private String message;

    /** 可选扩展元数据。 */
    private Map<String, Object> metadata;
}
```

### SessionMode

路径建议：

- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/session/SessionMode.java`

```java
public enum SessionMode {
    NEW_CONVERSATION,
    CONTINUE_EXACT_SESSION,
    CONTINUE_ACTIVE_SESSION,
    START_NEW_SESSION
}
```

### 协议规则

1. `NEW_CONVERSATION`
   - `conversationId` 必须为空。
   - `sessionId` 必须为空。

2. `CONTINUE_EXACT_SESSION`
   - `conversationId` 必填。
   - `sessionId` 必填。

3. `CONTINUE_ACTIVE_SESSION`
   - `conversationId` 必填。
   - `sessionId` 必须为空。

4. `START_NEW_SESSION`
   - `conversationId` 必填。
   - `sessionId` 必须为空。

> 本阶段主路径推荐前端使用 `CONTINUE_EXACT_SESSION`，不要把 `CONTINUE_ACTIVE_SESSION` 当常规路径。

---

## 5.2 ChatResponse

路径：

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ChatResponse.java`

按完整方案实现，不做轻量化：

```java
public class ChatResponse {

    /** 客户端请求 ID。 */
    private String requestId;

    /** 本次 run 状态。 */
    private RunStatus runStatus;

    /** 会话窗口 ID。 */
    private String conversationId;

    /** 当前 session ID。 */
    private String sessionId;

    /** 本轮 turn ID。 */
    private String turnId;

    /** 本轮用户消息 ID。 */
    private String userMessageId;

    /** 本轮助手消息 ID。 */
    private String assistantMessageId;

    /** 本次 runtime 执行 ID。 */
    private String runId;

    /** 最终助手文本；处理中时可为空。 */
    private String content;

    /** 完成原因；处理中时为空。 */
    private FinishReason finishReason;

    /** 本轮聚合 token 使用量；处理中时为空。 */
    private UsageInfo usage;

    /** 响应创建时间。 */
    private Instant createdAt;
}
```

> `ChatResponse` 不返回 `traceId`。

---

## 5.3 ChatStreamEvent

路径：

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ChatStreamEvent.java`

按完整事件协议实现：

```java
public class ChatStreamEvent {

    /** 事件类型。 */
    private StreamEventType eventType;

    /** 本次 run 状态。 */
    private RunStatus runStatus;

    /** 客户端请求 ID。 */
    private String requestId;

    /** 会话窗口 ID。 */
    private String conversationId;

    /** 当前 session ID。 */
    private String sessionId;

    /** 本轮 turn ID。 */
    private String turnId;

    /** 本次 runtime 执行 ID。 */
    private String runId;

    /** 当前消息 ID。 */
    private String messageId;

    /** 增量输出。 */
    private String delta;

    /** 完整输出内容。 */
    private String content;

    /** 完成原因。 */
    private FinishReason finishReason;

    /** 本轮聚合 token 使用量。 */
    private UsageInfo usage;

    /** 工具调用载荷。 */
    private ToolCallPayload toolCall;

    /** 工具结果载荷。 */
    private ToolResultPayload toolResult;

    /** 错误载荷。 */
    private ErrorPayload error;
}
```

### StreamEventType

```java
public enum StreamEventType {
    RUN_STARTED,
    MESSAGE_STARTED,
    MESSAGE_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    MESSAGE_COMPLETED,
    RUN_COMPLETED,
    RUN_FAILED
}
```

### RunStatus

路径建议：

- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/runtime/RunStatus.java`

```java
public enum RunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

### ToolCallPayload

路径：

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ToolCallPayload.java`

```java
public class ToolCallPayload {

    /** 工具调用唯一 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 工具入参 JSON 字符串。 */
    private String argumentsJson;

    /** 本次 run 内第几个 tool call。 */
    private Integer sequence;

    /** 事件创建时间。 */
    private Instant createdAt;
}
```

### ToolResultPayload

路径：

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ToolResultPayload.java`

```java
public class ToolResultPayload {

    /** 对应的 tool call id。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 是否执行成功。 */
    private boolean success;

    /** 工具输出 JSON 字符串；失败时可为空。 */
    private String outputJson;

    /** 错误码；成功时为空。 */
    private String errorCode;

    /** 错误信息；成功时为空。 */
    private String errorMessage;

    /** 执行耗时毫秒数。 */
    private Long durationMs;

    /** 事件创建时间。 */
    private Instant createdAt;
}
```

### ErrorPayload

路径：

- `vi-agent-core-app/src/main/java/com/vi/agent/core/app/api/dto/response/ErrorPayload.java`

```java
public class ErrorPayload {

    /** 标准错误码。 */
    private String errorCode;

    /** 面向前端的错误信息。 */
    private String errorMessage;

    /** 错误类型：VALIDATION / CONCURRENCY / MODEL / TOOL / SYSTEM。 */
    private String errorType;

    /** 是否建议前端重试。 */
    private boolean retryable;

    /** 事件创建时间。 */
    private Instant createdAt;
}
```

### 事件说明

- `RUN_STARTED`：本次 run 开始。
- `MESSAGE_STARTED`：助手消息开始输出。
- `MESSAGE_DELTA`：增量 token/delta。
- `TOOL_CALL`：模型发起工具调用。
- `TOOL_RESULT`：工具执行完成。
- `MESSAGE_COMPLETED`：本条助手消息结束，可带 `content + finishReason + usage`。
- `RUN_COMPLETED`：整个 run 结束。
- `RUN_FAILED`：run 失败。

---

## 5.4 UsageInfo

路径建议：

- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/UsageInfo.java`

```java
public class UsageInfo {

    /** 输入 token 数。 */
    private Integer inputTokens;

    /** 输出 token 数。 */
    private Integer outputTokens;

    /** 总 token 数。 */
    private Integer totalTokens;

    /** provider 名称。 */
    private String provider;

    /** model 名称。 */
    private String model;
}
```

---

## 5.5 FinishReason

路径建议：

- `vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/FinishReason.java`

```java
public enum FinishReason {
    STOP,
    TOOL_CALL,
    LENGTH,
    ERROR,
    CANCELLED
}
```

---

## 6. 领域模型设计

## 6.1 新增目录建议

### model 模块

```text
vi-agent-core-model/src/main/java/com/vi/agent/core/model/
├── conversation/
│   └── Conversation.java
├── session/
│   ├── Session.java
│   ├── SessionMode.java
│   ├── SessionStatus.java
│   ├── SessionResolution.java
│   └── SessionResolutionType.java
├── turn/
│   ├── Turn.java
│   └── TurnStatus.java
├── llm/
│   ├── UsageInfo.java
│   ├── FinishReason.java
│   ├── ModelRequest.java
│   ├── ModelResponse.java
│   └── ModelToolCall.java
├── runtime/
│   ├── AgentRunContext.java
│   ├── RunIdentity.java
│   ├── RunStatus.java
│   └── LoopExecutionResult.java
├── message/
│   ├── Message.java
│   ├── UserMessage.java
│   ├── AssistantMessage.java
│   ├── ToolCallMessage.java
│   └── ToolResultMessage.java
└── port/
    ├── LlmGateway.java
    ├── ConversationRepository.java
    ├── SessionRepository.java
    ├── TurnRepository.java
    ├── SessionStateRepository.java
    ├── RequestDedupRepository.java
    ├── SessionLockRepository.java
    └── ToolExecutionRepository.java
```

---

## 6.2 Conversation

路径：

- `model/conversation/Conversation.java`

字段：

- `conversationId`
- `title`
- `status`
- `activeSessionId`
- `createdAt`
- `updatedAt`
- `lastMessageAt`

方法：

- `activateSession(String sessionId)`
- `touchLastMessageAt(Instant time)`
- `close()`

说明：

- 只表示前端聊天窗口元数据。
- 不承载 transcript，不承载 traceId，不承载 runId。

---

## 6.3 Session

路径：

- `model/session/Session.java`

字段：

- `sessionId`
- `conversationId`
- `parentSessionId`
- `status`
- `archiveReason`
- `createdAt`
- `updatedAt`
- `archivedAt`

方法：

- `archive(String reason, Instant archivedAt)`
- `markFailed()`
- `touch(Instant time)`

说明：

- 一个 session 永远只属于一个 conversation。
- 不允许 session 改绑 conversation。

---

## 6.4 Turn

路径：

- `model/turn/Turn.java`

字段：

- `turnId`
- `conversationId`
- `sessionId`
- `requestId`
- `runId`
- `status`
- `userMessageId`
- `assistantMessageId`
- `finishReason`
- `usage`
- `errorCode`
- `errorMessage`
- `createdAt`
- `completedAt`

方法：

- `markRunning()`
- `markCompleted(FinishReason finishReason, UsageInfo usage, Instant completedAt)`
- `markFailed(String errorCode, String errorMessage, Instant completedAt)`

说明：

- usage 归属于 turn。
- requestId 幂等归属于 turn。

---

## 6.5 AgentRunContext

路径：

- `model/runtime/AgentRunContext.java`

建议字段：

- `RunIdentity runIdentity`
- `Conversation conversation`
- `Session session`
- `Turn turn`
- `String userInput`
- `List<Message> workingMessages`
- `List<ToolDefinition> availableTools`
- `AgentRunState state`
- `int iteration`

建议方法：

- `appendWorkingMessage(Message message)`
- `nextIteration()`
- `markCompleted()`
- `markFailed()`

改造要求：

- 去掉类级别 `@Setter`。
- 只保留明确意图方法。
- 不再直接持有 `ConversationTranscript` 作为唯一状态载体。

---

## 6.6 RunIdentity

路径：

- `model/runtime/RunIdentity.java`

字段：

- `requestId`
- `runId`
- `turnId`
- `userMessageId`
- `assistantMessageId`
- `traceId`

说明：

- `traceId` 只存在于 runtime/observability 内部对象中。
- 不透传到 API Response。

---

## 6.7 ModelRequest / ModelResponse

### ModelRequest

路径：

- `model/llm/ModelRequest.java`

字段：

- `String provider`
- `String model`
- `List<Message> messages`
- `List<ToolDefinition> tools`
- `boolean streaming`

### ModelResponse

路径：

- `model/llm/ModelResponse.java`

字段：

- `String text`
- `List<ModelToolCall> toolCalls`
- `FinishReason finishReason`
- `UsageInfo usage`
- `String provider`
- `String model`

说明：

- `LlmGateway` 返回 `ModelResponse`，不再直接返回 `AssistantMessage`。
- `AssistantMessage` 由 `MessageFactory` 基于 `ModelResponse` 创建。

---

## 7. runtime 模块详细设计

## 7.1 新目录结构

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/
├── orchestrator/
│   └── RuntimeOrchestrator.java
├── command/
│   └── RuntimeExecuteCommand.java
├── session/
│   ├── SessionResolutionService.java
│   └── DefaultSessionResolutionService.java
├── turn/
│   ├── TurnLifecycleService.java
│   └── DefaultTurnLifecycleService.java
├── context/
│   ├── ContextAssembler.java
│   ├── SimpleContextAssembler.java
│   ├── AgentRunContextFactory.java
│   └── DefaultAgentRunContextFactory.java
├── identity/
│   ├── ConversationSessionIdFactory.java
│   ├── DefaultConversationSessionIdFactory.java
│   ├── RunIdentityFactory.java
│   └── DefaultRunIdentityFactory.java
├── lock/
│   ├── SessionLockService.java
│   └── DefaultSessionLockService.java
├── engine/
│   ├── AgentLoopEngine.java
│   └── DefaultAgentLoopEngine.java
├── message/
│   ├── MessageFactory.java
│   └── DefaultMessageFactory.java
├── persistence/
│   ├── PersistenceCoordinator.java
│   └── DefaultPersistenceCoordinator.java
├── event/
│   ├── RuntimeEvent.java
│   ├── RuntimeEventType.java
│   ├── RuntimeEventPublisher.java
│   └── DefaultRuntimeEventPublisher.java
├── mdc/
│   ├── RuntimeMdcManager.java
│   └── MdcScope.java
└── result/
    ├── AgentExecutionResult.java
    └── LoopExecutionResult.java
```

---

## 7.2 RuntimeExecuteCommand

路径：

- `runtime/command/RuntimeExecuteCommand.java`

字段：

- `requestId`
- `conversationId`
- `sessionId`
- `sessionMode`
- `userInput`
- `metadata`
- `boolean streaming`

作用：

- 统一同步/流式入口参数。
- app 层先把 `ChatRequest` 转换成 `RuntimeExecuteCommand`。

---

## 7.3 SessionResolutionService

接口：

```java
public interface SessionResolutionService {
    SessionResolution resolve(RuntimeExecuteCommand command);
}
```

### SessionResolution

字段：

- `Conversation conversation`
- `Session session`
- `boolean newConversation`
- `boolean newSession`
- `String previousActiveSessionId`
- `SessionResolutionType resolutionType`

### SessionResolutionType

- `NEW_CONVERSATION_NEW_SESSION`
- `CONTINUE_EXACT_SESSION`
- `CONTINUE_ACTIVE_SESSION`
- `EXISTING_CONVERSATION_NEW_SESSION`

### DefaultSessionResolutionService.resolve() 逻辑

1. 校验 `sessionMode` 与 `conversationId/sessionId` 的组合是否合法。
2. `NEW_CONVERSATION`
   - 调 `ConversationSessionIdFactory.newConversationId()` 生成 `conversationId`
   - 调 `ConversationSessionIdFactory.newSessionId()` 生成 `sessionId`
   - 创建 `Conversation`
   - 创建 `Session(status=ACTIVE)`
   - 将 conversation.activeSessionId 指向新 session
3. `CONTINUE_EXACT_SESSION`
   - 查询 conversation
   - 查询 session
   - 校验 session.conversationId == conversation.conversationId
   - 校验 session.status == ACTIVE
4. `CONTINUE_ACTIVE_SESSION`
   - 查询 conversation
   - 取 activeSessionId
   - 查询 active session
5. `START_NEW_SESSION`
   - 查询 conversation
   - 读取 old active session
   - 将 old session 标记 ARCHIVED
   - 调 `ConversationSessionIdFactory.newSessionId()` 生成新的 `sessionId`
   - 创建新 session
   - conversation.activeSessionId 更新为新 session
6. 返回 `SessionResolution`

> `conversationId` 与新建 `sessionId` 均由后端统一生成，不允许前端在新建场景自行生成业务主键。  
> `START_NEW_SESSION` 的标准行为是“归档旧 session + 创建新 session”，不是迁移旧 transcript，也不是改绑旧 session。

---

## 7.4 ConversationSessionIdFactory

接口：

```java
public interface ConversationSessionIdFactory {
    String newConversationId();
    String newSessionId();
}
```

### DefaultConversationSessionIdFactory 逻辑

- `newConversationId()`：统一生成 conversation 业务主键。
- `newSessionId()`：统一生成 session 业务主键。
- 推荐使用统一前缀策略，例如 `conv-`、`sess-`，便于日志与排查。

说明：

- 业务主键与 run/turn/message 这些运行期标识分开管理。
- 该工厂只负责 `conversationId/sessionId`，不负责 `runId/turnId/messageId/traceId`。

## 7.5 RunIdentityFactory

接口：

```java
public interface RunIdentityFactory {
    RunIdentity create(String requestId);
}
```

### DefaultRunIdentityFactory.create() 逻辑

生成：

- `runId`
- `turnId`
- `userMessageId`
- `assistantMessageId`
- `traceId`

说明：

- `assistantMessageId` 在 run 开始时预分配，流式输出时保持稳定。
- 不要在主链路散落生成这些 ID。

---

## 7.6 SessionLockService

接口：

```java
public interface SessionLockService {
    void acquireOrThrow(String sessionId, String runId);
    void release(String sessionId, String runId);
}
```

### DefaultSessionLockService 逻辑

1. 基于 `SessionLockRepository` 调用 Redis `SETNX + TTL` 加锁。
2. 若加锁失败，抛出并发冲突异常。
3. 正常结束与异常结束都必须在 finally 中释放。
4. 本阶段不做排队等待，不做阻塞重试。

---

## 7.7 AgentRunContextFactory

接口：

```java
public interface AgentRunContextFactory {
    AgentRunContext create(
        RuntimeExecuteCommand command,
        SessionResolution resolution,
        RunIdentity runIdentity,
        Turn turn,
        List<Message> workingMessages,
        List<ToolDefinition> availableTools
    );
}
```

### 逻辑

1. 接收解析后的 conversation/session。
2. 接收已加载 working messages。
3. 组装 `AgentRunContext`。
4. 初始状态为 `STARTED`。

---

## 7.8 MessageFactory

接口：

```java
public interface MessageFactory {
    UserMessage createUserMessage(RunIdentity runIdentity, String content);
    AssistantMessage createAssistantMessage(RunIdentity runIdentity, ModelResponse modelResponse);
    ToolCallMessage createToolCallMessage(String turnId, ModelToolCall toolCall);
    ToolResultMessage createToolResultMessage(String turnId, ToolResult toolResult);
}
```

说明：

- 负责统一生成消息对象。
- 不允许 `RuntimeOrchestrator` 自己拼 message。

---

## 7.9 AgentLoopEngine

接口：

```java
public interface AgentLoopEngine {
    LoopExecutionResult run(AgentRunContext runContext);
    LoopExecutionResult runStreaming(AgentRunContext runContext, Consumer<RuntimeEvent> eventConsumer);
}
```

### LoopExecutionResult

字段：

- `AssistantMessage finalAssistantMessage`
- `List<Message> generatedMessages`
- `List<ToolCall> toolCalls`
- `List<ToolResult> toolResults`
- `UsageInfo usage`
- `FinishReason finishReason`
- `int iterationCount`

### DefaultAgentLoopEngine.run() 标准逻辑

```text
for iteration in [1..maxIterations]
  1. publish ITERATION(optional internal)
  2. 组装 ModelRequest
  3. 调用 llmGateway.generate(...)
  4. 将 ModelResponse 转为 AssistantMessage
  5. 追加到 workingMessages 和 generatedMessages
  6. 聚合 usage
  7. 若无 toolCalls
       return LoopExecutionResult
  8. 若有 toolCalls
       对每个 toolCall：
         - publish TOOL_CALL
         - toolGateway.execute(toolCall)
         - 生成 ToolResultMessage
         - 追加到 workingMessages 和 generatedMessages
         - publish TOOL_RESULT
继续下一轮
若超过 maxIterations，抛错
```

### runStreaming() 额外逻辑

1. 先发布 `RUN_STARTED`。
2. 发布 `MESSAGE_STARTED`。
3. 在 `llmGateway.stream(...)` 过程中不断发布 `MESSAGE_DELTA`。
4. 工具调用与工具结果通过 `TOOL_CALL / TOOL_RESULT` 事件透出。
5. 最终发布 `MESSAGE_COMPLETED` 与 `RUN_COMPLETED`。

---

## 7.10 RuntimeOrchestrator

路径：

- `runtime/orchestrator/RuntimeOrchestrator.java`

### 最终职责

只保留 run 级总控职责：

1. 校验输入。
2. 走幂等判断。
3. 解析 session。
4. 获取 session lock。
5. 生成 run/turn/message identity。
6. 启动 turn 生命周期。
7. 加载 session working state。
8. 构建 runContext。
9. 调用 `AgentLoopEngine`。
10. 持久化 conversation/session/turn/message/tool/cache。
11. 组装 `AgentExecutionResult`。

### 不再负责的事情

- 不自己 for-loop。
- 不自己拼 assistant message。
- 不自己执行工具。
- 不自己维护工具循环。
- 不自己散写 MDC。
- 不自己散落 save transcript。

### 方法设计

```java
public class RuntimeOrchestrator {

    public AgentExecutionResult execute(RuntimeExecuteCommand command);

    public AgentExecutionResult executeStreaming(
        RuntimeExecuteCommand command,
        Consumer<RuntimeEvent> eventConsumer
    );
}
```

### execute() 伪逻辑

```text
1. validate(command)
2. dedupResult = dedupRepository.tryStart(requestId)
3. 若 dedupResult == RUNNING，直接返回 `AgentExecutionResult(runStatus=RUNNING)`
4. 若 dedupResult == COMPLETED，直接返回缓存结果
5. resolution = sessionResolutionService.resolve(command)
6. runIdentity = runIdentityFactory.create(requestId)
7. sessionLockService.acquireOrThrow(resolution.getSession().getSessionId(), runIdentity.getRunId())
8. open MdcScope(runIdentity, resolution)
9. turn = turnLifecycleService.startTurn(...)
10. workingMessages = sessionStateRepository.loadWorkingMessages(sessionId)
11. userMessage = messageFactory.createUserMessage(...)
12. workingMessages + userMessage
13. runContext = agentRunContextFactory.create(...)
14. result = agentLoopEngine.run/runStreaming(...)
15. persistenceCoordinator.persist(...)
16. turnLifecycleService.completeTurn(...)
17. dedupRepository.markCompleted(...)
18. release session lock
19. return agentExecutionResult
```

---

## 7.11 RuntimeMdcManager / MdcScope

### MdcScope

```java
public interface MdcScope extends AutoCloseable {
    @Override
    void close();
}
```

### RuntimeMdcManager

```java
public interface RuntimeMdcManager {
    MdcScope open(RunIdentity runIdentity, SessionResolution resolution);
}
```

逻辑：

1. 先保存旧 MDC 值。
2. 再写入新值。
3. `close()` 时恢复旧值。

MDC 字段：

- `traceId`
- `runId`
- `conversationId`
- `sessionId`
- `turnId`
- `messageId`（必要时）
- `toolCallId`（必要时）

---

## 7.12 PersistenceCoordinator

接口：

```java
public interface PersistenceCoordinator {
    void persist(PersistenceCommand command);
}
```

### PersistenceCommand 字段

- `SessionResolution resolution`
- `Turn turn`
- `List<Message> messages`
- `List<ToolCall> toolCalls`
- `List<ToolResult> toolResults`
- `UsageInfo usage`
- `FinishReason finishReason`

### 逻辑

1. 若是新 conversation，保存 conversation。
2. 若是新 session，保存 session；若有旧 session 归档，更新旧 session。
3. 保存或更新 turn。
4. 批量保存 message。
5. 批量保存 tool_call。
6. 批量保存 tool_result。
7. 刷新 Redis session cache。
8. 更新 conversation.lastMessageAt。

> 统一持久化必须收口到一个 coordinator，不允许在 orchestrator/engine/app 三处各写一部分。

---

## 8. provider 层设计

## 8.1 删除 LlmProvider

删除：

- `vi-agent-core-infra/.../provider/LlmProvider.java`

统一使用：

- `vi-agent-core-model/.../port/LlmGateway.java`

## 8.2 LlmGateway 接口升级

```java
public interface LlmGateway {
    ModelResponse generate(ModelRequest request);
    void stream(ModelRequest request, ModelStreamObserver observer);
}
```

### ModelStreamObserver

路径建议：

- `model/llm/ModelStreamObserver.java`

方法：

- `onMessageStart()`
- `onDelta(String delta)`
- `onToolCall(ModelToolCall toolCall)`
- `onComplete(ModelResponse finalResponse)`
- `onError(Throwable throwable)`

## 8.3 provider 保留策略

- `DeepSeekChatGateway implements LlmGateway`
- `OpenAiChatGateway implements LlmGateway`
- `DoubaoChatGateway implements LlmGateway`
- 保留 OpenAI-compatible provider 代码，但本阶段不做动态路由。

## 8.4 工厂

路径：

- `infra/provider/factory/DefaultLlmGatewayFactory.java`

方法：

- `LlmGateway createDefaultGateway()`

逻辑：

- 读取 yml 中的 `vi.agent.core.provider.default-provider`
- 默认值固定为 `deepseek`
- 工厂按配置返回一个默认 gateway
- 本阶段不做运行时路由，不做按请求动态切换

### yml 示例

```yaml
vi:
  agent:
    core:
      provider:
        default-provider: deepseek
```

---

## 9. 持久化分层设计

## 9.1 总体要求

MySQL 与 Redis 都必须按明确分层拆开。

### MySQL 五层

1. `entity`
2. `converter`
3. `mapper`
4. `dao`
5. `repository`

### Redis 四层

1. `document`
2. `mapper`
3. `dao`
4. `repository`

### 规则

- `entity / document` 只承载存储字段。
- `converter` 只做 MySQL domain/entity 转换，不做数据库 IO。
- `mapper` 在 MySQL 侧专指 MyBatis-Plus Mapper；在 Redis 侧仍用于 document/domain 映射。
- `dao` 只做存储系统直接访问与原子操作封装。
- `repository` 只做面向领域的聚合读写。

---

## 9.2 infra 模块目录建议

```text
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/persistence/
├── mysql/
│   ├── entity/
│   │   ├── ConversationRecord.java
│   │   ├── SessionRecord.java
│   │   ├── TurnRecord.java
│   │   ├── MessageRecord.java
│   │   ├── ToolCallRecord.java
│   │   └── ToolResultRecord.java
│   ├── converter/
│   │   ├── ConversationRecordConverter.java
│   │   ├── SessionRecordConverter.java
│   │   ├── TurnRecordConverter.java
│   │   ├── MessageRecordConverter.java
│   │   ├── ToolCallRecordConverter.java
│   │   └── ToolResultRecordConverter.java
│   ├── mapper/
│   │   ├── ConversationMybatisMapper.java
│   │   ├── SessionMybatisMapper.java
│   │   ├── TurnMybatisMapper.java
│   │   ├── MessageMybatisMapper.java
│   │   ├── ToolCallMybatisMapper.java
│   │   └── ToolResultMybatisMapper.java
│   ├── dao/
│   │   ├── ConversationMysqlDao.java
│   │   ├── SessionMysqlDao.java
│   │   ├── TurnMysqlDao.java
│   │   ├── MessageMysqlDao.java
│   │   ├── ToolCallMysqlDao.java
│   │   └── ToolResultMysqlDao.java
│   └── repository/
│       ├── MysqlConversationRepository.java
│       ├── MysqlSessionRepository.java
│       ├── MysqlTurnRepository.java
│       └── MysqlToolExecutionRepository.java
├── redis/
│   ├── document/
│   │   ├── SessionStateCacheDocument.java
│   │   ├── RequestDedupDocument.java
│   │   └── SessionLockDocument.java
│   ├── mapper/
│   │   ├── SessionStateCacheMapper.java
│   │   ├── RequestDedupMapper.java
│   │   └── SessionLockMapper.java
│   ├── dao/
│   │   ├── SessionStateRedisDao.java
│   │   ├── RequestDedupRedisDao.java
│   │   └── SessionLockRedisDao.java
│   └── repository/
│       ├── RedisSessionStateRepository.java
│       ├── RedisRequestDedupRepository.java
│       └── RedisSessionLockRepository.java
└── config/
    ├── MysqlPersistenceConfig.java
    └── RedisPersistenceConfig.java
```

---

## 9.3 MySQL 层类职责

### entity

每个 `Record` 只对应一张表字段，不带业务逻辑。

### converter

每个 converter 负责两类工作：

1. `domain -> record`
2. `record -> domain`

例如：

```java
public class ConversationRecordConverter {
    public Conversation toDomain(ConversationRecord record);
    public ConversationRecord toRecord(Conversation domain);
}
```

### mapper

每个 mapper 都是 MyBatis-Plus Mapper，例如：

```java
@Mapper
public interface ConversationMybatisMapper extends BaseMapper<ConversationRecord> {
}
```

### dao

dao 组合 MyBatis-Plus mapper，并统一封装 `LambdaQueryWrapper` / `LambdaUpdateWrapper` 读写逻辑。

建议方法：

#### ConversationMysqlDao
- `insert(ConversationRecord record)`
- `updateActiveSession(String conversationId, String activeSessionId, Instant updatedAt)`
- `updateLastMessageAt(String conversationId, Instant lastMessageAt, Instant updatedAt)`
- `findById(String conversationId)`

#### SessionMysqlDao
- `insert(SessionRecord record)`
- `updateStatus(String sessionId, String status, String archiveReason, Instant archivedAt, Instant updatedAt)`
- `findById(String sessionId)`
- `findActiveByConversationId(String conversationId)`

#### TurnMysqlDao
- `insert(TurnRecord record)`
- `markCompleted(...)`
- `markFailed(...)`
- `findByRequestId(String requestId)`
- `findById(String turnId)`

#### MessageMysqlDao
- `batchInsert(List<MessageRecord> records)`
- `findBySessionIdOrderBySequenceNo(String sessionId)`

#### ToolCallMysqlDao
- `batchInsert(List<ToolCallRecord> records)`
- `findByTurnId(String turnId)`

#### ToolResultMysqlDao
- `batchInsert(List<ToolResultRecord> records)`
- `findByTurnId(String turnId)`

### repository

面向 runtime 输出聚合能力：

#### MysqlConversationRepository
- `Optional<Conversation> findById(String conversationId)`
- `void save(Conversation conversation)`
- `void updateActiveSession(...)`
- `void touchLastMessage(...)`

#### MysqlSessionRepository
- `Optional<Session> findById(String sessionId)`
- `Optional<Session> findActiveByConversationId(String conversationId)`
- `void save(Session session)`
- `void archive(String sessionId, String reason, Instant archivedAt)`

#### MysqlTurnRepository
- `void save(Turn turn)`
- `void markCompleted(...)`
- `void markFailed(...)`
- `Optional<Turn> findByRequestId(String requestId)`

#### MysqlToolExecutionRepository
- `void saveMessages(List<Message> messages)`
- `void saveToolCalls(List<ToolCall> toolCalls)`
- `void saveToolResults(List<ToolResult> toolResults)`
- `List<Message> loadMessagesBySessionId(String sessionId)`

---

## 9.4 Redis 层类职责

### Redis 使用边界

Redis 只做：

1. session working state 热缓存。
2. requestId 幂等标记。
3. session 级并发锁（本阶段强制实现）。

不做：

- conversation/session/turn/message/tool 的唯一事实源。

### document

#### SessionStateCacheDocument
字段：

- `sessionId`
- `conversationId`
- `latestTurnId`
- `latestMessageSequence`
- `messagesJson`
- `updatedAt`

#### RequestDedupDocument
字段：

- `requestId`
- `status` (`RUNNING/COMPLETED/FAILED`)
- `runId`
- `turnId`
- `conversationId`
- `sessionId`
- `responseJson`
- `createdAt`
- `expiredAt`

### mapper

#### SessionStateCacheMapper
- `SessionStateCacheDocument toDocument(SessionStateSnapshot snapshot)`
- `SessionStateSnapshot toDomain(SessionStateCacheDocument document)`

#### RequestDedupMapper
- `RequestDedupDocument toDocument(RequestDedupState state)`
- `RequestDedupState toDomain(RequestDedupDocument document)`

### dao

#### SessionStateRedisDao
- `void save(SessionStateCacheDocument document, Duration ttl)`
- `Optional<SessionStateCacheDocument> findBySessionId(String sessionId)`
- `void delete(String sessionId)`

#### RequestDedupRedisDao
- `DedupStartResult trySetRunning(String requestId, RequestDedupDocument document, Duration ttl)`
- `Optional<RequestDedupDocument> findByRequestId(String requestId)`
- `void markCompleted(String requestId, RequestDedupDocument document, Duration ttl)`
- `void markFailed(String requestId, RequestDedupDocument document, Duration ttl)`

#### SessionLockRedisDao
- `boolean tryLock(String sessionId, SessionLockDocument document, Duration ttl)`
- `void renew(String sessionId, Duration ttl)`
- `void unlock(String sessionId, String runId)`

### repository

#### RedisSessionStateRepository
- `Optional<SessionStateSnapshot> load(String sessionId)`
- `void save(SessionStateSnapshot snapshot)`
- `void evict(String sessionId)`

#### RedisRequestDedupRepository
- `DedupStartResult tryStart(String requestId, RunIdentity runIdentity)`
- `Optional<CachedExecutionResult> findCompleted(String requestId)`
- `Optional<ProcessingExecutionSnapshot> findRunning(String requestId)`
- `void markCompleted(String requestId, CachedExecutionResult result)`
- `void markFailed(String requestId, String reason)`

#### RedisSessionLockRepository
- `void acquireOrThrow(String sessionId, String runId, Duration ttl)`
- `void release(String sessionId, String runId)`

---

## 10. MySQL 表结构设计

> 本阶段 MySQL 使用 MyBatis-Plus Lambda Wrapper；DDL 统一通过 Flyway 管理。表引擎使用 InnoDB、字符集使用 utf8mb4。

## 10.0 DDL 管理方式

- Flyway 脚本目录：`src/main/resources/db/migration`
- 命名示例：
  - `V1__create_agent_conversation.sql`
  - `V2__create_agent_session.sql`
  - `V3__create_agent_turn.sql`
  - `V4__create_agent_message.sql`
  - `V5__create_agent_tool_call.sql`
  - `V6__create_agent_tool_result.sql`
- DDL 变更以 Flyway 为唯一正式入口；禁止长期依赖零散手工脚本维护主线表结构。


## 10.1 agent_conversation

```sql
CREATE TABLE agent_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  conversation_id VARCHAR(64) NOT NULL COMMENT '会话窗口 ID',
  title VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
  status VARCHAR(32) NOT NULL COMMENT '窗口状态：ACTIVE/CLOSED/DELETED',
  active_session_id VARCHAR(64) DEFAULT NULL COMMENT '当前活跃 session ID',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  last_message_at DATETIME DEFAULT NULL COMMENT '最近一条消息时间',
  UNIQUE KEY uk_conversation_id (conversation_id),
  KEY idx_active_session_id (active_session_id),
  KEY idx_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端会话窗口表';
```

字段说明：

- `conversation_id`：前端聊天窗口唯一 ID。
- `active_session_id`：当前活跃 runtime session。
- `last_message_at`：前端列表排序使用。

---

## 10.2 agent_session

```sql
CREATE TABLE agent_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  session_id VARCHAR(64) NOT NULL COMMENT 'session ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '所属 conversation ID',
  parent_session_id VARCHAR(64) DEFAULT NULL COMMENT '父 session ID，未来分叉预留',
  status VARCHAR(32) NOT NULL COMMENT '状态：ACTIVE/ARCHIVED/FAILED',
  archive_reason VARCHAR(255) DEFAULT NULL COMMENT '归档原因',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  archived_at DATETIME DEFAULT NULL COMMENT '归档时间',
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_conversation_id (conversation_id),
  KEY idx_conversation_status (conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='conversation 下的 runtime session 表';
```

字段说明：

- `session_id`：当前 runtime 生命周期 ID。
- `parent_session_id`：后续支持“从某处重开/分叉”。
- `status`：仅允许一个 ACTIVE session。

---

## 10.3 agent_turn

```sql
CREATE TABLE agent_turn (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '所属 conversation ID',
  session_id VARCHAR(64) NOT NULL COMMENT '所属 session ID',
  request_id VARCHAR(128) NOT NULL COMMENT '客户端请求唯一 ID',
  run_id VARCHAR(64) NOT NULL COMMENT '服务端 runtime 执行 ID',
  status VARCHAR(32) NOT NULL COMMENT '状态：RUNNING/COMPLETED/FAILED/CANCELLED',
  user_message_id VARCHAR(64) NOT NULL COMMENT '用户消息 ID',
  assistant_message_id VARCHAR(64) DEFAULT NULL COMMENT '助手消息 ID',
  finish_reason VARCHAR(32) DEFAULT NULL COMMENT '完成原因',
  input_tokens INT DEFAULT NULL COMMENT '输入 token 数',
  output_tokens INT DEFAULT NULL COMMENT '输出 token 数',
  total_tokens INT DEFAULT NULL COMMENT '总 token 数',
  error_code VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  error_message VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  completed_at DATETIME DEFAULT NULL COMMENT '完成时间',
  UNIQUE KEY uk_turn_id (turn_id),
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_session_id (session_id),
  KEY idx_conversation_id (conversation_id),
  KEY idx_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次请求的一轮执行表';
```

字段说明：

- `request_id`：幂等核心字段。
- `run_id`：服务端执行链路 ID。
- `usage` 拆成三个数值字段，查询更直接。

---

## 10.4 agent_message

```sql
CREATE TABLE agent_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  message_id VARCHAR(64) NOT NULL COMMENT '消息 ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '所属 conversation ID',
  session_id VARCHAR(64) NOT NULL COMMENT '所属 session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT '所属 turn ID',
  role VARCHAR(32) NOT NULL COMMENT '角色：USER/ASSISTANT/TOOL/SYSTEM/SUMMARY',
  message_type VARCHAR(32) NOT NULL COMMENT '消息类型：USER_INPUT/ASSISTANT_OUTPUT/TOOL_CALL/TOOL_RESULT',
  sequence_no BIGINT NOT NULL COMMENT 'session 内顺序号',
  content MEDIUMTEXT COMMENT '消息内容',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_message_id (message_id),
  UNIQUE KEY uk_session_seq (session_id, sequence_no),
  KEY idx_turn_id (turn_id),
  KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息事实表';
```

字段说明：

- `sequence_no`：重建 working messages 的确定顺序，不依赖时间戳。

---

## 10.5 agent_tool_call

```sql
CREATE TABLE agent_tool_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  tool_call_id VARCHAR(64) NOT NULL COMMENT '工具调用 ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '所属 conversation ID',
  session_id VARCHAR(64) NOT NULL COMMENT '所属 session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT '所属 turn ID',
  message_id VARCHAR(64) NOT NULL COMMENT '对应 tool call message ID',
  tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
  arguments_json MEDIUMTEXT NOT NULL COMMENT '工具入参 JSON',
  sequence_no INT NOT NULL COMMENT '本次 run 内第几个 tool call',
  status VARCHAR(32) NOT NULL COMMENT '状态：REQUESTED/EXECUTED/FAILED',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_tool_call_id (tool_call_id),
  KEY idx_turn_id (turn_id),
  KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用事实表';
```

---

## 10.6 agent_tool_result

```sql
CREATE TABLE agent_tool_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  tool_call_id VARCHAR(64) NOT NULL COMMENT '工具调用 ID',
  conversation_id VARCHAR(64) NOT NULL COMMENT '所属 conversation ID',
  session_id VARCHAR(64) NOT NULL COMMENT '所属 session ID',
  turn_id VARCHAR(64) NOT NULL COMMENT '所属 turn ID',
  message_id VARCHAR(64) NOT NULL COMMENT '对应 tool result message ID',
  success TINYINT(1) NOT NULL COMMENT '是否成功',
  output_json MEDIUMTEXT COMMENT '工具输出 JSON 或文本',
  error_code VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  error_message VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
  duration_ms BIGINT DEFAULT NULL COMMENT '执行耗时毫秒数',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  KEY idx_tool_call_id (tool_call_id),
  KEY idx_turn_id (turn_id),
  KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具结果事实表';
```

---

## 11. Redis Key 设计

## 11.1 session state cache

- key：`agent:session:state:{sessionId}`
- value：`SessionStateCacheDocument`
- ttl：建议 24h，可配置

作用：

- 缓存最近 working messages
- 避免每次全量回放数据库

## 11.2 request dedup

- key：`agent:request:dedup:{requestId}`
- value：`RequestDedupDocument`
- ttl：建议 24h，可配置

作用：

- 幂等防重
- 重复请求直接返回已完成结果

## 11.3 session lock（本阶段强制）

- key：`agent:session:lock:{sessionId}`
- value：`runId`
- ttl：建议 60s，执行时续期

作用：

- 防止同一 session 并发写入。
- 本阶段命中锁时直接拒绝新的并发请求，不排队。

---

## 11.4 旧 TranscriptStore 切换策略

本阶段直接切主：

- MySQL 事实表成为唯一正式事实源。
- Redis 只保留热缓存、幂等和锁。
- 旧 `TranscriptStore` 不做双写，不做回退读取窗口。
- 旧内存实现最多只保留给局部单测，不进入正式运行主链路。

---

## 12. app 模块设计

## 12.1 目录建议

```text
vi-agent-core-app/src/main/java/com/vi/agent/core/app/
├── api/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   └── advice/
├── application/
│   ├── ChatApplicationService.java
│   ├── ChatStreamApplicationService.java
│   ├── assembler/
│   │   ├── RuntimeCommandAssembler.java
│   │   ├── ChatResponseAssembler.java
│   │   └── ChatStreamEventAssembler.java
│   └── validator/
│       └── ChatRequestValidator.java
└── config/
    ├── RuntimeCoreConfig.java
    ├── PersistenceConfig.java
    └── ProviderConfig.java
```

---

## 12.2 ChatApplicationService

方法：

- `ChatResponse chat(ChatRequest request)`

逻辑：

1. `ChatRequestValidator.validate(request)`
2. `RuntimeCommandAssembler.toCommand(request, false)`
3. `runtimeOrchestrator.execute(command)`
4. `ChatResponseAssembler.toResponse(result)`

---

## 12.3 ChatStreamApplicationService

方法：

- `Flux<ServerSentEvent<ChatStreamEvent>> chatStream(ChatRequest request)`

逻辑：

1. 校验请求
2. 转 command
3. 调 `runtimeOrchestrator.executeStreaming(...)`
4. `ChatStreamEventAssembler` 将 `RuntimeEvent` 转为 API 事件

---

## 12.4 Assembler 说明

### RuntimeCommandAssembler
- `RuntimeExecuteCommand toCommand(ChatRequest request, boolean streaming)`

### ChatResponseAssembler
- `ChatResponse toResponse(AgentExecutionResult result)`

### ChatStreamEventAssembler
- `ChatStreamEvent toApiEvent(RuntimeEvent event)`

---

## 13. AgentExecutionResult 设计

路径：

- `runtime/result/AgentExecutionResult.java`

字段：

- `requestId`
- `runStatus`
- `conversationId`
- `sessionId`
- `turnId`
- `userMessageId`
- `assistantMessageId`
- `runId`
- `AssistantMessage finalAssistantMessage`
- `FinishReason finishReason`
- `UsageInfo usage`
- `Instant createdAt`

说明：

- `runStatus=RUNNING` 时表示本次请求命中了进行中的幂等记录，返回处理中结果。
- 对 app 层暴露的执行结果不含 `traceId`。
- `traceId` 只保留在 runtime 内部与日志中。

---

## 14. 关键实现细节

## 14.1 requestId 幂等

### 规则

1. 同一个 `requestId` 第一次进入：写入 Redis `RUNNING`。
2. 若重复请求到达：
   - 若状态为 `COMPLETED`：直接返回缓存结果。
   - 若状态为 `RUNNING`：直接返回“处理中”结果，`runStatus=RUNNING`。
   - 若状态为 `FAILED`：允许重新提交，但必须使用新的 `requestId`。

### 推荐实现

由 `RedisRequestDedupRepository` 提供：

- `DedupStartResult tryStart(String requestId, RunIdentity runIdentity)`
- `Optional<CachedExecutionResult> findCompleted(String requestId)`
- `void markCompleted(String requestId, CachedExecutionResult result)`
- `void markFailed(String requestId, String reason)`

---

## 14.2 session 并发保护

规则：

- 同一 `sessionId` 同时仅允许一个 RUNNING turn。
- session lock 本阶段必须落地。
- 命中锁时直接拒绝新的并发请求，不排队。

建议实现：

- 基于 Redis `SETNX + TTL` 做 session lock。
- 在 `SessionResolutionService` 得到最终 `sessionId` 后立即尝试加锁。
- 在 `RuntimeOrchestrator` 正常结束或异常结束时统一释放。

---

## 14.3 message sequence 生成

推荐：

- 以 session 为单位，Redis 缓存当前 sequence。
- cache miss 时以 MySQL `max(sequence_no)` 初始化。

接口建议：

- `SessionSequenceService.nextSequence(String sessionId)`

---

## 14.4 session state 加载规则

规则：

1. 先读 Redis session state cache。
2. miss 时从 MySQL 按 `sessionId + sequence_no` 回放 message。
3. 组装 workingMessages。
4. 回填 Redis。

本阶段不做 summary/checkpoint，仅回放消息事实。

---

## 14.5 Usage 聚合

规则：

- 一次 turn 中如果发生多轮 LLM 调用，最终 `usage` 为所有模型调用的累加值。

建议新增：

- `UsageAggregator`

方法：

- `void add(UsageInfo usage)`
- `UsageInfo snapshot()`

---

## 15. 代码落地顺序（阶段实施顺序）

> 以下顺序必须按阶段推进，减少大范围返工。

## 阶段 1：协议与领域模型落地

### 目标

先把接口边界和核心对象定死。

### 修改清单

1. 新增 `SessionMode`。
2. 升级 `ChatRequest`。
3. 升级 `ChatResponse`。
4. 升级 `ChatStreamEvent`。
5. 新增 `UsageInfo`、`FinishReason`、`ModelRequest`、`ModelResponse`、`RunIdentity`、`Turn`、`Conversation`、`Session`。
6. 调整 `AgentExecutionResult`，移除 `traceId`。
7. 调整 `LlmGateway` 签名，返回 `ModelResponse`。

### 阶段完成标准

- 编译通过。
- app 层 DTO 与 model 层对象边界清晰。
- 不再有 API 层 `traceId` 返回。

---

## 阶段 2：provider 与 loop 改造

### 目标

让模型调用与 loop 逻辑回归正确边界。

### 修改清单

1. 删除 `LlmProvider` 接口。
2. DeepSeek provider 改为直接实现 `LlmGateway`。
3. 保留 OpenAI / Doubao gateway 代码，但本阶段不做动态路由。
4. `DefaultLlmGatewayFactory` 改为读取 yml 的 `default-provider`，默认 `deepseek`。
5. OpenAI-compatible provider 统一返回 `ModelResponse`。
6. provider 协议对象补 `usage` 解析。
7. 流式请求打开 `stream_options.include_usage=true`。
8. 新增 `MessageFactory`。
9. 新增 `DefaultAgentLoopEngine`，把 loop/tool 执行完整下沉。
10. `ToolGateway.route(...)` 改名为 `execute(...)`.

### 阶段完成标准

- `RuntimeOrchestrator` 不再 for-loop。
- `AgentLoopEngine` 可独立完成 llm-tool-llm 循环。
- usage 可在 turn 级聚合。

---

## 阶段 3：MySQL/Redis 持久化分层落地

### 目标

建立事实源与热缓存的标准边界。

### 修改清单

1. 新增 Flyway DDL 迁移脚本。
2. 引入 MyBatis-Plus 与 MySQL 驱动。
3. 新增 MySQL `entity/converter/mapper/dao/repository`。
4. 新增 Redis `document/mapper/dao/repository`。
5. 新增 `SessionStateRepository`、`RequestDedupRepository`、`SessionLockRepository` 端口与实现。
6. 新增 `PersistenceCoordinator`。
7. 旧 `TranscriptStore` 直接退出正式主链路，不做双写与回退窗口。 

### 阶段完成标准

- conversation/session/turn/message/tool 均可持久化到 MySQL。
- Redis 仅做热缓存/幂等/锁。
- mapper 与 dao 彻底拆开。

---

## 阶段 4：会话解析与 orchestrator 收口

### 目标

让新会话、继续会话、同窗口新 session 都有明确主链路。

### 修改清单

1. 新增 `SessionResolutionService`。
2. 新增 `ConversationSessionIdFactory`。
3. 新增 `RunIdentityFactory`。
4. 新增 `TurnLifecycleService`。
5. 新增 `SessionLockService`。
6. 新增 `AgentRunContextFactory`。
7. 新增 `RuntimeMdcManager/MdcScope`。
8. 重构 `RuntimeOrchestrator` 为 run-level orchestration。

### 阶段完成标准

- `NEW_CONVERSATION / CONTINUE_EXACT_SESSION / CONTINUE_ACTIVE_SESSION / START_NEW_SESSION` 四种模式都能走通。
- 主链路职责清晰。
- orchestrator 不再包含工具/loop/MDC 细碎实现。

---

## 阶段 5：幂等、并发、流式事件补齐

### 目标

把对外协议与执行安全性补完整。

### 修改清单

1. requestId 幂等。
2. session lock（强制拒绝并发）。
3. `RuntimeEvent` 升级为完整事件对象。
4. `ChatStreamEventAssembler` 完整映射事件类型与 payload。
5. 补同步/流式集成测试。 

### 阶段完成标准

- 重复 requestId 不会重复执行；命中 RUNNING 时返回处理中结果。
- 同一 session 并发请求会被直接拒绝，不会排队。
- stream 事件结构稳定，前端可直接消费。

---

## 16. 测试建议

## 16.1 单元测试

### runtime
- `DefaultSessionResolutionServiceTest`
- `DefaultRunIdentityFactoryTest`
- `DefaultAgentLoopEngineTest`
- `DefaultPersistenceCoordinatorTest`
- `RuntimeOrchestratorTest`

### infra-mysql
- `ConversationRecordMapperTest`
- `SessionMysqlDaoTest`
- `TurnMysqlDaoTest`
- `MessageMysqlDaoTest`

### infra-redis
- `SessionStateCacheMapperTest`
- `RequestDedupRedisDaoTest`

### provider
- `DeepSeekChatGatewayTest`
- `OpenAICompatibleChatProviderProtocolTest`

## 16.2 集成测试

- 新建会话同步聊天
- 指定 conversation+session 继续聊天
- 同 conversation 下 `START_NEW_SESSION`
- 流式 tool call + tool result 输出
- requestId 重复提交幂等（RUNNING 返回处理中）
- session 并发锁（直接拒绝并发）

---

## 17. Codex 执行约束

1. 不要为了兼容旧 test 而保留过时逻辑；如果 test 不适配新架构，直接重写或删除旧 test。
2. 不要保留 `traceId` 到 API Response。
3. 不要继续让 `ConversationTranscript` 作为唯一事实源。
4. 不要把 MySQL 和 Redis 的 mapper、dao、repository 合并到一个文件。
5. 不要让 `RuntimeOrchestrator` 再次膨胀回“大一统类”。
6. 不要新增 `LlmRouter`。
7. 保留 OpenAI / Doubao 等 gateway 代码，但默认 provider 由 yml 固定为 DeepSeek。
8. DDL 统一走 Flyway，不要再散落手工 SQL 主线脚本。
9. 本阶段不实现 summary/checkpoint，但不能把未来扩展路径堵死。

---

## 18. 最终落地目标

完成本设计后，系统应具备以下结构特征：

1. 前端会话窗口与 runtime session 分层清晰。
2. API、runtime、model、infra 四层职责清晰。
3. MySQL 做事实源，Redis 做热缓存与幂等辅助。
4. loop 真正属于 `AgentLoopEngine`。
5. provider 通过 `LlmGateway` 统一接入，默认 provider 由 yml 固定为 DeepSeek。
6. `ChatResponse` 与 `ChatStreamEvent` 协议足够完整，可直接服务后续前端开发。

