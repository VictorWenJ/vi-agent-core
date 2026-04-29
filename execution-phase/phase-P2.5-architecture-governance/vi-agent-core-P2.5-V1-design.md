# Vi Agent Core P2.5-V1 模块治理与架构重构设计文档

版本：P2.5-V1 Draft  
状态：设计初稿  
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

### 2.3 `contract` 只承载最小公共契约

新增：

```text
vi-agent-core-contract
```

`contract` 的职责是防止能力模块之间形成循环依赖。

允许进入 `contract` 的对象必须满足以下条件：

1. 被多个能力模块共同使用。
2. 不属于某个能力模块的私有领域语义。
3. 是跨模块边界传输需要的稳定契约。
4. 不包含复杂业务行为。
5. 不依赖 Spring、DB、Redis、Provider 实现。

`contract` 允许放：

```text
TraceId / RunId / TurnId / MessageId / AgentExecutionId
ExecutionStatus / ResultStatus / FailureInfo
UsageInfo
ArtifactRef
基础 Message Contract
基础 EvidenceRef Contract
跨模块 Command / Result 基础接口
基础枚举
```

`contract` 不允许变成新的 `model` 大仓库。

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
| `RuntimePipelineState -> AgentInvocation` | `lifecycle` 或 `agent` |
| `AgentResult -> TurnCompletionCommand` | `lifecycle` |
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

### 2.5 固定生命周期与可编排工作流分离

系统外层请求生命周期由 `vi-agent-core-lifecycle` 管理，职责是接收请求、建立运行上下文、调用 Agent 执行、完成持久化、触发记忆更新、收口事件。

Agent 执行内部的复杂任务编排由 `vi-agent-core-graph`、`vi-agent-core-agent`、`vi-agent-core-skill` 等模块共同支撑。

设计边界：

```text
Runtime Lifecycle = 外层固定请求生命周期
Agent / Graph      = 中间任务执行与编排区域
Skill              = 任务剧本与执行指导
Tool               = 系统可执行能力
Prompt             = LLM 调用前表达装配
Context            = 模型可见上下文
Memory             = 状态与记忆闭环
Audit              = 全链路可追踪记录
```

---

## 3. 目标模块总览

| 模块 | 核心职责 |
|---|---|
| `vi-agent-core-common` | 通用异常、工具类、基础 ID 生成工具，不承载 Agent 语义 |
| `vi-agent-core-contract` | 跨模块最小公共契约，避免循环依赖 |
| `vi-agent-core-lifecycle` | 固定请求生命周期与 RuntimePipeline |
| `vi-agent-core-agent` | 统一 AgentRuntime，支持 root / child / evaluator / internal 等执行范围 |
| `vi-agent-core-graph` | AgentWorkflow 的 Graph Kernel、Node、Route、State、Checkpoint 边界 |
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

### 4.3 `vi-agent-core-lifecycle`

#### 4.3.1 职责

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

#### 4.3.2 RuntimeStepKey 设计

推荐使用以下固定步骤：

| Step Key | 中文说明 | 主要输入 | 主要输出 |
|---|---|---|---|
| `DEDUP_CHECK` | 请求幂等检查 | `RuntimeCommand.requestId` | 命中则 short-circuit；未命中则继续 |
| `SESSION_RESOLVE` | 会话解析 | conversationId、sessionId、sessionMode | conversation/session 结果 |
| `RUN_IDENTITY_INIT` | 运行标识初始化 | request metadata | traceId、runId、turnId、MDC scope |
| `TURN_START` | 轮次启动 | user message、session 信息 | user message、turn running 状态 |
| `ROOT_CONTEXT_BUILD` | 根上下文构建 | session、summary、state、recent messages | WorkingContext / projection |
| `AGENT_EXECUTION` | Agent 执行入口 | WorkingContext、当前用户消息、工具/模型配置 | AgentResult |
| `TURN_COMPLETE` | 轮次完成持久化 | AgentResult、turn 信息 | assistant message、turn/session/run event 持久化结果 |
| `POST_TURN_MEMORY_UPDATE` | 轮次后记忆更新 | transcript、assistant result、tool result | summary/state/evidence 更新结果 |
| `RUN_EVENT_FLUSH` | 运行事件收口 | pipeline state、audit buffer | 最终 runtime event / stream event / audit flush |
| `RESPONSE_RETURN` | 响应组装返回 | completion result、failure info | RuntimeResult |

新设计使用 `AGENT_EXECUTION`，不建议继续使用 `AGENT_LOOP` 作为长期步骤名，因为未来该步骤内部可能由 Single Agent Loop、Graph Kernel、SubAgent 调度或其他执行模式承载，不一定是 loop。

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

#### 4.3.3 RuntimeStep 使用方式

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

#### 4.3.4 维护规则

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

#### 4.3.5 使用示例

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
处理：调用 AgentRuntime；P2.5 可继续包装当前 SimpleAgentLoopEngine；P3 后可切换为 GraphKernel
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

### 4.4 `vi-agent-core-agent`

| 概念 / 模型 | 说明 |
|---|---|
| `AgentRuntime` | 统一 Agent 执行入口，承载 main、sub、evaluator、internal task 等执行形态 |
| `AgentInvocation` | 一次 Agent 执行命令，包含执行范围、任务输入、context、skill、tool policy、output contract |
| `AgentResult` | 一次 Agent 执行结果，包含文本输出、结构化输出、tool calls、artifacts、usage、failure |
| `AgentExecutionScope` | 执行范围，例如 `ROOT / CHILD / EVALUATOR / INTERNAL_TASK` |
| `AgentProfile` | Agent 执行配置画像，定义默认模型、上下文策略、工具策略、记忆策略、风险等级 |
| `AgentExecutionContext` | Agent 执行时上下文，包含 trace/run/turn/agentExecutionId、skill snapshot、toolset、memory scope |
| `AgentDelegationPolicy` | Agent 是否允许委派子任务、委派给谁、允许多少并发、是否需要审批 |
| `AgentOutputContract` | Agent 输出契约，定义文本 / JSON / artifact / evidence 的输出要求 |
| `SingleAgentRunner` | 单 Agent 执行器，用于普通聊天或简单 tool loop |
| `SubAgentInvocationRunner` | 子 Agent 调用执行器，负责 parent-child 执行隔离与结果回传 |

设计要求：

```text
AgentRuntime 是统一执行引擎。
不同角色由 AgentExecutionScope + AgentProfile + Skill + ToolPolicy + ContextPolicy 决定。
```

---

### 4.5 `vi-agent-core-graph`

| 概念 / 模型 | 说明 |
|---|---|
| `GraphKernel` | AgentWorkflow 调度内核，负责加载定义、维护状态、执行节点、处理路由 |
| `GraphDefinition` | 图定义，包含节点、边、入口、出口、条件路由规则 |
| `GraphExecutor` | 图执行器，按 ready node、edge、route policy 推进执行 |
| `GraphNode` | 图中的可执行节点，如 PlanNode、ToolExecuteNode、ValidatorNode |
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
Graph 是 Agent 执行区域的编排内核。
Graph 不替代 lifecycle，不直接操作 storage，不直接执行 provider/tool。
```

---

### 4.6 `vi-agent-core-skill`

| 概念 / 模型 | 说明 |
|---|---|
| `SkillDefinition` | Skill 的定义，包含 key、版本、描述、适用场景、执行模式、资源列表 |
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

---

### 4.7 `vi-agent-core-tool`

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

---

### 4.8 `vi-agent-core-prompt`

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

---

### 4.9 `vi-agent-core-context`

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

---

### 4.10 `vi-agent-core-memory`

#### 4.10.1 职责边界

`memory` 负责 Agent 运行过程中沉淀和读取的记忆能力。

本模块同时承载：

1. **Session Memory / 短期记忆**：P2 已有核心能力，P2.5 重点治理。
2. **Long-term Memory / 长期记忆**：P4 重点实现，P2.5 只预留归属和边界。
3. **Evidence / 证据绑定**：用于说明 memory 写入来源和可靠性。
4. **Internal Memory Task / 内部记忆任务**：summary/state/evidence 等内部 LLM 任务。

RAG 不归 memory。RAG 是外部知识库检索，归 `vi-agent-core-rag`。

---

#### 4.10.2 核心概念

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

#### 4.10.3 短期记忆与长期记忆

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

---

### 4.11 `vi-agent-core-provider`

| 概念 / 模型 | 说明 |
|---|---|
| `LlmGateway` | 模型调用统一入口 |
| `ModelRequest` | 统一模型请求，不绑定具体供应商协议 |
| `ModelResponse` | 统一模型响应 |
| `ModelToolCall` | 模型返回的工具调用意图 |
| `ProviderType` | 供应商类型，如 OpenAI、DeepSeek、Doubao、local |
| `ProviderConfig` | Provider 配置 |
| `StreamingAdapter` | 流式响应适配器 |
| `StructuredOutputRequestAdapter` | 结构化输出请求适配器 |
| `StructuredOutputResponseExtractor` | 结构化输出响应提取器 |
| `ProviderStructuredOutputCapability` | 供应商结构化输出能力描述 |
| `OpenAICompatibleMessageProjector` | OpenAI-compatible 消息投影适配 |
| `ProviderProtocolDTO` | 具体供应商协议 DTO，只允许在 provider 模块内部使用 |

设计要求：

```text
Provider 只做模型协议适配。
Provider DTO 不得泄漏到 Agent / Prompt / Memory / Tool 模块。
```

---

### 4.12 `vi-agent-core-storage`

| 概念 / 模型 | 说明 |
|---|---|
| `MysqlEntity` | MySQL 表实体 |
| `MybatisMapper` | MyBatis Mapper |
| `RedisDocument` | Redis 缓存文档 |
| `RedisKeyBuilder` | Redis key 构造器 |
| `RepositoryImplementation` | 各模块 port 的存储实现 |
| `StorageConverter` | Domain / API DTO 与 Entity / Document 的转换器 |
| `Migration` | Flyway migration 脚本 |
| `TransactionBoundary` | 事务边界实现 |
| `TypeHandler` | MyBatis 类型处理器 |

设计要求：

```text
业务模块定义 repository port。
storage 实现这些 port。
业务模块不能依赖 storage。
Storage Entity / Redis Document 不得泄漏到业务模块。
```

示例：

```text
memory 模块：MemoryStateRepository port
storage 模块：MysqlMemoryStateRepository implements MemoryStateRepository
app 模块：负责 Spring Bean 装配
```

---

### 4.13 `vi-agent-core-audit`

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

---

### 4.14 `vi-agent-core-safety`

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

---

### 4.15 `vi-agent-core-evaluation`

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

### 4.16 `vi-agent-core-rag`

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

### 4.17 `vi-agent-core-app`

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
contract -> common
```

所有模块可以依赖：

```text
common
contract
```

---

### 5.2 能力模块依赖原则

推荐方向：

```text
prompt    -> contract, common
skill     -> contract, common, prompt
audit     -> contract, common
safety    -> contract, common, audit
provider  -> contract, common
tool      -> contract, common, safety, audit
memory    -> contract, common, prompt, provider, audit
context   -> contract, common, memory, prompt, audit
evaluation-> contract, common, prompt, provider, audit
rag       -> contract, common, provider, audit, safety
agent     -> contract, common, context, prompt, skill, tool, provider, safety, audit, evaluation
graph     -> contract, common, audit
lifecycle -> contract, common, context, memory, agent, graph, audit
```

`graph` 保持调度内核属性。具体 node 实现可以由能力模块提供，并通过 app 组合注册。

---

### 5.3 Storage 依赖原则

```text
storage -> contract, common
storage -> 各模块 port
```

禁止：

```text
memory -> storage
context -> storage
tool -> storage
agent -> storage
lifecycle -> storage
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
| `app.controller`、`app.dto`、`app.application`、`app.config` | `vi-agent-core-app` |

---

## 7. 目标调用链

### 7.1 普通 Chat

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
          -> AgentRuntime
              -> SingleAgent execution
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
      -> AgentRuntime
          -> GraphKernel
              -> MainAgentNode
              -> SkillSelectNode
              -> PlanNode
              -> StepDispatchNode
                  -> SubAgentInvokeNode
                  -> ToolExecuteNode
                  -> RagRetrieveNode
                  -> ValidatorNode
              -> FinalSynthesisNode
```

外层 lifecycle 不变，Agent 执行内部可替换为 Graph Kernel。

---

### 7.3 SubAgent 执行

```text
GraphKernel
  -> SubAgentInvokeNode
      -> AgentRuntime.execute(
           executionScope = CHILD,
           skill = selectedSkill,
           toolPolicy = restrictedToolPolicy,
           contextPolicy = childContextPolicy,
           outputContract = subTaskOutputContract
         )
      -> SubAgentResult
  -> Parent GraphState
```

SubAgent 结果回到 parent execution，不直接污染主 transcript。

---

## 8. 关键治理规则

1. 禁止 Maven module 循环依赖。
2. 禁止跨模块 import `internal / application / domain`。
3. 跨模块只能依赖 `api / port / contract / common`。
4. Storage 只能实现各模块 port，业务模块不能依赖 storage。
5. Provider 协议 DTO 不得泄漏到 Agent / Prompt / Memory / Tool。
6. Skill 不能直接执行 Tool、调用 Provider、写 Memory、写 Storage。
7. Tool 不反向调用 Agent。
8. Prompt 不决定执行路径。
9. Graph 不替代 Lifecycle。
10. Agent 不直接写长期记忆；记忆写入必须经过 MemoryPolicy、Evidence、Audit。
11. App 是唯一 HTTP 入口和 Spring 组合根。
12. 所有新增 enum 必须包含 code value 和中文 desc。

---

## 9. P2.5-V1 设计边界

### 9.1 本设计包含

```text
模块划分
模块职责
核心概念与领域模型说明
对象归属
依赖方向
固定 lifecycle step 的维护与使用方式
runtime / graph / agent / skill / tool / memory 的边界
contract 模块原则
storage 模块原则
显式转换规则
当前代码迁移归属口径
```

### 9.2 本设计不包含

```text
具体迁移步骤
测试计划
验收命令
Codex 执行 prompt
完整 Graph Kernel 实现细节
完整 Skill Runtime 实现细节
完整 RAG 实现细节
P3/P4/P5 详细计划
```

---

## 10. 最终结论

P2.5-V1 的目标结构是：

```text
common / contract
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
各能力模块负责自己的领域对象和运行逻辑
storage 负责持久化实现
app 负责组合和 HTTP 入口
lifecycle 负责固定系统生命周期
agent 负责统一 Agent 执行
stack graph 负责动态任务编排
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

P2.5-V1 重构后，项目应从“技术分层模块”升级为“能力域自治模块”，为 P3 Graph Kernel、Skill 驱动 AgentRuntime、Tool Runtime Core、长期记忆、RAG、Human Approval、Checkpoint/Resume 打下稳定边界。
