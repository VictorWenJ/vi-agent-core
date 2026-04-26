# P2-E Prompt Engineering Governance 详细设计

> 更新日期：2026-04-26  
> 阶段：P2-E  
> 阶段目录：`execution-phase/phase-P2-E-prompt-governance/`  
> 文档类型：`design.md`  
> 文档版本：v4  
> 状态：Draft  
> 设计口径：企业级内部系统 Prompt Governance，MySQL 版本化 catalog + YML 启用版本选择 + 运行期只读 Registry + 内部 LLM Worker 统一结构化 JSON 输出

---

## 1. 文档元信息

- 文档名称：P2-E Prompt Engineering Governance 详细设计
- 变更主题：将当前 P2 中分散的内部系统 prompt / prompt-like 能力收口为企业级内部 Prompt Governance
- 目标分支 / 迭代：P2-E
- 文档版本：v4
- 状态：Draft
- 作者：Victor Yu / ChatGPT
- 评审人：Victor Yu
- 关联文档：
    - `AGENTS.md`
    - `ARCHITECTURE.md`
    - `CODE_REVIEW.md`
    - `PROJECT_PLAN.md`
    - `CHAT_HANDOFF.md`
    - `execution-phase/README.md`
    - `execution-phase/phase-P2-context-memory/system-design-P2-v5.md`
    - `execution-phase/phase-P2-context-memory/system-design-P2-implementation-plan-v3.md`
    - `execution-phase/phase-P2-E-prompt-governance/README.md`
    - `execution-phase/phase-P2-E-prompt-governance/plan.md`
    - `execution-phase/phase-P2-E-prompt-governance/test.md`
    - `execution-phase/phase-P2-E-prompt-governance/prompts.md`
    - `execution-phase/phase-P2-E-prompt-governance/closure.md`

本文档是 P2-E 阶段新增内部系统 Prompt Governance 对象、MySQL catalog、YML 版本选择、运行期 prompt registry、结构化 LLM 输出契约和 provider structured output adapter 的强契约源。

P2-A 到 P2-D 已完成对象仍以历史强契约文档为准，尤其是：

- `system-design-P2-v5.md`
- `system-design-P2-implementation-plan-v3.md`

P2-E 不允许通过修改 P2-A 到 P2-D 已冻结对象来迁就新的 prompt 实现。

---

## 2. 变更摘要（Executive Summary）

### 2.1 一句话摘要

P2-E 将当前项目中分散在 context builder、memory extractor、output parser、internal task audit 中的内部系统 prompt 能力，统一收口为强类型、可持久化、可版本化、可配置启用、可启动校验、可运行期稳定读取、可审计、可测试的企业级 Prompt Governance 体系。

---

### 2.2 设计口径调整

本版设计明确采用企业级方案：

```text
MySQL 作为 system prompt template 与 structured LLM output contract 的版本化事实源。
application.yml 只负责选择当前环境启用哪个 prompt template version。
应用启动时根据 yml 从 MySQL 加载指定版本，做 fail-fast 校验，并构建只读 SystemPromptRegistry。
请求运行期间只读内存 registry，不每次请求查 DB。
internal task audit 记录本次使用的 prompt key、prompt version、contract key、contract version、schema hash、provider structured output mode。
```

本阶段不做 Prompt UI、热加载、A/B、审批流、动态编辑，但必须先建立数据模型、版本模型、配置选择模型和运行期加载模型，避免未来从代码内置模板迁移到 DB catalog 时发生结构性返工。

---

### 2.3 P2-E 当前治理对象

P2-E 当前只治理项目内部固定流程中的 system prompt template，不治理通用业务 prompt。

当前进入 `system_prompt_template` 表的系统 prompt 类型如下：

| 类型 | 所属阶段 | 是否单独调用 LLM | 模板形态 | 用途说明 |
|---|---|---:|---|---|
| `runtime_instruction_render` | 主聊天调用前的 context building 阶段 | 否 | `TEXT` | 渲染主模型运行指令，告诉主模型当前 agent mode、working mode、回答边界、禁止暴露 internal context / evidence / trace / snapshot 等内部信息。它本身不是一次独立 LLM 调用，而是主模型请求上下文的一部分。 |
| `session_state_render` | 主聊天调用前的 context building 阶段 | 否 | `TEXT` | 把当前 `SessionStateSnapshot` 渲染成主模型可读的上下文块。它表达当前会话状态，例如任务目标、确认事实、约束、用户偏好、open loops、working mode、phase state 等。 |
| `conversation_summary_render` | 主聊天调用前的 context building 阶段 | 否 | `TEXT` | 把已经存在的 `ConversationSummary` 渲染成主模型可读的上下文块。它不是生成新摘要，而是把历史摘要包装成主模型可理解的上下文，帮助主模型理解之前对话，但不能覆盖当前用户最新消息。 |
| `state_delta_extract` | 一轮主聊天完成后的 post-turn memory update 阶段 | 是 | `CHAT_MESSAGES` | 内部 LLM worker 从当前 turn 消息、已有 state、summary 中抽取 `StateDelta`。输出必须走结构化 LLM 输出契约，只能表达状态增量，例如 confirmed facts、constraints、decisions、open loops、user preference patch、phase state patch 等。 |
| `conversation_summary_extract` | 一轮主聊天完成后的 post-turn memory update 阶段 | 是 | `CHAT_MESSAGES` | 内部 LLM worker 根据上一版 summary、当前 turn 消息、最新 state 生成新的 summary update。它负责“生成 / 更新摘要”，不是把摘要展示给主模型。输出必须走结构化 LLM 输出契约。 |

`evidence_bind_deterministic` 当前不是 system prompt template，也不进入 `system_prompt_template` 表。它是 deterministic internal task audit key，用于 post-turn evidence binding 的审计标识。P2-E 只要求它常量化 / enum 化管理，不把它建模为 LLM prompt template。

P2-E 范围内真正调用 LLM 能力的内部 prompt 只有 `state_delta_extract` 与 `conversation_summary_extract`。这两类 internal LLM worker 的输出统一要求为 structured JSON：不接受 markdown fenced JSON、不接受 prose、不接受半结构化文本、不接受自由文本解释。不同 provider 的 strict tool call、json_schema response format、json_object 等差异只属于 provider adapter 的结构化输出承载方式，不进入 prompt contract 核心模型。

---

### 2.4 本次范围

1. 新增 `vi-agent-core-model` 下的 `model.prompt` 契约对象。
2. 新增 `SystemPromptKey` enum，替代字符串形式 prompt key。
3. 新增 `StructuredLlmOutputContractKey` enum，替代原先设计中的 `PromptOutputContractKey`。
4. 新增 `SystemPromptTemplate`，表示当前阶段内部固定系统 prompt 模板。
5. 新增 `StructuredLlmOutputContract`，表示 LLM 输出映射成业务 bean 前的结构化输出契约。
6. 新增 `StructuredLlmOutputMode`，描述 provider adapter 如何承载结构化输出契约。
8. 新增 `PromptInputVariable` / `PromptInputVariableType`，替代容易误解的 `PromptVariable` / `PromptVariableType`。
9. 新增 `system_prompt_template` MySQL 表，存储系统 prompt 模板版本。
10. 新增 `structured_llm_output_contract` MySQL 表，存储结构化 LLM 输出契约版本。
11. 新增 `model.port.SystemPromptCatalogRepository`，由 runtime 通过 port 读取 prompt catalog。
12. 新增 `infra.prompt.MySqlSystemPromptCatalogRepository`，负责从 MySQL 加载 prompt template / contract。
13. 新增 `application.yml` prompt active version selector。
14. 新增 `runtime.prompt.SystemPromptRegistry`，启动时加载 yml 指定版本并构建只读 registry。
15. 新增 `PromptRenderer`，统一负责 prompt 变量校验与模板渲染。
16. 新增 `NormalizedStructuredLlmOutput`，作为 provider adapter 归一化后的结构化 JSON 输出对象。
17. 新增 `StructuredLlmOutputContractGuard`，基于 `schemaJson` 做 JSON Schema 校验。
18. 支持 provider-native structured output adapter；DeepSeek 场景优先将 `schemaJson` 转换为 strict tool call 请求格式，并将 provider response 归一化为 structured JSON。
19. 将 D-2 的 `StateDelta` 抽取 prompt 从 inline 字符串拼接迁移到 Prompt Governance。
20. 将 D-3 的 `ConversationSummary` 抽取 prompt 从 inline 字符串拼接迁移到 Prompt Governance。
21. 将 runtime instruction、session state render、conversation summary render 的 key / version 收口到统一内部 prompt key。
22. 将 parser allowlist 改为来源于 `StructuredLlmOutputContract.schemaJson`，避免 prompt contract 与 parser allowlist 双轨漂移。
23. 将 prompt audit metadata 稳定进入 internal task audit 和 context block metadata。
24. 补齐 prompt governance、MySQL catalog、YML selector、registry startup、structured output adapter、contract guard、parser alignment、防回退测试。

---

### 2.5 明确不做

1. 不推进 P2.5。
2. 不推进 P3。
3. 不做 Graph Workflow。
4. 不做业务 Tool Runtime 大扩展。
5. 不做 Long-term Memory。
6. 不做 RAG / Knowledge。
7. 不做 Prompt UI。
8. 不做通用 Prompt 平台。
9. 不做 prompt 文件热加载。
10. 不做 prompt A/B test。
11. 不做 prompt 审批流。
12. 不做运行期动态编辑 prompt。
13. 不新增 debug API。
14. 不修改 `/chat` 对外协议。
15. 不修改 `/chat/stream` SSE event 协议。
16. 不新增 Redis key。
17. 不改变 P2-D 已完成的 state / summary / evidence 主链路语义。
18. 不为兼容旧测试保留 `state_extract_inline`、`summary_extract_inline` 等旧语义。
19. 不把 DeepSeek strict tool call 误建模为业务 Tool Runtime；它只是 provider-native structured output channel。

---

## 3. 背景与问题定义

### 3.1 当前现状

当前项目在 P2-D 后已经具备以下能力：

- provider 调用前构建 `WorkingContext`；
- context block 包括 runtime instruction、session state、conversation summary、recent messages、current user message；
- post-turn 阶段执行 state extraction、summary extraction、evidence binding；
- `StateDeltaExtractionPromptBuilder` 负责构建 state extraction prompt；
- `ConversationSummaryExtractionPromptBuilder` 负责构建 summary extraction prompt；
- `StateDeltaExtractionOutputParser` 负责解析 StateDelta JSON；
- `ConversationSummaryExtractionOutputParser` 负责解析 summary JSON；
- `InternalMemoryTaskService` 已经记录 internal task 的 `promptTemplateKey` / `promptTemplateVersion`；
- context block 中也已经有 `promptTemplateKey` / `promptTemplateVersion` 字段。

也就是说，项目已经有 prompt-like 能力，但这些能力还不是一个企业级内部系统 Prompt Governance 体系。

---

### 3.2 已确认问题

1. `ContextBlockFactory` 中存在硬编码 prompt template key：

```text
runtime-instruction
session-state
conversation-summary
p2-c-v1
```

2. `StateDeltaExtractionPromptBuilder` 中存在 inline prompt key：

```text
state_extract_inline
p2-d-2-v1
```

3. `ConversationSummaryExtractionPromptBuilder` 中存在 inline prompt key：

```text
summary_extract_inline
p2-d-3-v1
```

4. state extraction prompt 的 allowed fields 写在 prompt builder 中。
5. state extraction parser 的 allowlist 又在 parser 中维护一份。
6. summary extraction prompt 的 allowed fields 写在 prompt builder 中。
7. summary extraction parser 的 allowlist 又在 parser 中维护一份。
8. prompt key、prompt version、structured output contract、parser allowlist 之间没有统一事实源。
9. internal task audit 只能记录 key / version 字符串，没有正式 prompt reference / purpose / contract / schema hash metadata。
10. D-2 / D-3 的 prompt 已经具有结构化输出约束，但没有以 MySQL 持久化的 `StructuredLlmOutputContract` 作为强契约。
11. 当前 prompt builder 的职责边界过重：既定义 prompt 文本，又定义输出字段规则，又拼接运行时输入。
12. 当前 parser 的职责边界过重：既解析 JSON，又维护字段白名单，又承担 structured output contract guard 职责。
13. 旧设计使用 String 常量作为 prompt key，无法体现当前内部固定系统 prompt 的强类型边界。
14. 旧设计使用 `allowedTopLevelFields`、`forbiddenFields`、`nestedFieldContracts` 自定义字段清单，容易变成手写简化 schema，且 `forbiddenFields` 理论上不可枚举。
15. 旧设计曾将模型原始输出解析策略放入核心契约，容易把 provider response 适配问题污染到 Prompt Governance 核心模型。
16. 旧设计不持久化 prompt template / contract，将来做 Prompt UI、审批、灰度、回放时会发生结构性迁移成本。

---

### 3.3 不改风险

如果不做 P2-E，后续会出现以下风险：

1. prompt template 与 parser allowlist 逐渐漂移。
2. prompt 文本要求模型输出 A 字段，parser 实际接受 B 字段。
3. audit 中记录的 prompt key / version 与真实执行的 prompt 不一致。
4. 新增内部 prompt 时继续复制旧 builder 逻辑，导致重复拼接。
5. 内部任务 prompt 和主 context render prompt 分别演化，缺少统一治理。
6. P3 Graph Workflow 引入后，node prompt 会进一步分散，后续治理成本更高。
7. 代码评审无法判断某个模型输出字段到底来自 prompt 契约、parser 契约、provider structured output 还是模型临时行为。
8. 企业级 Agent Runtime 的 prompt 可审计性不足。
9. 旧字段如 `upsert/remove`、`locale/timezone`、旧 `phaseKey/status` 结构可能通过 prompt 或 parser 回流。
10. 面试或技术评审中无法清晰回答 prompt 存储、版本化、启用选择、审计、回滚、schema 校验等关键问题。

---

## 4. 项目固定基线（不可绕开）

### 4.1 模块与依赖方向（项目固定基线）

- `vi-agent-core-common`
- `vi-agent-core-model -> common`
- `vi-agent-core-runtime -> model + common`
- `vi-agent-core-infra -> model + common`
- `vi-agent-core-app -> runtime + infra + model + common`

禁止出现：

```text
runtime -> infra
model -> runtime
model -> infra
common -> model/runtime/infra/app
```

---

### 4.2 分层职责（项目固定基线）

- `app`：controller / application service / Spring 装配 / 协议适配 / 配置绑定
- `runtime`：主链路编排、loop、上下文装配、工具协调、prompt 渲染治理、运行期只读 registry
- `infra`：provider、DB / Redis repository、外部适配实现、MySQL prompt catalog repository
- `model`：领域模型、值对象、port 契约
- `common`：异常、ID、通用无状态工具

---

### 4.3 P2-E 分层职责

| 模块 | P2-E 职责 |
|---|---|
| `vi-agent-core-model` | 新增 `model.prompt`，定义内部系统 prompt key、template、version、input variable、structured output contract、metadata 等值对象；新增 `model.port.SystemPromptCatalogRepository` |
| `vi-agent-core-runtime` | 新增 `runtime.prompt`，负责 system prompt registry、renderer、contract guard、provider structured output request adapter 的运行时协作 |
| `vi-agent-core-infra` | 新增 `infra.prompt`，实现 MySQL prompt catalog repository、entity、mapper、seed 数据读取 |
| `vi-agent-core-app` | 新增 prompt properties / Spring Bean 装配；不新增 prompt API，不修改 chat / stream DTO |
| `vi-agent-core-common` | 原则上不变，除非确实需要通用异常、hash 工具或 JSON 工具方法 |

---

### 4.4 代码治理红线（项目固定基线）

- 禁止 `runtime -> infra` 反向依赖。
- 禁止 mapper / dao / repository 混在同一文件。
- 禁止 `model.port` 放 DTO / Command / Record。
- 禁止为兼容旧测试保留已确认过时逻辑。
- 禁止在主 runtime 链路中继续手写大段 prompt 字符串。
- 禁止 parser 与 structured output contract 各自维护字段白名单。
- 禁止把 prompt audit metadata 塞进对外 response。
- 禁止把 internal prompt、evidence、snapshot id、trace id 暴露给用户。
- 禁止 runtime 直接读取 MySQL prompt 表。
- 禁止请求运行期间每次查 DB 获取 prompt。
- 禁止把 DeepSeek strict tool call 当成业务 tool runtime。

---

## 5. 术语、语义与标识

### 5.1 术语表

| 术语 | 定义 | 本次是否涉及 |
|---|---|---|
| `SystemPromptTemplate` | 当前系统内部固定流程使用的 prompt 模板，不是通用 prompt 平台模板 | Y |
| `SystemPromptKey` | 内部系统 prompt key 枚举，代码中必须使用 enum | Y |
| `SystemPromptTemplateRef` | system prompt 模板引用，包含 key 与 version | Y |
| `PromptPurpose` | prompt 的用途枚举 | Y |
| `PromptTemplateKind` | prompt 渲染结果形态，分为 TEXT 与 CHAT_MESSAGES | Y |
| `PromptInputVariable` | prompt 渲染输入变量声明 | Y |
| `StructuredLlmOutputContract` | LLM 输出映射成业务 bean 前的结构化输出契约，核心内容是 JSON Schema | Y |
| `StructuredLlmOutputContractKey` | 结构化 LLM 输出契约 key 枚举 | Y |
| `StructuredLlmOutputMode` | provider adapter 如何承载结构化输出契约，属于 provider 边界适配，不属于 prompt contract 字段 | Y |
| `SystemPromptRegistry` | 启动时加载 yml 指定 prompt 版本后的运行期只读 registry | Y |
| `PromptRenderer` | prompt 渲染器，负责变量校验与模板渲染 | Y |
| `StructuredLlmOutputContractGuard` | 根据 contract 的 JSON Schema 校验模型输出字段和结构 | Y |
| `Provider-native structured output` | provider 原生结构化输出能力，例如 DeepSeek strict tool call | Y |
| `Parser Allowlist` | 旧设计中的 parser 字段白名单；P2-E 后必须删除，统一由 JSON Schema contract 承担 | Y |
| `Prompt Audit Metadata` | prompt key、version、purpose、contract、schema hash、variables 等审计元信息 | Y |
| `Internal LLM Task` | post-turn 阶段内部 LLM 任务，如 state / summary extraction | Y |
| `Context Block Render` | provider 调用前，将 state / summary 等渲染为模型可读文本 | Y |
| `Generic Prompt Platform` | 未来通用 prompt 管理平台，P2-E 不实现 | N |

---

### 5.2 标识符定义

| 标识符 | 用途 | 生成方 | 代码内类型 | 持久化 / 审计形态 | 是否对外返回 |
|---|---|---|---|---|---|
| `promptKey` | 内部系统 prompt 逻辑标识 | `SystemPromptKey` | enum | `prompt_key` / `value()` | 否 |
| `promptVersion` | prompt 模板版本 | MySQL + YML selector | String | `template_version` | 否 |
| `contractKey` | structured output contract 逻辑标识 | `StructuredLlmOutputContractKey` | enum | `contract_key` / `value()` | 否 |
| `contractVersion` | structured output contract 版本 | MySQL | String | `contract_version` | 否 |
| `schemaHash` | schema 内容摘要 | 启动校验或 seed 生成 | String | `schema_hash` | 否 |
| `promptPurpose` | prompt 用途 | `PromptPurpose` | enum | `purpose` / `value()` | 否 |
| `structuredOutputMode` | provider structured output 承载模式 | provider capability / request builder | enum | audit metadata | 否 |
| `internalTaskId` | internal memory task 审计 ID | `InternalTaskIdGenerator` | String | internal task audit | 否 |
| `traceId` | 内部观测链路 | 后端 | String | 日志 / MDC / internal audit | 否 |

设计规则：

- 代码内部使用 enum。
- DB / audit / JSON / 日志中使用 enum 的稳定 string value。
- 不允许业务类中散落 prompt key 字符串字面量。
- prompt template version 由 MySQL 存储，当前环境启用版本由 yml 选择。

---

### 5.3 render 与 extract 的区别

`render` 表示“把系统已有信息渲染给主模型看”。

例如：

```text
runtime_instruction_render
session_state_render
conversation_summary_render
```

它们发生在主聊天 LLM 调用之前，作用是构建主模型上下文，不生成新的 memory 数据，不单独调用 LLM。

`extract` 表示“让内部 LLM worker 从本轮对话中抽取新的结构化信息”。

例如：

```text
state_delta_extract
conversation_summary_extract
```

它们发生在主聊天 LLM 调用之后，作用是更新 memory / state / summary，不直接面向用户。

`conversation_summary_extract` 负责写摘要：

```text
raw turn messages + previous summary + latest state
-> new summary update
```

`conversation_summary_render` 负责读摘要：

```text
existing ConversationSummary
-> rendered context block for main LLM
```

两者不能合并。

---

### 5.4 input / render result / output contract 的区别

| 概念 | 负责什么 | 是否表示模型输出 JSON |
|---|---|---:|
| `PromptInputVariable` | 模板输入变量声明 | 否 |
| `PromptRenderRequest` | 本次渲染请求，携带 prompt key 和变量值 | 否 |
| `PromptRenderResult` | 模板渲染后的文本或 message 列表 | 否 |
| `StructuredLlmOutputContract` | 约束 LLM 最终返回的结构化输出 | 是，仅 extract 类 prompt 使用 |
| 业务 bean | 通过 contract guard 后由 parser 映射出的业务对象 | 是模型输出解析后的业务结果 |

关键原则：

```text
PromptInputVariable = 给模板填坑用的输入变量
StructuredLlmOutputContract = 约束内部 LLM worker 结构化 JSON 输出的契约
PromptRenderResult = PromptRenderer 的输出，不是 LLM 输出；可序列化为 JSON 用于审计 / 快照，但组装 LLM request 时只取 renderedText 或 renderedMessages
```

---

## 6. 对外协议设计（API / Stream）

### 6.1 请求契约

P2-E 不修改 `/chat` 请求契约。

现有 request DTO 不因 P2-E 增加 prompt 字段。

---

### 6.2 同步响应契约

P2-E 不修改 `/chat` 同步响应契约。

不得向 response 增加以下字段：

```text
promptKey
promptVersion
promptPurpose
promptVariables
structuredOutputContractKey
structuredOutputContractVersion
schemaHash
renderedPrompt
traceId
workingContextSnapshotId
internalTaskId
```

---

### 6.3 流式事件契约

P2-E 不修改 `/chat/stream` SSE event 契约。

不得向 stream event 增加以下字段：

```text
promptKey
promptVersion
promptPurpose
promptVariables
structuredOutputContractKey
structuredOutputContractVersion
schemaHash
renderedPrompt
traceId
workingContextSnapshotId
internalTaskId
```

---

### 6.4 协议红线

- `traceId` 不得出现在对外 DTO。
- prompt audit metadata 不得出现在对外 DTO。
- internal task id 不得出现在对外 DTO。
- working context snapshot id 不得出现在对外 DTO。
- Prompt Governance 只能改变内部 prompt 组织方式，不改变用户可见协议。

---

## 7. 持久化与配置设计

### 7.1 总体口径

P2-E 新增 MySQL prompt catalog 基线。

MySQL 负责存储版本化 prompt template 和 structured LLM output contract。

`application.yml` 负责选择当前环境启用哪个 prompt template version。

运行时启动加载并校验，构建只读 `SystemPromptRegistry`。

请求期间不查 DB。

---

### 7.2 表一：`system_prompt_template`

职责：存储内部系统 prompt template 的版本化数据。

建议 DDL：

```sql
CREATE TABLE system_prompt_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_key VARCHAR(128) NOT NULL COMMENT '内部系统 prompt key，对应 SystemPromptKey.value()',
  template_version VARCHAR(64) NOT NULL COMMENT 'prompt 模板版本',
  purpose VARCHAR(128) NOT NULL COMMENT 'prompt 用途，对应 PromptPurpose.value()',
  template_kind VARCHAR(64) NOT NULL COMMENT '模板形态，text 或 chat_messages',
  text_template LONGTEXT COMMENT 'TEXT 模式模板正文',
  message_templates_json LONGTEXT COMMENT 'CHAT_MESSAGES 模式消息模板 JSON',
  input_variables_json LONGTEXT NOT NULL COMMENT '模板输入变量声明 JSON',
  structured_output_contract_key VARCHAR(128) COMMENT '结构化 LLM 输出契约 key，仅 extract 类 prompt 有值',
  structured_output_contract_version VARCHAR(64) COMMENT '结构化 LLM 输出契约版本，仅 extract 类 prompt 有值',
  status VARCHAR(32) NOT NULL COMMENT '模板版本状态',
  description VARCHAR(512) COMMENT '版本说明',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_system_prompt_template_key_version (prompt_key, template_version)
);
```

字段说明：

| 字段 | 作用 |
|---|---|
| `prompt_key` | 对应 `SystemPromptKey.value()` |
| `template_version` | prompt 模板版本，例如 `p2-e-v1` |
| `purpose` | 对应 `PromptPurpose.value()` |
| `template_kind` | `text` 或 `chat_messages` |
| `text_template` | `TEXT` 类型模板正文 |
| `message_templates_json` | `CHAT_MESSAGES` 类型消息模板列表 |
| `input_variables_json` | 模板输入变量声明 |
| `structured_output_contract_key` | 只有 extract 类 prompt 有值 |
| `structured_output_contract_version` | 只有 extract 类 prompt 有值 |
| `status` | 模板版本状态，例如 `released`、`disabled` |
| `description` | 版本说明 |

---

### 7.3 表二：`structured_llm_output_contract`

职责：存储内部 LLM 任务的结构化输出契约版本。

建议 DDL：

```sql
CREATE TABLE structured_llm_output_contract (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_key VARCHAR(128) NOT NULL COMMENT '结构化 LLM 输出契约 key，对应 StructuredLlmOutputContractKey.value()',
  contract_version VARCHAR(64) NOT NULL COMMENT '结构化 LLM 输出契约版本',
  output_target VARCHAR(128) NOT NULL COMMENT '最终业务映射目标',
  schema_json LONGTEXT NOT NULL COMMENT '结构化输出 JSON Schema',
  schema_hash VARCHAR(128) NOT NULL COMMENT 'schema_json 规范化后的 SHA-256 摘要',
  status VARCHAR(32) NOT NULL COMMENT '契约版本状态',
  description VARCHAR(512) COMMENT '契约说明',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_structured_llm_output_contract_key_version (contract_key, contract_version)
);
```

字段说明：

| 字段 | 作用 |
|---|---|
| `contract_key` | 对应 `StructuredLlmOutputContractKey.value()` |
| `contract_version` | 输出契约版本，例如 `p2-e-v1` |
| `output_target` | 最终映射目标，例如 `state_delta_extraction_result` |
| `schema_json` | 完整 JSON Schema，使用 closed schema 思想约束字段 |
| `schema_hash` | 用于审计和回放核对 schema 内容 |
| `status` | 契约版本状态 |
| `description` | 契约说明 |

---

### 7.4 YML 启用版本选择

`application.yml` 只选择每个 `SystemPromptKey` 当前启用的 template version，不保存 prompt 正文。

示例：

```yaml
vi-agent:
  prompt:
    fail-fast: true
    active-versions:
      runtime_instruction_render: p2-e-v1
      session_state_render: p2-e-v1
      conversation_summary_render: p2-e-v1
      state_delta_extract: p2-e-v1
      conversation_summary_extract: p2-e-v1
```

规则：

1. MySQL 存所有版本。
2. YML 决定当前环境启用哪一版。
3. YML 不直接选择 contract version。
4. extract 类 prompt template 行自己绑定 `structured_output_contract_key` 与 `structured_output_contract_version`。
5. 如果 YML 指向的 prompt version 不存在，应用启动失败。
6. 如果 prompt row 绑定的 contract 不存在，应用启动失败。
7. 如果 enum value 不合法，应用启动失败。
8. 如果 `fail-fast=true`，任何 catalog 缺失或校验失败都不允许应用启动。

---

### 7.5 启动加载流程

```text
Application start
-> 读取 application.yml 中 active-versions
-> SystemPromptCatalogRepository 查询 MySQL 中指定 prompt template
-> 对 extract 类 template 加载其绑定的 StructuredLlmOutputContract
-> 校验 enum value 是否合法
-> 校验 template kind 与模板字段是否匹配
-> 校验 input variables 与模板占位符是否匹配
-> 校验 JSON Schema 合法性与 schema hash
-> 校验 render 类 prompt 不绑定 contract
-> 校验 extract 类 prompt 必须绑定 contract
-> 构建只读 SystemPromptRegistry
-> 后续请求只使用内存 registry
```

请求期间不得每次查 DB 获取 prompt。

---

### 7.6 种子数据与迁移

P2-E 需要新增 Flyway 或等价数据库迁移脚本：

```text
Vxxx__create_prompt_governance_tables.sql
Vxxx__seed_p2e_system_prompt_catalog.sql
```

种子数据必须包含：

```text
runtime_instruction_render:p2-e-v1
session_state_render:p2-e-v1
conversation_summary_render:p2-e-v1
state_delta_extract:p2-e-v1
conversation_summary_extract:p2-e-v1
state_delta_output:p2-e-v1
conversation_summary_output:p2-e-v1
```

---

## 8. 模块与包设计

### 8.1 新增 / 调整包结构

```text
vi-agent-core-model
├── model/prompt
│   ├── PromptTemplate
│   ├── AbstractPromptTemplate
│   ├── SystemPromptKey
│   ├── SystemPromptTemplate
│   ├── SystemPromptTemplateRef
│   ├── PromptPurpose
│   ├── PromptTemplateKind
│   ├── PromptInputVariable
│   ├── PromptInputVariableType
│   ├── PromptMessageTemplate
│   ├── StructuredLlmOutputContract
│   ├── StructuredLlmOutputContractKey
│   ├── StructuredLlmOutputContractRef
│   ├── StructuredLlmOutputTarget
│   ├── StructuredLlmOutputMode
│   ├── PromptCatalogStatus
│   └── PromptRenderMetadata
└── model/port
    └── SystemPromptCatalogRepository

vi-agent-core-infra
└── infra/prompt
    ├── MySqlSystemPromptCatalogRepository
    ├── SystemPromptTemplateEntity
    ├── StructuredLlmOutputContractEntity
    └── mapper / converter

vi-agent-core-runtime
└── runtime/prompt
    ├── SystemPromptRegistry
    ├── DefaultSystemPromptRegistry
    ├── SystemPromptRegistryFactory
    ├── PromptRenderer
    ├── PromptRenderRequest
    ├── PromptRenderResult
    ├── PromptRenderedMessage
    ├── NormalizedStructuredLlmOutput
    ├── StructuredLlmOutputContractGuard
    ├── StructuredLlmOutputContractValidationResult
    └── InternalTaskPromptResolver

vi-agent-core-app
└── config
    ├── PromptProperties
    └── PromptGovernanceConfiguration
```

---

### 8.2 依赖边界

正确依赖：

```text
runtime.prompt -> model.prompt + model.port
infra.prompt -> model.prompt + model.port
app -> runtime + infra + model
```

禁止：

```text
runtime.prompt -> infra.prompt
model.prompt -> runtime.prompt
model.prompt -> infra.prompt
```

---

## 9. 领域模型设计

### 9.1 强契约规则

本节所有 class / enum 片段为 P2-E 新增强契约。

要求：

1. 字段名不得自行改名。
2. 字段类型不得自行替换。
3. 字段数量不得自行扩展。
4. 字段语义不得自行推断。
5. 如发现现有代码与本文档不一致，优先改代码，不得用工程习惯替代文档。
6. 除非 Victor 明确确认，否则不得反向修改本文档迁就代码。
7. 新增字段必须先更新设计文档，再改代码，再补测试。
8. P2-E 内部 prompt key 必须使用 enum，不使用 String 常量类。
9. 所有 enum 必须符合项目 enum 风格：`CODE("value", "中文说明")`。
10. `StructuredLlmOutputContract` 不是业务 bean，不得替代 `StateDelta` / summary extraction result 等业务对象.

### 9.1.1 PromptTemplate 体系与 Message 体系对齐原则

P2-E 的 prompt template 模型必须仿照项目现有 `model.message` 体系设计。

当前 message 体系是：

```text
Message 接口
-> AbstractMessage 抽取 messageId / conversationId / sessionId / role / type / status / contentText 等公共字段
-> SystemMessage / UserMessage / AssistantMessage / ToolMessage / SummaryMessage 等 final 具体消息类型固定自身 role 与 messageType
```

P2-E 的 prompt template 体系采用同样思想：

```text
PromptTemplate 接口
-> AbstractPromptTemplate 抽取 promptKeyValue / promptVersion / purpose / templateKind / template 内容 / inputVariables / contractRef / status 等公共字段
-> SystemPromptTemplate 作为当前 P2-E 的 final 具体类型，固定使用 SystemPromptKey
-> 未来 GenericPromptTemplate 如需实现，应作为另一个 final 具体类型，不与 SystemPromptTemplate 共用同一个 type 字段
```

设计原则：

1. 共享公共模板结构，但不把 system prompt 与未来 generic prompt 混成一个大对象。
2. 不使用 `PromptTemplate.type = system/generic` 这种 type field 驱动设计。
3. `SystemPromptTemplate` 属于 runtime control plane，key 必须是 `SystemPromptKey` enum。
4. 未来 `GenericPromptTemplate` 属于业务配置面，可以有 namespace、tenant、owner、approval、experiment 等字段，但不得污染 `SystemPromptTemplate`。
5. 公共字段进入 `AbstractPromptTemplate`；具体身份、key 类型、生命周期边界由 final 子类表达。


---

### 9.2 `SystemPromptKey`

```java
public enum SystemPromptKey {

    RUNTIME_INSTRUCTION_RENDER("runtime_instruction_render", "主聊天运行指令渲染"),

    SESSION_STATE_RENDER("session_state_render", "会话状态上下文渲染"),

    CONVERSATION_SUMMARY_RENDER("conversation_summary_render", "会话摘要上下文渲染"),

    STATE_DELTA_EXTRACT("state_delta_extract", "状态增量抽取"),

    CONVERSATION_SUMMARY_EXTRACT("conversation_summary_extract", "会话摘要抽取");

    private final String value;

    private final String description;

    SystemPromptKey(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

说明：

- `SystemPromptKey` 只包含真正进入 `system_prompt_template` 表的 prompt template。
- `evidence_bind_deterministic` 当前不是 system prompt template，不放入该 enum。
- audit / DB 中保存 `value()`，代码中使用 enum。

---

### 9.3 `InternalTaskDefinitionKey`

```java
public enum InternalTaskDefinitionKey {

    EVIDENCE_BIND_DETERMINISTIC("evidence_bind_deterministic", "确定性证据绑定任务");

    private final String value;

    private final String description;

    InternalTaskDefinitionKey(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

说明：

- 该 enum 用于 deterministic internal task audit key。
- 当前不进入 `system_prompt_template` 表。
- 未来如果 evidence binding 升级为 LLM task，再进入新的 prompt 设计阶段。

---

### 9.4 `StructuredLlmOutputContractKey`

```java
public enum StructuredLlmOutputContractKey {

    STATE_DELTA_OUTPUT("state_delta_output", "状态增量结构化输出契约"),

    CONVERSATION_SUMMARY_OUTPUT("conversation_summary_output", "会话摘要结构化输出契约");

    private final String value;

    private final String description;

    StructuredLlmOutputContractKey(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

说明：

- 该 enum 只对应需要 LLM 返回结构化 JSON 的内部任务。
- `runtime_instruction_render`、`session_state_render`、`conversation_summary_render` 不需要结构化输出契约。
- `evidence_bind_deterministic` 当前不是 LLM prompt，也不需要结构化输出契约。

---

### 9.5 `StructuredLlmOutputTarget`

```java
public enum StructuredLlmOutputTarget {

    STATE_DELTA_EXTRACTION_RESULT(
        "state_delta_extraction_result",
        "状态增量抽取结果"
    ),

    CONVERSATION_SUMMARY_EXTRACTION_RESULT(
        "conversation_summary_extraction_result",
        "会话摘要抽取结果"
    );

    private final String value;

    private final String description;

    StructuredLlmOutputTarget(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

说明：

- `StructuredLlmOutputContract` 不替代业务 bean。
- `StructuredLlmOutputTarget` 用来明确这个 contract 最终服务哪个业务对象。

---

### 9.6 `PromptPurpose`

```java
public enum PromptPurpose {

    RUNTIME_INSTRUCTION_RENDER("runtime_instruction_render", "主聊天运行指令渲染"),

    SESSION_STATE_RENDER("session_state_render", "会话状态上下文渲染"),

    CONVERSATION_SUMMARY_RENDER("conversation_summary_render", "会话摘要上下文渲染"),

    STATE_DELTA_EXTRACTION("state_delta_extraction", "状态增量抽取"),

    CONVERSATION_SUMMARY_EXTRACTION("conversation_summary_extraction", "会话摘要抽取");

    private final String value;

    private final String description;

    PromptPurpose(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

---

### 9.7 `PromptTemplateKind`

```java
public enum PromptTemplateKind {

    TEXT("text", "渲染成一段文本，用于主聊天上下文 block"),

    CHAT_MESSAGES("chat_messages", "渲染成一组 system / user messages，用于内部 LLM worker 调用模型");

    private final String value;

    private final String description;

    PromptTemplateKind(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

---

### 9.8 `PromptInputVariableType`

```java
public enum PromptInputVariableType {

    TEXT("text", "普通文本"),

    JSON("json", "已序列化 JSON 文本"),

    NUMBER("number", "数字文本"),

    BOOLEAN("boolean", "布尔文本"),

    ENUM("enum", "枚举名称文本");

    private final String value;

    private final String description;

    PromptInputVariableType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

---

### 9.9 `StructuredLlmOutputMode`

```java
public enum StructuredLlmOutputMode {

    STRICT_TOOL_CALL("strict_tool_call", "严格工具调用结构化输出"),

    JSON_SCHEMA_RESPONSE_FORMAT("json_schema_response_format", "JSON Schema 响应格式结构化输出"),

    JSON_OBJECT("json_object", "普通 JSON 对象输出");

    private final String value;

    private final String description;

    StructuredLlmOutputMode(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

设计说明：

- 该 enum 描述 provider adapter 如何承载 `StructuredLlmOutputContract`。
- DeepSeek 场景优先使用 `STRICT_TOOL_CALL`。
- 该 enum 不等于业务 Tool Runtime 类型。
- 该 enum 不改变 Prompt Governance 的模板、变量、版本和契约模型。

---

### 9.11 `PromptCatalogStatus`

```java
public enum PromptCatalogStatus {

    RELEASED("released", "已发布可使用"),

    DISABLED("disabled", "已禁用不可使用");

    private final String value;

    private final String description;

    PromptCatalogStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }
}
```

说明：

- P2-E 不引入复杂生命周期，如 draft / review / approved / archived。
- 当前只需要区分可加载版本与禁用版本。
- YML 选择的版本必须是 `RELEASED`。

---

### 9.12 `SystemPromptTemplateRef`

```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class SystemPromptTemplateRef {

    /** 内部系统 prompt key。 */
    SystemPromptKey promptKey;

    /** prompt 模板版本。 */
    String promptVersion;
}
```

---

### 9.13 `StructuredLlmOutputContractRef`

```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StructuredLlmOutputContractRef {

    /** 结构化输出契约 key。 */
    StructuredLlmOutputContractKey contractKey;

    /** 结构化输出契约版本。 */
    String contractVersion;
}
```

---

### 9.14 `PromptInputVariable`

```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PromptInputVariable {

    /** 变量名。 */
    String variableName;

    /** 变量类型。 */
    PromptInputVariableType variableType;

    /** 是否必填。 */
    Boolean required;

    /** 变量说明。 */
    String description;

    /** 默认值。 */
    String defaultValue;
}
```

字段说明：

| 字段 | 类型 | 含义 | 什么时候用到 |
|---|---|---|---|
| `variableName` | `String` | 模板占位符名称，例如 `sessionId` | `PromptRenderer` 查找 `{{sessionId}}` 时使用 |
| `variableType` | `PromptInputVariableType` | 变量语义类型 | contract test、阅读、未来扩展使用 |
| `required` | `Boolean` | 是否必填 | renderer 渲染前校验 |
| `description` | `String` | 变量用途说明 | 设计和代码可读性 |
| `defaultValue` | `String` | 可选默认值 | 非必填变量缺失时使用 |

---

### 9.15 `PromptMessageTemplate`

```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PromptMessageTemplate {

    /** 消息顺序。 */
    Integer order;

    /** 消息角色。 */
    MessageRole role;

    /** 消息内容模板。 */
    String contentTemplate;
}
```

---

### 9.16 `StructuredLlmOutputContract`

```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StructuredLlmOutputContract {

    /** 结构化输出契约 key。 */
    StructuredLlmOutputContractKey contractKey;

    /** 结构化输出契约版本。 */
    String contractVersion;

    /** 输出最终映射目标。 */
    StructuredLlmOutputTarget outputTarget;

    /** 结构化输出 JSON Schema。 */
    String schemaJson;

    /** JSON Schema 摘要哈希。 */
    String schemaHash;

    /** 契约状态。 */
    PromptCatalogStatus status;

    /** 契约说明。 */
    String description;
}
```

字段说明：

| 字段 | 类型 | 含义 | 为什么需要 |
|---|---|---|---|
| `contractKey` | `StructuredLlmOutputContractKey` | 输出契约 key | 防止 state 与 summary contract 混用 |
| `contractVersion` | `String` | 输出契约版本 | 审计、测试、问题排查、未来升级 |
| `outputTarget` | `StructuredLlmOutputTarget` | 最终业务映射目标 | 明确 contract 不是 bean，但服务于哪个 bean |
| `schemaJson` | `String` | 完整 JSON Schema | 统一字段边界，避免 parser allowlist 双轨维护 |
| `schemaHash` | `String` | schema 摘要 | 审计、回放、排查时确认 schema 内容 |
| `status` | `PromptCatalogStatus` | 是否可加载 | 防止禁用版本被启用 |
| `description` | `String` | 契约说明 | 代码阅读和审计辅助 |

关键说明：

- `StructuredLlmOutputContract` 不是业务 bean。
- `StructuredLlmOutputContract` 不替代 `StateDelta`。
- `StructuredLlmOutputContract` 不替代 summary extraction result。
- `StructuredLlmOutputContract` 不负责 merge state。
- `StructuredLlmOutputContract` 不负责生成 `ConversationSummary`。
- `StructuredLlmOutputContract` 只负责在模型输出映射成 bean 之前定义结构化输出边界。

标准流程：

```text
Provider response
-> Provider adapter / response extractor 归一化为 NormalizedStructuredLlmOutput
-> StructuredLlmOutputContractGuard 使用 schemaJson 做 JSON Schema 校验
-> ObjectMapper / parser 映射成业务 bean
-> 业务 parser 做 skipped / degraded / merge 前置判断
-> runtime 后续处理
```

不允许直接采用以下流程：

```text
LLM raw text
-> 直接 ObjectMapper 转 bean
```

---

### 9.17 `PromptTemplate`

```java
public interface PromptTemplate {

    String getPromptKeyValue();

    String getPromptVersion();

    PromptPurpose getPurpose();

    PromptTemplateKind getTemplateKind();

    String getTextTemplate();

    List<PromptMessageTemplate> getMessageTemplates();

    List<PromptInputVariable> getInputVariables();

    StructuredLlmOutputContractRef getStructuredOutputContractRef();

    PromptCatalogStatus getStatus();

    String getDescription();
}
```

字段说明：

| 方法 | 含义 | 为什么放在接口层 |
|---|---|---|
| `getPromptKeyValue()` | prompt key 的稳定字符串值 | registry / DB / audit 可以统一读取，不关心具体 key enum 类型 |
| `getPromptVersion()` | prompt 模板版本 | prompt catalog 版本化查询、审计、回放 |
| `getPurpose()` | prompt 用途 | renderer 校验调用方期望用途和模板用途是否一致 |
| `getTemplateKind()` | prompt 渲染形态 | 决定渲染为 TEXT 还是 CHAT_MESSAGES |
| `getTextTemplate()` | TEXT 模板正文 | context block render 使用 |
| `getMessageTemplates()` | CHAT_MESSAGES 模板片段 | internal LLM worker 使用 |
| `getInputVariables()` | 模板输入变量声明 | renderer 做变量校验 |
| `getStructuredOutputContractRef()` | 结构化输出契约引用 | 只有 extract 类 prompt 有值 |
| `getStatus()` | catalog 状态 | 启动加载时校验 yml 指向版本是否可用 |
| `getDescription()` | 模板说明 | 审计、排查和文档可读性 |

设计说明：

- `PromptTemplate` 是模板体系的统一抽象，作用类似现有 `Message` 接口。
- 该接口只暴露 prompt 模板公共读取能力，不承载 system / generic 的具体生命周期规则。
- P2-E 当前只有 `SystemPromptTemplate` 一个具体实现。
- 未来如果新增 `GenericPromptTemplate`，应实现该接口或继承同一抽象基类，但不能把二者混成一个带 `type` 字段的大对象。

---

### 9.18 `AbstractPromptTemplate`

```java
@Getter
public abstract class AbstractPromptTemplate implements PromptTemplate {

    private final String promptKeyValue;

    private final String promptVersion;

    private final PromptPurpose purpose;

    private final PromptTemplateKind templateKind;

    private final String textTemplate;

    private final List<PromptMessageTemplate> messageTemplates;

    private final List<PromptInputVariable> inputVariables;

    private final StructuredLlmOutputContractRef structuredOutputContractRef;

    private final PromptCatalogStatus status;

    private final String description;

    protected AbstractPromptTemplate(
        String promptKeyValue,
        String promptVersion,
        PromptPurpose purpose,
        PromptTemplateKind templateKind,
        String textTemplate,
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        StructuredLlmOutputContractRef structuredOutputContractRef,
        PromptCatalogStatus status,
        String description
    ) {
        this.promptKeyValue = promptKeyValue;
        this.promptVersion = promptVersion;
        this.purpose = purpose;
        this.templateKind = templateKind;
        this.textTemplate = textTemplate == null ? "" : textTemplate;
        this.messageTemplates = messageTemplates == null || messageTemplates.isEmpty()
            ? List.of()
            : List.copyOf(messageTemplates);
        this.inputVariables = inputVariables == null || inputVariables.isEmpty()
            ? List.of()
            : List.copyOf(inputVariables);
        this.structuredOutputContractRef = structuredOutputContractRef;
        this.status = status == null ? PromptCatalogStatus.DISABLED : status;
        this.description = description == null ? "" : description;
    }
}
```

字段说明：

| 字段 | 类型 | 含义 | 为什么放在抽象基类 |
|---|---|---|---|
| `promptKeyValue` | `String` | prompt key 的持久化 / 审计字符串值 | 不同具体 prompt key enum 可以统一落库和审计 |
| `promptVersion` | `String` | prompt 模板版本 | system / future generic prompt 都需要版本化 |
| `purpose` | `PromptPurpose` | prompt 用途 | renderer 通用校验需要 |
| `templateKind` | `PromptTemplateKind` | TEXT 或 CHAT_MESSAGES | renderer 通用渲染分支需要 |
| `textTemplate` | `String` | TEXT 模板正文 | 公共模板内容 |
| `messageTemplates` | `List<PromptMessageTemplate>` | CHAT_MESSAGES 消息模板 | 公共模板内容 |
| `inputVariables` | `List<PromptInputVariable>` | 输入变量声明 | 公共变量契约 |
| `structuredOutputContractRef` | `StructuredLlmOutputContractRef` | 结构化输出契约引用 | extract 类 prompt 的公共能力 |
| `status` | `PromptCatalogStatus` | 模板状态 | catalog 加载校验需要 |
| `description` | `String` | 模板说明 | 审计和排查需要 |

设计说明：

- `AbstractPromptTemplate` 对齐项目现有 `AbstractMessage` 的设计思想。
- 它只抽取模板公共字段，不表达 system prompt 或 generic prompt 的身份规则。
- 它不使用 `@Value`，因为当前设计采用继承体系；这与 `AbstractMessage` 使用普通抽象基类的方式一致。
- 集合字段必须在构造时复制为不可变列表，防止运行期被外部修改。
- `promptKeyValue` 只用于统一持久化 / 审计读取；具体子类仍应保留强类型 key 字段。

---

### 9.19 `SystemPromptTemplate`

```java
@Getter
public final class SystemPromptTemplate extends AbstractPromptTemplate {

    private final SystemPromptKey promptKey;

    private SystemPromptTemplate(
        SystemPromptKey promptKey,
        String promptVersion,
        PromptPurpose purpose,
        PromptTemplateKind templateKind,
        String textTemplate,
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        StructuredLlmOutputContractRef structuredOutputContractRef,
        PromptCatalogStatus status,
        String description
    ) {
        super(
            promptKey.value(),
            promptVersion,
            purpose,
            templateKind,
            textTemplate,
            messageTemplates,
            inputVariables,
            structuredOutputContractRef,
            status,
            description
        );
        this.promptKey = promptKey;
    }

    public static SystemPromptTemplate create(
        SystemPromptKey promptKey,
        String promptVersion,
        PromptPurpose purpose,
        PromptTemplateKind templateKind,
        String textTemplate,
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        StructuredLlmOutputContractRef structuredOutputContractRef,
        PromptCatalogStatus status,
        String description
    ) {
        return new SystemPromptTemplate(
            promptKey,
            promptVersion,
            purpose,
            templateKind,
            textTemplate,
            messageTemplates,
            inputVariables,
            structuredOutputContractRef,
            status,
            description
        );
    }

    public static SystemPromptTemplate restore(
        SystemPromptKey promptKey,
        String promptVersion,
        PromptPurpose purpose,
        PromptTemplateKind templateKind,
        String textTemplate,
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        StructuredLlmOutputContractRef structuredOutputContractRef,
        PromptCatalogStatus status,
        String description
    ) {
        return new SystemPromptTemplate(
            promptKey,
            promptVersion,
            purpose,
            templateKind,
            textTemplate,
            messageTemplates,
            inputVariables,
            structuredOutputContractRef,
            status,
            description
        );
    }
}
```

约束：

1. `SystemPromptTemplate` 是当前 P2-E 的唯一 prompt template 具体类型。
2. `promptKey` 必须是 `SystemPromptKey`，不允许退化为自由 String。
3. `promptKey.value() + promptVersion` 必须唯一，对应 MySQL 唯一键 `prompt_key + template_version`。
4. `templateKind=TEXT` 时，`textTemplate` 必须非空，`messageTemplates` 必须为空。
5. `templateKind=CHAT_MESSAGES` 时，`messageTemplates` 必须非空，`textTemplate` 必须为空。
6. `STATE_DELTA_EXTRACTION` 必须有 `structuredOutputContractRef`。
7. `CONVERSATION_SUMMARY_EXTRACTION` 必须有 `structuredOutputContractRef`。
8. context render prompt 不需要 `structuredOutputContractRef`。
9. 是否启用不由 `active` 字段决定，而由 `application.yml` 的 `active-versions` 决定。
10. YML 选择的模板版本必须是 `RELEASED`。

为什么不使用一个通用 `PromptTemplate + type` 字段：

- `SystemPromptTemplate` 属于 runtime control plane，是系统核心控制配置。
- 未来 `GenericPromptTemplate` 属于 product / business configuration plane，可能涉及 tenant、owner、approval、experiment、visibility 等字段。
- 二者生命周期、权限、安全等级和启用方式不同。
- 使用一个大对象加 type 字段会导致大量 nullable 字段和 if/else 分支。
- 当前采用 interface + abstract base + final concrete class 的方式，可以复用公共模板结构，同时保持领域边界清晰。

---

### 9.20 `PromptRenderMetadata`

```java
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PromptRenderMetadata {

    /** 模板引用。 */
    SystemPromptTemplateRef templateRef;

    /** prompt 用途。 */
    PromptPurpose purpose;

    /** 结构化输出契约引用。 */
    StructuredLlmOutputContractRef structuredOutputContractRef;

    /** schema 摘要哈希。 */
    String schemaHash;

    /** 本次参与渲染的变量名。 */
    @Singular("renderedVariableName")
    List<String> renderedVariableNames;
}
```

约束：

- metadata 用于内部审计。
- metadata 不得进入主 chat response。
- metadata 不得进入 stream event。
- metadata 默认不存储完整 rendered prompt。
- 如果后续要保存完整 rendered prompt，必须另开阶段设计，不在 P2-E 偷做。

---

### 9.21 `PromptRenderResult` 体系

P2-E 中 `PromptRenderer` 不返回裸字符串。`PromptRenderer` 必须返回结构化 `PromptRenderResult` 对象；该对象可以序列化为 JSON 用于 audit / snapshot / debug，但在组装 LLM request 时只取 `renderedText` 或 `renderedMessages`。

```java
public interface PromptRenderResult {

    SystemPromptTemplateRef getTemplateRef();

    PromptPurpose getPurpose();

    PromptTemplateKind getTemplateKind();

    PromptRenderMetadata getMetadata();
}
```

```java
@Getter
public abstract class AbstractPromptRenderResult implements PromptRenderResult {

    private final SystemPromptTemplateRef templateRef;

    private final PromptPurpose purpose;

    private final PromptTemplateKind templateKind;

    private final PromptRenderMetadata metadata;
}
```

```java
@Getter
public final class TextPromptRenderResult extends AbstractPromptRenderResult {

    /** 渲染后的文本内容。 */
    private final String renderedText;
}
```

```java
@Getter
public final class ChatMessagesPromptRenderResult extends AbstractPromptRenderResult {

    /** 渲染后的消息列表。 */
    private final List<PromptRenderedMessage> renderedMessages;

    /** 结构化 LLM 输出契约引用；仅 extract 类 prompt 使用。 */
    private final StructuredLlmOutputContractRef structuredOutputContractRef;
}
```

对应关系：

| prompt 类型 | PromptRenderResult 具体类型 | 是否调用 LLM | 是否绑定 StructuredLlmOutputContract |
|---|---|---:|---:|
| `runtime_instruction_render` | `TextPromptRenderResult` | 否 | 否 |
| `session_state_render` | `TextPromptRenderResult` | 否 | 否 |
| `conversation_summary_render` | `TextPromptRenderResult` | 否 | 否 |
| `state_delta_extract` | `ChatMessagesPromptRenderResult` | 是 | 是 |
| `conversation_summary_extract` | `ChatMessagesPromptRenderResult` | 是 | 是 |

设计说明：

- 统一的是结构化 Java 对象，不是在 runtime 内部把所有内容序列化成 JSON 字符串再解析。
- JSON 是边界序列化格式，可用于 audit / snapshot / debug。
- 真正请求 LLM 时，assembler 只读取 `renderedText` 或 `renderedMessages`。
- render 类 prompt 是确定性模型输入渲染，不存在 LLM 输出解析。
- extract 类 prompt 是内部 LLM worker，输出统一要求为 structured JSON。

---

## 10. Prompt 模板与拼接边界

### 10.1 总原则

P2-E 中 prompt 相关职责分为三层：

```text
业务对象 / command
-> PromptVariablesFactory：把业务对象转成 Map<String, String>
-> SystemPromptTemplate：定义固定话术骨架和占位符
-> PromptRenderer：只做变量校验和占位符替换
```

底层确实是确定性字符串渲染，但它不是散落在业务类里的字符串拼接，而是由模板、变量、版本、MySQL catalog、YML selector、registry、测试和 audit 共同约束的确定性模板渲染。

---

### 10.2 `textTemplate` 的职责

`textTemplate` 不是业务拼接逻辑。

`textTemplate` 只负责：

- 固定系统话术；
- 固定输出要求；
- 固定边界约束；
- 变量占位符。

`textTemplate` 不负责：

- 从 `SessionStateSnapshot` 中取字段；
- 从 transcript 中筛选消息；
- 把对象序列化成 JSON；
- 判断哪些内容应该进入上下文；
- 调用 LLM；
- 处理 parser degraded。

这些职责属于调用方或专门的 variable factory / renderer / parser。

---

### 10.3 VariableFactory 的职责

P2-E 推荐新增或收口以下变量工厂：

| 工厂 | 输入 | 输出 | 职责 |
|---|---|---|---|
| `StateDeltaExtractionPromptVariablesFactory` | `StateDeltaExtractionCommand` | `Map<String, String>` | 将 state extraction 所需上下文转为稳定文本 / JSON |
| `ConversationSummaryExtractionPromptVariablesFactory` | `ConversationSummaryExtractionCommand` | `Map<String, String>` | 将 summary extraction 所需上下文转为稳定文本 / JSON |
| `ContextBlockPromptVariablesFactory` | context block 相关对象 | `Map<String, String>` | 将 state / summary / mode 等 context render 输入转为变量 |

约束：

- variable factory 可以依赖业务 command。
- `PromptRenderer` 不依赖业务 command。
- `SystemPromptTemplate` 不关心业务对象结构。
- 对象转 JSON 的细节不放在 template 中。

---

## 11. 结构化输出契约设计

### 11.1 为什么使用 JSON Schema


原因：

- `forbiddenFields` 如果用于表达所有禁止字段，理论上是无穷大的。
- `allowedTopLevelFields` + `nestedFieldContracts` 本质是在手写简化版 JSON Schema。
- 企业级结构化输出应采用 closed schema 思想：只声明允许出现什么；没有声明的，一律不允许。

因此，`StructuredLlmOutputContract.schemaJson` 是唯一结构化字段事实源。

JSON Schema 中每个 object 必须使用：

```json
{
  "additionalProperties": false
}
```

旧字段、危险字段、系统字段不通过黑名单无限枚举拦截，而是因为不在 schema 中，并且 `additionalProperties:false`，自然被拒绝。

P2-E 提示词工程内部只有 `state_delta_extract` 与 `conversation_summary_extract` 会调用 LLM 能力。二者输出统一要求为 structured JSON。Prompt Governance 层不接受 markdown fenced JSON、自然语言解释、半结构化文本作为核心契约；如果 provider 返回格式存在差异，由 provider adapter 归一化为 `NormalizedStructuredLlmOutput` 后再进入本地 schema 校验。

---

### 11.2 `STATE_DELTA_OUTPUT:p2-e-v1`

基础信息：

```text
contractKey: STATE_DELTA_OUTPUT
contractKey.value(): state_delta_output
contractVersion: p2-e-v1
outputTarget: STATE_DELTA_EXTRACTION_RESULT
schemaHash: 由 schemaJson 规范化后计算
```

schema 必须表达以下字段边界：

顶层允许字段：

```text
taskGoalOverride
confirmedFactsAppend
constraintsAppend
userPreferencesPatch
decisionsAppend
openLoopsAppend
openLoopIdsToClose
recentToolOutcomesAppend
workingModeOverride
phaseStatePatch
sourceCandidateIds
```

嵌套字段说明：

```text
confirmedFactsAppend[]: factId, content, confidence, lastVerifiedAt, stalePolicy
constraintsAppend[]: constraintId, content, scope, confidence, lastVerifiedAt
decisionsAppend[]: decisionId, content, decidedBy, decidedAt, confidence
openLoopsAppend[]: loopId, kind, content, status, sourceType, sourceRef, createdAt, closedAt
recentToolOutcomesAppend[]: digestId, toolCallRecordId, toolExecutionId, toolName, summary, freshnessPolicy, validUntil, lastVerifiedAt
userPreferencesPatch: answerStyle, detailLevel, termFormat
phaseStatePatch: promptEngineeringEnabled, contextAuditEnabled, summaryEnabled, stateExtractionEnabled, compactionEnabled
```

必须通过 schema 拒绝的典型旧字段 / 危险字段：

```text
upsert
remove
patches
operations
memory
messages
fullState
sessionState
summary
debug
phaseKey
phaseName
locale
timezone
```

这些字段不需要写入 `forbiddenFields`，而应通过 JSON Schema closed object 机制拒绝。

---

### 11.3 `CONVERSATION_SUMMARY_OUTPUT:p2-e-v1`

基础信息：

```text
contractKey: CONVERSATION_SUMMARY_OUTPUT
contractKey.value(): conversation_summary_output
contractVersion: p2-e-v1
outputTarget: CONVERSATION_SUMMARY_EXTRACTION_RESULT
schemaHash: 由 schemaJson 规范化后计算
```

schema 必须表达以下顶层允许字段：

```text
summaryText
skipped
reason
```

必须通过 schema 拒绝的典型旧字段 / 系统字段：

```text
summaryId
sessionId
conversationId
summaryVersion
coveredFromSequenceNo
coveredToSequenceNo
summaryTemplateKey
summaryTemplateVersion
generatorProvider
generatorModel
createdAt
memory
messages
stateDelta
evidence
debug
upsert
remove
```

说明：

- 模型只输出 summary extraction result，不输出完整 `ConversationSummary` 领域对象。
- `summaryId`、`summaryVersion`、`coveredFromSequenceNo`、`coveredToSequenceNo` 等系统字段由 runtime 填充。
- `summaryTemplateKey` / `summaryTemplateVersion` 由 P2-E prompt governance 写入，不允许模型生成。
- `generatorProvider` / `generatorModel` 由 provider response 填充，不允许模型生成。

---

### 11.4 本地校验流程

```text
Provider response
-> Provider adapter / response extractor 归一化为 NormalizedStructuredLlmOutput
-> StructuredLlmOutputContractGuard 使用 schemaJson 做 JSON Schema 校验
-> 通过后交给业务 parser
-> parser 映射 StateDelta / SummaryExtractionResult
-> runtime 执行 merge / summary save / audit
```

约束：

- 本地 schema 校验不能省略。
- 即使 provider 使用 strict structured output，也必须保留本地 schema 校验。
- parser 不再维护独立 allowlist。
- parser 只负责业务对象映射、skipped / degraded 语义和业务校验。

---

## 12. Provider structured output adapter 设计

### 12.1 设计口径

P2-E 的 Prompt Governance 不直接绑定某个模型厂商的结构化输出 API。

系统统一以 `StructuredLlmOutputContract.schemaJson` 表达内部 LLM 任务的输出契约。

在 `LlmGateway` / provider adapter 边界，根据 provider capability 将该契约转换为 provider-native structured output 请求格式。

DeepSeek 场景下优先转换为 strict tool call；这只是结构化输出承载方式，不等同于业务 Tool Runtime，也不改变 P2-E 的 prompt 模板治理模型。

P2-E 不在 Prompt Governance 核心模型中设计多种 LLM 输出类型。对于提示词工程内部 LLM worker，系统只接受 structured JSON。`StructuredLlmOutputMode` 只描述 provider adapter 如何把同一份 `schemaJson` 承载到不同 provider 请求中。

---

### 12.2 Provider 输出模式

| 模式 | 含义 | 使用场景 |
|---|---|---|
| `STRICT_TOOL_CALL` | 将 schemaJson 包装为 provider tool/function parameters，并要求模型输出 tool call arguments | DeepSeek strict mode、其他 function calling strict schema 能力 |
| `JSON_SCHEMA_RESPONSE_FORMAT` | 使用 provider 原生 response_format json_schema | 支持 JSON Schema response format 的 provider |
| `JSON_OBJECT` | 只要求返回合法 JSON object | 只支持普通 JSON output 的 provider |

---

### 12.3 DeepSeek 适配口径

DeepSeek 普通 JSON Output 只能保证输出是合法 JSON object，不等于 schema 强一致。

DeepSeek strict tool call 支持在 function parameters 中传入 JSON Schema，并通过 `strict=true` 要求模型按 schema 输出 tool call arguments。

因此对于：

```text
state_delta_extract
conversation_summary_extract
```

DeepSeek provider adapter 优先采用：

```text
StructuredLlmOutputContract.schemaJson
-> tools[].function.parameters
-> function.strict = true
-> tool_choice 指定内部结构化输出 function
-> 读取 tool_calls[].function.arguments
-> 本地 schema 校验
-> 业务 parser
```

建议内部结构化输出 function 名称：

```text
emit_state_delta
emit_conversation_summary
```

约束：

- 这些 function 不进入业务 Tool Runtime。
- 不执行外部工具。
- 只是 provider-native structured output channel。
- 即使 provider strict tool call 成功，也必须执行本地 schema 校验。

---

## 13. Runtime 主链路设计

### 13.1 新增 runtime.prompt 组件清单

| 组件 | 职责 | 本次变更 |
|---|---|---|
| `SystemPromptRegistry` | system prompt template 查询入口 | 新增 |
| `DefaultSystemPromptRegistry` | 运行期只读 registry 默认实现 | 新增 |
| `SystemPromptRegistryFactory` | 启动时根据 yml + repository 构建 registry | 新增 |
| `PromptRenderer` | prompt 变量校验与渲染 | 新增 |
| `PromptRenderRequest` | prompt 渲染请求 | 新增 |
| `PromptRenderResult` | prompt 渲染结果 | 新增 |
| `PromptRenderedMessage` | 渲染后的 message 片段 | 新增 |
| `NormalizedStructuredLlmOutput` | provider response 归一化后的结构化 JSON 输出对象 | 新增 |
| `StructuredLlmOutputContractGuard` | 基于 JSON Schema 的结构化输出校验 | 新增 |
| `StructuredLlmOutputContractValidationResult` | contract 校验结果 | 新增 |
| `PromptRenderException` | prompt 渲染异常 | 新增 |
| `InternalTaskPromptResolver` | internal task type 到 prompt ref 的映射 | 新增 |
| `StateDeltaExtractionPromptVariablesFactory` | state extraction 变量构造 | 新增或替代旧 builder |
| `ConversationSummaryExtractionPromptVariablesFactory` | summary extraction 变量构造 | 新增或替代旧 builder |

---

### 13.2 `SystemPromptRegistry`

接口语义：

```java
public interface SystemPromptRegistry {

    SystemPromptTemplate getByRef(SystemPromptTemplateRef ref);

    SystemPromptTemplate getActiveByKey(SystemPromptKey promptKey);

    SystemPromptTemplate getActiveByPurpose(PromptPurpose purpose);

    StructuredLlmOutputContract getStructuredOutputContract(StructuredLlmOutputContractRef ref);

    List<SystemPromptTemplate> listActiveTemplates();
}
```

职责：

- 保存应用启动时加载并校验后的 active templates。
- 按 key 查询当前启用版本。
- 按 purpose 查询当前启用模板。
- 查询 template 绑定的 structured output contract。
- 为 renderer、parser、internal task audit 提供同一个 prompt 事实源。

约束：

- registry 不读文件。
- registry 不读 DB。
- registry 不写 Redis / MySQL。
- registry 不调用 provider。
- registry 不做变量替换。
- registry 是运行期只读快照。

---

### 13.3 `SystemPromptCatalogRepository`

所在模块：`vi-agent-core-model/src/main/java/.../model/port`

接口语义：

```java
public interface SystemPromptCatalogRepository {

    Optional<SystemPromptTemplate> findTemplate(SystemPromptKey promptKey, String promptVersion);

    Optional<StructuredLlmOutputContract> findContract(
        StructuredLlmOutputContractKey contractKey,
        String contractVersion
    );
}
```

约束：

- 这是 port，不是 infra 实现。
- runtime 只能依赖该 port。
- MySQL 读取逻辑在 infra 实现中。

---

### 13.4 `PromptRenderer`

职责：

- 根据 `PromptRenderRequest` 获取 template；
- 校验 purpose；
- 校验 required variables；
- 校验 request 是否传入未声明变量；
- 校验模板中是否存在未声明占位符；
- 替换 `{{variableName}}`；
- 生成 `PromptRenderResult`；
- 生成 `PromptRenderMetadata`。

核心流程：

```text
PromptRenderRequest
-> SystemPromptRegistry.getActiveByKey / getByRef
-> 校验 template purpose
-> 校验变量声明与传入变量
-> 渲染 TEXT 或 CHAT_MESSAGES
-> 生成 SystemPromptTemplateRef
-> 生成 PromptRenderMetadata
-> 返回 PromptRenderResult
```

约束：

- 不调用 LLM。
- 不访问 DB。
- 不访问 Redis。
- 不读取文件。
- 不写 audit 表。
- 不吞异常。

---

## 14. 内置 Prompt 契约

### 14.1 `RUNTIME_INSTRUCTION_RENDER`

```text
promptKey: RUNTIME_INSTRUCTION_RENDER
promptKey.value(): runtime_instruction_render
promptVersion: p2-e-v1
purpose: RUNTIME_INSTRUCTION_RENDER
kind: TEXT
structuredOutputContract: N/A
```

变量：

| 变量 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `agentMode` | `ENUM` | Y | 当前 AgentMode |
| `workingMode` | `ENUM` | N | 当前 WorkingMode |
| `phaseStateText` | `TEXT` | N | 阶段状态说明 |

---

### 14.2 `SESSION_STATE_RENDER`

```text
promptKey: SESSION_STATE_RENDER
promptKey.value(): session_state_render
promptVersion: p2-e-v1
purpose: SESSION_STATE_RENDER
kind: TEXT
structuredOutputContract: N/A
```

变量：

| 变量 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `stateVersion` | `NUMBER` | Y | state 版本 |
| `sessionStateText` | `TEXT` | Y | 由 `SessionStateBlockRenderer` 生成的紧凑状态文本 |

---

### 14.3 `CONVERSATION_SUMMARY_RENDER`

```text
promptKey: CONVERSATION_SUMMARY_RENDER
promptKey.value(): conversation_summary_render
promptVersion: p2-e-v1
purpose: CONVERSATION_SUMMARY_RENDER
kind: TEXT
structuredOutputContract: N/A
```

变量：

| 变量 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `summaryVersion` | `NUMBER` | Y | summary 版本 |
| `summaryText` | `TEXT` | Y | 会话摘要正文 |

---

### 14.4 `STATE_DELTA_EXTRACT`

```text
promptKey: STATE_DELTA_EXTRACT
promptKey.value(): state_delta_extract
promptVersion: p2-e-v1
purpose: STATE_DELTA_EXTRACTION
kind: CHAT_MESSAGES
structuredOutputContract: STATE_DELTA_OUTPUT:p2-e-v1
```

消息结构：

| order | role | 职责 |
|---|---|---|
| 1 | `SYSTEM` | 定义内部 state extraction worker 身份、输出 JSON 限制、禁止用户可见回复 |
| 2 | `USER` | 提供 metadata、current state、summary、turn messages、输出字段约束 |

变量：

| 变量 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `conversationId` | `TEXT` | N | 对话 ID |
| `sessionId` | `TEXT` | Y | 会话 ID |
| `turnId` | `TEXT` | Y | 回合 ID |
| `runId` | `TEXT` | Y | 运行 ID |
| `traceId` | `TEXT` | N | 链路 ID |
| `agentMode` | `ENUM` | N | AgentMode |
| `workingContextSnapshotId` | `TEXT` | N | working context snapshot ID |
| `currentStateVersion` | `NUMBER` | N | 当前 state version |
| `currentStateJson` | `JSON` | Y | 当前 state JSON |
| `conversationSummaryText` | `TEXT` | N | 最新摘要文本 |
| `turnMessagesText` | `TEXT` | Y | 当前 turn transcript 文本 |

---

### 14.5 `CONVERSATION_SUMMARY_EXTRACT`

```text
promptKey: CONVERSATION_SUMMARY_EXTRACT
promptKey.value(): conversation_summary_extract
promptVersion: p2-e-v1
purpose: CONVERSATION_SUMMARY_EXTRACTION
kind: CHAT_MESSAGES
structuredOutputContract: CONVERSATION_SUMMARY_OUTPUT:p2-e-v1
```

消息结构：

| order | role | 职责 |
|---|---|---|
| 1 | `SYSTEM` | 定义内部 summary worker 身份、输出 JSON 限制、禁止用户可见回复 |
| 2 | `USER` | 提供 metadata、previous summary、latest state、turn messages、输出字段约束 |

变量：

| 变量 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `conversationId` | `TEXT` | N | 对话 ID |
| `sessionId` | `TEXT` | Y | 会话 ID |
| `turnId` | `TEXT` | Y | 回合 ID |
| `runId` | `TEXT` | Y | 运行 ID |
| `traceId` | `TEXT` | N | 链路 ID |
| `agentMode` | `ENUM` | N | AgentMode |
| `workingContextSnapshotId` | `TEXT` | N | working context snapshot ID |
| `previousSummaryVersion` | `NUMBER` | N | 旧 summary version |
| `previousSummaryText` | `TEXT` | N | 旧 summary 文本 |
| `latestStateJson` | `JSON` | N | 最新 state JSON |
| `turnMessagesText` | `TEXT` | Y | 当前 turn transcript 文本 |

---

## 15. 现有代码迁移设计

### 15.1 `ContextBlockFactory`

当前问题：

```text
RUNTIME_TEMPLATE_KEY = "runtime-instruction"
SESSION_STATE_TEMPLATE_KEY = "session-state"
SUMMARY_TEMPLATE_KEY = "conversation-summary"
TEMPLATE_VERSION = "p2-c-v1"
```

P2-E 后要求：

1. 注入 `PromptRenderer`。
2. runtime instruction block 通过 `PromptRenderer` 渲染。
3. session state block 通过 `SessionStateBlockRenderer` 先生成 `sessionStateText`，再交给 `PromptRenderer` 渲染外层模板。
4. conversation summary block 通过 `PromptRenderer` 渲染。
5. block 中的 `promptTemplateKey` 来源于 `PromptRenderResult.templateRef.promptKey.value()`。
6. block 中的 `promptTemplateVersion` 来源于 `PromptRenderResult.templateRef.promptVersion`。
7. 删除旧硬编码 key / version。

---

### 15.2 `StateDeltaExtractionPromptBuilder`

当前问题：

```text
PROMPT_TEMPLATE_KEY = "state_extract_inline"
PROMPT_TEMPLATE_VERSION = "p2-d-2-v1"
```

P2-E 后优先方式：

- 删除该类；
- 新增 `StateDeltaExtractionPromptVariablesFactory`；
- `LlmStateDeltaExtractor` 直接调用 `PromptRenderer`。

最终语义：

```text
StateDeltaExtractionCommand
-> StateDeltaExtractionPromptVariablesFactory
-> PromptRenderer.render(STATE_DELTA_EXTRACT)
-> PromptRenderedMessage list
-> LlmGateway.generate with StructuredLlmOutputContract
-> Provider adapter converts contract to provider-native structured output format
-> StateDeltaExtractionOutputParser
```

---

### 15.3 `ConversationSummaryExtractionPromptBuilder`

当前问题：

```text
PROMPT_TEMPLATE_KEY = "summary_extract_inline"
PROMPT_TEMPLATE_VERSION = "p2-d-3-v1"
```

P2-E 后优先方式：

- 删除该类；
- 新增 `ConversationSummaryExtractionPromptVariablesFactory`；
- `LlmConversationSummaryExtractor` 直接调用 `PromptRenderer`。

最终语义：

```text
ConversationSummaryExtractionCommand
-> ConversationSummaryExtractionPromptVariablesFactory
-> PromptRenderer.render(CONVERSATION_SUMMARY_EXTRACT)
-> PromptRenderedMessage list
-> LlmGateway.generate with StructuredLlmOutputContract
-> Provider adapter converts contract to provider-native structured output format
-> ConversationSummaryExtractionOutputParser
```

---

### 15.4 Parser 迁移

`StateDeltaExtractionOutputParser` 与 `ConversationSummaryExtractionOutputParser` 必须：

1. 删除内部独立维护的 allowed fields。
2. 删除内部独立维护的 forbidden fields。
3. 删除内部独立维护的 nested fields。
4. 接收 `StructuredLlmOutputContract`。
5. 从 provider adapter 获得 `NormalizedStructuredLlmOutput`。
6. 调用 `StructuredLlmOutputContractGuard` 完成 JSON Schema 校验。
7. 校验通过后再映射业务对象。
8. degraded result 仍由 parser 负责返回。

---

### 15.5 Internal task audit 迁移

`InternalMemoryTaskService` 必须：

1. 删除对旧 prompt builder 常量的依赖。
2. 使用 `InternalTaskPromptResolver` 解析 task type 对应的 prompt key / version。
3. `STATE_EXTRACT` audit key 改为 `SystemPromptKey.STATE_DELTA_EXTRACT.value()`。
4. `SUMMARY_EXTRACT` audit key 改为 `SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT.value()`。
5. `EVIDENCE_ENRICH` audit key 使用 `InternalTaskDefinitionKey.EVIDENCE_BIND_DETERMINISTIC.value()`。
6. `requestJson` 补充 prompt audit metadata，但不进入用户响应。

---

## 16. 配置、Provider 与 Tool 边界

### 16.1 配置设计

P2-E 新增 application.yml 配置：

```yaml
vi-agent:
  prompt:
    fail-fast: true
    active-versions:
      runtime_instruction_render: p2-e-v1
      session_state_render: p2-e-v1
      conversation_summary_render: p2-e-v1
      state_delta_extract: p2-e-v1
      conversation_summary_extract: p2-e-v1
```

不在 yml 中存 prompt 正文，不在 yml 中存 schema 正文。

---

### 16.2 Provider 选择策略

P2-E 不修改现有 provider 选择策略。

State extraction 与 summary extraction 仍通过当前 runtime 已有 `LlmGateway` 调用。

但是 `LlmGateway` / provider adapter 需要能接收 `StructuredLlmOutputContract`，并根据 provider capability 选择 structured output mode。

---

### 16.3 Tool 协议边界

P2-E 不新增业务 Tool Runtime 能力。

DeepSeek strict tool call 只作为 provider-native structured output channel：

```text
schemaJson -> function.parameters -> strict tool call arguments
```

它不是业务工具调用，不进入 P3 Tool Runtime，不执行外部工具。

---

## 17. 幂等、并发与状态机

### 17.1 幂等策略

P2-E 不改变现有 request 幂等策略。

---

### 17.2 并发策略

`SystemPromptRegistry` 是应用启动时构建的只读快照，天然线程安全。

`PromptRenderer` 必须是无状态组件，天然线程安全。

`StructuredLlmOutputContractGuard` 必须是无状态组件，天然线程安全。

请求运行期间不查 DB，不修改 registry。

---

### 17.3 状态机

P2-E 不新增业务状态机。

Internal task 状态机沿用现有：

```text
PENDING -> RUNNING -> SUCCEEDED / FAILED / DEGRADED / SKIPPED
```

---

## 18. 可测试性与测试计划

### 18.1 必测场景

1. `SystemPromptCatalogRepository` 能按 key/version 加载模板。
2. `SystemPromptCatalogRepository` 能加载模板绑定的 contract。
3. YML 指向不存在版本时启动失败。
4. YML 指向 disabled 版本时启动失败。
5. render 类 prompt 绑定 contract 时启动失败。
6. extract 类 prompt 未绑定 contract 时启动失败。
7. JSON Schema 非法时启动失败。
8. schema hash 与 schema_json 不一致时启动失败。
9. `SystemPromptRegistry` 能按 key 查询 active template。
10. `SystemPromptRegistry` 能按 purpose 查询 active template。
11. `PromptRenderer` 能渲染 TEXT template。
12. `PromptRenderer` 能渲染 CHAT_MESSAGES template。
13. `PromptRenderer` 在 required variable 缺失时失败。
14. `PromptRenderer` 在 request 传入未声明变量时失败。
15. `PromptRenderer` 在 template 存在未声明 placeholder 时失败。
16. Provider adapter 能把 provider response 归一化为 `NormalizedStructuredLlmOutput`。
17. `StructuredLlmOutputContractGuard` 能用 JSON Schema 拒绝 schema 外字段。
18. `StructuredLlmOutputContractGuard` 能拒绝旧字段如 `upsert/remove/locale/timezone/phaseKey`。
19. `StateDeltaExtractionOutputParser` 使用 `STATE_DELTA_OUTPUT` contract。
20. `ConversationSummaryExtractionOutputParser` 使用 `CONVERSATION_SUMMARY_OUTPUT` contract。
21. parser 不再维护独立 allowlist。
22. provider adapter 能将 DeepSeek structured contract 转换为 strict tool call 请求格式。
23. tool call structured output 不进入业务 Tool Runtime。
24. Internal task audit 写入 P2-E 新 prompt key / version / contract key / contract version / schema hash。
25. 旧 key 防回退测试覆盖 `state_extract_inline`、`summary_extract_inline`、`runtime-instruction` 等旧 key 不再出现在新断言中。
26. 所有 enum 都符合 `CODE("value", "中文说明")` 风格。

---

### 18.2 测试分层

#### 单元测试

建议新增：

```text
vi-agent-core-model/src/test/java/.../prompt/SystemPromptTemplateTest.java
vi-agent-core-model/src/test/java/.../prompt/StructuredLlmOutputContractTest.java
vi-agent-core-model/src/test/java/.../prompt/SystemPromptKeyTest.java
vi-agent-core-model/src/test/java/.../prompt/StructuredLlmOutputContractKeyTest.java
vi-agent-core-runtime/src/test/java/.../prompt/SystemPromptRegistryFactoryTest.java
vi-agent-core-runtime/src/test/java/.../prompt/PromptRendererTest.java
vi-agent-core-runtime/src/test/java/.../prompt/NormalizedStructuredLlmOutputTest.java
vi-agent-core-runtime/src/test/java/.../prompt/StructuredLlmOutputContractGuardTest.java
vi-agent-core-runtime/src/test/java/.../prompt/InternalTaskPromptResolverTest.java
vi-agent-core-infra/src/test/java/.../prompt/MySqlSystemPromptCatalogRepositoryTest.java
```

#### 迁移测试

建议新增或更新：

```text
StateDeltaExtractionOutputParserTest
ConversationSummaryExtractionOutputParserTest
LlmStateDeltaExtractorTest
LlmConversationSummaryExtractorTest
InternalMemoryTaskServiceTest
ContextBlockFactoryTest
SessionMemoryCoordinatorTest
```

#### 合同测试

建议新增：

```text
PromptContractParserAlignmentTest
SystemPromptKeyContractTest
PromptNoLegacyInlineKeyContractTest
PromptEnumStyleContractTest
StructuredOutputSchemaClosedContractTest
ProviderStructuredOutputAdapterContractTest
```

---

### 18.3 与旧测试冲突处理

若旧测试仍断言以下内容：

```text
state_extract_inline
summary_extract_inline
runtime-instruction
session-state
conversation-summary
p2-c-v1
p2-d-2-v1
p2-d-3-v1
```

处理原则：

1. 如果旧测试表达的是过时语义，直接更新或删除。
2. 不允许为了旧测试保留旧 key。
3. 不允许双写新旧 key。
4. 不允许在代码中保留兼容旧 key 的分支。
5. 新测试必须断言 P2-E 新 key / version。

---

## 19. 分阶段实施计划

详细开发计划以 `plan.md` 为准。

本文档只定义设计级阶段。

| 阶段 | 目标 | 改动范围 | 完成标准 | 回滚点 |
|---|---|---|---|---|
| P2-E-0 | 补齐阶段文档 | `README.md`、`design.md`、`plan.md`、`test.md`、`prompts.md`、`closure.md` | 文档结构完整，职责边界清晰 | 文档回滚 |
| P2-E-1 | 新增 MySQL catalog schema | infra / db migration | 两张表和 seed 数据完成 | 回滚迁移脚本 |
| P2-E-2 | 新增 model.prompt / model.port 契约 | `vi-agent-core-model` | enum / template / contract / port 测试通过 | 删除新增 model.prompt |
| P2-E-3 | 新增 infra.prompt repository | `vi-agent-core-infra` | repository 能加载 prompt template / contract | 回退 infra.prompt |
| P2-E-4 | 新增 runtime.prompt registry / renderer | `vi-agent-core-runtime` | registry / renderer / startup validation 测试通过 | 回退 runtime.prompt |
| P2-E-5 | 新增 structured output guard / provider adapter | runtime / infra provider | schema validation 与 DeepSeek strict tool call adapter 测试通过 | 回退 adapter 改动 |
| P2-E-6 | 迁移 context render prompt | `ContextBlockFactory` | block key/version 全部切到 P2-E | 回退 ContextBlockFactory 改动 |
| P2-E-7 | 迁移 StateDelta extraction prompt/parser | memory extract | state extraction 使用 PromptRenderer + StructuredLlmOutputContract | 回退 state extraction 改动 |
| P2-E-8 | 迁移 Summary extraction prompt/parser | memory extract / SessionMemoryCoordinator | summary extraction 使用 PromptRenderer + StructuredLlmOutputContract | 回退 summary extraction 改动 |
| P2-E-9 | internal task audit 收口 | `InternalMemoryTaskService` | audit key/version/contract/schemaHash 与 registry 一致 | 回退 audit resolver 改动 |
| P2-E-10 | 测试与防回退收口 | test 全量 | contract/parser/key/schema/provider 防回退测试通过 | 回退测试改动 |

---

## 20. 风险与回滚

### 20.1 风险清单

| 风险 | 影响 | 概率 | 应对方案 | Owner |
|---|---|---|---|---|
| DB catalog 与 yml selector 不一致 | 应用启动失败 | 中 | fail-fast，启动时完整校验 | Victor / Codex |
| schema 与 parser 仍然双轨维护 | 后续字段漂移 | 中 | parser 必须依赖 `StructuredLlmOutputContract.schemaJson` | Victor / Codex |
| prompt key 新旧并存 | audit 混乱 | 中 | 删除旧 key 常量，新增 no legacy key test | Victor / Codex |
| enum 和 DB string value 不一致 | 加载失败或审计错误 | 中 | 启动时校验 DB value 是否能映射 enum | Victor / Codex |
| DeepSeek strict tool call 被误当成业务 tool | P2/P3 边界污染 | 中 | 文档和测试明确它只是 structured output channel | Victor / Codex |
| prompt registry 请求期查 DB | 性能与稳定性下降 | 中 | 只允许启动加载，运行期只读 | Victor / Codex |
| PromptRenderer 过度侵入业务 | runtime.prompt 边界污染 | 中 | 复杂对象转文本逻辑留在 variable factory | Victor / Codex |
| post-turn extraction render / parse 失败影响主聊天 | 用户响应失败 | 低 | post-turn internal task 失败继续 degraded，不影响主 response | Victor / Codex |

---

### 20.2 回滚策略

- 协议层回滚：N/A，P2-E 不修改对外协议。
- 数据层回滚：回滚 prompt catalog 迁移脚本与 seed 数据。
- 主链路回滚：可回退 `ContextBlockFactory` 对 `PromptRenderer` 的调用。
- internal task 回滚：可回退 state / summary extractor 对 `PromptRenderer` 的调用。
- provider adapter 回滚：可回退到 JSON_OBJECT 模式，但本地 schema 校验不可移除。
- 测试回滚：禁止仅为了通过旧测试回滚到旧语义；若设计已确认，应更新测试。

---

## 21. 验收标准

### 21.1 功能验收

- MySQL 中存在 `system_prompt_template` 与 `structured_llm_output_contract` 表。
- seed 数据包含 P2-E 需要的 5 个 prompt template 与 2 个 structured output contract。
- `application.yml` 能选择各 prompt key 当前启用版本。
- 应用启动时能加载 yml 指定版本并构建只读 registry。
- `RUNTIME_INSTRUCTION_RENDER`、`SESSION_STATE_RENDER`、`CONVERSATION_SUMMARY_RENDER` 能通过 `PromptRenderer` 渲染。
- `STATE_DELTA_EXTRACT` 能通过 `PromptRenderer` 渲染为 `SYSTEM + USER` message。
- `CONVERSATION_SUMMARY_EXTRACT` 能通过 `PromptRenderer` 渲染为 `SYSTEM + USER` message。
- `StateDeltaExtractionOutputParser` 使用 `STATE_DELTA_OUTPUT` contract 校验字段。
- `ConversationSummaryExtractionOutputParser` 使用 `CONVERSATION_SUMMARY_OUTPUT` contract 校验字段。
- DeepSeek provider adapter 能把 `schemaJson` 包装成 strict tool call 请求格式。
- Internal task audit 写入 P2-E 新 key / version / contract key / contract version / schema hash。
- Summary 保存写入 P2-E 新 summary template key / version。
- Context block 写入 P2-E 新 key / version。

---

### 21.2 架构与分层验收

- 新增 prompt 契约只出现在 `model.prompt`。
- 新增 prompt repository port 只出现在 `model.port`。
- MySQL 实现只出现在 `infra.prompt`。
- 新增 prompt runtime 逻辑只出现在 `runtime.prompt` 或相关 runtime 调用方。
- `runtime` 不依赖 `infra`。
- `model` 不依赖 `runtime`。
- `PromptRenderer` 不调用 LLM。
- `SystemPromptRegistry` 不读 DB。
- `StructuredLlmOutputContractGuard` 不构建业务对象。
- parser 不再维护独立 allowlist。
- business command -> variables 的转换不放入 `model.prompt`。
- 代码内部 prompt key 使用 enum，不使用自由字符串常量类。

---

### 21.3 协议与数据验收

- `/chat` request / response 不变。
- `/chat/stream` event 不变。
- Redis schema 不变。
- 旧 audit 数据不迁移。
- 新 audit 数据使用 P2-E key / version / contract / schema hash。
- prompt metadata 不出现在用户响应。
- 请求运行期间不查 prompt catalog DB。

---

### 21.4 测试验收

必须通过：

```text
./mvnw -q -pl vi-agent-core-model -am test
./mvnw -q -pl vi-agent-core-runtime -am test
./mvnw -q -pl vi-agent-core-infra -am test
./mvnw -q test
```

如果本地 Windows 环境使用 PowerShell，可使用：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-model -am test
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
.\mvnw.cmd -q -pl vi-agent-core-infra -am test
.\mvnw.cmd -q test
```

---

## 22. 最终交付清单

P2-E 完成后应交付：

1. `model.prompt` 新增对象。
2. `model.port.SystemPromptCatalogRepository`。
3. `infra.prompt.MySqlSystemPromptCatalogRepository`。
4. `system_prompt_template` 表。
5. `structured_llm_output_contract` 表。
6. P2-E prompt catalog seed 数据。
7. `application.yml` active version selector。
8. `SystemPromptKey` enum。
9. `StructuredLlmOutputContractKey` enum。
10. `StructuredLlmOutputMode` enum。
12. `SystemPromptTemplate`。
13. `StructuredLlmOutputContract`。
14. `SystemPromptRegistry`。
15. `PromptRenderer`。
16. `NormalizedStructuredLlmOutput`。
17. `StructuredLlmOutputContractGuard`。
18. Provider structured output adapter。
19. Internal task prompt resolver。
20. Context block prompt key / version 迁移。
21. StateDelta extraction prompt 迁移。
22. ConversationSummary extraction prompt 迁移。
23. Parser contract guard 迁移。
24. Internal task audit key / version / contract / schemaHash 迁移。
25. Summary template key / version 迁移。
26. 单元测试。
27. 合同测试。
28. 防回退测试。
29. 阶段收口记录。

---

## 23. 未来通用 Prompt 平台边界

P2-E 不做通用 Prompt 平台，但本阶段已经铺好未来平台的数据基础。

未来通用 Prompt 平台如果实现，应新增独立 `GenericPromptTemplate` 具体类型，并复用 `PromptTemplate` / `AbstractPromptTemplate` 的公共能力，但不得与 `SystemPromptTemplate` 共用一个带 type 字段的大对象。

未来通用 Prompt 平台可能包含：

- `GenericPromptTemplate`；
- String 类型外部 prompt key；
- prompt UI；
- prompt version lifecycle；
- prompt approval workflow；
- prompt experiment / A-B test；
- prompt hot reload；
- prompt usage analytics；
- prompt 回放与 diff；
- prompt 灰度发布。

这些能力不进入 P2-E。

P2-E 当前只做：

```text
Enterprise Internal System Prompt Governance
```

因此当前选择：

```text
enum key
+ MySQL catalog
+ yml active version selector
+ startup fail-fast validation
+ runtime readonly registry
+ fixed SystemPromptTemplate
+ JSON Schema based StructuredLlmOutputContract
+ provider-native structured output adapter
+ business bean 作为最终解析对象
+ contract guard 作为 bean 映射前的安全门
+ parser 不再维护独立 allowlist
+ audit 记录 key/version/contract/schemaHash metadata
```

---

## 24. 给实现代理的执行指令模板

正式 Codex prompt 必须记录到：

```text
execution-phase/phase-P2-E-prompt-governance/prompts.md
```

本文档不直接承载最终 Codex prompt。

实现代理执行前必须按统一阅读顺序阅读：

```text
AGENTS.md
PROJECT_PLAN.md
ARCHITECTURE.md
CODE_REVIEW.md
execution-phase/README.md
execution-phase/phase-P2-E-prompt-governance/README.md
execution-phase/phase-P2-E-prompt-governance/design.md
execution-phase/phase-P2-E-prompt-governance/plan.md
execution-phase/phase-P2-E-prompt-governance/test.md
execution-phase/phase-P2-context-memory/system-design-P2-v5.md
execution-phase/phase-P2-context-memory/system-design-P2-implementation-plan-v3.md
相关模块 AGENTS.md
相关源码与测试
```

执行要求：

- 严格按 P2-E 范围执行。
- 不推进 P2.5。
- 不推进 P3。
- 不新增 Prompt UI。
- 不做通用 prompt 平台。
- 不做热加载、A/B、审批流。
- 不修改主 chat / stream 协议。
- 不改变 P2-D state / summary / evidence 主链路语义。
- 不保留旧 prompt key 兼容逻辑。
- 不让 runtime 依赖 infra。
- 不让请求期每次查 DB 读取 prompt。
- 若旧测试与新设计冲突，更新或删除旧测试。
- 最终输出修改清单、测试清单、未做项、验收命令。

---

## 25. 文档维护规则

- 本文档是 P2-E 新增内部系统 Prompt Governance 对象的强契约源。
- 本文档只维护 P2-E 设计，不回写 P2-A 到 P2-D 历史设计。
- P2-A 到 P2-D 已有对象仍以历史强契约为准。
- 如果 P2-E 实现过程中发现本文档与历史强契约冲突，必须先停止并回到设计层确认。
- 不允许 Codex 自行修改本文档来迁就代码。
- 不允许在实现阶段扩展本文档未定义的新字段。
- 若确需扩展，必须先更新本文档，再更新 `plan.md` 和 `test.md`，最后再执行代码修改。
- 未来通用 Prompt 平台必须另开阶段，不允许在 P2-E 中偷做。
