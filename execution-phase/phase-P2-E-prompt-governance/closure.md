# P2-E Prompt Engineering Governance 阶段收口记录

## 1. 文档元信息

- 阶段：P2-E
- 阶段名称：Prompt Engineering Governance
- 状态：Completed
- 收口日期：2026-04-29
- 设计基线：`execution-phase/phase-P2-E-prompt-governance/design.md`
- 执行计划：`execution-phase/phase-P2-E-prompt-governance/plan.md`
- 测试基线：`execution-phase/phase-P2-E-prompt-governance/test.md`

本文档记录 P2-E 阶段最终完成范围、验收命令、未做项、后续风险与阶段收口结论。P2-E 的详细设计、执行拆分与专项测试标准仍以对应阶段文档为准，本文档不新增设计强契约。

---

## 2. 阶段目标回顾

P2-E 的目标是将分散在 context render、state extraction、summary extraction、parser allowlist、internal task audit 中的 prompt-like 能力，收口为系统级 Prompt Governance。

本阶段通过 resource prompt catalog、manifest、registry、renderer、structured output contract、provider structured output adapter、parser alignment 与 audit metadata 收口，使系统级 prompt 具备启动期校验、运行期只读、单次安全渲染、结构化输出校验和可审计复现能力。

---

## 3. 完成范围

### 3.1 P2-E1：Resource Catalog + Manifest + Renderer

- 新增 `model.prompt`、`model.memory`、`model.port` 相关契约对象。
- 新增 `SystemPromptKey`、`PromptPurpose`、`PromptRenderOutputType`、`PromptInputVariable`、`StructuredLlmOutputContract` 等对象。
- `InternalTaskDefinitionKey` 放入 `model.memory`，其中 `EVIDENCE_BIND_DETERMINISTIC` 对应 `evidence_bind_deterministic`。
- 新增 app classpath 正式 prompt catalog：`vi-agent-core-app/src/main/resources/prompt-catalog/system/`。
- 新增 `infra.prompt` resource repository，负责 classpath manifest / prompt / contract 读取、组装、hash 计算与启动校验。
- 新增 `runtime.prompt` 的 `SystemPromptRegistry`、`PromptRenderer`、`PromptRenderResult` 等运行期能力。
- 新增 app 层 `SystemPromptProperties` 与 `PromptGovernanceConfiguration` 装配。
- 完成 P2-E1 验收修复：正式 contract 字段校准、render prompt 变量名校准、TEXT prompt `UNTRUSTED_DATA` 边界校验、`PromptRenderResult` / `SystemPromptRegistry` accessor 对齐。

### 3.2 P2-E2：Structured Output Contract + Schema Guard + Parser Alignment

- root `pom.xml` 在 `dependencyManagement` 中统一管理 `com.networknt:json-schema-validator` 2.x。
- `vi-agent-core-runtime` 显式依赖 `json-schema-validator`，不在子模块声明版本。
- 新增 `StructuredLlmOutputContractGuard` 与 `StructuredLlmOutputContractValidationResult`。
- 完善 `state_delta_extract` 与 `conversation_summary_extract` 的 `contract.json` closed schema。
- 保留 `x-structuredOutputContractKey`、`x-outputTarget`、`x-description` 扩展字段，并保证原始 `schemaJson` 完整保存。
- `StateDeltaExtractionOutputParser` 与 `ConversationSummaryExtractionOutputParser` 删除独立 allowlist / forbidden / nested allowlist，改为走 `StructuredLlmOutputContractGuard`。
- 修复 summary `skipped` / blank summary 语义，避免非法 summary 被自动转为 skipped。
- 修复 StateDelta append 空对象、空白正文和空白 `taskGoalOverride` 语义，非法输出进入 degraded。

### 3.3 P2-E3：Provider Structured Output Adapter + Failure Policy

- `ModelRequest` 支持携带 `StructuredLlmOutputContract`、preferred `StructuredLlmOutputMode` 与 structured output function name。
- `ModelResponse` 支持携带 `StructuredOutputChannelResult`。
- 新增 `ProviderStructuredSchemaCompiler`，生成 provider schema view，并剥离 `x-*` 扩展字段。
- 新增 `ProviderStructuredOutputCapabilityValidator`，在请求前选择本次 structured output mode。
- 新增 `StructuredOutputRequestAdapter`，支持 strict tool call / JSON object 等 provider-native 请求片段。
- 新增 `StructuredOutputResponseExtractor`，归一化 tool call arguments 或 JSON object content。
- `OpenAICompatibleChatProvider` 接入 structured output selection / request adapter / response extractor。
- DeepSeek strict capability 收口：默认不启用 strict；仅 `strictToolCallEnabled=true` 且 `baseUrl` 包含 `/beta` 时可选择 `STRICT_TOOL_CALL`。
- strict-compatible schema 判断收紧，无法确认 provider strict 支持的 schema fail-safe fallback 到请求前可用 mode。
- selection 只计算一次，selected mode 失败后不静默降级重发，P2-E `retryCount` 固定为 0。
- structured output function 不进入业务 Tool Runtime，也不注册为业务 tool。

### 3.4 P2-E4：旧代码迁移 + Audit 收口 + 测试补齐

- `ContextBlockFactory` 使用 `PromptRenderer` 渲染 `runtime_instruction_render`、`session_state_render`、`conversation_summary_render`。
- context block `promptTemplateKey` 写入 P2-E `SystemPromptKey` value。
- context block `promptTemplateVersion` 写入 `catalogRevision`。
- 删除 `StateDeltaExtractionPromptBuilder`。
- 删除 `ConversationSummaryExtractionPromptBuilder`。
- `LlmStateDeltaExtractor` 使用 `PromptRenderer.render(STATE_DELTA_EXTRACT)`，internal worker message 为 `SYSTEM + USER`。
- `LlmConversationSummaryExtractor` 使用 `PromptRenderer.render(CONVERSATION_SUMMARY_EXTRACT)`，provider / model 信息仍从 `ModelResponse` 回填。
- internal LLM worker 的 `ModelRequest` 携带 structured output contract 与 function name。
- provider channel failure、schema failure、parser failure 均进入 degraded，不 fallback 到旧 raw content 解析。
- degraded 不写 durable state，不保存错误 durable summary；`skipped=true` 不保存无意义 summary。
- 新增 `InternalTaskPromptResolver`，收口 audit key：
  - `STATE_EXTRACT -> state_delta_extract`
  - `SUMMARY_EXTRACT -> conversation_summary_extract`
  - `EVIDENCE_ENRICH -> evidence_bind_deterministic`
- `promptTemplateVersion` 与 `summaryTemplateVersion` 写入 `catalogRevision`。
- `requestJson.promptAudit` 写入 promptKey、contractKey、content hashes、catalogRevision、actual mode、retryCount、failureReason。
- 新主链路不再写 `state_extract_inline`、`summary_extract_inline`、`runtime-instruction`、`session-state`、`conversation-summary`、`p2-c-v1`、`p2-d-2-v1`、`p2-d-3-v1`、`p2-d-4-v1`。

---

## 4. 架构交付物

- `model.prompt` 契约：系统 prompt key、purpose、render output type、变量声明、模板对象、render metadata、structured output contract。
- `model.llm` structured output result：`NormalizedStructuredLlmOutput` 与 `StructuredOutputChannelResult`。
- `model.port.SystemPromptCatalogRepository`。
- `model.memory.InternalTaskDefinitionKey`。
- app resources prompt catalog：`vi-agent-core-app/src/main/resources/prompt-catalog/system/`。
- `infra.prompt` resource repository：manifest / template / contract 加载、校验、hash 计算。
- `runtime.prompt` registry / renderer / guard：只读 registry、安全 renderer、JSON Schema guard。
- provider structured output adapter：schema compiler、capability validator、request adapter、response extractor。
- parser contract alignment：StateDelta / Summary parser 使用 contract guard，不维护独立字段白名单。
- context render migration：context block 使用 PromptRenderer 与 P2-E key / catalogRevision。
- state / summary extraction migration：internal LLM worker 使用 PromptRenderer + structured output channel。
- internal task audit migration：audit key、template version、promptAudit metadata 收口到 P2-E。

---

## 5. 关键设计决策

- system prompt catalog 使用 app classpath resources，不使用 MySQL。
- `SystemPromptKey` 直接定位 prompt catalog 目录。
- 请求运行期只读 `SystemPromptRegistry`，不重复扫描资源目录。
- `PromptRenderer` 只做单次替换，不递归替换变量值中的 `{{xxx}}`。
- `UNTRUSTED_DATA` 必须进入固定 `BEGIN_UNTRUSTED_*` / `END_UNTRUSTED_*` data block。
- structured output 使用 JSON Schema closed schema。
- parser 不再维护独立 allowlist / forbidden / nested allowlist。
- provider strict tool call 只是 structured output channel，不是业务 Tool Runtime。
- DeepSeek strict 需要显式 `strictToolCallEnabled` 与 beta endpoint。
- `promptTemplateVersion` / `summaryTemplateVersion` 写入 `catalogRevision`。
- content hash 只进入 audit metadata，不替代 `catalogRevision`。
- P2-E 不做 LLM repair、自动重试、同 mode retry，`retryCount` 固定为 0。

---

## 6. 测试与验收

P2-E 最终验收记录包含以下命令，均登记为已通过：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-model -am test
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
.\mvnw.cmd -q -pl vi-agent-core-infra -am test
.\mvnw.cmd -q -pl vi-agent-core-app -am test
.\mvnw.cmd -q test
```

验收覆盖范围包括：

- model 契约测试。
- prompt catalog resource / manifest / hash / safety boundary 测试。
- registry / renderer 测试。
- JSON Schema guard / closed schema 测试。
- parser alignment 与防回退测试。
- provider structured output adapter / no-silent-downgrade 测试。
- context render 迁移测试。
- state / summary extraction 迁移测试。
- internal task audit 与 summary save key/version 测试。
- `/chat` 与 `/chat/stream` 主协议防污染测试。

---

## 7. 明确未做项

P2-E 明确未做以下事项：

- 未做 Prompt UI。
- 未做 MySQL prompt center。
- 未做 prompt hot reload。
- 未做 A/B prompt。
- 未做运行时多版本 prompt center。
- 未做 Graph Workflow。
- 未做 SubAgent / Agent Prompt Taxonomy。
- 未做业务 Tool Runtime 扩展。
- 未新增 MySQL 表。
- 未新增 Redis key。
- 未修改 `/chat` request / response DTO。
- 未修改 `/chat/stream` SSE event DTO。
- 未做 LLM repair / 自动重试 / 同 mode retry / 激进 JSON repair。
- 未推进 P2.5 / P3。

---

## 8. 后续风险与建议

- `SessionMemoryCoordinator` 仍存在 `@Resource` field injection 风格，建议 P2.5 统一治理。
- `JSON_SCHEMA_RESPONSE_FORMAT` 对不同 provider 的 strict schema subset 仍需后续按 provider 细化。
- P2-E 已引入较多框架级基础设施，建议下一阶段先做 P2.5 架构 / 技术债 / 契约治理，不建议直接跳 P3。
- 后续如果引入业务 Tool Runtime，要继续隔离 internal structured output function 和 business tool。
- 后续如果进入 Graph Workflow，应复用 Prompt Governance 作为 node / internal worker prompt 基础，不要重新散落 prompt 字符串。

---

## 9. P2-E 总体验收记录

### 9.1 完成范围

P2-E1 / P2-E2 / P2-E3 / P2-E4 全部完成并通过验收。Prompt Governance 已完成从资源 catalog、运行期 registry / renderer、structured output contract、provider adapter、parser alignment 到旧主链路迁移和 audit 收口的闭环。

### 9.2 测试命令

```powershell
.\mvnw.cmd -q -pl vi-agent-core-model -am test
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
.\mvnw.cmd -q -pl vi-agent-core-infra -am test
.\mvnw.cmd -q -pl vi-agent-core-app -am test
.\mvnw.cmd -q test
```

### 9.3 未做项

未做项以本文档第 7 节为准，尤其包括未推进 P2.5 / P3、未新增 DB / Redis、未修改主聊天协议、未做 Prompt UI / MySQL prompt center / hot reload / A-B、未做业务 Tool Runtime 扩展。

### 9.4 后续风险

后续风险以本文档第 8 节为准，重点是 P2.5 架构治理、provider schema subset 细化、field injection 技术债、future Tool Runtime 隔离风险与 Graph Workflow 对 Prompt Governance 的复用边界。

### 9.5 结论

P2-E Completed。允许进入 P2.5 准备；不建议直接跳 P3。

---

## 10. 收口结论

P2-E 已完成，可以收口。

下一步建议进入 P2.5 架构 / 技术债 / 契约治理，而不是直接进入 P3。
