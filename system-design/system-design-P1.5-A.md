# RuntimeOrchestrator `executeInternal` 拆分与运行编排收口系统详细设计

> 文档用途：指导 Codex / 实现代理对 `RuntimeOrchestrator.executeInternal(...)` 进行职责拆分与结构收口。  
> 设计目标：在不改变既有 API、数据库表结构和主业务语义的前提下，将当前过大的运行编排方法拆分为清晰、可测试、可维护的协作者体系。  
> 执行原则：这是一次 **runtime 内部结构重构**，不是协议重构、存储重构或功能扩展。

---

## 0. 使用说明

### 0.1 模板适用范围

本设计适用于本次 `RuntimeOrchestrator.executeInternal(...)` 拆分任务，覆盖：

- Runtime 主链路编排职责收口
- runtime 内部执行上下文抽象
- 幂等处理逻辑拆分
- session lock / MDC / 资源清理逻辑拆分
- turn 初始化逻辑拆分
- `AgentRunContext` 构建逻辑拆分
- runtime event 构建与发送逻辑拆分
- loop 调用分支拆分
- 成功 / 失败收口逻辑拆分
- 结果对象构建逻辑拆分

不适用：

- 新增 API 字段
- 修改 `ChatRequest / ChatResponse / ChatStreamEvent` 协议
- 修改 MySQL 表结构
- 修改 Redis key 结构
- 修改 provider 选择策略
- 新增 LlmRouter
- 长会话 summary checkpoint / 上下文工程能力建设

### 0.2 填写规则

- 本文档已按本次任务补全，无需 Codex 再自行扩展范围。
- 若当前代码与本文档存在局部命名差异，允许在保持职责一致的前提下做最小命名适配。
- 若当前代码中已有等价类，应优先复用并改造，不要重复新建近似职责类。
- 本次重构不允许改变对外协议语义，不允许改变数据库事实模型。

---

## 1. 文档元信息

- 文档名称：`RuntimeOrchestrator executeInternal 拆分与运行编排收口系统详细设计`
- 变更主题：`RuntimeOrchestrator 瘦身、内部职责拆分、runtime 主链路可维护性提升`
- 目标分支 / 迭代：`当前 RuntimeOrchestrator 收口迭代`
- 文档版本：`v1.0`
- 状态：`Confirmed`
- 作者：`ChatGPT`
- 评审人：`Victor`
- 关联文档：
  - `AGENTS.md`
  - `ARCHITECTURE.md`
  - `CODE_REVIEW.md`
  - `PROJECT_PLAN.md`
  - `agent-runtime-session-refactor-design-v2.md`
  - `project-design-template-agent-runtime.md`

---

## 2. 变更摘要（Executive Summary）

### 2.1 一句话摘要

本次重构将当前过大的 `RuntimeOrchestrator.executeInternal(...)` 拆分为一组职责明确的 runtime 内部协作者，使 `RuntimeOrchestrator` 只保留 run-level 主流程编排，不再直接承载校验、幂等、锁、MDC、turn 初始化、上下文构建、事件构建、loop 分支、成功收口、失败收口和结果构建等细节。

### 2.2 本次范围

1. 将 `RuntimeOrchestrator.executeInternal(...)` 拆成清晰的主流程 + 协作者调用。
2. 新增或改造 runtime 内部协作者：
   - `RuntimeExecutionContext`
   - `RuntimeCommandValidator`
   - `RuntimeDeduplicationHandler`
   - `RuntimeRunScopeManager`
   - `RuntimeRunScope`
   - `TurnInitializationService`
   - `TurnStartResult`
   - `AgentRunContextFactory`
   - `RuntimeEventFactory`
   - `RuntimeEventSink`
   - `RuntimeEventSinkFactory`
   - `LoopInvocationService`
   - `LoopStreamObserver`
   - `LoopStreamObserverFactory`
   - `RuntimeCompletionHandler`
   - `RuntimeFailureHandler`
   - `AgentExecutionResultFactory`
3. 保持当前 API、数据库表、Redis key、provider 策略和业务状态语义不变。
4. 补充对应单元测试，重点验证拆分后主链路行为不变。

### 2.3 明确不做

1. 不修改 `ChatRequest / ChatResponse / ChatStreamEvent` 的对外字段结构。
2. 不新增 MySQL 表，不修改 Flyway DDL。
3. 不修改 Redis key 设计。
4. 不新增 LlmRouter。
5. 不改变 DeepSeek 默认 provider 策略。
6. 不实现长会话 summary checkpoint。
7. 不修改 session / turn / message / tool 的事实模型语义。
8. 不为缩短方法而把逻辑简单拆成一堆无职责边界的 private method。

---

## 3. 背景与问题定义

### 3.1 当前现状（仅写与本次直接相关）

- 当前 `RuntimeOrchestrator` 已承担 runtime 主链路总控职责，并已接入 conversation/session/turn/message/tool/run 新模型。
- 当前 `executeInternal(...)` 已经包含完整主链路，但方法体过大，混入多个层次的逻辑。
- 当前代码已经具备：
  - `SessionResolutionService`
  - `RunIdentityFactory`
  - `TurnLifecycleService`
  - `SessionStateLoader`
  - `MessageFactory`
  - `PersistenceCoordinator`
  - `RuntimeMdcManager`
  - `AgentLoopEngine`
  - `RuntimeEvent`
  - `AgentExecutionResult`
- 当前存在同步与流式共用一条内部执行链路的需求，因此编排流程需要更清晰的职责分层。

### 3.2 已确认问题

1. `executeInternal(...)` 同时处理请求校验、幂等、会话解析、run metadata、加锁、MDC、turn 创建、消息持久化、上下文构建、事件发送、loop 调用、成功/失败持久化和结果组装，职责过多。
2. `RuntimeEvent.builder()`、`AgentExecutionResult.builder()` 等构建逻辑散落在 orchestrator 中，字段重复且容易出现 messageId / runStatus / finishReason 等语义错误。
3. session lock、MDC scope、message sequence cursor 清理等资源生命周期逻辑混在业务主流程中，降低可读性和异常安全性。
4. 同步 / 流式 loop 分支混在 orchestrator 中，使 streaming 事件发送细节再次侵入编排器。
5. 幂等命中 `COMPLETED / RUNNING / FAILED` 的处理细节不应继续堆在 orchestrator 中。
6. 成功和失败收口逻辑存在可复用性，应统一进入 handler，减少重复 catch / persist / emit 代码。

### 3.3 不改风险

- `RuntimeOrchestrator` 会继续膨胀，后续任何 runtime 功能都会往同一个方法堆逻辑。
- 流式事件字段、assistant messageId、runStatus、usage 等字段容易在不同分支中出现不一致。
- 加锁、MDC、异常收口、cache 清理等 finally 逻辑容易被后续修改破坏。
- Codex 后续开发时容易误把 orchestrator 当成“所有逻辑都能放”的上帝类。
- 单元测试难以聚焦，只能做大范围主链路测试，局部行为不易验证。

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
- 禁止将本次 runtime 内部重构扩大成 API / DDL / Provider 路由重构。
- 禁止把有副作用的生命周期逻辑命名为 Factory。

---

## 5. 术语、语义与标识

### 5.1 术语表（按需裁剪）

| 术语 | 定义 | 本次是否涉及 |
|---|---|---|
| `conversation` | 前端会话窗口 | Y |
| `session` | 会话窗口下的一段运行时生命周期 | Y |
| `turn` | 一次请求执行单元 | Y |
| `message` | 对话事实记录 | Y |
| `tool_call` | 工具调用事实 | Y |
| `tool_result` | 工具执行结果事实 | Y |
| `run` | 运行实体及状态 | Y |
| `runtime execution context` | 本次 runtime 执行过程中的内部上下文容器，不持久化 | Y |
| `runtime run scope` | 本次运行的资源作用域，包括 session lock、MDC、cursor cleanup | Y |
| `event sink` | runtime 内部事件发送门面，封装 eventConsumer 空值判断与事件工厂 | Y |
| `completion handler` | 成功完成后的持久化、事件和结果组装收口 | Y |
| `failure handler` | 失败后的状态、持久化、事件和异常转换收口 | Y |

### 5.2 标识符定义

| 标识符 | 用途 | 生成方 | 是否对外返回 |
|---|---|---|---|
| `requestId` | 幂等键 | 前端 | 是 |
| `conversationId` | 会话窗口标识 | 后端 | 是 |
| `sessionId` | runtime 生命周期标识 | 后端 | 是 |
| `turnId` | 单次执行标识 | 后端 | 是 |
| `runId` | 运行标识 | 后端 | 是 |
| `messageId` | 消息标识 | 后端 | 是 |
| `traceId` | 内部观测链路 | 后端 | 否（仅日志/MDC） |

### 5.3 显式语义枚举（按需）

- `SessionMode`：本次不改语义，只沿用既有 `NEW_CONVERSATION / CONTINUE_ACTIVE_SESSION / CONTINUE_EXACT_SESSION / START_NEW_SESSION`。
- `RunStatus`：本次不改语义，只沿用既有 `RUNNING / COMPLETED / FAILED / CANCELLED` 等状态。
- `StreamEventType`：本次不改对外协议，只保证事件构建统一。
- `FinishReason`：本次不改语义。
- `TurnStatus`：本次不改语义。
- `SessionStatus`：本次不改语义。

---

## 6. 对外协议设计（API / Stream）

### 6.1 请求契约

本次不修改 `ChatRequest` 字段结构。

现有请求契约继续使用当前项目中的定义，至少包含：

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
| `requestId` | `String` | Y | 前端请求幂等键 | 不允许为空 |
| `conversationId` | `String` | 按 `SessionMode` | 会话窗口 ID | 由 `SessionResolutionService` 校验 |
| `sessionId` | `String` | 按 `SessionMode` | runtime session ID | 由 `SessionResolutionService` 校验 |
| `sessionMode` | `SessionMode` | Y | 会话处理模式 | 不允许为空 |
| `message` | `String` | Y | 用户输入 | 不允许为空 |

### 6.2 同步响应契约

本次不修改 `ChatResponse` 字段结构。

现有响应契约继续使用当前项目中的定义，至少包含：

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

### 6.3 流式事件契约（按需）

本次不修改 `ChatStreamEvent` 字段结构。

现有流式事件契约继续使用当前项目中的定义，至少包含：

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

### 6.4 协议红线

- `traceId` 不得出现在对外 DTO。
- 语义不得依赖“id 是否为空”的隐式推断。
- `requestId` 命中幂等时，同步与流式语义必须一致。
- 本次重构不得新增、删除、重命名对外字段。
- 流式 message 类事件的 `messageId` 必须继续保持 assistant messageId 语义，不允许回退到 user messageId。

---

## 7. 领域模型设计

### 7.1 领域对象清单

| 对象 | 职责 | 所在模块/包 | 本次变更类型（新增/修改/删除） |
|---|---|---|---|
| `RuntimeExecutionContext` | 承载一次 runtime 执行过程中的共享状态 | `runtime/execution` | 新增 |
| `RuntimeCommandValidator` | 校验 `RuntimeExecuteCommand` | `runtime/validation` | 新增 |
| `RuntimeDeduplicationHandler` | 处理 requestId 幂等命中分支 | `runtime/dedup` | 新增 |
| `RuntimeRunScopeManager` | 打开运行资源作用域，处理 lock、running turn 校验、MDC | `runtime/scope` | 新增 |
| `RuntimeRunScope` | `AutoCloseable` 资源作用域，关闭时释放 lock、MDC、sequence cursor | `runtime/scope` | 新增 |
| `TurnInitializationService` | 创建 turn 与 user message，并持久化本轮初始事实 | `runtime/lifecycle` | 新增 |
| `TurnStartResult` | turn 初始化结果值对象 | `runtime/lifecycle` | 新增 |
| `AgentRunContextFactory` | 构建 `AgentRunContext` | `runtime/factory` | 新增 |
| `RuntimeEventFactory` | 构建 `RuntimeEvent` | `runtime/factory` 或 `runtime/event` | 新增 |
| `RuntimeEventSink` | 封装 runtime event 发送 | `runtime/event` | 新增 |
| `RuntimeEventSinkFactory` | 创建 `RuntimeEventSink` | `runtime/event` 或 `runtime/factory` | 新增 |
| `LoopInvocationService` | 统一调用同步/流式 AgentLoopEngine | `runtime/loop` | 新增 |
| `LoopStreamObserver` | loop 流式回调接口 | `runtime/loop` | 新增或改造 |
| `LoopStreamObserverFactory` | 创建 loop 流式回调适配器 | `runtime/factory` | 新增 |
| `RuntimeCompletionHandler` | 成功完成后的持久化、事件、结果构建 | `runtime/completion` | 新增 |
| `RuntimeFailureHandler` | 异常处理、失败持久化、失败事件、异常转换 | `runtime/failure` | 新增 |
| `AgentExecutionResultFactory` | 统一构建 `AgentExecutionResult` | `runtime/factory` | 新增 |
| `RuntimeOrchestrator` | 只保留 run-level orchestration 主流程 | `runtime/orchestrator` | 修改 |

### 7.2 关键值对象 / 命令对象

#### 7.2.1 `RuntimeExecutionContext`

```java
public class RuntimeExecutionContext {

    private RuntimeExecuteCommand command;
    private boolean streaming;
    private Consumer<RuntimeEvent> eventConsumer;

    private SessionResolutionResult resolution;
    private RunMetadata runMetadata;

    private UserMessage userMessage;
    private Turn turn;
    private AgentRunContext runContext;
    private LoopExecutionResult loopResult;

    private Instant startedAt;

    public static RuntimeExecutionContext create(
        RuntimeExecuteCommand command,
        Consumer<RuntimeEvent> eventConsumer,
        boolean streaming
    );

    public String requestId();
    public String conversationId();
    public String sessionId();
    public String turnId();
    public String runId();
    public boolean hasTurn();
    public boolean hasRunContext();
}
```

设计说明：

- 该对象只存在于 runtime 执行过程中，不持久化。
- 用于减少协作者方法参数数量。
- 不应放入 `model` 模块。
- 不应暴露给 app / infra 层。

#### 7.2.2 `TurnStartResult`

```java
public class TurnStartResult {

    private final Turn turn;
    private final UserMessage userMessage;

    public Turn getTurn();
    public UserMessage getUserMessage();
}
```

设计说明：

- 表示 turn 初始化动作的结果。
- 由于初始化包含持久化副作用，因此创建过程不应命名为 factory。

### 7.3 领域规则与状态流转

1. `RuntimeOrchestrator` 只表达 run-level 主流程，不直接构建 event/result，不直接处理 lock/MDC 细节。
2. 有副作用的逻辑使用 `Service / Handler / Manager / Coordinator` 命名。
3. 纯对象构建逻辑使用 `Factory` 命名。
4. `RuntimeExecutionContext` 仅用于一次 runtime 调用内部，不持久化、不跨请求复用。
5. `RuntimeRunScope` 必须实现 `AutoCloseable`，确保任何异常下都释放 lock、关闭 MDC、清理 message sequence cursor。
6. `RuntimeEventFactory` 负责 event 字段一致性，禁止继续在 orchestrator 内散落 `RuntimeEvent.builder()`。
7. `AgentExecutionResultFactory` 负责结果对象一致性，禁止继续在 orchestrator 内散落 `AgentExecutionResult.builder()`。
8. `RuntimeFailureHandler` 必须保持既有失败语义：普通 turn 失败不废 session，cache evict，后续只加载 completed turn。

---

## 8. 持久化与数据设计（按需）

### 8.1 事实源与缓存边界

本次不修改持久化边界。

- 事实源：MySQL 事实表，沿用当前 conversation/session/turn/message/tool 表结构。
- 缓存层职责：Redis session state / lock / cache，沿用当前实现。
- miss 回填策略：沿用当前 `SessionStateLoader` / repository 实现，本次不新增策略。

### 8.2 关系型存储设计（若涉及）

本次不涉及表结构变更。

| 表名 | 用途 | 主键/唯一约束 | 关键索引 | 本次动作 |
|---|---|---|---|---|
| `agent_conversation` | 会话窗口事实 | 沿用当前设计 | 沿用当前设计 | N/A |
| `agent_session` | runtime session 生命周期事实 | 沿用当前设计 | 沿用当前设计 | N/A |
| `agent_turn` | 单次请求执行事实 | 沿用当前设计 | 沿用当前设计 | N/A |
| `agent_message` | 消息事实 | 沿用当前设计 | 沿用当前设计 | N/A |
| `agent_tool_call` | 工具调用事实 | 沿用当前设计 | 沿用当前设计 | N/A |
| `agent_tool_result` | 工具结果事实 | 沿用当前设计 | 沿用当前设计 | N/A |

### 8.3 Redis / 文档结构（若涉及）

本次不修改 Redis 文档结构。

| 文档或 Key 模式 | 用途 | TTL | miss 处理 |
|---|---|---|---|
| session state cache | session 热状态缓存 | 沿用当前配置 | 从 MySQL 重建 |
| session lock key | session 并发控制 | 沿用当前配置 | 获取失败直接拒绝 |
| 其他已有 key | 沿用当前实现 | 沿用当前配置 | 沿用当前实现 |

### 8.4 迁移策略（若涉及）

- 迁移机制：N/A
- 脚本命名：N/A
- 回滚策略：本次不涉及数据结构变更，可通过代码回滚恢复旧编排结构。

---

## 9. Runtime 主链路设计

### 9.1 角色职责分配

| 组件 | 职责 | 本次变更 |
|---|---|---|
| `RuntimeOrchestrator` | run-level orchestration | 修改：只保留主流程，不再承载细节逻辑 |
| `AgentLoopEngine` | llm-tool loop owner | 保持既有职责；如需要，支持 `LoopStreamObserver` |
| `SessionResolutionService` | 会话解析与创建 | 不改语义，只由 orchestrator 调用 |
| `TurnLifecycleService` | turn 生命周期 | 部分逻辑由 `TurnInitializationService` 调用 |
| `PersistenceCoordinator` | 持久化协调 | 不改语义，由 completion/failure handler 调用 |
| `RuntimeCommandValidator` | 请求命令校验 | 新增 |
| `RuntimeDeduplicationHandler` | requestId 幂等处理 | 新增 |
| `RuntimeRunScopeManager` | lock、MDC、running turn 检查、资源作用域 | 新增 |
| `TurnInitializationService` | turn + user message 初始化 | 新增 |
| `AgentRunContextFactory` | 构建运行上下文 | 新增 |
| `RuntimeEventFactory` | 统一构建 runtime events | 新增 |
| `RuntimeEventSink` | 统一发送 runtime events | 新增 |
| `LoopInvocationService` | 同步/流式 loop 调用分发 | 新增 |
| `RuntimeCompletionHandler` | 成功收口 | 新增 |
| `RuntimeFailureHandler` | 失败收口 | 新增 |
| `AgentExecutionResultFactory` | 结果对象构建 | 新增 |

### 9.2 主链路时序（同步）

```text
ChatController
-> ChatApplicationService
-> RuntimeOrchestrator.execute(command)
   -> RuntimeExecutionContext.create(...)
   -> RuntimeCommandValidator.validate(command)
   -> RuntimeDeduplicationHandler.tryHandle(context)
   -> SessionResolutionService.resolve(command)
   -> RunIdentityFactory.createRunMetadata()
   -> RuntimeEventSinkFactory.create(context)
   -> RuntimeRunScopeManager.open(context)
      -> SessionLockRepository.tryLock(...)
      -> TurnLifecycleService.existsRunningTurn(...)
      -> RuntimeMdcManager.open(...)
   -> TurnInitializationService.start(context)
      -> MessageFactory.createUserMessage(...)
      -> TurnLifecycleService.createRunningTurn(...)
      -> PersistenceCoordinator.persistUserMessage(...)
   -> RuntimeEventSink.runStarted()
   -> AgentRunContextFactory.create(context)
      -> SessionStateLoader.load(...)
      -> ToolGateway.listDefinitions(...)
      -> AgentRunContext.builder(...)
   -> LoopInvocationService.invoke(context, eventSink)
      -> AgentLoopEngine.run(...)
   -> RuntimeCompletionHandler.complete(context, eventSink)
      -> PersistenceCoordinator.persistSuccess(...)
      -> RuntimeEventSink.runCompleted(...)
      -> AgentExecutionResultFactory.completed(...)
   -> RuntimeRunScope.close()
-> ChatResponseAssembler
-> ChatResponse
```

### 9.3 主链路时序（流式，按需）

```text
ChatStreamController
-> ChatStreamApplicationService
-> RuntimeOrchestrator.executeStreaming(command, eventConsumer)
   -> RuntimeExecutionContext.create(...)
   -> RuntimeCommandValidator.validate(command)
   -> RuntimeDeduplicationHandler.tryHandle(context)
   -> SessionResolutionService.resolve(command)
   -> RunIdentityFactory.createRunMetadata()
   -> RuntimeEventSinkFactory.create(context)
   -> RuntimeRunScopeManager.open(context)
   -> TurnInitializationService.start(context)
   -> RuntimeEventSink.runStarted()
   -> AgentRunContextFactory.create(context)
   -> LoopInvocationService.invoke(context, eventSink)
      -> LoopStreamObserverFactory.create(eventSink)
      -> AgentLoopEngine.runStreaming(runContext, observer)
         -> observer.onMessageStarted(assistantMessageId)
         -> observer.onMessageDelta(assistantMessageId, delta)
         -> observer.onToolCall(toolCall)
         -> observer.onToolResult(toolResult)
         -> observer.onMessageCompleted(message, finishReason, usage)
   -> RuntimeCompletionHandler.complete(context, eventSink)
      -> RuntimeEventSink.runCompleted(...)
   -> RuntimeRunScope.close()
-> SSE Event Stream
```

### 9.4 拆分后 `RuntimeOrchestrator` 目标结构

```java
@Slf4j
@Component
public class RuntimeOrchestrator {

    private final RuntimeCommandValidator commandValidator;
    private final RuntimeDeduplicationHandler deduplicationHandler;
    private final SessionResolutionService sessionResolutionService;
    private final RunIdentityFactory runIdentityFactory;
    private final RuntimeRunScopeManager runScopeManager;
    private final TurnInitializationService turnInitializationService;
    private final AgentRunContextFactory agentRunContextFactory;
    private final RuntimeEventSinkFactory eventSinkFactory;
    private final LoopInvocationService loopInvocationService;
    private final RuntimeCompletionHandler completionHandler;
    private final RuntimeFailureHandler failureHandler;

    public AgentExecutionResult execute(RuntimeExecuteCommand command) {
        return executeInternal(command, null, false);
    }

    public void executeStreaming(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer) {
        executeInternal(command, eventConsumer, true);
    }

    private AgentExecutionResult executeInternal(
        RuntimeExecuteCommand command,
        Consumer<RuntimeEvent> eventConsumer,
        boolean streaming
    ) {
        RuntimeExecutionContext context = RuntimeExecutionContext.create(command, eventConsumer, streaming);

        commandValidator.validate(command);

        Optional<AgentExecutionResult> dedupResult = deduplicationHandler.tryHandle(context);
        if (dedupResult.isPresent()) {
            return dedupResult.get();
        }

        context.setResolution(sessionResolutionService.resolve(command));
        context.setRunMetadata(runIdentityFactory.createRunMetadata());

        RuntimeEventSink eventSink = eventSinkFactory.create(context);

        try (RuntimeRunScope ignored = runScopeManager.open(context)) {
            TurnStartResult turnStart = turnInitializationService.start(context);
            context.setTurn(turnStart.getTurn());
            context.setUserMessage(turnStart.getUserMessage());

            eventSink.runStarted();

            AgentRunContext runContext = agentRunContextFactory.create(context);
            context.setRunContext(runContext);

            LoopExecutionResult loopResult = loopInvocationService.invoke(context, eventSink);
            context.setLoopResult(loopResult);

            return completionHandler.complete(context, eventSink);
        } catch (Throwable throwable) {
            throw failureHandler.handle(context, throwable, eventSink);
        }
    }
}
```

验收要求：

- `executeInternal(...)` 控制在 40~70 行以内。
- `executeInternal(...)` 读起来必须像主流程，而不是实现细节堆叠。
- 不再直接出现 `RuntimeEvent.builder()`。
- 不再直接出现 `AgentExecutionResult.builder()`。
- 不再直接操作 MDC。
- 不再直接操作 tryLock/unlock。
- 不再直接构建 `AgentRunContext`。

---

## 10. 幂等、并发与状态机（按需）

### 10.1 幂等策略

本次不改变幂等语义，只移动实现位置。

| 命中场景 | 处理策略 | 返回语义 |
|---|---|---|
| `COMPLETED` | 由 `RuntimeDeduplicationHandler` 使用 `AgentExecutionResultFactory.completedFromTurn(...)` 复用历史结果 | 返回已完成结果 |
| `RUNNING` | 由 `RuntimeDeduplicationHandler` 使用 `AgentExecutionResultFactory.processing(...)` 构建处理中结果 | 返回 `runStatus=RUNNING` |
| `FAILED` | 按当前项目既有策略处理；应由 `RuntimeDeduplicationHandler` 统一收口 | 返回失败语义或保持当前策略 |

### 10.2 并发策略

- 并发范围：按 `sessionId`。
- 控制策略：直接拒绝并发，不排队。
- 实现位置：`RuntimeRunScopeManager.open(context)`。
- 同步与流式一致性：同步和流式都必须走同一个 `RuntimeRunScopeManager`。

### 10.3 状态机

本次不修改状态机，只保持既有状态语义。

```text
Turn:
RUNNING -> COMPLETED
RUNNING -> FAILED
RUNNING -> CANCELLED

Session:
ACTIVE -> ARCHIVED
ACTIVE -> FAILED（仅会话级不可恢复错误）
```

重要约束：

- 普通 turn 失败不得自动将 session 标为 `FAILED`。
- 失败后 cache evict 的行为保持不变。
- `SessionStateLoader` 只装载 `COMPLETED` turn 的消息进入后续上下文，这一语义不得被本次重构破坏。

---

## 11. Provider 与 Tool 设计（按需）

### 11.1 模型调用端口

本次不修改模型调用端口。

- 统一端口：`LlmGateway`
- 返回对象：`ModelResponse`
- Provider-neutral 字段：
  - `content/text`
  - `toolCalls`
  - `finishReason`
  - `usage`
  - `provider`
  - `model`

### 11.2 Provider 选择策略

- 当前默认 provider：DeepSeek
- 备选 provider：OpenAI / Doubao 保留但本次不修改
- 路由策略：本次不做 LlmRouter

### 11.3 Tool 协议

本次不修改 tool 协议。

- tool call 记录策略：沿用当前 `ToolCallRecord` / `agent_tool_call`。
- tool result 记录策略：沿用当前 `ToolResultRecord` / `agent_tool_result`。
- 异常工具处理：沿用当前 loop engine / persistence 逻辑。

与本次相关的要求：

- 若引入 `LoopStreamObserver`，tool call / tool result 事件应通过 observer 实时转给 `RuntimeEventSink`。
- 不允许由 `RuntimeOrchestrator` 手动遍历 tool records 再重复构建事件，避免重复发送或字段不一致。

---

## 12. 配置设计（按需）

本次不新增配置项。

| 配置项 | 默认值 | 是否必填 | 说明 |
|---|---|---|---|
| N/A | N/A | N/A | 本次无配置变更 |

配置校验规则：

- 沿用当前项目配置校验。
- 不修改 provider 配置。
- 不修改 Redis / MySQL / Flyway 配置。

---

## 13. 可测试性与测试计划

### 13.1 必测场景

1. `RuntimeOrchestrator.executeInternal(...)` 同步成功链路行为不变。
2. `RuntimeOrchestrator.executeStreaming(...)` 流式成功链路行为不变。
3. 幂等命中 `COMPLETED` 时，由 `RuntimeDeduplicationHandler` 返回历史完成结果。
4. 幂等命中 `RUNNING` 时，由 `RuntimeDeduplicationHandler` 返回处理中结果。
5. session lock 获取失败时，由 `RuntimeRunScopeManager` 直接拒绝并发。
6. `RuntimeRunScope.close()` 必须释放 lock、关闭 MDC、清理 message sequence cursor。
7. `TurnInitializationService.start(...)` 必须创建 running turn、user message，并持久化 user message。
8. `AgentRunContextFactory.create(...)` 必须加载 session history、追加 user message、加载工具定义并构建 run context。
9. `RuntimeEventFactory` 构建的 `RUN_STARTED / RUN_COMPLETED / RUN_FAILED / MESSAGE_* / TOOL_*` 字段必须完整一致。
10. `RuntimeCompletionHandler.complete(...)` 必须持久化成功结果、发送 `RUN_COMPLETED`、返回 `AgentExecutionResult`。
11. `RuntimeFailureHandler.handle(...)` 必须保持既有失败语义：turn failed、session 不自动 failed、cache evict、发送 `RUN_FAILED`。
12. 流式 messageId 语义不得回退：`MESSAGE_STARTED / MESSAGE_DELTA / MESSAGE_COMPLETED` 使用 assistant messageId。

### 13.2 测试分层

- 单元测试：
  - `RuntimeCommandValidatorTest`
  - `RuntimeDeduplicationHandlerTest`
  - `RuntimeRunScopeManagerTest`
  - `TurnInitializationServiceTest`
  - `AgentRunContextFactoryTest`
  - `RuntimeEventFactoryTest`
  - `AgentExecutionResultFactoryTest`
  - `RuntimeCompletionHandlerTest`
  - `RuntimeFailureHandlerTest`
- 集成测试：
  - `RuntimeOrchestratorRefactorTest`
  - 覆盖同步成功、流式成功、幂等、并发拒绝、失败收口。
- 回归测试：
  - 保留现有 DTO contract 测试。
  - 保留现有 session resolution、persistence、loop 相关测试。
  - 不得为了拆分删除有效的业务行为测试。

### 13.3 与旧测试冲突处理

- 若旧测试只依赖 `RuntimeOrchestrator` 内部实现细节，应更新为面向新协作者或行为断言。
- 若旧测试断言对外协议、状态语义、持久化结果，必须继续通过。
- 本次需删除/替换测试清单：N/A，除非存在只校验旧私有方法结构的测试。

---

## 14. 分阶段实施计划

| 阶段 | 目标 | 改动范围 | 完成标准 | 回滚点 |
|---|---|---|---|---|
| 阶段 1 | 拆纯构建逻辑 | 新增 `RuntimeEventFactory`、`RuntimeEventSink`、`AgentExecutionResultFactory`、`RuntimeDeduplicationHandler` | orchestrator 不再直接构建 event/result，幂等分支移出 | 回滚新增 factory/handler，恢复旧 builder |
| 阶段 2 | 拆资源作用域与 turn 初始化 | 新增 `RuntimeExecutionContext`、`RuntimeRunScopeManager`、`RuntimeRunScope`、`TurnInitializationService`、`TurnStartResult` | orchestrator 不再直接操作 lock/MDC/user message/turn start | 回滚 scope/initialization 改造 |
| 阶段 3 | 拆 AgentRunContext 构建 | 新增 `AgentRunContextFactory` | orchestrator 不再直接加载 history、追加 user message、加载 tools、builder runContext | 回滚 context factory |
| 阶段 4 | 拆 loop 调用与 streaming observer | 新增 `LoopInvocationService`、`LoopStreamObserver`、`LoopStreamObserverFactory` | 同步/流式 loop 分支移出 orchestrator；stream event messageId 语义保持正确 | 回滚 loop invocation service |
| 阶段 5 | 拆成功/失败收口 | 新增 `RuntimeCompletionHandler`、`RuntimeFailureHandler` | orchestrator catch/complete 分支只调用 handler | 回滚 completion/failure handler |
| 阶段 6 | 收口 orchestrator 与测试 | 精简 `RuntimeOrchestrator`，补测试 | `executeInternal` 40~70 行，全部测试通过 | 回滚到阶段 5 前 |

实施要求：

- Codex 必须按阶段执行，不要一次性乱改。
- 每阶段结束后应保证工程可编译。
- 阶段之间不允许改变对外协议和数据结构。
- 若中途发现命名冲突，应保持职责不变，使用当前项目风格命名。

---

## 15. 风险与回滚

### 15.1 风险清单

| 风险 | 影响 | 概率 | 应对方案 | Owner |
|---|---|---|---|---|
| 拆分中遗漏原有失败持久化逻辑 | turn 状态或 cache 清理异常 | 中 | `RuntimeFailureHandlerTest` 覆盖失败收口 | 实现代理 |
| 流式事件重复发送 | 前端收到重复 `MESSAGE_COMPLETED` 或 tool event | 中 | `LoopStreamObserver` 与 `RuntimeCompletionHandler` 明确职责，测试验证事件序列 | 实现代理 |
| lock 未释放 | session 后续请求被误拒绝 | 中 | `RuntimeRunScope` 实现 `AutoCloseable`，测试异常路径 close | 实现代理 |
| MDC 未清理 | 日志上下文串线 | 中 | scope close 测试验证 restore/clear | 实现代理 |
| RuntimeExecutionContext 变成新上帝对象 | 未来继续膨胀 | 中 | 限制其只存执行过程状态，不放业务逻辑 | 评审人 |
| 过度拆分类导致包结构混乱 | 可读性下降 | 低 | 按本文档目录与职责命名 | 实现代理 |
| 误改 API / DDL | 破坏既有联调 | 低 | 红线明确禁止 | 评审人 |

### 15.2 回滚策略

- 协议层回滚：N/A，本次不改协议。
- 数据层回滚：N/A，本次不改数据结构。
- 主链路回滚：逐阶段回滚新增协作者，并恢复 `RuntimeOrchestrator` 原有内部实现。
- 测试回滚：只回滚本次新增结构测试，不删除既有行为测试。

---

## 16. 验收标准

### 16.1 功能验收

- 同步 chat 主链路结果与重构前一致。
- 流式 chat 主链路事件语义与重构前一致或更稳定。
- 幂等 `COMPLETED / RUNNING / FAILED` 行为不变。
- session lock 直接拒绝并发的语义不变。
- 普通 turn 失败不废 session 的语义不变。
- `CONTINUE_EXACT_SESSION` 状态限制不被破坏。
- 流式 message 类事件继续使用 assistant messageId。

### 16.2 架构与分层验收

- 不破坏模块依赖方向。
- `RuntimeOrchestrator.executeInternal(...)` 控制在 40~70 行以内。
- `RuntimeOrchestrator` 不直接构建 runtime event。
- `RuntimeOrchestrator` 不直接构建 `AgentExecutionResult`。
- `RuntimeOrchestrator` 不直接操作 MDC。
- `RuntimeOrchestrator` 不直接操作 session lock。
- `RuntimeOrchestrator` 不直接组装 `AgentRunContext`。
- `RuntimeOrchestrator` 不直接处理复杂失败持久化。
- Factory 只用于纯构建。
- Service / Handler / Manager / Coordinator 用于有副作用或生命周期逻辑。

### 16.3 协议与数据验收

- 协议字段、语义与当前设计一致。
- `traceId` 不得回到对外 DTO。
- 数据结构无变更。
- Flyway 脚本无新增。
- Redis key 无变更。
- 缓存回填策略不变。

### 16.4 测试验收

- 必测场景全部通过。
- 新增协作者单元测试。
- 保留主链路集成/回归测试。
- 过时的内部实现细节测试已更新。
- 不允许用删除有效行为测试的方式让构建通过。

---

## 17. 最终交付清单

1. 代码改造清单（类/包级别）
   - `RuntimeOrchestrator`
   - `RuntimeExecutionContext`
   - `RuntimeCommandValidator`
   - `RuntimeDeduplicationHandler`
   - `RuntimeRunScopeManager`
   - `RuntimeRunScope`
   - `TurnInitializationService`
   - `TurnStartResult`
   - `AgentRunContextFactory`
   - `RuntimeEventFactory`
   - `RuntimeEventSink`
   - `RuntimeEventSinkFactory`
   - `LoopInvocationService`
   - `LoopStreamObserver`
   - `LoopStreamObserverFactory`
   - `RuntimeCompletionHandler`
   - `RuntimeFailureHandler`
   - `AgentExecutionResultFactory`
2. 配置改造清单
   - N/A
3. 数据迁移脚本清单
   - N/A
4. 测试新增/修改/删除清单
   - 新增协作者单元测试
   - 更新 orchestrator 主链路测试
   - 保留现有 DTO / session / persistence / loop 测试
5. 变更总结
   - 主链路变化
   - Orchestrator 瘦身结果
   - 新增协作者职责
   - 未做项：API、DDL、Redis key、Provider 路由、长会话 summary checkpoint

---

## 18. 给实现代理的执行指令模板（可直接复用）

```text
你现在是本仓库实现代理。先完整阅读并严格遵守：
1) 根目录 AGENTS.md
2) 根目录 PROJECT_PLAN.md
3) 根目录 ARCHITECTURE.md
4) 根目录 CODE_REVIEW.md
5) agent-runtime-session-refactor-design-v2.md
6) 本设计文档：runtime-orchestrator-execute-internal-refactor-design.md

本次任务是 RuntimeOrchestrator.executeInternal(...) 拆分与运行编排收口，不是协议重构、存储重构或 provider 重构。

执行要求：
- 严格按本文档“分阶段实施计划”执行，不跳阶段。
- 不修改 ChatRequest / ChatResponse / ChatStreamEvent 对外协议。
- 不修改 MySQL 表结构，不新增 Flyway 脚本。
- 不修改 Redis key 结构。
- 不新增 LlmRouter。
- 不改变当前 session/turn/message/tool 业务语义。
- 不把逻辑简单拆成无职责边界的 private method。
- Factory 只负责纯构建。
- Service / Handler / Manager / Coordinator 承担生命周期、副作用或流程收口。
- 若旧测试不符合新内部结构，更新测试；若旧测试验证的是外部行为，必须继续通过。

阶段顺序：
1. 拆 RuntimeEventFactory / RuntimeEventSink / AgentExecutionResultFactory / RuntimeDeduplicationHandler。
2. 拆 RuntimeExecutionContext / RuntimeRunScopeManager / RuntimeRunScope / TurnInitializationService。
3. 拆 AgentRunContextFactory。
4. 拆 LoopInvocationService / LoopStreamObserver / LoopStreamObserverFactory。
5. 拆 RuntimeCompletionHandler / RuntimeFailureHandler。
6. 精简 RuntimeOrchestrator，补齐测试。

最终交付：
- 修改代码
- 新增/更新测试
- 输出变更总结：
  - 修改了哪些类
  - RuntimeOrchestrator 现在保留哪些职责
  - 新增协作者各自职责
  - executeInternal 行数和结构变化
  - 哪些行为保持不变
  - 未做项
```

---

## 19. 文档维护规则

- 本文档是 `vi-agent-core` 针对 `RuntimeOrchestrator.executeInternal(...)` 拆分的一次专题设计文档。
- 本文档采用增量更新，不做无理由整体改写。
- 若后续 `RuntimeOrchestrator` 又承担新的大块职责，应优先评估是否新增协作者，而不是继续堆到 orchestrator。
- 新设计点必须同步落到：术语、契约、领域、存储、主链路、测试、阶段计划。
- 本次不涉及的 API / 数据库 / Redis / Provider 决策，不应通过本文档间接修改。
