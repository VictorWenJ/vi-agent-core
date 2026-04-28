# P2-E Codex 执行记录

本文档记录 P2-E Prompt Engineering Governance 阶段的 Codex 执行批次与验收结果。本文档只保留执行摘要，不粘贴完整超长 prompt 原文，不新增设计口径。

---

## P2-E1 Resource Catalog + Manifest + Renderer

- 批次名称：P2-E1 Resource Catalog + Manifest + Renderer
- 执行目标：建立 system prompt resource catalog、manifest、model.prompt / model.memory / model.port 契约、infra resource repository、runtime registry / renderer 与 app 装配。
- 主要修改范围：
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/prompt/`
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/memory/`
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/port/SystemPromptCatalogRepository.java`
  - `vi-agent-core-app/src/main/resources/prompt-catalog/system/`
  - `vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/prompt/`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/`
  - `vi-agent-core-app/src/main/java/com/vi/agent/core/app/config/`
- 执行结果摘要：
  - 新增 5 个固定 `SystemPromptKey` 对应的正式 catalog resources。
  - infra 能从 classpath 读取 manifest / prompt / contract，并计算 content hash。
  - runtime 能构建只读 `SystemPromptRegistry`，`PromptRenderer` 支持 TEXT 与 CHAT_MESSAGES。
  - app 完成 `SystemPromptProperties` 与 prompt governance Bean 装配。
- 验收修复：
  - 修正式 app prompt catalog 的 contract 字段。
  - 修正式 render prompt 变量名。
  - 补 TEXT prompt 的 `UNTRUSTED_DATA` BEGIN / END 边界校验。
  - 对齐 `PromptRenderResult` 与 `SystemPromptRegistry` accessor 强契约。
  - 收口非本地 profile 下 `catalogRevision` 不得落到 `local-dev`。
- 测试命令：
  - `.\mvnw.cmd -q -pl vi-agent-core-model -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-infra -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-runtime -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-app -am test`
  - `.\mvnw.cmd -q test`
- 验收状态：Passed

---

## P2-E2 Structured Output Contract + Schema Guard + Parser Alignment

- 批次名称：P2-E2 Structured Output Contract + Schema Guard + Parser Alignment
- 执行目标：引入 JSON Schema validator，完善 structured output closed schema，实现本地 guard，并将 StateDelta / ConversationSummary parser 对齐 `StructuredLlmOutputContract`。
- 主要修改范围：
  - 根 `pom.xml`
  - `vi-agent-core-runtime/pom.xml`
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/`
  - `vi-agent-core-app/src/main/resources/prompt-catalog/system/*/contract.json`
  - `vi-agent-core-infra/src/test/resources/prompt-catalog/system/*/contract.json`
- 执行结果摘要：
  - root POM 统一管理 `com.networknt:json-schema-validator` 2.x。
  - runtime module 显式依赖 validator，guard 使用 networknt validator 做真实 JSON Schema 校验。
  - `state_delta_extract` 与 `conversation_summary_extract` 的 `contract.json` 成为结构化输出字段事实源。
  - parser 删除独立 allowlist / forbidden / nested allowlist，guard 失败返回 degraded。
- 验收修复：
  - summary `skipped=false` 且缺少有效 `summaryText` 时 degraded，不自动改 skipped。
  - `summaryText` blank / whitespace 时 degraded。
  - StateDelta append 空对象与空白正文 degraded。
  - `taskGoalOverride` 空字符串或全空白字符串 degraded，避免后续 durable state merge 覆盖已有 taskGoal。
- 测试命令：
  - `.\mvnw.cmd -q -pl vi-agent-core-model -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-runtime -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-infra -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-app -am test`
  - `.\mvnw.cmd -q test`
- 验收状态：Passed

---

## P2-E3 Provider Structured Output Adapter + Failure Policy

- 批次名称：P2-E3 Provider Structured Output Adapter + Failure Policy
- 执行目标：在 provider-neutral `ModelRequest` / `ModelResponse` 边界支持 structured output contract，完成 provider schema compiler、capability validator、request adapter、response extractor 与 OpenAI-compatible provider 接入。
- 主要修改范围：
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/ModelRequest.java`
  - `vi-agent-core-model/src/main/java/com/vi/agent/core/model/llm/ModelResponse.java`
  - `vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/`
  - `vi-agent-core-infra/src/main/java/com/vi/agent/core/infra/provider/base/OpenAICompatibleChatProvider.java`
  - provider protocol request / response DTO 与测试
- 执行结果摘要：
  - `ModelRequest` 可携带 structured output contract、preferred mode、structured output function name。
  - `ModelResponse` 可携带 `StructuredOutputChannelResult`。
  - provider schema view 会剥离 `x-*`，且不修改原始 `schemaJson`。
  - strict tool call / JSON object request mapping 与 response normalization 完成。
  - selected mode 失败后不静默降级重发，`retryCount` 固定为 0。
  - structured output function 不进入业务 Tool Runtime。
- 验收修复：
  - DeepSeek strict capability 收口：默认不启用 strict；仅 `strictToolCallEnabled=true` 且 baseUrl 包含 `/beta` 时可选择 `STRICT_TOOL_CALL`。
  - strict-compatible schema 判断收紧：object properties / required / `additionalProperties:false`、string / array 限制、type array 与 composition keyword fail-safe。
  - OpenAI-compatible provider selection only once，request adapter 与 response extractor 使用同一 selection。
  - 明确 P2-E3 不支持 structured output function 与业务 tools 混用，避免进入业务 Tool Runtime。
- 测试命令：
  - `.\mvnw.cmd -q -pl vi-agent-core-model -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-infra -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-runtime -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-app -am test`
  - `.\mvnw.cmd -q test`
- 验收状态：Passed

---

## P2-E4 旧代码迁移 + Audit 收口 + 测试补齐

- 批次名称：P2-E4 旧代码迁移 + Audit 收口 + 测试补齐
- 执行目标：迁移 ContextBlockFactory、StateDelta extraction、ConversationSummary extraction、internal task audit 与 summary save key/version，删除旧 inline prompt builder，并补齐防回退测试。
- 主要修改范围：
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/builder/ContextBlockFactory.java`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/context/prompt/ContextBlockPromptVariablesFactory.java`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/extract/prompt/`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/task/InternalTaskPromptResolver.java`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/task/InternalMemoryTaskService.java`
  - `vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/memory/SessionMemoryCoordinator.java`
  - P2-E 相关 runtime / app / infra / model 测试
- 执行结果摘要：
  - `ContextBlockFactory` 使用 `PromptRenderer` 渲染 runtime instruction、session state、conversation summary。
  - context block key 切到 P2-E `SystemPromptKey`，version 写 `catalogRevision`。
  - 删除 `StateDeltaExtractionPromptBuilder` 与 `ConversationSummaryExtractionPromptBuilder`。
  - `LlmStateDeltaExtractor` 与 `LlmConversationSummaryExtractor` 使用 `PromptRenderer.render(...)`，internal worker message 为 `SYSTEM + USER`。
  - internal worker `ModelRequest` 携带 structured output contract 与 function name。
  - provider channel / schema / parser failure 均 degraded，不写 durable state / summary。
  - `InternalTaskPromptResolver` 收口 state / summary / evidence audit key。
  - `requestJson.promptAudit` 写入 promptKey、contractKey、content hashes、catalogRevision、actual mode、retryCount、failureReason。
  - 新主链路不再使用旧 key / old version。
- 验收修复：无单独后续修复轮次，P2-E4 验收通过。
- 测试命令：
  - `.\mvnw.cmd -q -pl vi-agent-core-model -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-runtime -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-infra -am test`
  - `.\mvnw.cmd -q -pl vi-agent-core-app -am test`
  - `.\mvnw.cmd -q test`
- 验收状态：Passed

---

## 阶段总体验收

- E1 / E2 / E3 / E4：全部 Passed。
- 最终全量回归命令：`.\mvnw.cmd -q test`。
- 阶段结论：P2-E Completed，可以进入 closure 收口。
