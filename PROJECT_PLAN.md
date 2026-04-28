# PROJECT_PLAN.md

> 更新日期：2026-04-29

## 1. 文档定位

本文件定义 `vi-agent-core` 的项目高层路线图、阶段状态、当前阶段索引与阶段推进边界。

本文件只负责回答：

- 当前项目处于哪个阶段；
- 已完成哪些阶段；
- 下一阶段是什么；
- 后续大方向是什么；
- 当前阶段的详细文档在哪里；
- 阶段推进时必须遵守哪些高层边界。

本文件不负责：

- 仓库级协作规则与通用开发约束（见 `AGENTS.md`）；
- 长期架构分层与模块边界（见 `ARCHITECTURE.md`）；
- 通用 code review 标准与测试门禁（见 `CODE_REVIEW.md`）；
- 阶段详细系统设计（见 `execution-phase/{phase-name}/design.md`）；
- 阶段开发计划（见 `execution-phase/{phase-name}/plan.md`）；
- 阶段专项测试验收（见 `execution-phase/{phase-name}/test.md`）；
- Codex 执行 prompt（见 `execution-phase/{phase-name}/prompts.md`）；
- 阶段完成收口记录（见 `execution-phase/{phase-name}/closure.md`）。

本文件是项目路线图和阶段索引，不是阶段详细设计文档。

---

## 2. 项目长期定位

`vi-agent-core` 的长期目标不是普通 chat backend，而是一个自研轻量 Agent Framework / Agent Runtime Kernel。

长期目标能力层包括：

- Model Provider Layer
- Context Engineering
- Prompt Engineering
- Memory Engineering
- Tool Runtime
- Workflow / Graph Orchestration
- Storage / Cache
- Safety / Permission
- Observability / Audit
- Evaluation / Testing
- RAG / Knowledge
- Checkpoint / Resume

项目采用单体先行、逻辑分层、阶段演进的方式推进。

当前阶段仍然处于 P2 系列，不提前推进 P3 / P4 / RAG / Graph Workflow / Long-term Memory。

---

## 3. 当前阶段索引

最近完成阶段为：

```text
P2-E：Prompt Engineering Governance（已完成）
```

P2-E 阶段目录为：

```text
execution-phase/phase-P2-E-prompt-governance/
```

P2-E 阶段文档索引：

```text
execution-phase/phase-P2-E-prompt-governance/README.md
execution-phase/phase-P2-E-prompt-governance/design.md
execution-phase/phase-P2-E-prompt-governance/plan.md
execution-phase/phase-P2-E-prompt-governance/test.md
```

P2-E Codex prompt 记录位置：

```text
execution-phase/phase-P2-E-prompt-governance/prompts.md
```

P2-E 完成收口记录位置：

```text
execution-phase/phase-P2-E-prompt-governance/closure.md
```

---

## 4. 历史强契约索引

P2-A 到 P2-D 的历史强契约基线位于：

```text
execution-phase/phase-P2-context-memory/system-design-P2-v5.md
```

P2-A 到 P2-D 的历史开发计划基线位于：

```text
execution-phase/phase-P2-context-memory/system-design-P2-implementation-plan-v3.md
```

对于 P2-A 到 P2-D 已有对象，仍然必须遵守历史强契约。

尤其包括：

- WorkingContext
- WorkingContextProjection
- ContextBlock / ContextBlockSet
- SessionWorkingSet
- SessionStateSnapshot
- ConversationSummary
- StateDelta
- EvidenceRef / EvidenceTarget / EvidenceSource
- InternalLlmTaskRecord
- StateDeltaMerger
- P2-D state / summary / evidence 主链路语义

P2-E 新增 Prompt Governance 对象，以当前阶段 `design.md` 为新增契约源。

---

## 5. 阶段状态总览

| 阶段 | 状态 | 阶段目录 | 说明 |
|---|---|---|---|
| P1 | 已完成 | `execution-phase/phase-P1-logical-distributed-baseline/` | 逻辑分布式基础边界与早期工程基线 |
| P1.5 | 已完成 | `execution-phase/phase-P1.5-project-governance/` | 项目治理、文档治理、工程边界初步收口 |
| P2-A | 已完成 | `execution-phase/phase-P2-context-memory/` | Context / Memory 核心领域模型落地 |
| P2-B | 已完成 | `execution-phase/phase-P2-context-memory/` | MySQL 事实源与 Redis snapshot cache 基座 |
| P2-C | 已完成 | `execution-phase/phase-P2-context-memory/` | provider 调用前 WorkingContext 主链路 |
| P2-D | 已完成 | `execution-phase/phase-P2-context-memory/` | post-turn state / summary / evidence memory update |
| P2-E | 已完成 | `execution-phase/phase-P2-E-prompt-governance/` | Prompt Engineering Governance：system prompt catalog、registry / renderer、structured output contract guard、provider structured output adapter、parser alignment、audit key/version 收口 |
| P2.5 | 待开始 | 待创建 | 架构治理与技术债收口 |
| P3 | 待开始 | 待创建 | Graph Workflow Kernel + Tool Runtime Core |
| P4+ | 待开始 | 待创建 | Long-term Memory、RAG、Browser / Document / Computer tools、Checkpoint / Resume、Evaluation 平台化 |

---

## 6. P2-E 阶段完成摘要

P2-E 已完成。该阶段治理了 P2 中已经出现的 prompt / prompt-like 能力，并将其收口为系统级 Prompt Engineering Governance。

P2-E 已完成的核心交付包括：

- system prompt catalog；
- registry / renderer；
- structured output contract guard；
- provider structured output adapter；
- parser alignment；
- audit key / version 收口。

P2-E 不是新增业务功能阶段，未改变主聊天对外协议。

P2-E 未做：

- 不推进 P2.5；
- 不推进 P3；
- 不做 Graph Workflow；
- 不做 Long-term Memory；
- 不做 RAG / Knowledge；
- 不做 Tool Runtime 大扩展；
- 不做 Computer / Mobile control；
- 不做 debug API；
- 不修改主聊天协议；
- 不改变 P2-D 已完成的 state / summary / evidence 主链路语义。

P2-E 的详细设计位于：

```text
execution-phase/phase-P2-E-prompt-governance/design.md
```

P2-E 的详细开发计划位于：

```text
execution-phase/phase-P2-E-prompt-governance/plan.md
```

P2-E 的专项测试验收位于：

```text
execution-phase/phase-P2-E-prompt-governance/test.md
```

---

## 7. 后续阶段路线

### 7.1 P2.5：Architecture Governance / Technical Debt Closure

P2.5 是 P2 完成后的架构治理与技术债收口阶段。

P2-E 已完成后，下一步建议进入 P2.5 架构 / 技术债 / 契约治理准备；P2.5 当前仍为待开始状态。

重点方向：

- 全量 v5 class 契约回归；
- 递归模型契约检查；
- prompt schema / parser / model 对齐；
- ID generator 统一治理；
- MySQL / Redis 边界治理；
- runtime 读链路 / 写链路边界治理；
- 测试防回退；
- 文档同步与历史阶段收口。

P2.5 不应在 P2-E 未完成前提前展开。

---

### 7.2 P3：Graph Workflow Kernel + Tool Runtime Core

P3 的目标是引入项目专用 Graph Workflow Kernel 与 Tool Runtime Core。

重点方向：

- 把现有 runtime 能力包装为 node / step / route；
- 建立项目专用 workflow kernel；
- 建立工具执行、工具审计、工具权限的基础边界；
- 为后续 Web / Document / Computer / Mobile tools 打基础。

P3 不做完整 LangGraph clone。  
P3 不替代 Provider、Context、Prompt、Memory、Storage 等能力层。

---

### 7.3 P4+：后续能力层

P4 之后的长期能力包括：

- Long-term Memory
- RAG / Knowledge
- Web / Browser tools
- Document tools
- Computer / Mobile control
- Checkpoint / Resume
- Human Approval
- Evaluation / Testing 平台化

这些能力必须按独立阶段推进。  
不得在 P2-E 或 P2.5 中提前混入实现。

---

## 8. 阶段推进规则

每个阶段开始前，必须先明确：

1. 本阶段目标是什么；
2. 输入是什么；
3. 输出是什么；
4. 会读写哪些存储；
5. 会调用哪些模型 / prompt / parser；
6. 哪些失败不能影响主聊天；
7. 哪些事情本阶段明确不做；
8. 最大风险点是什么。

每个阶段必须建立或补齐：

```text
execution-phase/{phase-name}/README.md
execution-phase/{phase-name}/design.md
execution-phase/{phase-name}/plan.md
execution-phase/{phase-name}/test.md
execution-phase/{phase-name}/prompts.md
execution-phase/{phase-name}/closure.md
```

阶段完成前，必须填写 `closure.md`。  
没有 `closure.md` 收口记录，不应宣布阶段正式完成。

---

## 9. 文档晋升规则

阶段详细内容默认留在阶段目录中。

只有当某个阶段产出的规则已经成为长期稳定规则时，才允许晋升到根目录文档。

晋升规则：

- 长期协作规则进入 `AGENTS.md`；
- 长期架构规则进入 `ARCHITECTURE.md`；
- 通用 review 规则进入 `CODE_REVIEW.md`；
- 阶段状态和路线图进入 `PROJECT_PLAN.md`；
- 模块内长期边界进入对应模块 `AGENTS.md`。

不得把阶段详细设计、阶段完整开发计划、阶段专项测试清单、Codex prompt 整体搬入根目录文档。

---

## 10. 一句话总结

`PROJECT_PLAN.md` 的职责，是说明项目当前走到哪里、下一步走向哪里、阶段文档在哪里。

它不承载阶段详细设计、阶段执行清单、阶段专项测试标准或 Codex prompt。
