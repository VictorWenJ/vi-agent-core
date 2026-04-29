# Vi Agent Core P2.5-V1 模块治理与架构重构设计文档

版本：P2.5-V1.1 Draft  
状态：设计修订稿  
适用范围：模块划分、职责边界、依赖方向、领域对象归属、固定生命周期、未来 Graph / Skill / AgentRuntime 的架构口径  
不包含：执行计划、测试计划、Codex prompt、迁移批次、验收命令

---

## 1. 设计目标

P2.5-V1 的目标是将项目从早期横向技术分层：

```text
vi-agent-core-common
vi-agent-core-model
vi-agent-core-runtime
vi-agent-core-infra
vi-agent-core-app
```

调整为面向 Agent Framework 的纵向能力模块：

```text
vi-agent-core-common
vi-agent-core-contract
vi-agent-core-bootstrap
vi-agent-core-lifecycle
vi-agent-core-agent
vi-agent-core-graph
vi-agent-core-skill
vi-agent-core-tool
vi-agent-core-prompt
vi-agent-core-context
vi-agent-core-memory
vi-agent-core-provider
vi-agent-core-storage
vi-agent-core-audit
vi-agent-core-safety
vi-agent-core-evaluation
vi-agent-core-rag
vi-agent-core-app
```

核心目标：

1. 让能力模块自治，减少每次开发横跨 `model / runtime / infra / app` 的改动面。
2. 让模块内部专有对象回到所属模块管理。
3. 用 `vi-agent-core-contract` 承载真正跨模块共享的最小公共契约，避免循环依赖。
4. 用显式转换隔离模块边界，禁止内部领域对象、Storage Entity、Provider DTO 在模块间泄漏。
5. 将固定请求生命周期、Agent 执行、Graph 编排、Skill 剧本、Tool 执行、Prompt 装配、Context 构建、Memory 更新、Audit 记录等能力边界拆清楚。
6. 用 `vi-agent-core-bootstrap` 统一模块启动协议、模块描述、资源定位和启动报告，使各能力模块配置自治但由 `app` 统一组合。Spring AutoConfiguration 由 `app` 或各能力模块 `config` 包维护，不进入 bootstrap 领域模型。

一句话定义：

> P2.5-V1 的重构目标不是简单移动包，而是把项目从“横向技术分层的聊天运行时”升级成“纵向能力域自治的 Agent Framework 基座”。

---

## 2. 总体设计原则

### 2.1 能力域自治

顶层 Maven module 按 Agent Framework 的能力域划分。

每个能力模块内部可以保留局部分层：

```text
api          对外稳定入口、Command、Result、Facade、公共 DTO
domain       模块内部领域对象和规则
application  模块内部用例编排
port         模块需要的外部依赖接口
internal     模块内部 parser / mapper / validator / helper
config       模块配置
resources    模块资源
```

模块之间默认只能依赖对方的 `api` 或 `port` 契约，不允许依赖对方的 `internal / application / domain`。

---

### 2.2 专有对象归属本模块

模块专有对象必须放在所属能力模块中。

示例：

| 对象类型 | 归属模块 |
|---|---|
| `WorkingContext`、`ContextBlock`、`ContextPolicy` | `vi-agent-core-context` |
| `SessionStateSnapshot`、`ConversationSummary`、`StateDelta` | `vi-agent-core-memory` |
| `PromptTemplate`、`PromptRenderer`、`StructuredOutputContract` | `vi-agent-core-prompt` |
| `ToolDefinition`、`ToolInvocation`、`ToolPolicy` | `vi-agent-core-tool` |
| `AgentInvocation`、`AgentProfile`、`AgentResult` | `vi-agent-core-agent` |
| `GraphDefinition`、`GraphNode`、`GraphState` | `vi-agent-core-graph` |
| `MysqlEntity`、`RedisDocument`、`Mapper` | `vi-agent-core-storage` |

---

### 2.3 `contract` 只承载最小公共协议

新增：

```text
vi-agent-core-contract
```

`contract` 的职责是防止能力模块之间形成循环依赖，但它不是新的 `model` 大仓库。

允许进入 `contract` 的对象必须满足以下准入标准：

1. 至少有两个能力模块存在真实跨模块依赖理由。
2. 对象属于跨模块通信协议，而不是某个能力模块的完整领域模型。
3. 对象不包含领域行为、业务规则、流程逻辑或存储细节。
4. 对象不依赖 Spring、DB、Redis、Provider 协议实现或具体模块 internal 类型。
5. 对象进入 `contract` 前必须在设计文档或代码注释中说明依赖理由。

`contract` 可以放：

```text
TraceId / RunId / TurnId / MessageId / AgentExecutionId
ExecutionStatus / ResultStatus / FailureInfo
UsageInfo
ArtifactRefContract
基础 Message 传输协议
轻量 EvidenceRefContract
跨模块 CommandMetadata / ResultMetadata
基础枚举
```

`contract` 不允许放：

```text
完整 Message 领域模型
完整 Memory / Tool / Prompt / Context / Agent / Graph 模型
带业务行为的方法
Storage Entity / Redis Document
Provider Protocol DTO
因为“懒得依赖目标模块 api”而下沉的对象
```

治理规则：

```text
contract 是跨模块协议层。
模块私有领域对象必须留在所属模块。
跨模块传输时优先使用模块 api DTO / facade command/result。
只有无法归属到单一能力模块且确实被多个模块共同使用的对象，才进入 contract。
```

---

### 2.4 显式转换

跨模块传递必须通过：

```text
contract DTO
模块 api DTO
module facade command/result
```

模块内部对象转换必须显式完成，例如：

```text
XxxAssembler
XxxMapper
XxxConverter
```

典型转换：

| 转换 | 负责方 |
|---|---|
| `ChatRequest -> RuntimeCommand` | `app` |
| `RuntimePipelineState -> GraphInvocation / AgentExecutionPackage` | `lifecycle` / `graph` / capability assembler 边界 |
| `GraphExecutionResult / AgentResult -> TurnCompletionCommand` | `lifecycle` |
| `ToolExecution -> ToolExecutionAuditEvent` | `tool` / `audit` |
| `SessionStateSnapshot -> AgentSessionStateEntity` | `storage` |
| `ModelResponse -> AgentResult` | `agent` / `provider` 边界适配 |

禁止：

```text
Storage Entity 泄漏到业务模块
Provider 协议 DTO 泄漏到 Agent / Prompt / Memory 模块
模块 internal/domain 对象被其他模块直接 import
Map / JSON 字符串作为跨模块长期契约裸传递
```

---

### 2.5 固定生命周期与 Graph 编排门面分离

系统外层请求生命周期由 `vi-agent-core-lifecycle` 管理，职责是接收请求、建立运行上下文、进入 Agent 执行区域、完成持久化、触发记忆更新、收口事件。

`AGENT_EXECUTION` 内部采用**Graph 编排门面优先**的所有权模型：

```text
lifecycle
  -> GraphExecutionFacade / GraphKernel
      -> GraphNode
          -> AgentExecutorPort / ToolRuntimeFacade / RagRuntimeFacade / EvaluationFacade ...
```

所有权规则：

```text
Graph 是 AGENT_EXECUTION 内部的编排门面。
Agent 是 GraphNode 可调用的一类执行能力。
GraphKernel 负责执行 GraphDefinition、维护 GraphState、调度 GraphNode。
GraphNode 通过 port/facade 调用 Agent、Tool、RAG、Evaluation 等能力。
AgentRuntime 不反向拥有 GraphKernel。
lifecycle 不理解复杂 Agent 任务流，只进入 AGENT_EXECUTION 编排门面。
```

设计边界：

```text
Runtime Lifecycle = 外层固定请求生命周期
Graph              = AGENT_EXECUTION 内部任务编排门面
Agent              = GraphNode 可调用的 LLM 执行能力
Skill              = 任务剧本与流程建议
Tool               = 系统可执行能力
Prompt             = LLM 调用前表达装配
Context            = 模型可见上下文
Memory             = 状态与记忆闭环
Audit              = 全链路可追踪记录
```

---


### 2.6 模块资源与配置自治

各能力模块独立维护自己的 `resources`、默认配置、`ConfigurationProperties` 和模块级配置类。

设计规则：

```text
1. 每个能力模块可以拥有自己的 resources 目录。
2. 每个能力模块可以提供自己的默认配置文件。
3. 每个能力模块可以提供自己的 config 包和 AutoConfiguration。
4. app 作为组合根，负责启用模块、覆盖配置、绑定 adapter、管理 profile 和 secrets。
5. bootstrap 只定义模块启动协议、模块描述符、资源位置、完整性检查抽象和启动报告。
6. bootstrap 不承载 Spring Boot AutoConfiguration 抽象，不污染能力模块领域代码。
7. 模块默认配置不得使用通用 application.yml 命名。
8. 密钥、数据库密码、Redis 密码、外部 API Key 不得放入模块 resources。
9. P2.5 采用模块化单体，不将 prompt、tool、skill、memory 等模块拆成独立 Docker 服务。
10. 未来服务化通过 Facade / Port 替换本地实现，不通过提前拆分微服务实现。
```

推荐默认配置路径：

```text
classpath:/vi-agent-core/{module}/{module}-defaults.yml
```

示例：

```text
classpath:/vi-agent-core/prompt/prompt-defaults.yml
classpath:/vi-agent-core/tool/tool-defaults.yml
classpath:/vi-agent-core/skill/skill-defaults.yml
classpath:/vi-agent-core/memory/memory-defaults.yml
```

模块内部资源应使用稳定命名空间，避免多个 jar 中出现多个 `application.yml` 造成加载顺序和覆盖关系不清。

---

### 2.7 P2.5 冻结范围与后续阶段边界

P2.5 只冻结模块职责边界、依赖方向、所有权模型、配置来源和禁止事项。

对于 `graph / skill / safety / evaluation / rag` 等后续阶段重点模块，P2.5 不冻结完整字段、表结构、节点类型清单和所有领域对象。文档中的名称和示例用于表达边界与方向，真正字段契约留到对应阶段设计中冻结。

设计规则：

```text
P2.5 冻结：职责、边界、依赖方向、不得做什么。
P3 冻结：Graph Kernel / Tool Runtime Core 的可执行契约。
P4 冻结：Long-term Memory 的字段、策略和存储契约。
P5 冻结：RAG / Knowledge 的字段、索引和检索契约。
P6+ 冻结：Browser / Computer / Document / Human Approval 等工具细节。
```

---


### 2.8 P2.5-V1.1 收口修正规则

针对 P2.5-V1 初稿中的架构阻塞点，V1.1 采用以下修正口径：

| 阻塞点 | V1.1 收口结论 |
|---|---|
| Agent 与 Graph 所有权不清 | 采用 Graph 编排门面模型：Graph 是 `AGENT_EXECUTION` 内部编排入口，Agent 是 GraphNode 可调用执行能力 |
| `contract` 有重新变成大 model 风险 | 增加 contract 准入标准和禁止事项，禁止领域模型下沉 |
| `bootstrap` 边界过宽 | bootstrap 收窄为启动协议、模块描述、资源定位、完整性检查抽象和启动报告 |
| `storage` 可能成为聚合依赖点 | storage 只能依赖各模块 port 包，不依赖 api/domain/application/internal |
| Provider 抽象和实现混合 | provider 内部分 api/facade 与 adapter/protocol/internal |
| 未来模块定义过实 | P2.5 只冻结职责和禁止事项，详细字段留到对应阶段 |
| Agent 依赖过密 | 引入 `EffectiveAgentCapabilities` / `AgentExecutionPackage`，AgentRuntime 只消费执行包 |

---

## 3. 目标模块总览

| 模块 | 核心职责 |
|---|---|
| `vi-agent-core-common` | 通用异常、工具类、基础 ID 生成工具，不承载 Agent 语义 |
| `vi-agent-core-contract` | 跨模块最小公共契约，避免循环依赖 |
| `vi-agent-core-bootstrap` | 模块启动协议、模块描述符、资源定位、完整性检查抽象、启动报告 |
| `vi-agent-core-lifecycle` | 固定请求生命周期与 RuntimePipeline |
| `vi-agent-core-agent` | Agent 执行能力，向 GraphNode 暴露 AgentExecutorPort / AgentRuntimeFacade |
| `vi-agent-core-graph` | AGENT_EXECUTION 内部编排门面，提供 Graph Kernel、Node、Route、State、Checkpoint 边界 |
| `vi-agent-core-skill` | Skill 定义、加载、匹配、版本、资源、剧本管理 |
| `vi-agent-core-tool` | Tool 定义、工具集合、权限、风险、执行边界、结果校验 |
| `vi-agent-core-prompt` | Prompt 模板、变量、渲染、安全边界、结构化输出契约、parser |
| `vi-agent-core-context` | WorkingContext、ContextBlock、预算、裁剪、投影、上下文快照 |
| `vi-agent-core-memory` | Session Memory、Long-term Memory 预留、State、Summary、Evidence、Memory Update |
| `vi-agent-core-provider` | LLM / embedding / rerank / vision 等模型供应商适配 |
| `vi-agent-core-storage` | MySQL、Redis、Entity、Mapper、Repository 实现、Migration、事务边界 |
| `vi-agent-core-audit` | Runtime / Graph / Agent / Skill / Tool / Prompt / Memory 审计事件和查询 |
| `vi-agent-core-safety` | 权限、安全策略、风险识别、人类确认契约 |
| `vi-agent-core-evaluation` | 运行时校验、评估 Agent、离线评测基础抽象 |
| `vi-agent-core-rag` | 外部知识库、文档解析、chunk、embedding、检索、rerank、citation |
| `vi-agent-core-app` | HTTP 入口、DTO、应用服务、Spring 组合根 |

---

## 4. 模块核心概念与领域模型

### 4.1 `vi-agent-core-common`

| 概念 / 模型 | 说明 |
|---|---|
| `BaseException` / `ErrorCode` | 全项目通用异常与错误码基类，不带具体业务语义 |
| `IdGenerator` | 基础 ID 生成工具或接口，具体 ID 语义进入 `contract` |
| `JsonUtils` | JSON 序列化基础工具 |
| `ValidationUtils` | 通用校验辅助工具 |
| `TextUtils` | 通用文本处理工具 |

设计要求：

```text
common 不放 Agent 领域对象。
common 不放 Message / Tool / Memory / Prompt / Runtime 专有模型。
```

---

### 4.2 `vi-agent-core-contract`

| 概念 / 模型 | 说明 |
|---|---|
| `TraceId` | 一次请求或链路追踪 ID |
| `RunId` | 一次后端运行 ID |
| `TurnId` | 一轮 user-assistant 交互 ID |
| `MessageId` | 消息 ID |
| `AgentExecutionId` | 一次 Agent 执行 ID，可用于 main/sub/evaluator/internal |
| `ExecutionStatus` | 通用执行状态，如 `CREATED/RUNNING/SUCCEEDED/FAILED/SKIPPED` |
| `FailureInfo` | 跨模块失败摘要，包含错误码、错误类型、可重试性、用户可见信息 |
| `UsageInfo` | token / cost / latency 等通用用量信息 |
| `ArtifactRef` | 跨模块产物引用，如文件、报告、截图、导出文档 |
| `MessageContract` | 跨模块基础消息契约，不包含 Provider 私有字段 |
| `EvidenceRefContract` | 跨模块轻量证据引用；完整 evidence 领域模型归 `memory` 或 `audit` |
| `CommandMetadata` | 跨模块 command 的基础元信息，如 traceId、runId、requestId |
| `ResultMetadata` | 跨模块 result 的基础元信息，如状态、失败摘要、用量信息 |

设计要求：

```text
contract 是最小公共契约，不是新 model 大仓库。
模块私有领域对象不得因为“方便引用”而进入 contract。
```

---


### 4.3 `vi-agent-core-bootstrap`

| 概念 / 模型 | 说明 |
|---|---|
| `CoreModuleKey` | 模块唯一标识，如 `prompt`、`tool`、`memory` |
| `CoreModuleDescriptor` | 模块描述符，声明 module key、名称、版本、默认资源位置、是否默认启用、依赖模块、完整性检查项 |
| `CoreModuleRegistry` | 模块注册表，收集当前运行时可用的模块描述符和模块状态 |
| `ModuleResourceLocation` | 模块资源位置定义，如 catalog、schema、template、manifest、defaults.yml |
| `ModuleResourceLoader` | 模块资源加载器抽象，负责按约定路径读取模块资源 |
| `ModuleIntegrityCheck` | 模块完整性检查项抽象，例如 catalog 可加载、schema 可解析、manifest 存在 |
| `ModuleIntegrityChecker` | 模块完整性检查器接口，由各能力模块或 app 提供具体检查实现 |
| `ModuleStartupReport` | 模块启动报告，记录启用状态、配置来源、资源检查结果、失败原因 |
| `ModuleEnablementPolicy` | 模块启用策略，决定模块是否启用、是否允许缺失资源、是否 fail-fast |

设计要求：

```text
bootstrap 是模块启动协议层，不是业务运行时。
bootstrap 只定义 module descriptor、module key、resource location、integrity check 抽象和 startup report。
bootstrap 不承载 Spring Boot AutoConfiguration 抽象。
bootstrap 不直接实现 prompt/tool/memory/agent 运行逻辑。
bootstrap 不替代 app 组合根。
bootstrap 不做服务发现和微服务注册中心。
能力模块的领域代码不应依赖 Spring Boot 自动装配抽象。
```

模块启动推荐流程：

```text
App 启动
  -> 读取各模块 CoreModuleDescriptor
  -> 读取模块 defaults.yml 位置
  -> 应用 app/application.yml 与 profile 覆盖
  -> 由 app 或各模块 config 绑定 ConfigurationProperties
  -> 由 app 或各模块 config 执行 AutoConfiguration
  -> 调用 ModuleIntegrityChecker
  -> 生成 ModuleStartupReport
  -> 暴露模块 Facade / API Bean
```

Spring 相关规则：

```text
AutoConfiguration 放在 app 或各能力模块 config 包中。
bootstrap 不定义 @AutoConfiguration 基类或 Spring Boot 装配接口。
能力模块可以依赖 bootstrap 的 descriptor/resource/report 协议。
能力模块 domain/application/internal 不应依赖 Spring Boot 自动装配类型。
```

---

### 4.4 `vi-agent-core-lifecycle`

#### 4.4.1 职责

`lifecycle` 负责一次请求的固定系统生命周期。

核心模型：

| 概念 / 模型 | 说明 |
|---|---|
| `RuntimeCommand` | app 层传入 lifecycle 的运行命令，包含 requestId、conversationId、sessionId、message 等 |
| `RuntimeResult` | lifecycle 返回给 app 的统一结果，包含最终响应、状态、失败信息、用量、事件摘要 |
| `RuntimePipeline` | 固定生命周期步骤定义，维护步骤顺序和执行模式 |
| `RuntimePipelineExecutor` | 按 `RuntimePipeline` 顺序执行 step 的执行器 |
| `RuntimePipelineState` | 生命周期内共享状态，保存 session、turn、context、agent result、completion result 等 |
| `RuntimeStep` | 固定生命周期步骤接口 |
| `RuntimeStepKey` | 固定步骤枚举，带 code 和中文 desc |
| `RuntimeStepResult` | 单个 step 的执行结果，指示继续、短路、失败、降级等 |
| `RuntimeExecutionMode` | 执行模式，如 `SYNC`、`STREAMING`、未来可扩展 `ASYNC` |
| `RuntimeFailureHandler` | lifecycle 级失败处理边界 |
| `RuntimeResultAssembler` | 将 pipeline state 转换为最终 `RuntimeResult` |

---

#### 4.4.2 RuntimeStepKey 设计

推荐使用以下固定步骤：

| Step Key | 中文说明 | 主要输入 | 主要输出 |
|---|---|---|---|
| `DEDUP_CHECK` | 请求幂等检查 | `RuntimeCommand.requestId` | 命中则 short-circuit；未命中则继续 |
| `SESSION_RESOLVE` | 会话解析 | conversationId、sessionId、sessionMode | conversation/session 结果 |
| `RUN_IDENTITY_INIT` | 运行标识初始化 | request metadata | traceId、runId、turnId、MDC scope |
| `TURN_START` | 轮次启动 | user message、session 信息 | user message、turn running 状态 |
| `ROOT_CONTEXT_BUILD` | 根上下文构建 | session、summary、state、recent messages | WorkingContext / projection |
| `AGENT_EXECUTION` | Agent 执行入口 | WorkingContext、当前用户消息、profile、capability package | GraphExecutionResult / AgentResult |
| `TURN_COMPLETE` | 轮次完成持久化 | AgentResult、turn 信息 | assistant message、turn/session/run event 持久化结果 |
| `POST_TURN_MEMORY_UPDATE` | 轮次后记忆更新 | transcript、assistant result、tool result | summary/state/evidence 更新结果 |
| `RUN_EVENT_FLUSH` | 运行事件收口 | pipeline state、audit buffer | 最终 runtime event / stream event / audit flush |
| `RESPONSE_RETURN` | 响应组装返回 | completion result、failure info | RuntimeResult |

新设计使用 `AGENT_EXECUTION`，不建议继续使用 `AGENT_LOOP` 作为长期步骤名。该步骤内部由 GraphExecutionFacade 承载，简单场景可使用 single-agent graph，复杂场景可使用完整 Graph Kernel。

枚举示例：

```java
public enum RuntimeStepKey {

    DEDUP_CHECK("dedup_check", "请求幂等检查"),
    SESSION_RESOLVE("session_resolve", "会话解析"),
    RUN_IDENTITY_INIT("run_identity_init", "运行标识初始化"),
    TURN_START("turn_start", "轮次启动"),
    ROOT_CONTEXT_BUILD("root_context_build", "根上下文构建"),
    AGENT_EXECUTION("agent_execution", "Agent 执行入口"),
    TURN_COMPLETE("turn_complete", "轮次完成持久化"),
    POST_TURN_MEMORY_UPDATE("post_turn_memory_update", "轮次后记忆更新"),
    RUN_EVENT_FLUSH("run_event_flush", "运行事件收口"),
    RESPONSE_RETURN("response_return", "响应组装返回");

    private final String code;
    private final String desc;
}
```

---

#### 4.4.3 RuntimeStep 使用方式

`RuntimeStep` 接口示例：

```java
public interface RuntimeStep {

    RuntimeStepKey key();

    RuntimeStepResult execute(RuntimeStepContext context, RuntimePipelineState state);
}
```

`RuntimePipelineExecutor` 不直接写业务逻辑，只负责：

```text
1. 按 RuntimePipeline 定义的顺序取 step；
2. 执行 step；
3. 将 step result 合并到 RuntimePipelineState；
4. 处理 CONTINUE / SHORT_CIRCUIT / DEGRADED / FAILED；
5. 记录 step audit；
6. 返回 RuntimeResult。
```

`RuntimeStepResult` 推荐状态：

| 状态 | 说明 |
|---|---|
| `CONTINUE` | 当前步骤成功，继续执行下一个步骤 |
| `SHORT_CIRCUIT` | 当前步骤命中可直接返回的结果，例如幂等命中 |
| `SKIPPED` | 当前步骤按策略跳过，例如关闭 memory update |
| `DEGRADED` | 当前步骤失败但不影响主结果，例如 post-turn memory update 失败 |
| `FAILED` | 当前步骤失败且需要进入 lifecycle failure handler |

---

#### 4.4.4 维护规则

新增或调整 lifecycle step 时，必须同时维护：

1. `RuntimeStepKey` 枚举。
2. `RuntimeStep` 实现类。
3. `RuntimePipeline` 步骤顺序定义。
4. `RuntimeStepResult` 状态语义。
5. `RuntimePipelineState` 中对应的输入输出字段。
6. step audit 事件类型。
7. step 顺序契约测试。
8. failure / degraded 语义测试。

不允许某个 step 直接调用下一个 step。流程顺序只由 `RuntimePipelineExecutor` 维护。

---

#### 4.4.5 使用示例

示例 1：`DEDUP_CHECK`

```text
输入：requestId、conversationId、sessionId
处理：查询幂等缓存或持久化记录
输出：
  - 未命中：CONTINUE
  - 命中已完成结果：SHORT_CIRCUIT + RuntimeResult
  - 命中 RUNNING：FAILED 或按策略返回冲突状态
```

示例 2：`AGENT_EXECUTION`

```text
输入：WorkingContext、current user message、agent profile、available tools
处理：调用 GraphExecutionFacade；P2.5 可使用 single-agent graph 包装当前 SimpleAgentLoopEngine；P3 后扩展为完整 GraphKernel
输出：AgentResult
```

示例 3：`POST_TURN_MEMORY_UPDATE`

```text
输入：turn completion result、transcript、assistant message、tool outcomes
处理：调用 MemoryRuntimeFacade 执行 summary/state/evidence 更新
输出：MemoryUpdateResult
失败语义：默认 DEGRADED，不应回滚已成功的主回答
```

示例 4：`RUN_EVENT_FLUSH`

```text
输入：RuntimePipelineState、step audit buffer、stream mode
处理：写入 run event，flush stream final event，收口 audit
输出：event flush result
```

---

### 4.5 `vi-agent-core-agent`

| 概念 / 模型 | 说明 |
|---|---|
| `AgentExecutorPort` | GraphNode 调用 Agent 执行能力的端口，用于避免 Graph 与 Agent 形成概念闭环 |
| `AgentRuntimeFacade` | Agent 模块对外执行门面，接收已解析好的 `AgentExecutionPackage` |
| `AgentExecutionPackage` | Agent 执行包，包含已解析的上下文、Prompt 输入、可见工具、Skill 快照、输出契约和策略快照 |
| `AgentInvocation` | 一次 Agent 执行命令，包含执行范围、任务输入、profile key、parent execution 等基础信息 |
| `AgentResult` | 一次 Agent 执行结果，包含文本输出、结构化输出、tool calls、artifacts、usage、failure |
| `AgentExecutionScope` | 执行范围，例如 `ROOT / CHILD / EVALUATOR / INTERNAL_TASK` |
| `AgentProfile` | Agent 执行配置画像，定义默认模型、可见 skill/tool/prompt、上下文策略、记忆策略、风险等级 |
| `AgentExecutionContext` | Agent 执行上下文，包含 trace/run/turn/agentExecutionId、parentAgentExecutionId、scope 等 |
| `AgentDelegationPolicy` | Agent 是否允许委派子任务、委派给谁、允许多少并发、是否需要审批 |
| `AgentOutputContract` | Agent 输出契约，定义文本 / JSON / artifact / evidence 的输出要求 |
| `SingleAgentRunner` | 单 Agent 执行器，用于普通聊天或简单 tool loop |

设计要求：

```text
Agent 是执行能力模块，不是 AGENT_EXECUTION 的编排所有者。
AGENT_EXECUTION 内部编排由 GraphExecutionFacade / GraphKernel 承载。
GraphNode 通过 AgentExecutorPort 或 AgentRuntimeFacade 调用 Agent 执行能力。
AgentRuntime 只消费 AgentExecutionPackage，不直接到处查询 context/prompt/skill/tool/safety/evaluation。
```

#### 4.5.1 AgentExecutionScope 与 AgentProfile

`AgentExecutionScope` 只表示一次执行的身份分类，`AgentProfile` 表示该身份在某个场景下的具体执行画像。

| 概念 | 说明 | 示例 |
|---|---|---|
| `AgentExecutionScope` | 执行身份分类 | `ROOT`、`CHILD`、`EVALUATOR`、`INTERNAL_TASK` |
| `AgentProfile` | 执行配置画像 | `default-main-agent`、`coding-main-agent`、`research-main-agent`、`code-review-worker` |
| `AgentInvocation` | 一次具体调用 | `executionScope=ROOT` + `agentProfileKey=coding-main-agent` |

设计规则：

```text
Scope 不承载全部配置，避免枚举膨胀。
Profile 承载模型、Skill、Tool、Prompt、Context、Memory、Safety、Audit 等可见能力与策略引用。
同一个 Scope 可以绑定多个 Profile。
同一个 Profile 必须声明所属 Scope。
```

推荐 `AgentProfile` 字段为概念级配置，P2.5 不冻结完整字段契约：

```text
profileKey
executionScope
modelProfileRef
visibleSkillSetRefs
visibleToolSetRefs
promptProfileRefs
contextPolicyRef
memoryPolicyRef
safetyPolicyRef
auditPolicyRef
allowedNodeTypes
outputContractRefs
maxSteps
maxToolCalls
maxSubAgents
```

配置示例：

```yaml
agentProfiles:
  default-main-agent:
    profileKey: default-main-agent
    executionScope: ROOT
    modelProfileRef: deepseek-chat-default
    visibleSkillSetRefs:
      - common-main-skills
    visibleToolSetRefs:
      - read-only-tools
    promptProfileRefs:
      - default-main-prompt-profile
    contextPolicyRef: main-agent-session-context
    memoryPolicyRef: main-agent-session-memory
    safetyPolicyRef: default-safety-policy
    maxSteps: 8
    maxToolCalls: 4
    maxSubAgents: 2

  coding-main-agent:
    profileKey: coding-main-agent
    executionScope: ROOT
    modelProfileRef: deepseek-coding
    visibleSkillSetRefs:
      - coding-orchestration-skills
      - project-governance-skills
    visibleToolSetRefs:
      - project-read-tools
      - code-edit-tools
      - test-run-tools
    promptProfileRefs:
      - coding-main-prompt-profile
    contextPolicyRef: coding-main-context
    memoryPolicyRef: coding-main-memory
    safetyPolicyRef: strict-code-safety
    maxSteps: 20
    maxToolCalls: 12
    maxSubAgents: 5

  code-review-worker:
    profileKey: code-review-worker
    executionScope: CHILD
    modelProfileRef: deepseek-chat-default
    visibleSkillSetRefs:
      - code-review-skills
    visibleToolSetRefs:
      - read-only-project-tools
    promptProfileRefs:
      - child-agent-review-profile
    contextPolicyRef: child-agent-task-context
    memoryPolicyRef: child-agent-readonly-memory
    safetyPolicyRef: read-only-safety
    maxSteps: 6
    maxToolCalls: 3
    maxSubAgents: 0
```

#### 4.5.2 Agent 能力解析与执行包

Agent 执行前必须先计算本次执行的有效能力快照，并组装为 `AgentExecutionPackage`。

| 概念 / 模型 | 说明 |
|---|---|
| `AgentCapabilityResolver` | 根据 AgentProfile、Skill、环境、策略计算本次可见能力；实现可位于 lifecycle/app 组合层或独立应用服务，不应让 AgentRuntime 直接依赖所有能力模块 |
| `EffectiveAgentCapabilities` | 本次执行固化后的可见能力集合 |
| `AgentExecutionPackage` | AgentRuntime 消费的执行包，包含上下文、prompt 输入、可见工具 schema、skill snapshot、策略快照、输出契约 |
| `CapabilitySnapshot` | 持久化或审计用能力快照，记录本次实际可见 skill/tool/prompt/graph/policy |

解析规则：

```text
visibleSkills = AgentProfile.visibleSkillSetRefs ∩ SkillActivationRule ∩ SafetyPolicy
visibleTools  = AgentProfile.visibleToolSetRefs ∩ SelectedSkill.required/optionalToolSetRefs ∩ ToolPolicy ∩ SafetyPolicy
promptProfile = AgentProfile.promptProfileRefs + SelectedSkill.promptRefs + OutputContract prompt instruction
contextPolicy = AgentProfile.contextPolicyRef，可被 Skill 或 GraphNode 在受控范围内收窄
memoryPolicy  = AgentProfile.memoryPolicyRef，可被 ExecutionScope 和 Skill 进一步限制
```

AgentRuntime 的依赖收口规则：

```text
AgentRuntime 不直接查询 SkillRegistry / ToolRegistry / PromptCatalog / ContextLoader / MemoryStore / SafetyPolicy。
AgentRuntime 只消费 AgentExecutionPackage。
需要调用模型、工具或 Prompt 时，通过 Agent 模块定义的 port/facade 完成，由 app 绑定具体实现。
EffectiveAgentCapabilities 必须进入 audit，用于回答本次 Agent 到底看见了哪些 Skill、Tool、Prompt、Graph 模板和策略。
```

#### 4.5.3 AGENT_EXECUTION 内部执行来源

`AGENT_EXECUTION` 内部由 Graph 编排门面承载。Agent 模块作为执行能力被 GraphNode 调用。

| 来源 | 作用 |
|---|---|
| `AgentProfile` | 提供角色能力边界、默认模型、可见 skill/tool/prompt/context/memory/safety 策略 |
| `SkillDefinition` | 提供任务剧本、推荐执行模式、输出契约、所需工具集合、可用节点类型 |
| `GraphDefinition` | 提供强类型、可执行、可审计的流程结构 |
| `GraphRoutePolicy` | 根据 GraphState、NodeResult、风险、评估结果决定下一步 |
| `ToolPolicy` | 控制工具可见性和执行权限 |
| `ContextPolicy` | 控制 Agent 可见上下文范围 |
| `MemoryPolicy` | 控制记忆读写范围 |
| `SafetyPolicy` | 控制高风险动作 allow / ask / deny |
| `EvaluationPolicy` | 控制是否校验、是否进入 review/fix/replan |

推荐执行入口：

```text
AGENT_EXECUTION
  -> GraphExecutionFacade.execute(rootGraphInvocation)
      -> AgentCapabilityResolver / CapabilityPackageAssembler
      -> load GraphDefinition
      -> GraphKernel
          -> GraphNode
              -> AgentExecutorPort / ToolRuntimeFacade / RagRuntimeFacade / EvaluationFacade
      -> GraphExecutionResult
      -> AgentResult / RuntimeResult boundary
```

简单 chat 也可以用最小 GraphDefinition 表达：

```text
START -> SingleAgentNode -> FinalSynthesisNode -> END
```

---

### 4.6 `vi-agent-core-graph`

| 概念 / 模型 | 说明 |
|---|---|
| `GraphExecutionFacade` | `AGENT_EXECUTION` 内部编排门面，对 lifecycle 暴露统一执行入口 |
| `GraphKernel` | AgentWorkflow 调度内核，负责加载定义、维护状态、执行节点、处理路由 |
| `GraphDefinition` | 图定义，包含节点、边、入口、出口、条件路由规则 |
| `GraphExecutor` | 图执行器，按 ready node、edge、route policy 推进执行 |
| `GraphNode` | 图中的可执行节点接口，如 PlanNode、ToolExecuteNode、ValidatorNode；具体实现由能力模块提供 |
| `GraphNodeKey` | 节点唯一标识，枚举或注册键 |
| `GraphEdge` | 节点间流转关系 |
| `GraphState` | 图执行状态，保存计划、步骤结果、上下文引用、tool result、validation result 等 |
| `GraphNodeResult` | 单节点执行结果，包含状态、输出、state patch、下一步建议 |
| `GraphStatePatch` | 节点对 GraphState 的增量修改 |
| `GraphRoutePolicy` | 路由策略，决定下一步进入哪个 node 或 subgraph |
| `GraphCheckpointBoundary` | checkpoint 边界定义，为 P7 checkpoint/resume 预留 |
| `InterruptSignal` | 图执行中断信号，例如等待人工确认 |

设计要求：

```text
Graph 是 AGENT_EXECUTION 内部的编排门面。
Graph 不替代 lifecycle。
GraphKernel 不直接依赖 agent/tool/rag/evaluation 等具体模块实现。
GraphNode 通过 port/facade 调用具体能力。
具体 GraphNode 实现由能力模块提供，并由 app 组合注册。
```

#### 4.6.1 Skill 与 GraphDefinition 的边界

`SkillDefinition` 可以声明推荐执行模式、可用节点类型、需要的工具集合和 `graphRef`，但不直接作为运行时流程执行结构。

| 对象 | 职责 |
|---|---|
| `SkillDefinition` | 描述任务剧本、执行建议、输出契约、资源和工具需求 |
| `GraphDefinition` | 定义强类型可执行流程结构，包含 node、edge、condition、state schema、failure policy |
| `GraphNode` | 可复用执行单元，如 Plan、ToolExecute、SubAgentInvoke、Validator |
| `GraphExecutor` | 根据 GraphDefinition 和 GraphState 执行流程 |
| `GraphRoutePolicy` | 根据状态和条件决定下一步 |

设计规则：

```text
Skill 负责“建议使用哪类流程和能力”。
GraphDefinition 负责“流程具体如何执行”。
GraphNode 负责“单个步骤做什么”。
GraphExecutor 负责“按状态推进流程”。
```

#### 4.6.2 GraphDefinition 结构

`GraphDefinition` 至少包含以下概念级字段，P2.5 不冻结完整字段契约：

| 字段 | 说明 |
|---|---|
| `graphKey` | 图定义唯一标识 |
| `version` | 图版本 |
| `entryNode` | 入口节点 |
| `nodes` | 节点列表 |
| `edges` | 节点之间的流转关系 |
| `conditions` | 条件表达式或 route key |
| `stateSchema` | GraphState 允许保存的数据结构 |
| `maxSteps` | 最大执行步数 |
| `checkpointPolicy` | checkpoint 边界预留 |
| `interruptPolicy` | 中断与人工确认边界 |
| `failurePolicy` | retry / replan / fail 策略 |

示例：代码开发类 Skill 只引用图模板：

```yaml
skillKey: code-development-skill
version: 1.0.0
description: 用于代码开发、测试、评审、修复的任务剧本
executionPattern: PLAN_EXECUTE_REVIEW_FIX
graphRef: code-development-graph-v1
allowedNodeTypes:
  - PLAN
  - STEP_DISPATCH
  - SUB_AGENT_INVOKE
  - TOOL_EXECUTE
  - VALIDATOR
  - REPLAN
  - FINAL_SYNTHESIS
requiredToolSetRefs:
  - project-read-tools
  - code-edit-tools
  - test-run-tools
outputContractRef: code-development-result-v1
riskLevel: medium
```

对应的可执行 `GraphDefinition`：

```yaml
graphKey: code-development-graph-v1
version: 1.0.0
entryNode: plan
maxSteps: 30

nodes:
  - id: plan
    nodeType: PLAN
    config:
      plannerSkillRef: code-development-planning
      outputContractRef: plan-result-v1

  - id: dispatch
    nodeType: STEP_DISPATCH
    config:
      allowedStepTypes:
        - SUB_AGENT
        - TOOL_EXECUTE
        - VALIDATE

  - id: subagent_execute
    nodeType: SUB_AGENT_INVOKE
    config:
      defaultAgentProfileRef: default-worker-agent

  - id: tool_execute
    nodeType: TOOL_EXECUTE
    config:
      requireSafetyCheck: true

  - id: validate
    nodeType: VALIDATOR
    config:
      evaluatorProfileRef: code-review-evaluator

  - id: replan
    nodeType: REPLAN
    config:
      maxReplanTimes: 2

  - id: final
    nodeType: FINAL_SYNTHESIS

edges:
  - from: plan
    to: dispatch
    condition: plan.created == true

  - from: dispatch
    to: subagent_execute
    condition: currentStep.type == 'SUB_AGENT'

  - from: dispatch
    to: tool_execute
    condition: currentStep.type == 'TOOL_EXECUTE'

  - from: subagent_execute
    to: validate
    condition: stepResult.completed == true

  - from: tool_execute
    to: validate
    condition: toolResult.completed == true

  - from: validate
    to: dispatch
    condition: validation.passed == true && hasNextStep == true

  - from: validate
    to: replan
    condition: validation.passed == false && replanCount < 2

  - from: replan
    to: dispatch
    condition: plan.updated == true

  - from: validate
    to: final
    condition: validation.passed == true && hasNextStep == false

  - from: final
    to: END
```

执行链路：

```text
GraphExecutionFacade
  -> 加载 GraphDefinition
  -> GraphKernel 创建 GraphState
  -> GraphExecutor 从 entryNode 开始执行
  -> GraphNodeResult 产生 GraphStatePatch
  -> GraphRoutePolicy 根据 edge condition 决定下一节点
  -> GraphExecutionResult 转 Runtime 边界结果
```

#### 4.6.3 Graph 与 Node 实现依赖规则

`vi-agent-core-graph` 保持纯调度内核。具体 node 实现可以由能力模块提供，并由 `app` 组合注册。

```text
vi-agent-core-agent      -> MainAgentNode / SingleAgentNode / SubAgentInvokeNode
vi-agent-core-tool       -> ToolExecuteNode
vi-agent-core-rag        -> RagRetrieveNode
vi-agent-core-evaluation -> ValidatorNode / CriticNode
vi-agent-core-safety     -> HumanApprovalNode / RiskCheckNode
```

`graph` 模块不得反向依赖这些能力模块的内部实现。

---

### 4.7 `vi-agent-core-skill`

| 概念 / 模型 | 说明 |
|---|---|
| `SkillDefinition` | Skill 的定义，包含 key、版本、描述、适用场景、执行模式、资源列表；P2.5 只冻结边界，不冻结完整字段 |
| `SkillSnapshot` | 一次执行使用的 Skill 固化快照，用于审计和复现 |
| `SkillType` | Skill 类型，如主编排、子任务执行、工具使用、RAG、评估、文档生成 |
| `SkillExecutionPattern` | Skill 推荐执行模式，如 single-agent、plan-execute、review-fix、rag-pipeline |
| `SkillActivationRule` | Skill 激活规则，定义何时可被选择 |
| `SkillResolver` | 根据任务、agent profile、上下文选择 Skill |
| `SkillRegistry` | Skill 注册表 |
| `SkillCatalogRepository` | Skill 目录读取 port，可由文件、DB、远程 registry 实现 |
| `SkillResourceLoader` | 读取 `SKILL.md`、examples、templates、resources |
| `SkillContractValidator` | 校验 Skill manifest、输出契约、工具声明、风险等级 |

Skill 资源结构建议：

```text
skill.yml
SKILL.md
examples.md
templates/
resources/
scripts/    可选，必须受 safety 和 sandbox 控制
```

设计要求：

```text
Skill 是剧本和作业指导书。
Skill 可以声明建议 tool set、output contract、execution pattern。
Skill 不能直接执行 tool、调用 provider、写 memory、写 storage、绕过 audit。
```

#### 4.7.1 Skill 配置来源

Skill Catalog 按阶段演进：

| 阶段 | 配置来源 | 说明 |
|---|---|---|
| P2.5 / P3 初期 | 模块 resources YAML | 内置默认 skill，便于版本管理和代码评审 |
| P3 / P4 后 | MySQL Catalog | 支持运行时管理、后台配置、租户化、灰度发布 |
| 后期 | Admin UI / Config Center / Remote Catalog Service | 支持企业级管理、审批、远程分发 |

推荐短期目录：

```text
vi-agent-core-skill/src/main/resources/vi-agent-core/skill/
  skill-defaults.yml
  module.yml
  catalog/
    p2-governance/
      skill.yml
      SKILL.md
      examples.md
    code-review/
      skill.yml
      SKILL.md
```

#### 4.7.2 Skill 可见性与快照

Skill 不能全局暴露给所有 Agent。`SkillResolver` 必须基于 `AgentProfile`、`SkillActivationRule`、环境、SafetyPolicy 计算可见 Skill。

```text
visibleSkills = AgentProfile.visibleSkillSetRefs ∩ SkillActivationRule ∩ Skill.enabled ∩ Environment gating ∩ SafetyPolicy
```

一次 Agent 执行使用的 Skill 必须固化为 `SkillSnapshot`，进入 audit，用于复现和问题排查。

#### 4.7.3 Skill 与 Tool / Prompt / Graph 的关系

Skill 可以声明：

```text
graphRef
executionPattern
allowedNodeTypes
requiredToolSetRefs
optionalToolSetRefs
promptRefs
outputContractRef
evaluatorRef
riskLevel
```

但最终可用能力必须由 `AgentCapabilityResolver`、`ToolPolicy`、`SafetyPolicy` 二次过滤。

---

### 4.8 `vi-agent-core-tool`

| 概念 / 模型 | 说明 |
|---|---|
| `ToolDefinition` | 工具定义，包含 name、description、inputSchema、outputSchema、风险等级 |
| `ToolSet` | 工具集合，用于按 agent profile、skill、场景授权 |
| `ToolRegistry` | 工具注册表 |
| `ToolInvocation` | 一次工具调用命令，包含 toolName、arguments、toolCallId、调用来源 |
| `ToolResult` | 工具执行结果，包含结构化输出、文本摘要、artifact、failure |
| `ToolExecution` | 工具执行记录，包含状态流转、耗时、错误、audit metadata |
| `ToolExecutor` | 工具执行接口 |
| `ToolPolicy` | 工具策略，决定哪些 agent / skill 可使用哪些 tool |
| `ToolRiskLevel` | 工具风险等级，如 low / medium / high / critical |
| `ToolPermissionDecision` | 工具权限判断结果，如 allow / ask / deny |
| `ToolResultValidator` | 工具结果校验器 |
| `ToolAuditMapper` | 工具执行记录到审计事件的转换器 |

Tool metadata 至少包含：

```text
toolName
toolType
description
inputSchema
outputSchema
riskLevel
sideEffectType
requiresApproval
allowedAgentProfiles
allowedSkillKeys
costLevel
latencyLevel
capabilityTags
```

#### 4.8.1 ToolSet 与 Agent 可见性

Tool 不应全局暴露给所有 Agent。最终可见工具集由以下交集决定：

```text
visibleTools = AgentProfile.visibleToolSetRefs
             ∩ SelectedSkill.requiredToolSetRefs / optionalToolSetRefs
             ∩ ToolPolicy.allowlist
             ∩ SafetyPolicy runtime decision
```

配置示例：

```yaml
toolSets:
  read-only-project-tools:
    tools:
      - file.read
      - file.search
      - project.grep

  code-edit-tools:
    tools:
      - file.read
      - file.search
      - project.grep
      - file.patch

agentProfiles:
  code-review-worker:
    visibleToolSetRefs:
      - read-only-project-tools

  coding-main-agent:
    visibleToolSetRefs:
      - read-only-project-tools
      - code-edit-tools
      - test-run-tools
```

即使模型输出了不可见 toolName，`ToolRuntime` 也必须按 `ToolPolicy` 和 `SafetyPolicy` 拦截。

---

### 4.9 `vi-agent-core-prompt`

| 概念 / 模型 | 说明 |
|---|---|
| `PromptTemplate` | Prompt 模板定义 |
| `PromptRenderer` | 模板渲染器，负责变量替换和输出格式生成 |
| `PromptVariableFactory` | Prompt 变量工厂，负责把上下文、skill、memory、tool schema 转成变量 |
| `PromptRenderRequest` | 渲染请求，包含 template key、变量、输出模式、trust boundary |
| `PromptRenderResult` | 渲染结果，包含 system/user/messages、metadata、contract ref |
| `PromptRegistry` | Prompt 注册表 |
| `PromptCatalogRepository` | Prompt catalog 读取 port |
| `SystemPromptKey` | 系统 prompt key |
| `StructuredOutputContract` | 结构化输出契约定义 |
| `StructuredOutputContractKey` | 结构化输出契约 key |
| `StructuredOutputContractGuard` | 输出契约守卫，用于 schema、target、mode 校验 |
| `OutputParser` | 模型输出解析器 |
| `PromptInputSafetyBoundary` | Prompt 输入安全边界，区分 instruction 与 data block |
| `PromptInputTrustLevel` | 输入信任级别，如 trusted / untrusted |
| `PromptInputPlacement` | 输入放置位置，如 instruction / data_block / tool_result_block |

设计要求：

```text
Prompt 模块只负责 LLM 调用前的表达装配和输出契约治理。
Prompt 不决定执行流程，不执行工具，不写 memory。
```

#### 4.9.1 PromptProfile 与角色隔离

Prompt 不应全局随意使用。不同 AgentProfile 应绑定不同 `PromptProfile`。

| Agent 类型 | Prompt 重点 |
|---|---|
| Main Agent | 任务理解、计划拆分、路由、结果聚合 |
| Child SubAgent | 只完成指定子任务、遵守上下文边界、结构化返回 |
| Evaluator Agent | 检查、打分、发现问题，不负责执行 |
| Internal Memory Agent | 严格 JSON 输出、按 schema 抽取 summary/state/evidence |

推荐配置：

```yaml
promptProfiles:
  main-agent-planning-profile:
    systemPromptRefs:
      - main-agent-system
      - planning-policy
    taskPromptRefs:
      - plan-task-template
    outputContractRefs:
      - plan-output-contract

  child-agent-review-profile:
    systemPromptRefs:
      - child-agent-system
      - code-review-policy
    taskPromptRefs:
      - review-task-template
    outputContractRefs:
      - review-finding-contract
```

最终 Prompt 由以下部分共同装配：

```text
AgentProfile.promptProfileRefs
+ SelectedSkill.promptRefs
+ OutputContract prompt instruction
+ ContextPolicy selected blocks
+ Tool schema / Skill metadata / Safety boundary
```

---

### 4.10 `vi-agent-core-context`

| 概念 / 模型 | 说明 |
|---|---|
| `WorkingContext` | 一次模型调用前的完整工作上下文 |
| `WorkingContextBuildResult` | 上下文构建结果，包含 block、budget、decision、snapshot refs |
| `ContextBlock` | 上下文块基础模型，如 runtime instruction、summary、state、recent messages |
| `ContextBlockSet` | 上下文块集合 |
| `ContextBlockType` | 上下文块类型枚举 |
| `ContextPolicy` | 上下文策略，定义优先级、预算、裁剪、来源选择 |
| `ContextPlanner` | 上下文规划器，决定纳入哪些 block |
| `ContextBudgetCalculator` | token budget 计算器 |
| `WorkingContextLoader` | 从 memory/transcript/session 加载上下文输入 |
| `WorkingContextBuilder` | 构建 WorkingContext |
| `WorkingContextProjector` | 将 WorkingContext 投影为 provider-ready messages |
| `WorkingContextValidator` | 上下文校验器 |
| `WorkingContextSnapshot` | 上下文快照，用于 audit/debug |
| `ContextSourceRef` | 上下文来源引用 |

设计要求：

```text
Context 负责模型可见信息的选择、排序、裁剪、投影。
Context 不负责 memory 写入，不负责 prompt catalog，不执行 provider 调用。
```

#### 4.10.1 多 Agent Context 隔离

不同 Agent 使用同一套 Context 构建逻辑，但通过 `ContextBuildRequest`、`ContextPolicy`、`AgentExecutionScope` 区分可见范围。

推荐 `ContextBuildRequest` 字段：

```text
runId
turnId
agentExecutionId
parentAgentExecutionId
executionScope
agentProfileKey
skillKey
taskInput
parentContextRefs
contextPolicyRef
```

推荐 `ContextPolicy` 字段：

```text
policyKey
allowedBlockTypes
maxTokenBudget
includeSessionState
includeConversationSummary
includeRecentMessages
includeToolResults
includeParentTask
includeEvidenceRefs
includeLongTermMemory
redactSensitiveContent
```

角色示例：

| 角色 | 可见 Context | 默认不可见 |
|---|---|---|
| Main Agent | runtime instruction、session state、summary、recent messages、current user message、visible tool schema | 未授权 tool schema、其他 agent 私有 scratchpad |
| Child SubAgent | parent 分配的子任务、必要 evidence refs、skill instruction、允许工具 schema | 完整主 transcript、无关历史、其他 subagent 私有数据 |
| Evaluator Agent | 待评估输出、评估标准、evidence refs、output contract、必要上下文摘要 | 高风险工具、非评估相关 skill |
| Internal Memory Agent | 本轮 transcript、old summary/state、extraction schema、evidence candidates | 工具执行权限、自由回答 prompt |

---

### 4.11 `vi-agent-core-memory`

#### 4.11.1 职责边界

`memory` 负责 Agent 运行过程中沉淀和读取的记忆能力。

本模块同时承载：

1. **Session Memory / 短期记忆**：P2 已有核心能力，P2.5 重点治理。
2. **Long-term Memory / 长期记忆**：P4 重点实现，P2.5 只预留归属和边界。
3. **Evidence / 证据绑定**：用于说明 memory 写入来源和可靠性。
4. **Internal Memory Task / 内部记忆任务**：summary/state/evidence 等内部 LLM 任务。

RAG 不归 memory。RAG 是外部知识库检索，归 `vi-agent-core-rag`。

---

#### 4.11.2 核心概念

| 概念 / 模型 | 说明 |
|---|---|
| `SessionWorkingSetSnapshot` | 当前 session 的工作集快照，包含最近消息、工具结果、summary 覆盖范围等 |
| `SessionStateSnapshot` | Session 级结构化状态，如目标、事实、约束、偏好、决策、open loops |
| `ConversationSummary` | 会话滚动摘要 |
| `StateDelta` | 一轮结束后抽取出的状态变更补丁 |
| `StateDeltaMerger` | 将 StateDelta 合并进 SessionStateSnapshot 的规则组件 |
| `ConversationSummaryExtractor` | 会话摘要抽取器 |
| `StateDeltaExtractor` | 状态补丁抽取器 |
| `MemoryUpdateWorkflow` | 轮次后记忆更新流程，协调 state、summary、evidence、cache refresh |
| `MemoryEvidenceBinder` | 记忆证据绑定器 |
| `EvidenceRef` | 证据引用 |
| `EvidenceSource` | 证据来源，如 message、tool result、summary、manual note |
| `EvidenceTarget` | 证据目标，如 fact、constraint、decision、preference、open loop |
| `InternalMemoryTask` | 内部记忆任务，如 summary extract、state extract、evidence bind |
| `MemoryPolicy` | 记忆读写策略 |
| `MemoryWriteMode` | 记忆写入模式，如 append、patch、override、close |
| `LongTermMemoryRecord` | 长期记忆记录，P4 目标模型 |
| `MemoryScope` | 记忆作用域，如 session、conversation、user、project、workspace |
| `MemoryRetentionPolicy` | 记忆保留、过期、删除策略，P4 目标模型 |
| `MemoryRecallQuery` | 长期记忆召回查询，P4 目标模型 |

---

#### 4.11.3 短期记忆与长期记忆

短期记忆：

```text
SessionWorkingSetSnapshot
SessionStateSnapshot
ConversationSummary
RecentMessages
StateDelta
EvidenceRef
```

特点：

```text
围绕当前 session / conversation 工作
服务本轮和后续短期上下文构建
P2 / P2.5 是当前重点
```

长期记忆：

```text
LongTermMemoryRecord
UserPreferenceMemory
ProjectMemory
LongTermFact
MemoryRetentionPolicy
MemoryRecallQuery
```

特点：

```text
跨 session / conversation 持久存在
需要召回、过期、删除、权限、证据、用户可控
P4 才进入重点实现
```

P2.5 结论：

```text
memory 模块同时拥有短期记忆和长期记忆的领域归属。
P2.5 只治理和迁移当前短期记忆链路。
长期记忆只做归属预留，不做完整实现。
```

#### 4.11.4 推荐目录边界

`memory` 模块内部必须区分短期记忆、长期记忆、证据、内部任务、策略、workflow。

```text
vi-agent-core-memory
  src/main/java/com/vi/agent/core/memory/
    api/
      MemoryRuntimeFacade.java
      MemoryReadCommand.java
      MemoryWriteCandidate.java
      MemoryUpdateResult.java

    session/
      SessionWorkingSetSnapshot.java
      SessionStateSnapshot.java
      ConversationSummary.java
      StateDelta.java
      StateDeltaMerger.java
      SessionMemoryService.java

    longterm/
      LongTermMemoryRecord.java
      LongTermMemoryType.java
      LongTermMemoryPolicy.java
      MemoryRecallQuery.java
      MemoryRetentionPolicy.java
      LongTermMemoryService.java

    evidence/
      EvidenceRef.java
      EvidenceSource.java
      EvidenceTarget.java
      MemoryEvidenceBinder.java

    task/
      InternalMemoryTask.java
      InternalMemoryTaskService.java
      StateDeltaExtractor.java
      ConversationSummaryExtractor.java

    policy/
      MemoryPolicy.java
      MemoryScope.java
      MemoryScopeType.java
      MemoryWriteMode.java

    workflow/
      MemoryUpdateWorkflow.java
      MemoryUpdateStep.java
      MemoryCacheRefreshStep.java

    port/
      SessionMemoryRepository.java
      ConversationSummaryRepository.java
      LongTermMemoryRepository.java
      InternalMemoryTaskRepository.java

    internal/
      MemoryMapper.java
      MemoryValidator.java
```

#### 4.11.5 MemoryScope 与多 Agent 隔离

不同 Agent 使用同一套 memory 读写逻辑，但通过 `MemoryScope`、`MemoryPolicy`、`agentExecutionId` 区分数据范围和写入权限。

推荐 `MemoryScopeType`：

```java
public enum MemoryScopeType {

    SESSION("session", "会话短期记忆"),
    CONVERSATION("conversation", "对话级记忆"),
    AGENT_EXECUTION("agent_execution", "单次 Agent 执行工作记忆"),
    CHILD_TASK("child_task", "子任务临时记忆"),
    USER("user", "用户长期记忆"),
    PROJECT("project", "项目长期记忆"),
    WORKSPACE("workspace", "工作区长期记忆");

    private final String code;
    private final String desc;
}
```

角色默认策略：

| 角色 | Memory 读写边界 |
|---|---|
| Main Agent | 可读 session summary/state 和授权长期记忆；可提出 memory write candidate；不直接写长期记忆 |
| Child SubAgent | 默认只读 parent 提供的 memory refs；可使用 child scratchpad；不直接写 session/long-term memory |
| Evaluator Agent | 只读待评估对象和证据；默认不写 memory |
| Internal Memory Agent | 专门执行 summary/state/evidence 更新；写入必须经过 MemoryUpdateWorkflow |

---

### 4.12 `vi-agent-core-provider`

Provider 模块内部必须区分统一模型调用契约和厂商协议实现，避免其他能力模块同时看见抽象入口和具体厂商 DTO。

推荐内部结构：

```text
vi-agent-core-provider
  api/
    LlmGateway
    ModelRequest
    ModelResponse
    ModelToolCall
    ProviderType
    ProviderCapability
    ProviderRuntimeFacade

  adapter/
    OpenAICompatibleChatProvider
    DeepSeekChatProvider
    DoubaoChatProvider
    LocalModelProvider

  internal/
    request builder
    response parser
    streaming adapter
    structured output adapter

  protocol/
    openai dto
    deepseek dto
    doubao dto
```

| 概念 / 模型 | 说明 |
|---|---|
| `LlmGateway` | 模型调用统一入口，只放在 provider api |
| `ModelRequest` | 统一模型请求，不绑定具体供应商协议 |
| `ModelResponse` | 统一模型响应 |
| `ModelToolCall` | 模型返回的工具调用意图 |
| `ProviderType` | 供应商类型，如 OpenAI、DeepSeek、Doubao、local |
| `ProviderConfig` | Provider 配置 |
| `ProviderRuntimeFacade` | Provider 对外门面 |
| `StreamingAdapter` | 流式响应适配器，属于 internal/adapter |
| `StructuredOutputRequestAdapter` | 结构化输出请求适配器，属于 internal/adapter |
| `StructuredOutputResponseExtractor` | 结构化输出响应提取器，属于 internal/adapter |
| `ProviderProtocolDTO` | 具体供应商协议 DTO，只允许在 provider `protocol/internal` 中使用 |

设计要求：

```text
其他模块只能依赖 provider api / facade。
Provider protocol DTO 不得泄漏到 Agent / Prompt / Memory / Tool / Context 模块。
Provider adapter 可以依赖 provider api，但 provider api 不依赖 adapter/protocol。
Prompt / Agent / Memory 等模块不直接拼厂商协议请求。
```

---

### 4.13 `vi-agent-core-storage`

| 概念 / 模型 | 说明 |
|---|---|
| `MysqlEntity` | MySQL 表实体 |
| `MybatisMapper` | MyBatis Mapper |
| `RedisDocument` | Redis 缓存文档 |
| `RedisKeyBuilder` | Redis key 构造器 |
| `RepositoryImplementation` | 各模块 port 的存储实现 |
| `StorageConverter` | Port DTO / Contract DTO 与 Entity / Document 的转换器 |
| `Migration` | Flyway migration 脚本 |
| `TransactionBoundary` | 事务边界实现 |
| `TypeHandler` | MyBatis 类型处理器 |

设计要求：

```text
业务模块定义 repository port。
storage 只能实现各模块 port。
storage 只能依赖各模块 port 包，不能依赖 api/domain/application/internal。
业务模块不能依赖 storage。
Storage Entity / Redis Document 不得泄漏到业务模块。
```

示例：

```text
memory 模块：memory.port.MemoryStateRepository + MemoryStateRecord
storage 模块：MysqlMemoryStateRepository implements MemoryStateRepository
app 模块：负责 Spring Bean 装配
```

如果 storage 实现需要领域对象字段，必须由对应模块在 `port` 包提供专用 persistence record / command，不允许 storage import 模块 `domain` 类型。

---

### 4.14 `vi-agent-core-audit`

| 概念 / 模型 | 说明 |
|---|---|
| `RunAudit` | 一次 run 的审计记录 |
| `RuntimeStepAudit` | 固定 lifecycle step 的审计记录 |
| `GraphNodeAudit` | Graph node 执行审计 |
| `AgentExecutionAudit` | Agent 执行审计 |
| `SkillUsageAudit` | Skill 使用审计，包含 skill key、version、snapshot |
| `PromptRenderAudit` | Prompt 渲染审计 |
| `ModelCallAudit` | 模型调用审计 |
| `ToolExecutionAudit` | 工具执行审计 |
| `MemoryUpdateAudit` | 记忆更新审计 |
| `EvidenceAudit` | 证据绑定审计 |
| `SafetyDecisionAudit` | 安全 / 权限判断审计 |
| `EvaluationAudit` | 评估结果审计 |
| `AuditEvent` | 通用审计事件 |
| `AuditQueryService` | 审计查询服务 |

Audit 需要回答：

```text
这次请求走了哪些 step / node？
使用了哪个 skill？
调用了哪些 tool？
模型看到了哪些 context？
prompt 是否通过安全边界？
memory 写入了什么？
哪个 validator / evaluator 给出了什么结果？
```

#### 4.14.1 低入侵审计设计

Audit 不应散落在业务逻辑内部。优先由执行器、Facade、Decorator 在边界统一记录。

三层审计入口：

| 层级 | 记录方 | 审计类型 |
|---|---|---|
| Runtime Lifecycle | `RuntimePipelineExecutor` | `RuntimeStepAudit` |
| Graph Workflow | `GraphKernel` / `GraphExecutor` | `GraphNodeAudit` / `GraphRunAudit` |
| 能力模块 | Facade Decorator / Gateway Decorator | `AgentExecutionAudit`、`ToolExecutionAudit`、`PromptRenderAudit`、`ModelCallAudit`、`MemoryUpdateAudit` 等 |

推荐 Decorator：

```text
PromptRuntimeFacadeDecorator  -> PromptRenderAudit
ToolRuntimeFacadeDecorator    -> ToolExecutionAudit
AgentRuntimeFacadeDecorator   -> AgentExecutionAudit
MemoryRuntimeFacadeDecorator  -> MemoryUpdateAudit
ProviderGatewayDecorator      -> ModelCallAudit
SafetyRuntimeFacadeDecorator  -> SafetyDecisionAudit
```

业务模块只返回 Result + AuditMetadata，审计事件由边界层统一组装。

#### 4.14.2 统一 AuditEventEnvelope

```text
AuditEventEnvelope
  - auditEventId
  - parentAuditEventId
  - traceId
  - runId
  - turnId
  - agentExecutionId
  - parentAgentExecutionId
  - graphRunId
  - graphNodeExecutionId
  - runtimeStepKey
  - eventType
  - sourceType
  - sourceKey
  - status
  - startedAt
  - endedAt
  - durationMs
  - inputSummary
  - outputSummary
  - evidenceRefs
  - artifactRefs
  - failureInfo
  - redactionLevel
  - payloadJson
```

推荐 `AuditSourceType`：

```java
public enum AuditSourceType {

    RUNTIME_STEP("runtime_step", "运行时固定步骤"),
    GRAPH_NODE("graph_node", "图节点执行"),
    AGENT_EXECUTION("agent_execution", "Agent 执行"),
    SKILL_USAGE("skill_usage", "Skill 使用"),
    PROMPT_RENDER("prompt_render", "Prompt 渲染"),
    MODEL_CALL("model_call", "模型调用"),
    TOOL_EXECUTION("tool_execution", "工具执行"),
    MEMORY_UPDATE("memory_update", "记忆更新"),
    SAFETY_DECISION("safety_decision", "安全决策"),
    EVALUATION("evaluation", "评估结果"),
    STORAGE_OPERATION("storage_operation", "存储操作");

    private final String code;
    private final String desc;
}
```

#### 4.14.3 动态 SubAgent 审计树

动态编排链路必须通过 `parentAuditEventId`、`agentExecutionId`、`parentAgentExecutionId`、`graphRunId`、`graphNodeExecutionId` 建立树状审计关系。

```text
RunAudit(runId=R1)
  -> RuntimeStepAudit(step=AGENT_EXECUTION)
      -> GraphRunAudit(graphRunId=G1)
          -> GraphNodeAudit(node=MainAgentNode)
              -> AgentExecutionAudit(agentExecutionId=A_ROOT)
                  -> SkillUsageAudit(skill=main-planning)
                  -> PromptRenderAudit
                  -> ModelCallAudit

          -> GraphNodeAudit(node=SubAgentInvokeNode)
              -> AgentExecutionAudit(agentExecutionId=A_CHILD_1, parent=A_ROOT)
                  -> SkillUsageAudit(skill=code-review)
                  -> PromptRenderAudit
                  -> ModelCallAudit
                  -> ToolExecutionAudit(tool=file.read)

          -> GraphNodeAudit(node=ToolExecuteNode)
              -> SafetyDecisionAudit
              -> ToolExecutionAudit(tool=test.run)

          -> GraphNodeAudit(node=ValidatorNode)
              -> EvaluationAudit
```

审计写入可以先采用同步边界记录 + 异步 sink 落库；业务执行路径不得直接依赖审计存储成功。

---

### 4.15 `vi-agent-core-safety`

P2.5 只冻结 Safety 的职责边界、调用入口和决策方向，不冻结完整字段、表结构或策略 DSL。

| 概念 / 模型 | 说明 |
|---|---|
| `RiskLevel` | 风险等级 |
| `PermissionPolicy` | 权限策略 |
| `PermissionDecision` | 权限判断结果，如 allow、ask、deny |
| `HumanApprovalRequirement` | 人类确认要求 |
| `SensitiveActionDetector` | 敏感动作识别器 |
| `ToolRiskPolicy` | 工具风险策略 |
| `AgentRiskPolicy` | Agent 执行风险策略 |
| `MemoryWriteSafetyPolicy` | 记忆写入安全策略 |
| `ApprovalRequest` | 人类审批请求 |
| `ApprovalResult` | 人类审批结果 |

设计要求：

```text
Safety 只做判断和拦截。
具体执行仍归 Tool / Agent / Graph。
```

#### 4.15.1 RiskAssessment 模型

Safety 以确定性规则和策略表为主，LLM 辅助判断作为后续增强。

推荐 `RiskAssessmentRequest` 字段：

```text
executionScope
agentProfileKey
skillKey
actionType
targetResource
toolName
argumentsSummary
dataSensitivity
sideEffectType
userExplicitlyRequested
environment
```

推荐 `RiskAssessmentResult` 字段：

```text
riskLevel
permissionDecision
reasons
matchedPolicyIds
requiredApprovals
mitigations
```

`PermissionDecision`：

```text
ALLOW   允许执行
ASK     需要人工确认
DENY    阻断执行
```

#### 4.15.2 风险评估维度

| 维度 | 示例 |
|---|---|
| `sideEffectType` | READ / WRITE / DELETE / EXECUTE / SUBMIT / PAYMENT |
| `dataSensitivity` | PUBLIC / USER_PRIVATE / SECRET / CREDENTIAL |
| `targetResource` | local file / database / browser / external API / shell |
| `reversibility` | 可恢复 / 不可恢复 |
| `scopeSize` | 单条 / 批量 / 全库 / 全目录 |
| `userIntentStrength` | 用户明确要求 / 模型自行推断 |
| `agentExecutionScope` | ROOT / CHILD / EVALUATOR / INTERNAL_TASK |
| `skillRiskLevel` | Skill 声明风险等级 |
| `toolRiskLevel` | Tool metadata 风险等级 |
| `environment` | dev / test / prod |

#### 4.15.3 Safety 调用边界

| 阶段 | 调用点 | 目标 |
|---|---|---|
| Skill 加载期 | `SkillCatalogLoader` / `SkillResolver` | 过滤不可用、不可信、不满足环境条件的 Skill |
| Agent 能力解析期 | `AgentCapabilityResolver` | 过滤当前 Agent 不允许看见的 Skill/Tool/Prompt/Graph |
| Graph 路由期 | `GraphRoutePolicy` | 进入高风险 node 前做 allow/ask/deny |
| Tool 执行期 | `ToolRuntime` | 执行 tool 前做权限和风险判断 |
| Memory 写入期 | `MemoryRuntime` | 写长期记忆、敏感记忆前做证据和权限检查 |
| Prompt 装配期 | `PromptRuntime` | 标记 untrusted input 和 data block，不做权限替代 |

#### 4.15.4 典型决策规则

| 场景 | 默认决策 |
|---|---|
| 只读工具、低敏感数据 | `ALLOW` |
| 读用户私有文件 | `ASK` 或按策略 allow |
| 写文件 | `ASK` |
| 删除文件 / 批量删除 | `ASK`，必要时二次确认 |
| shell execute | 默认 `ASK` 或 `DENY` |
| 数据库写操作 | 默认 `ASK` |
| 支付 / 提交表单 / 发邮件 | 默认 `ASK`，高风险场景 `DENY` |
| SubAgent 调用未授权 tool | `DENY` |
| Evaluator 调用执行类 tool | 默认 `DENY` |
| Internal memory task 调用外部 tool | `DENY` |

#### 4.15.5 Safety 与其他模块的边界

```text
Safety 负责风险和权限判断。
Evaluation 负责质量和正确性判断。
Audit 负责记录。
ToolRuntime 负责执行工具。
GraphKernel 负责路由。
MemoryRuntime 负责记忆写入。
```

---

### 4.16 `vi-agent-core-evaluation`

P2.5 只冻结 Evaluation 的职责边界和与 Graph/Agent 的关系，不冻结完整评估指标、表结构或离线平台设计。

| 概念 / 模型 | 说明 |
|---|---|
| `OutputContractValidator` | 输出契约校验器 |
| `ToolResultValidator` | 工具结果校验器 |
| `EvidenceValidator` | 证据完整性和可靠性校验器 |
| `PlanValidator` | 执行计划校验器 |
| `AgentResultValidator` | Agent 结果校验器 |
| `EvaluatorAgentProfile` | 评估 Agent 的 profile |
| `EvaluationResult` | 评估结果 |
| `ReviewFinding` | 评审发现 |
| `QualityScore` | 质量分 |
| `GoldenDataset` | 离线评估数据集 |
| `ScenarioEval` | 场景评估 |
| `PromptRegressionEval` | Prompt 回归评估 |
| `MemoryQualityEval` | 记忆质量评估 |
| `RagQualityEval` | RAG 质量评估 |
| `CostLatencyEval` | 成本与延迟评估 |

设计要求：

```text
Evaluation 负责判断质量，不直接决定系统是否重试。
是否 replan / retry 由 Graph / Agent 根据 EvaluationResult 决定。
```

---

### 4.17 `vi-agent-core-rag`

P2.5 只冻结 RAG 与 Memory 的边界及依赖方向，不冻结完整索引、chunk、向量库、rerank、citation 字段契约。

| 概念 / 模型 | 说明 |
|---|---|
| `KnowledgeSource` | 知识来源，如文档库、网页、项目文档、用户上传文件 |
| `DocumentIngestion` | 文档导入流程 |
| `DocumentChunk` | 文档切片 |
| `EmbeddingRequest` | 向量化请求 |
| `VectorSearchRequest` | 向量检索请求 |
| `RerankRequest` | 重排序请求 |
| `RetrievalResult` | 检索结果 |
| `Citation` | 引用信息 |
| `RetrievalPolicy` | 检索策略 |
| `KnowledgeAudit` | 知识检索审计 |
| `RagContextPack` | RAG 结果打包后提供给 context 的数据包 |

设计要求：

```text
RAG 是外部知识检索层。
Memory 是交互过程中沉淀的记忆。
两者可以共同进入 Context，但来源和治理策略不同。
```

---

### 4.18 `vi-agent-core-app`

| 概念 / 模型 | 说明 |
|---|---|
| `ChatController` | HTTP chat 入口 |
| `ChatStreamController` | SSE stream 入口 |
| `HealthController` | 健康检查入口 |
| `ChatApplicationService` | chat 应用服务，负责 DTO 与 lifecycle command 转换 |
| `ChatRequest` | HTTP 请求 DTO |
| `ChatResponse` | HTTP 响应 DTO |
| `ChatStreamEvent` | SSE 事件 DTO |
| `GlobalExceptionHandler` | 全局异常处理 |
| `SpringConfiguration` | Spring Bean 装配配置 |
| `ApplicationProperties` | 应用配置 |

设计要求：

```text
app 是唯一 HTTP 入口和 Spring 组合根。
各能力模块暴露 Facade / API，不各自暴露 Controller。
```

---

## 5. 目标依赖关系

### 5.1 基础依赖

```text
contract  -> common
bootstrap -> common, contract
```

所有模块可以依赖：

```text
common
contract
bootstrap（仅限模块启动协议、资源定位、完整性检查抽象和启动报告）
```

---

### 5.2 能力模块依赖原则

推荐方向：

```text
prompt    -> contract, common, bootstrap
skill     -> contract, common, bootstrap, prompt api
provider  -> contract, common, bootstrap
audit     -> contract, common, bootstrap
safety    -> contract, common, bootstrap, audit api
tool      -> contract, common, bootstrap, safety api, audit api
memory    -> contract, common, bootstrap, prompt api, provider api, audit api
context   -> contract, common, bootstrap, memory api, prompt api, audit api
evaluation-> contract, common, bootstrap, prompt api, provider api, audit api
rag       -> contract, common, bootstrap, provider api, audit api, safety api
graph     -> contract, common, bootstrap, audit api
agent     -> contract, common, bootstrap, graph api, provider api
lifecycle -> contract, common, bootstrap, context api, memory api, graph api, audit api
```

依赖收口规则：

```text
agent 不直接依赖 context/prompt/skill/tool/safety/evaluation 的具体模块实现。
AgentRuntime 只消费 AgentExecutionPackage / EffectiveAgentCapabilities。
AgentCapabilityResolver 或 CapabilityPackageAssembler 负责在组合层预先解析能力。
Graph 模块只依赖 GraphNode 接口和调度协议，不依赖具体 node 实现。
具体 node 实现由 agent/tool/rag/evaluation/safety 等模块提供，并由 app 组合注册。
所有跨模块调用优先通过 api/facade/port，不允许 import internal/application/domain。
```

---

### 5.3 Storage 依赖原则

```text
storage -> contract, common, bootstrap
storage -> 各模块 port
```

硬规则：

```text
storage 只能依赖各模块 port 包。
storage 不能依赖各模块 api/domain/application/internal。
storage 需要的持久化 record/command 必须由模块 port 包定义。
storage entity / redis document 不能泄漏给业务模块。
```

禁止：

```text
memory -> storage
context -> storage
tool -> storage
agent -> storage
lifecycle -> storage
storage -> memory.domain
storage -> tool.domain
storage -> prompt.application
storage -> agent.internal
```

`app` 负责把 storage 实现注入到各模块 port。

---

### 5.4 App 依赖原则

```text
app -> all modules
```

`app` 是组合根，负责 HTTP 入口、Spring Bean 装配、profile 配置、异常处理。

---

## 6. 当前代码迁移归属映射

| 当前路径 / 类别 | 目标模块 |
|---|---|
| `common.exception`、`common.util` | `vi-agent-core-common` |
| 基础 ID 工具 | `vi-agent-core-common`；跨模块 ID 语义进入 `contract` |
| `model.message` | `vi-agent-core-contract`，复杂 message subtype 可按边界再拆 |
| `model.artifact` | `vi-agent-core-contract` 或产物所属模块 |
| `model.llm`、`model.port.LlmGateway` | `vi-agent-core-provider` |
| `model.context`、`runtime.context` | `vi-agent-core-context` |
| `model.prompt`、`runtime.prompt`、`infra.prompt` | `vi-agent-core-prompt` |
| `prompt-catalog` resources | `vi-agent-core-prompt/src/main/resources` |
| `model.memory`、`runtime.memory` | `vi-agent-core-memory` |
| `model.tool`、`runtime.tool` | `vi-agent-core-tool` |
| `runtime.engine`、`runtime.loop` | `vi-agent-core-agent` |
| `runtime.orchestrator`、`runtime.command`、`runtime.completion`、`runtime.failure`、`runtime.dedup`、`runtime.lifecycle`、`runtime.session` | `vi-agent-core-lifecycle` |
| `runtime.persistence.PersistenceCoordinator` | 拆分为 lifecycle 编排 port + storage 实现 |
| `infra.provider` | `vi-agent-core-provider` |
| `infra.persistence.mysql`、`infra.persistence.cache` | `vi-agent-core-storage` |
| `infra.observability` | `vi-agent-core-audit` 或 `app` config |
| 模块默认配置、模块资源定位、模块启动协议、模块完整性检查 | `vi-agent-core-bootstrap` + 各能力模块 `config/resources` |
| `app.controller`、`app.dto`、`app.application`、`app.config` | `vi-agent-core-app` |

---

## 7. 目标调用链

### 7.1 普通 Chat

普通 Chat 也通过 `AGENT_EXECUTION -> GraphExecutionFacade` 进入编排门面，但使用最小图模板表达单 Agent 执行。

```text
ChatController
  -> ChatApplicationService
  -> RuntimeLifecycleFacade
  -> RuntimePipelineExecutor
      -> DEDUP_CHECK
      -> SESSION_RESOLVE
      -> RUN_IDENTITY_INIT
      -> TURN_START
      -> ROOT_CONTEXT_BUILD
      -> AGENT_EXECUTION
          -> GraphExecutionFacade
              -> GraphDefinition(single-agent-chat-graph)
                  -> SingleAgentNode
                      -> AgentExecutorPort / AgentRuntimeFacade
                  -> FinalSynthesisNode
      -> TURN_COMPLETE
      -> POST_TURN_MEMORY_UPDATE
      -> RUN_EVENT_FLUSH
      -> RESPONSE_RETURN
  -> ChatResponseAssembler
```

---

### 7.2 未来复杂任务

```text
RuntimePipelineExecutor
  -> AGENT_EXECUTION
      -> GraphExecutionFacade
          -> GraphKernel
              -> MainAgentNode
              -> SkillSelectNode
              -> PlanNode
              -> StepDispatchNode
                  -> SubAgentInvokeNode
                      -> AgentExecutorPort / AgentRuntimeFacade
                  -> ToolExecuteNode
                      -> ToolRuntimeFacade
                  -> RagRetrieveNode
                      -> RagRuntimeFacade
                  -> ValidatorNode
                      -> EvaluationFacade
              -> FinalSynthesisNode
```

外层 lifecycle 不变，`AGENT_EXECUTION` 内部由 Graph 编排门面承载。

---

### 7.3 SubAgent 执行

```text
GraphKernel
  -> SubAgentInvokeNode
      -> AgentExecutorPort.execute(
           executionScope = CHILD,
           agentProfile = selectedProfile,
           agentExecutionPackage = preparedPackage,
           outputContract = subTaskOutputContract
         )
      -> SubAgentResult
  -> Parent GraphState
```

SubAgent 结果回到 parent graph state，不直接污染主 transcript。

---

## 8. 模块 Resources 与 Configuration 设计

### 8.1 设计目标

P2.5-V1 采用“可服务化的模块化单体”设计。

当前阶段不将 `prompt`、`tool`、`skill`、`memory` 等能力模块拆成独立 Docker 服务；先通过 Maven 模块、模块资源、模块配置、Facade / Port 边界实现自治。未来需要独立部署时，通过替换 Facade / Port 的本地实现为远程实现完成服务化。

核心目标：

```text
1. 每个能力模块维护自己的默认配置和 resources。
2. 每个能力模块提供自己的 ConfigurationProperties。
3. 每个能力模块提供自己的 AutoConfiguration。
4. app 作为组合根统一启用模块、覆盖配置、绑定 adapter。
5. bootstrap 负责统一模块启动协议、资源定位、完整性检查和启动报告。
6. secrets 只由 app profile、环境变量或 Secret Manager 管理。
```

---

### 8.2 模块默认配置命名规范

模块不得在自身 resources 中放置通用 `application.yml` 或 `application-{profile}.yml`。

推荐命名：

```text
classpath:/vi-agent-core/{module}/{module}-defaults.yml
```

示例：

```text
vi-agent-core-prompt/src/main/resources/vi-agent-core/prompt/prompt-defaults.yml
vi-agent-core-tool/src/main/resources/vi-agent-core/tool/tool-defaults.yml
vi-agent-core-skill/src/main/resources/vi-agent-core/skill/skill-defaults.yml
vi-agent-core-memory/src/main/resources/vi-agent-core/memory/memory-defaults.yml
vi-agent-core-provider/src/main/resources/vi-agent-core/provider/provider-defaults.yml
```

默认配置只放模块内稳定默认值，例如：

```text
enabled 默认值
catalog 默认路径
schema 默认路径
模块开关默认策略
非敏感策略默认值
完整性检查开关
```

不得放入：

```text
API Key
数据库密码
Redis 密码
生产环境 URL
用户隐私数据
临时本地路径
```

---

### 8.3 配置优先级

配置优先级从低到高：

```text
1. Java 代码默认值
2. 模块内 {module}-defaults.yml
3. app/application.yml
4. app/application-{profile}.yml
5. 环境变量 / 启动参数 / Secret Manager
```

模块默认配置提供“模块可运行默认值”。

`app` 负责环境相关覆盖，例如：

```yaml
vi:
  agent:
    modules:
      prompt:
        enabled: true
      tool:
        enabled: true
      skill:
        enabled: false
      graph:
        enabled: false
    provider:
      default-provider: deepseek
```

---

### 8.4 能力目录配置来源演进

AgentProfile、SkillDefinition、ToolDefinition、PromptProfile、GraphDefinition、SafetyPolicy 等能力目录配置按阶段演进。

| 阶段 | 配置来源 | 适用内容 |
|---|---|---|
| P2.5 / P3 初期 | 模块 resources YAML + app YAML override | 内置默认 Profile、Skill、Tool、Prompt、Graph、Safety 策略 |
| P3 / P4 后 | MySQL Catalog | 运行时管理、后台配置、租户化、灰度发布、启停控制 |
| 后期 | Admin UI / Config Center / Remote Catalog Service | 企业级配置管理、审批、远程分发、热更新 |

推荐最终加载顺序：

```text
1. 模块内 defaults.yml
2. app application.yml / profile.yml 覆盖
3. MySQL Catalog 覆盖
4. Runtime CapabilitySnapshot 固化
```

推荐未来 catalog 表方向：

```text
agent_profile
agent_profile_skill_set
agent_profile_tool_set
agent_profile_prompt_profile
skill_definition
skill_tool_requirement
skill_prompt_binding
tool_definition
tool_policy_rule
prompt_profile
prompt_profile_template_binding
graph_definition
safety_policy_rule
capability_snapshot
```

`capability_snapshot` 用于记录一次 Agent 执行实际可见的 skill/tool/prompt/graph/policy 版本，支撑 audit、复现和回归分析。

---

### 8.5 模块资源目录规范

#### Prompt 模块资源

```text
vi-agent-core-prompt
  src/main/resources/
    vi-agent-core/
      prompt/
        prompt-defaults.yml
        module.yml
        catalog/
          system/
          contracts/
```

#### Tool 模块资源

```text
vi-agent-core-tool
  src/main/resources/
    vi-agent-core/
      tool/
        tool-defaults.yml
        module.yml
        builtin-tools/
        schemas/
```

#### Skill 模块资源

```text
vi-agent-core-skill
  src/main/resources/
    vi-agent-core/
      skill/
        skill-defaults.yml
        module.yml
        catalog/
          p2-governance/
            skill.yml
            SKILL.md
            examples.md
```

#### Memory 模块资源

```text
vi-agent-core-memory
  src/main/resources/
    vi-agent-core/
      memory/
        memory-defaults.yml
        module.yml
        schemas/
        policies/
```

---

### 8.6 Module Descriptor 规范

每个能力模块可以提供：

```text
classpath:/vi-agent-core/{module}/module.yml
```

推荐字段：

```yaml
moduleKey: prompt
displayName: Prompt Module
version: 1
enabledByDefault: true
requiredResources:
  - vi-agent-core/prompt/prompt-defaults.yml
  - vi-agent-core/prompt/catalog/
optionalResources:
  - vi-agent-core/prompt/contracts/
startupChecks:
  - PROMPT_CATALOG_LOADABLE
  - STRUCTURED_OUTPUT_CONTRACT_LOADABLE
```

`module.yml` 只描述模块启动元信息，不承载业务配置。

---

### 8.7 AutoConfiguration 规范

AutoConfiguration 不属于 bootstrap 的领域模型。它只能出现在 `app` 或各能力模块 `config` 包中。

每个能力模块可以提供自己的 AutoConfiguration，并通过 `@ConditionalOnProperty` 控制启用。

示例：

```java
@AutoConfiguration
@EnableConfigurationProperties(PromptModuleProperties.class)
@ConditionalOnProperty(
    prefix = "vi.agent.modules.prompt",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PromptModuleAutoConfiguration {
}
```

AutoConfiguration 规则：

```text
AutoConfiguration 只负责本模块 bean 装配。
AutoConfiguration 不应装配其他模块的内部实现。
跨模块依赖通过 Facade / Port 注入。
能力模块 domain/application/internal 不依赖 Spring Boot AutoConfiguration 抽象。
bootstrap 只提供 descriptor/resource/report 协议，不提供 Spring Boot 装配基类。
```

---



### 8.8 App 组合根职责

`vi-agent-core-app` 是唯一组合根，负责：

```text
1. 选择启用哪些模块。
2. 覆盖模块默认配置。
3. 管理 profile、环境变量、secrets。
4. 绑定 storage/provider 等 adapter 实现。
5. 暴露 HTTP Controller。
6. 汇总模块启动报告。
```

`app` 不负责：

```text
不实现 prompt render 细节
不实现 tool permission 细节
不实现 memory merge 细节
不实现 skill 解析细节
不实现 graph route 细节
```

---

### 8.9 Facade 与未来服务化边界

每个可独立演进的能力模块应暴露 Facade：

```text
PromptRuntimeFacade
ToolRuntimeFacade
SkillRuntimeFacade
MemoryRuntimeFacade
AgentRuntimeFacade
GraphRuntimeFacade
```

当前使用本地实现：

```text
DefaultToolRuntimeFacade
DefaultPromptRuntimeFacade
DefaultSkillRuntimeFacade
```

未来如需服务化，可替换为远程实现：

```text
RemoteToolRuntimeFacade
RemotePromptCatalogFacade
RemoteSkillCatalogFacade
```

设计要求：

```text
P2.5 不拆独立 Docker 服务。
高风险或重资源 Tool Executor 未来可以独立部署。
Prompt / Skill 未来可以演进为 Catalog Management Service。
主链路仍通过 Facade / Port 调用，不直接依赖部署形态。
```

---

## 9. 关键治理规则

1. 禁止 Maven module 循环依赖。
2. 禁止跨模块 import `internal / application / domain`。
3. 跨模块只能依赖 `api / port / contract / common / bootstrap`。
4. `contract` 只放跨模块协议，不放完整领域模型、领域行为、Storage Entity、Provider Protocol DTO。
5. 进入 `contract` 的对象必须有至少两个模块真实依赖理由，禁止因为“懒得依赖目标模块 api”而下沉到 contract。
6. `bootstrap` 只定义模块启动协议、资源位置、完整性检查抽象和启动报告，不承载 Spring Boot AutoConfiguration 抽象。
7. 能力模块领域代码不依赖 Spring Boot 自动装配抽象。
8. Storage 只能依赖各模块 `port` 包并实现 port，不能依赖 api/domain/application/internal。
9. Provider 必须区分 api/facade 与 adapter/protocol/internal，其他模块只能依赖 provider api/facade。
10. Skill 不能直接执行 Tool、调用 Provider、写 Memory、写 Storage。
11. Tool 不反向调用 Agent。
12. Prompt 不决定执行路径。
13. Graph 是 `AGENT_EXECUTION` 内部编排门面，不替代 Lifecycle。
14. Agent 是 GraphNode 可调用的执行能力，不反向拥有 GraphKernel。
15. AgentRuntime 只消费 `AgentExecutionPackage` / `EffectiveAgentCapabilities`，不直接查询所有能力模块。
16. Agent 不直接写长期记忆；记忆写入必须经过 MemoryPolicy、Evidence、Audit。
17. App 是唯一 HTTP 入口和 Spring 组合根。
18. 模块默认配置必须使用 `{module}-defaults.yml`，禁止模块 resources 中出现通用 `application.yml`。
19. 密钥、数据库密码、Redis 密码、外部 API Key 不得进入模块 resources。
20. P2.5 不拆 prompt/tool/skill/memory 等独立 Docker 服务；未来服务化通过 Facade / Port 替换实现。
21. 所有新增 enum 必须包含 code value 和中文 desc。
22. AgentProfile / Skill / Tool / Prompt / Graph / SafetyPolicy 的最终可见能力必须通过 `AgentCapabilityResolver` 计算并固化为 capability snapshot。
23. Skill 只能引用 `graphRef`、`executionPattern`、`allowedNodeTypes`、`toolSetRefs`、`promptRefs`，不能直接定义不受约束的运行时代码路径。
24. GraphDefinition 是可执行流程结构的事实源，必须可校验、可审计、可版本化。
25. Context / Memory 对不同 Agent 的隔离必须通过 `agentExecutionId`、`executionScope`、`ContextPolicy`、`MemoryPolicy` 显式表达。
26. Audit 记录优先由 RuntimePipelineExecutor、GraphKernel、Facade Decorator 在边界自动生成，避免业务逻辑内散落审计调用。
27. Safety 必须在 Skill 加载期、Agent 能力解析期、Graph 路由期、Tool 执行期、Memory 写入期提供检查入口。
28. `rag / evaluation / safety / graph / skill` 等后续阶段模块，P2.5 只冻结职责边界、依赖方向和禁止事项，不冻结完整字段、表名、节点类型清单。

---

## 10. P2.5-V1 设计边界

### 10.1 本设计包含

```text
模块划分
模块职责
核心概念与领域模型说明
对象归属
依赖方向
固定 lifecycle step 的维护与使用方式
模块 resources/config/AutoConfiguration/bootstrap 设计
AgentProfile / Skill / Tool / Prompt 可见性模型
Skill 与 GraphDefinition 的流程定义边界
Context / Memory 多 Agent 隔离模型
Audit 低入侵记录模型与动态 SubAgent 审计树
Safety 风险评估和 allow/ask/deny 决策模型
runtime / graph / agent / skill / tool / memory 的边界
contract 模块原则
storage 模块原则
显式转换规则
当前代码迁移归属口径
```

### 10.2 本设计不包含

```text
具体迁移步骤
测试计划
验收命令
Codex 执行 prompt
完整 Graph Kernel 实现细节
完整 Skill Runtime 实现细节
完整 Safety / Evaluation / RAG 字段契约与存储设计
独立 Docker 服务拆分方案
P3/P4/P5 详细计划
```

---

## 11. 最终结论

P2.5-V1 的目标结构是：

```text
common / contract / bootstrap
  +
lifecycle / agent / graph / skill / tool / prompt / context / memory
  +
provider / storage / audit / safety / evaluation / rag
  +
app
```

架构口径：

```text
contract 负责公共契约
bootstrap 负责模块启动协议、默认配置加载、资源定位和完整性检查
各能力模块负责自己的领域对象和运行逻辑
storage 负责持久化实现
app 负责组合根、配置覆盖、adapter 绑定和 HTTP 入口
lifecycle 负责固定系统生命周期
agent 负责统一 Agent 执行
graph 负责动态任务编排
skill 负责任务剧本和专业化指导
tool 负责可执行能力边界
prompt 负责 LLM 调用前表达
context 负责模型可见信息
memory 负责短期记忆、长期记忆归属、状态和证据闭环
audit 负责全过程可追踪
safety 负责权限和风险控制
evaluation 负责结果质量判断
rag 负责外部知识检索
```

关键执行口径：

```text
AGENT_EXECUTION 内部由 GraphExecutionFacade / GraphKernel 承载。
GraphKernel 根据 GraphDefinition 执行可编排流程；AgentRuntime 作为 GraphNode 调用的执行能力存在。
SkillDefinition 负责任务剧本和 graphRef。
GraphDefinition 负责强类型可执行流程结构。
AgentCapabilityResolver / CapabilityPackageAssembler 负责计算本次可见 skill/tool/prompt/graph/policy，并组装 AgentExecutionPackage。
ContextPolicy / MemoryPolicy 负责不同 Agent 的上下文和记忆隔离。
Audit 由执行器与 Facade Decorator 在边界低入侵记录。
Safety 在能力解析、Graph 路由、Tool 执行、Memory 写入等边界执行 allow/ask/deny。
```

P2.5-V1 重构后，项目应从“技术分层模块”升级为“能力域自治模块 + 模块化单体启动体系”，为 P3 Graph Kernel、Skill 驱动 AgentRuntime、Tool Runtime Core、长期记忆、RAG、Human Approval、Checkpoint/Resume 以及未来可选服务化打下稳定边界。
