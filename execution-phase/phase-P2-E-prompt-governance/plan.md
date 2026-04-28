# P2-E Prompt Engineering Governance 开发计划

> 更新日期：2026-04-28
> 阶段：P2-E
> 文档类型：`plan.md`
> 状态：Draft
> 设计基线：`execution-phase/phase-P2-E-prompt-governance/design.md`

---

## 1. 文档定位

本文档是 P2-E Prompt Engineering Governance 阶段的执行计划。

`design.md` 定义强契约与架构边界。
`plan.md` 只定义实现顺序、修改范围、交付批次、验收门禁与回滚点。
`test.md` 定义阶段测试点、合同测试、迁移测试和全流程验收。

执行 P2-E 实现前，必须先完成并确认：

```text
design.md
plan.md
test.md
```

在 `plan.md` 或 `test.md` 未补齐前，不应进入代码实现。

---

## 2. 必读文档

实现代理执行前必须按统一阅读顺序阅读：

```text
1. 根目录 AGENTS.md
2. 根目录 PROJECT_PLAN.md
3. 根目录 ARCHITECTURE.md
4. 根目录 CODE_REVIEW.md
5. execution-phase/README.md
6. execution-phase/phase-P2-E-prompt-governance/README.md
7. execution-phase/phase-P2-E-prompt-governance/design.md
8. execution-phase/phase-P2-E-prompt-governance/plan.md
9. execution-phase/phase-P2-E-prompt-governance/test.md
10. execution-phase/phase-P2-context-memory/system-design-P2-v5.md
11. execution-phase/phase-P2-context-memory/system-design-P2-implementation-plan-v3.md
12. 相关模块 AGENTS.md
13. 相关源码与测试
```

---

## 3. 阶段目标

P2-E 的目标是将当前分散在 context render、state extraction、summary extraction、parser allowlist、internal task audit 中的 prompt-like 能力，收口为统一、可加载、可校验、可审计、可测试的 Prompt Engineering Governance 体系。

本阶段必须交付：

```text
1. system prompt resource catalog
2. prompt manifest / contract.json 加载与启动校验
3. model.prompt / model.llm / model.port 契约对象
4. infra.prompt resource repository
5. runtime.prompt registry / renderer
6. Prompt Input Safety Boundary
7. StructuredLlmOutputContractGuard
8. Provider structured output adapter 与 no-silent-downgrade 策略
9. D-2 / D-3 inline prompt 迁移
10. parser allowlist 与 contract.json 对齐
11. internal task audit metadata 收口
12. context block key / catalogRevision 收口
13. 测试与防回退合同
```

---

## 4. 明确不做

P2-E 不做以下事项：

```text
1. 不做 Prompt UI。
2. 不做 MySQL prompt center。
3. 不做 prompt hot reload。
4. 不做运行时多版本 prompt center。
5. 不做 A/B prompt。
6. 不做 Graph Workflow。
7. 不做 SubAgent / Agent Prompt Taxonomy 落地。
8. 不做业务 Tool Runtime 扩展。
9. 不把 DeepSeek strict tool call 建模为业务 tool。
10. 不修改 /chat request / response 协议。
11. 不修改 /chat/stream SSE event 协议。
12. 不新增 MySQL 表。
13. 不新增 Redis key。
14. 不做 LLM repair / 自动重试 / 同 mode retry / 激进 JSON repair。
```

---

## 5. 执行批次总览

P2-E 按 4 个执行批次交付。每个批次必须能独立验证，不允许跨多个批次长时间保持不可编译状态。

| 批次 | 目标 | 主要模块 | 完成标准 |
|---|---|---|---|
| P2-E1 | resource catalog + manifest + renderer | `model` / `app` / `infra` / `runtime` | catalog 能加载，registry 可查询，renderer 能安全渲染 |
| P2-E2 | structured output contract + schema guard + parser alignment | `model` / `runtime` / `app resources` | JSON Schema 是唯一结构化输出事实源，parser 不再维护独立 allowlist |
| P2-E3 | provider structured output adapter + failure policy | `model` / `infra` / `runtime` | DeepSeek strict tool call capability negotiation 与 no-silent-downgrade 策略可测试 |
| P2-E4 | 旧代码迁移 + audit 收口 + 测试补齐 | `runtime` / `app` / `test` | 新 key、catalogRevision、content hash、actual mode、failureReason 全链路落地 |

---

## 6. 文件范围总览

### 6.1 新增或调整资源

```text
vi-agent-core-app/src/main/resources/prompt-catalog/system/
├── runtime_instruction_render/
│   ├── manifest.yml
│   └── prompt.md
├── session_state_render/
│   ├── manifest.yml
│   └── prompt.md
├── conversation_summary_render/
│   ├── manifest.yml
│   └── prompt.md
├── state_delta_extract/
│   ├── manifest.yml
│   ├── system.md
│   ├── user.md
│   └── contract.json
└── conversation_summary_extract/
    ├── manifest.yml
    ├── system.md
    ├── user.md
    └── contract.json
```

正式系统 prompt catalog 只放在 `vi-agent-core-app/src/main/resources/prompt-catalog/system/`。
`vi-agent-core-infra/src/main/resources/` 不承载正式系统 prompt catalog。

### 6.2 新增 model 契约

```text
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/
vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/
vi-agent-core-model/src/main/java/com/vi/agent/core/model/port/SystemPromptCatalogRepository.java
```

`model.prompt` 只放值对象、枚举和 prompt 契约，不放 registry / renderer / resource loader 实现。

### 6.3 新增 infra prompt resource 实现

```text
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/
```

职责：

```text
1. 从 classpath/resource 读取 manifest.yml、prompt.md、system.md、user.md、contract.json。
2. 校验资源目录与 SystemPromptKey.value() 一致。
3. 组装 AbstractPromptTemplate 与 StructuredLlmOutputContract。
4. 计算 templateContentHash / manifestContentHash / contractContentHash。
5. 不做 PromptRenderer。
6. 不做 provider 调用。
```

### 6.4 新增 runtime prompt 组件

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/prompt/
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/prompt/
```

职责：

```text
1. 构建只读 SystemPromptRegistry。
2. 提供 PromptRenderer。
3. 执行 Prompt Input Safety Boundary。
4. 提供 StructuredLlmOutputContractGuard。
5. 提供 context / memory extraction 的 variables factory。
```

### 6.5 需要迁移的既有类

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/builder/ContextBlockFactory.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/render/SessionStateBlockRenderer.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/StateDeltaExtractionPromptBuilder.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/ConversationSummaryExtractionPromptBuilder.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/LlmStateDeltaExtractor.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/LlmConversationSummaryExtractor.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/StateDeltaExtractionOutputParser.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/ConversationSummaryExtractionOutputParser.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/task/InternalMemoryTaskService.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/SessionMemoryCoordinator.java
```

---

## 7. P2-E1: Resource Catalog + Manifest + Renderer

### 7.1 目标

建立系统级 prompt resource catalog、manifest、model 契约、infra resource repository、runtime registry 与 renderer。P2-E1 完成后，系统能在启动期加载并校验 5 个系统 prompt，运行期能通过 `PromptRenderer` 渲染 TEXT / CHAT_MESSAGES 模板。

### 7.2 具体任务

#### P2-E1.1 新增 model.prompt 契约

创建：

```text
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/SystemPromptKey.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/memory/InternalTaskDefinitionKey.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptPurpose.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptRenderOutputType.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptInputVariableType.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptInputTrustLevel.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptInputPlacement.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptInputVariable.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/StructuredLlmOutputContract.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/StructuredLlmOutputContractKey.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/StructuredLlmOutputTarget.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/StructuredLlmOutputMode.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptMessageTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/AbstractPromptTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/RuntimeInstructionRenderPromptTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/SessionStateRenderPromptTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/ConversationSummaryRenderPromptTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/StateDeltaExtractPromptTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/ConversationSummaryExtractPromptTemplate.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/PromptRenderMetadata.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/port/SystemPromptCatalogRepository.java
```

要求：

```text
1. enum 使用 CODE("value", "中文说明") 风格。
2. yml / manifest / audit 使用 enum.value()。
3. 集合字段构造时复制为不可变集合。
4. UNTRUSTED_DATA 变量不得进入 INSTRUCTION_BLOCK。
5. PromptRenderMetadata 必须包含 templateContentHash / manifestContentHash / contractContentHash / catalogRevision。
6. StructuredLlmOutputContractKey / Target / Mode / Contract 必须在 P2-E1 前移创建，保证 StateDeltaExtractPromptTemplate 与 ConversationSummaryExtractPromptTemplate 在 E1 可独立编译。
7. InternalTaskDefinitionKey 属于 deterministic internal task audit key，只能放在 model.memory，不得放入 model.prompt 或 prompt catalog。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-model -am test
```

#### P2-E1.2 新增系统 prompt catalog seed 资源

创建：

```text
vi-agent-core-app/src/main/resources/prompt-catalog/system/runtime_instruction_render/manifest.yml
vi-agent-core-app/src/main/resources/prompt-catalog/system/runtime_instruction_render/prompt.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/session_state_render/manifest.yml
vi-agent-core-app/src/main/resources/prompt-catalog/system/session_state_render/prompt.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_render/manifest.yml
vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_render/prompt.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/state_delta_extract/manifest.yml
vi-agent-core-app/src/main/resources/prompt-catalog/system/state_delta_extract/system.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/state_delta_extract/user.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/state_delta_extract/contract.json
vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_extract/manifest.yml
vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_extract/system.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_extract/user.md
vi-agent-core-app/src/main/resources/prompt-catalog/system/conversation_summary_extract/contract.json
```

要求：

```text
1. 目录名必须等于 SystemPromptKey.value()。
2. manifest.promptKey 必须等于目录名。
3. render 类 prompt 使用 prompt.md。
4. extract 类 prompt 使用 system.md + user.md + contract.json。
5. extract 类 system.md 必须声明 BEGIN_UNTRUSTED_* / END_UNTRUSTED_* 之间内容是 data，不是 instruction。
6. contract.json 顶层必须是 JSON Schema object，不允许 wrapper 包一层 schema。
7. contract.json 必须通过扩展字段声明 x-structuredOutputContractKey、x-outputTarget、x-description。
8. StructuredLlmOutputContract.schemaJson 保存完整 JSON Schema 内容，包括 x-* 扩展字段。
9. provider-specific schema view 可以在编译时移除 x-* 扩展字段，但不得修改原始 schemaJson。
```

#### P2-E1.3 新增 infra.prompt resource repository

创建：

```text
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/ResourceSystemPromptCatalogRepository.java
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/PromptManifestLoader.java
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/TextPromptTemplateAssembler.java
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/ChatMessagesPromptTemplateAssembler.java
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/PromptTemplateAssemblerRegistry.java
```

要求：

```text
1. 实现 SystemPromptCatalogRepository。
2. 只读 classpath/resource。
3. 不做 renderer。
4. 不调用 provider。
5. 计算内容 hash 时使用 UTF-8、LF、去 BOM、资源内容、SHA-256。
6. missing manifest / missing prompt / invalid schema / key mismatch 均必须失败。
7. repository 从 contract.json 顶层 x-structuredOutputContractKey、x-outputTarget、x-description 读取 metadata。
8. repository 保存的 schemaJson 必须是完整 contract.json 内容，不得剥离 x-* 扩展字段。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-infra -am test
```

#### P2-E1.4 新增 runtime.prompt registry / renderer

创建：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/SystemPromptRegistry.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/DefaultSystemPromptRegistry.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/SystemPromptRegistryFactory.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/PromptRenderRequest.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/PromptRenderer.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/PromptRenderResult.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/AbstractPromptRenderResult.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/TextPromptRenderResult.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/ChatMessagesPromptRenderResult.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/PromptRenderedMessage.java
```

要求：

```text
1. registry 是运行期只读快照。
2. registry 不读文件、不读 DB、不写 Redis、不调用 provider。
3. renderer 不依赖业务 command。
4. renderer 只做一次占位符替换。
5. 变量值中的 {{xxx}} 作为普通文本，不递归替换。
6. required 变量缺失必须失败。
7. request 传入未声明变量必须失败。
8. 模板存在未声明占位符必须失败。
9. UNTRUSTED_DATA 必须按 maxChars 截断并追加 truncateMarker。
10. UNTRUSTED_DATA 不得进入 INSTRUCTION_BLOCK。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
```

#### P2-E1.5 新增 app 层装配与正式 catalog 加载

创建或修改：

```text
vi-agent-core-app/src/main/java/com/vi/agent/core/app/config/properties/SystemPromptProperties.java
vi-agent-core-app/src/main/java/com/vi/agent/core/app/config/PromptGovernanceConfiguration.java
vi-agent-core-app/src/main/resources/application.yml
```

配置基线：

```yaml
vi-agent:
  system-prompt:
    fail-fast: true
    catalog-base-path: prompt-catalog/system
    catalog-revision: ${VI_AGENT_PROMPT_CATALOG_REVISION:local-dev}
```

要求：

```text
1. application.yml 增加 vi-agent.system-prompt.fail-fast、catalog-base-path、catalog-revision。
2. SystemPromptProperties 至少声明 catalog base path、fail-fast、catalogRevision。
3. PromptGovernanceConfiguration 装配 ResourceSystemPromptCatalogRepository、SystemPromptRegistryFactory、SystemPromptRegistry、PromptRenderer。
4. 正式系统 prompt catalog 只从 app classpath 的 vi-agent-core-app/src/main/resources/prompt-catalog/system/ 加载。
5. fail-fast=true 时，正式资源缺失、manifest 不一致、contract 不合法均必须阻断启动。
6. 请求运行期不得重新扫描 resource 目录；运行期只使用启动完成后的 SystemPromptRegistry 只读快照。
7. catalogRevision 优先来自环境变量或构建注入，本地开发默认 local-dev。
8. 生产或非本地 profile 下 catalogRevision 不得为空。
9. promptTemplateVersion / summaryTemplateVersion 写入 catalogRevision。
10. content hash 不得替代 catalogRevision；二者分别用于版本锚点和内容复现。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-app -am test
```

---

## 8. P2-E2: Structured Output Contract + Schema Guard + Parser Alignment

### 8.1 目标

将 `state_delta_extract` 与 `conversation_summary_extract` 的模型输出契约统一收口到 `contract.json`，并通过本地 JSON Schema guard 执行 closed schema 校验。parser 不再维护独立 allowlist。

### 8.2 具体任务

#### P2-E2.0 新增 JSON Schema validator 依赖

修改：

```text
pom.xml
vi-agent-core-runtime/pom.xml
```

要求：

```text
1. root pom.xml 的 dependencyManagement 声明 com.networknt:json-schema-validator。
2. 使用 networknt 2.x 线，具体版本只能在根 POM 管理。
3. vi-agent-core-runtime/pom.xml 显式依赖 com.networknt:json-schema-validator，不写版本。
4. StructuredLlmOutputContractGuard 只在 runtime 使用 JSON Schema validator。
5. infra 只负责加载 contract.json 的 schemaJson，不执行业务 schema guard。
6. 子模块不得自行声明 validator 版本，不得把 validator 依赖扩散到 app / infra 作为业务依赖。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
```

#### P2-E2.1 新增结构化输出结果 model 契约

创建或修改：

```text
vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/NormalizedStructuredLlmOutput.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/StructuredOutputChannelResult.java
```

要求：

```text
1. StructuredLlmOutputContract / Key / Target / Mode 已在 P2-E1 创建，本批次不得重复迁移包位置。
2. NormalizedStructuredLlmOutput 只表达 provider response 归一化后的结构化 JSON。
3. StructuredOutputChannelResult 记录 actualStructuredOutputMode、retryCount、failureReason。
```

#### P2-E2.2 新增 StructuredLlmOutputContractGuard

创建：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/StructuredLlmOutputContractGuard.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/StructuredLlmOutputContractValidationResult.java
```

要求：

```text
1. 使用 contract.json 中的 JSON Schema 做本地校验。
2. 每个 object 必须按 additionalProperties:false 拒绝 schema 外字段。
3. schema 校验通过不等于业务语义通过。
4. guard 不构建 StateDelta / SummaryExtractionResult。
5. guard 不做 degraded 结果组装。
```

#### P2-E2.3 迁移 parser allowlist

修改：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/StateDeltaExtractionOutputParser.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/ConversationSummaryExtractionOutputParser.java
```

要求：

```text
1. 删除 parser 内部独立 allowed top-level fields。
2. 删除 parser 内部独立 nested allowed fields。
3. 删除 parser 内部独立 forbidden fields。
4. parser 接收或注入 StructuredLlmOutputContract。
5. parser 调用 StructuredLlmOutputContractGuard。
6. JSON 反序列化、skipped、degraded、业务语义判断仍由 parser 负责。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
```

---

## 9. P2-E3: Provider Structured Output Adapter + Failure Policy

### 9.1 目标

在 provider-neutral `LlmGateway` 边界支持 structured output contract，将同一份 `schemaJson` 按 provider capability 转换为 provider-native structured output 请求。DeepSeek 优先 strict tool call；失败后不得静默降级到弱模式继续写 durable memory。

### 9.2 具体任务

#### P2-E3.1 扩展 provider-neutral request / response

修改：

```text
vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/ModelRequest.java
vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/ModelResponse.java
```

要求：

```text
1. ModelRequest 可携带 StructuredLlmOutputContract。
2. ModelRequest 可携带 preferred StructuredLlmOutputMode。
3. ModelRequest 可携带 structured output function name。
4. ModelResponse 可携带 StructuredOutputChannelResult。
5. 这些字段不进入 /chat 对外 DTO。
```

#### P2-E3.2 新增 provider schema compiler / capability validator

创建或调整：

```text
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/protocol/openai/
```

建议类：

```text
ProviderStructuredSchemaCompiler
ProviderStructuredOutputCapabilityValidator
StructuredOutputRequestAdapter
StructuredOutputResponseExtractor
```

要求：

```text
1. provider-specific schema view 不修改 StructuredLlmOutputContract.schemaJson。
2. provider-specific schema view 必须剥离 x-* 扩展字段，避免 provider 拒绝自定义 keyword。
3. DeepSeek strict-compatible schema 编译失败时，请求前选择下一级可用 mode。
4. 已选 mode 请求失败后，不得静默降级到弱 mode 并继续成功路径。
5. P2-E retryCount 固定为 0。
6. P2-E 不做 LLM repair。
7. P2-E 不做自动重试。
8. strict tool call function 只作为 structured output channel，不进入业务 Tool Runtime。
```

#### P2-E3.3 更新 OpenAI-compatible provider 请求构建和响应解析

修改：

```text
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/base/OpenAICompatibleChatProvider.java
vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/protocol/openai/*.java
```

要求：

```text
1. STRICT_TOOL_CALL 模式下把 schema view 放入 tools[].function.parameters。
2. STRICT_TOOL_CALL 模式下 tool_choice 指定内部 function name。
3. JSON_OBJECT 模式下只要求合法 JSON object。
4. response extractor 能读取 tool_calls[].function.arguments。
5. response extractor 归一化为 NormalizedStructuredLlmOutput。
6. 归一化失败时写 failureReason。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-infra -am test
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
```

---

## 10. P2-E4: 旧代码迁移 + Audit 收口 + 测试补齐

### 10.1 目标

将 P2-D inline prompt builder、context block hardcoded key、parser allowlist、internal task audit key/version 全部迁移到 P2-E Prompt Governance。

### 10.2 具体任务

#### P2-E4.1 迁移 ContextBlockFactory

修改：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/builder/ContextBlockFactory.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/prompt/ContextBlockPromptVariablesFactory.java
```

要求：

```text
1. runtime instruction block 使用 PromptRenderer 渲染。
2. session state block 先由 SessionStateBlockRenderer 生成 sessionStateText，再交给 PromptRenderer。
3. conversation summary block 使用 PromptRenderer 渲染。
4. promptTemplateKey 写入 SystemPromptKey.value()。
5. promptTemplateVersion 写入 catalogRevision。
6. 删除 runtime-instruction / session-state / conversation-summary / p2-c-v1 旧硬编码。
```

#### P2-E4.2 迁移 StateDelta extraction

修改：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/StateDeltaExtractionPromptBuilder.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/LlmStateDeltaExtractor.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/prompt/StateDeltaExtractionPromptVariablesFactory.java
```

要求：

```text
1. 优先删除 StateDeltaExtractionPromptBuilder。
2. 如暂时保留类名，只能作为 Prompt Governance adapter。
3. 不得继续维护 state_extract_inline。
4. 不得继续手写完整 prompt 拼接。
5. LlmStateDeltaExtractor 使用 PromptRenderer.render(STATE_DELTA_EXTRACT)。
6. internal worker message 使用 SYSTEM + USER。
```

#### P2-E4.3 迁移 ConversationSummary extraction

修改：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/ConversationSummaryExtractionPromptBuilder.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/LlmConversationSummaryExtractor.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/prompt/ConversationSummaryExtractionPromptVariablesFactory.java
```

要求：

```text
1. 优先删除 ConversationSummaryExtractionPromptBuilder。
2. 如暂时保留类名，只能作为 Prompt Governance adapter。
3. 不得继续维护 summary_extract_inline。
4. LlmConversationSummaryExtractor 使用 PromptRenderer.render(CONVERSATION_SUMMARY_EXTRACT)。
5. provider / model 信息仍从 ModelResponse 回填。
```

#### P2-E4.4 迁移 audit 与 summary 保存

修改：

```text
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/task/InternalMemoryTaskService.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/task/InternalTaskPromptResolver.java
vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/SessionMemoryCoordinator.java
```

要求：

```text
1. STATE_EXTRACT audit key 写入 state_delta_extract。
2. SUMMARY_EXTRACT audit key 写入 conversation_summary_extract。
3. EVIDENCE_ENRICH audit key 继续使用 evidence_bind_deterministic。
4. promptTemplateVersion / summaryTemplateVersion 写入 catalogRevision。
5. requestJson.promptAudit 至少包含 promptKey、structuredOutputContractKey、templateContentHash、manifestContentHash、contractContentHash、catalogRevision、actualStructuredOutputMode、retryCount、failureReason。
6. prompt audit metadata 不进入用户响应。
7. prompt audit metadata 不进入 stream event。
```

#### P2-E4.5 补齐防回退测试

新增或更新：

```text
PromptContractParserAlignmentTest
SystemPromptKeyContractTest
PromptNoLegacyInlineKeyContractTest
PromptEnumStyleContractTest
StructuredOutputSchemaClosedContractTest
ProviderStructuredOutputAdapterContractTest
```

要求：

```text
1. 旧 key 不再出现在新断言中。
2. 旧 version 不再写入新 audit。
3. parser 不维护独立 allowlist。
4. prompt metadata 不进入对外 DTO。
5. DeepSeek strict tool call 不进入业务 Tool Runtime。
```

验收命令：

```powershell
.\mvnw.cmd -q -pl vi-agent-core-model -am test
.\mvnw.cmd -q -pl vi-agent-core-runtime -am test
.\mvnw.cmd -q -pl vi-agent-core-infra -am test
.\mvnw.cmd -q -pl vi-agent-core-app -am test
.\mvnw.cmd -q test
```

---

## 11. 阶段门禁

### 11.1 进入实现前门禁

进入代码实现前必须满足：

```text
1. design.md 已确认。
2. plan.md 已补齐。
3. test.md 已补齐。
4. README.md 中阶段目标与阅读顺序不冲突。
5. 不再要求实现代理自行推断 P2-E 范围。
```

### 11.2 每个批次完成门禁

每个批次完成时必须满足：

```text
1. 当前批次涉及模块的测试通过。
2. 当前批次没有新增跨层反向依赖。
3. 当前批次不修改 /chat 和 /chat/stream 对外协议。
4. 当前批次不新增 DB 表或 Redis key。
5. 当前批次不保留过时 key / version 兼容分支。
```

### 11.3 阶段完成门禁

P2-E 完成时必须满足：

```text
1. .\mvnw.cmd -q test 通过。
2. P2-E 所有防回退合同测试通过。
3. StateDelta / Summary extraction 均使用 PromptRenderer + StructuredLlmOutputContract。
4. parser allowlist 与 contract.json 不再双轨维护。
5. prompt audit metadata 可复现本次 catalog 内容。
6. prompt metadata 未进入用户响应或 SSE event。
7. closure.md 记录完成范围、未做项和是否晋升根文档。
```

---

## 12. 回滚策略

| 批次 | 回滚方式 | 不允许的回滚 |
|---|---|---|
| P2-E1 | 回滚 prompt catalog、model.prompt、infra.prompt、runtime.prompt 初始新增 | 不允许留下半套 registry 又继续使用 inline prompt |
| P2-E2 | 回滚 contract guard 与 parser 迁移 | 不允许恢复 parser / prompt schema 双轨 allowlist |
| P2-E3 | 回滚 provider adapter 的 strict tool call 实现，保留 JSON_OBJECT capability negotiation 路径 | 单次请求选定 mode 后仍不得失败后静默降级 |
| P2-E4 | 回滚具体迁移点 | 不允许为旧测试恢复 state_extract_inline / summary_extract_inline |

---

## 13. 最终交付清单

P2-E 最终交付时应包含：

```text
1. design.md
2. plan.md
3. test.md
4. prompt catalog resources
5. model.prompt / model.llm / model.port 契约
6. infra.prompt resource repository
7. runtime.prompt registry / renderer / contract guard
8. provider structured output adapter
9. context render prompt 迁移
10. state delta extract prompt/parser 迁移
11. summary extract prompt/parser 迁移
12. internal task audit 收口
13. unit tests
14. contract tests
15. migration tests
16. full regression tests
17. closure.md
```
