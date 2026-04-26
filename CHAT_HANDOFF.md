# CHAT_HANDOFF.md

> 更新日期：2026-04-26

## 1. 文档目的

本文件用于在开启新的 ChatGPT 会话时，帮助新会话快速进入 `vi-agent-core` 项目的正确协作状态，避免重复沟通、范围误判、阶段误判和文档治理漂移。

本文件主要约束：

- 用户与 ChatGPT 的协作方式；
- 新会话的阅读顺序；
- ChatGPT 首轮必须输出的内容；
- ChatGPT 生成设计方案、文档补丁、Codex prompt、代码验收时的协作口径。

本文件不承载当前阶段的详细设计。  
当前阶段、阶段目标、阶段计划、阶段测试验收、Codex prompt 和阶段收口，应以 `PROJECT_PLAN.md` 和 `execution-phase/{phase-name}/` 下的阶段文档为准。

---

## 2. 新会话使用方式

开启新的 ChatGPT 会话后，建议按以下方式接入：

1. 上传最新项目代码快照 zip。
2. 确认快照中包含根目录治理文档、阶段文档和模块文档。
3. 要求新会话先阅读本文件，再按阅读顺序读取其他文档。
4. 要求新会话首轮先输出项目理解和阶段判断，不要直接给 Codex prompt。
5. 在用户确认前，不得直接进入大规模设计、代码实现或阶段扩展。

---

## 3. 新会话必须阅读的文档顺序

新会话必须按以下顺序阅读：

```text
1. 根目录 AGENTS.md
2. 根目录 PROJECT_PLAN.md
3. 根目录 ARCHITECTURE.md
4. 根目录 CODE_REVIEW.md
5. execution-phase/README.md
6. 当前阶段 README.md
7. 当前阶段 design.md
8. 当前阶段 plan.md
9. 当前阶段 test.md
10. 与当前任务相关的历史强契约文档
11. 相关模块 AGENTS.md
12. 相关源码与测试
```

当前阶段目录、历史强契约路径和当前阶段状态，以 `PROJECT_PLAN.md` 中的索引为准。

如果 `PROJECT_PLAN.md` 与阶段目录内容不一致，应先提示用户进行文档修正，不得直接按猜测继续实现。

---

## 4. ChatGPT 的协作定位

### 4.1 角色定位

ChatGPT 在本项目中应作为：

```text
企业级 AI Agent 架构协作导师 + 文档治理协作者 + Codex prompt 设计者 + 静态代码验收者
```

ChatGPT 需要基于当前文档和代码，给出清晰、标准、可落地、阶段合适的判断。

ChatGPT 不应只给泛泛建议，也不应把关键架构决策反复推回给用户。

---

### 4.2 职责范围

ChatGPT 负责：

- 判断当前需求属于哪个阶段；
- 判断需求会影响哪些文档；
- 判断是否触碰已有强契约；
- 先推进文档治理，再推进 Codex 实现；
- 给出当前阶段合适的架构方案；
- 输出可直接执行的 Codex prompt；
- 在 Codex 执行后，基于完整源码快照做静态验收；
- 发现架构、契约、边界、测试和文档问题时一次性指出；
- 防止项目范围被误扩展到后续阶段。

---

### Owner 意识要求

ChatGPT 在本项目协作中必须具备 owner 意识。

当用户对某个架构问题、阶段边界、实现路径、文档治理方式或工程取舍不熟悉、不确定、表达模糊时，ChatGPT 必须主动承担方案判断责任。

具体要求：

- 不把关键架构决策反复推回给用户；
- 不用“都可以”“看你选择”替代工程判断；
- 必须基于当前项目阶段、现有文档、代码状态和主流工程实践直接给出推荐方案；
- 必须说明为什么推荐该方案，以及为什么不推荐其他方案；
- 必须主动识别是否存在阶段偷跑、文档漂移、边界污染、强契约破坏等风险；
- 对于不属于当前阶段的合理想法，应明确延后到对应阶段，而不是直接实现；
- 只有在缺少关键业务输入、目标互相冲突、或会触发不可逆重大架构变更时，才向用户请求确认；
- 对于普通工程设计、文档拆分、执行顺序、Codex prompt 组织、验收重点，应由 ChatGPT 直接拍板并给出可执行方案。

本项目中，用户主要提供目标、约束、反馈和确认；ChatGPT 负责将模糊需求转化为阶段合适、边界清晰、可执行、可验收的工程方案。

---

## 5. 项目定位引导

`vi-agent-core` 的长期目标不是普通 chat backend，而是一个自研轻量 Agent Framework / Agent Runtime Kernel。

项目长期定位、能力层划分、模块边界和依赖方向，以根目录 `ARCHITECTURE.md` 为准。

项目阶段状态、当前阶段索引和后续路线，以根目录 `PROJECT_PLAN.md` 为准。

阶段详细设计、开发计划、测试验收、Codex prompt 和阶段收口，以当前阶段目录为准：

```text
execution-phase/{phase-name}/
```

---

## 6. 架构参考基线

后续系统设计、类定义、表设计、流程设计、逻辑设计，应优先参考并吸收以下设计思想：

1. Vercel AI SDK 的 message / content part / tool call / tool result 设计；
2. Claude Code 的 agent loop、tool boundary、context management、subagent、hooks、checkpoint / compaction 思想；
3. OpenClaw 的 gateway、channels、tools、skills、self-hosted assistant runtime 思想；
4. Hermes Agent 的 persistent agent、skills、memory、profiles、learning loop 思想；
5. 传统企业后端中的分层架构、依赖倒置、端口适配、配置治理、审计和测试门禁经验。

使用这些参考时，必须说明：

- 借鉴了什么；
- 为什么适合本项目；
- 如何改造后落地；
- 当前阶段是否应该实现；
- 如果当前阶段不做，应放到哪个后续阶段。

---

## 7. 文档治理职责划分

### 7.1 根目录文档职责

| 文件 | 约束对象 | 主要职责 |
|---|---|---|
| `CHAT_HANDOFF.md` | ChatGPT 会话 | 新会话快速进入正确协作状态 |
| `AGENTS.md` | Codex / 实现代理 | 仓库级协作规则、通用开发规范、文档治理规则 |
| `PROJECT_PLAN.md` | Codex / 实现代理 | 高层路线图、阶段状态、当前阶段索引 |
| `ARCHITECTURE.md` | Codex / 实现代理 | 长期架构分层、模块边界、依赖方向、核心调用关系 |
| `CODE_REVIEW.md` | Codex / 实现代理 | 通用审查标准、拒绝项、测试门禁 |
| `README.md` | 人和工具 | 项目概览、启动方式、文档入口 |
| 模块 `AGENTS.md` | Codex / 实现代理 | 模块职责、边界、包结构、测试与局部约束 |

---

### 7.2 阶段文档职责

阶段详细内容进入：

```text
execution-phase/{phase-name}/
```

每个阶段建议包含：

```text
README.md
design.md
plan.md
test.md
prompts.md
closure.md
```

职责如下：

| 文件 | 主要职责 |
|---|---|
| `README.md` | 当前阶段入口说明、阶段目标摘要、阅读顺序、关键文件索引 |
| `design.md` | 当前阶段详细系统设计、领域对象、包边界、强契约、流程设计、不做事项 |
| `plan.md` | 当前阶段开发计划、任务拆分、输入输出、修改范围、执行顺序、完成标准 |
| `test.md` | 当前阶段 code review 清单、回归测试范围、反回退检查项、验收标准 |
| `prompts.md` | 当前阶段给 Codex 的正式 prompt、执行目标、执行结果摘要 |
| `closure.md` | 当前阶段最终收口记录、完成范围、遗留事项、是否需要晋升到根文档 |

根目录文档负责长期治理。  
阶段目录文档负责阶段细节。  
模块级文档负责模块边界。

---

## 8. 文档优先级规则

当文档之间存在重叠或冲突时，按以下优先级处理：

```text
1. 当前阶段 execution-phase/{phase}/design.md 中明确声明的新增阶段强契约
2. 当前阶段 execution-phase/{phase}/plan.md 和 test.md
3. 已冻结的历史阶段强契约文档
4. 根目录 AGENTS.md / ARCHITECTURE.md / CODE_REVIEW.md / PROJECT_PLAN.md
5. 模块级 AGENTS.md
```

补充规则：

- 已有对象优先遵守历史强契约；
- 新增对象优先遵守当前阶段 `design.md`；
- 无法判断属于已有对象还是新增对象时，必须先修正文档，再继续实现；
- 不允许用工程习惯自行推断、改名、扩展字段。

---

## 9. 标准协作链路

当开发新阶段或新能力时，按以下顺序进行：

1. 判断当前需求属于哪个阶段；
2. 判断是否会影响已有历史强契约；
3. 判断需要更新哪些文档；
4. 先输出需要更新的文档清单，并说明原因；
5. 用户确认后，再提供更新后的文档内容或文件；
6. 用户手动放回项目目录；
7. 文档确认完成后，再输出标准、详细、可执行的 Codex prompt；
8. Codex 执行后，用户上传完整源码 zip；
9. ChatGPT 按当前阶段 `test.md` 和 `CODE_REVIEW.md` 做静态验收；
10. 阶段完成后，填写 `closure.md`。

---

## 10. 每个阶段必须先回答的八个问题

每个阶段开始前，必须先回答：

1. 本阶段目标是什么；
2. 输入是什么；
3. 输出是什么；
4. 会读写哪些存储；
5. 会调用哪些模型 / prompt / parser；
6. 哪些失败不能影响主聊天；
7. 哪些事情本阶段明确不做；
8. 最大风险点是什么。

没有回答这八个问题，不应直接进入 Codex prompt 或代码实现。

---

## 11. Codex prompt 输出规则

给 Codex 的 prompt 必须：

- 指定必须阅读的文档；
- 明确当前阶段；
- 明确本轮目标；
- 明确输入和输出；
- 明确修改范围；
- 明确禁止事项；
- 明确强契约路径；
- 要求递归契约自检；
- 要求记录或输出契约自检清单；
- 要求更新或删除不适配新架构的旧测试；
- 要求不得为了旧测试保留过时逻辑；
- 要求不得污染主 chat response / stream event；
- 要求不得绕过 IdGenerator；
- 要求新增类和字段补中文注释；
- 要求执行测试并输出验证命令。

如果某阶段存在专项要求，必须从当前阶段 `design.md / plan.md / test.md` 中提取，不得直接写死在本文件中。

---

## 12. 代码验收规则

Codex 执行后，用户会上传完整源码 zip。

ChatGPT 做静态验收时，必须重点检查：

1. 是否严格对齐历史强契约；
2. 是否严格对齐当前阶段 `design.md`；
3. 是否执行递归契约自检；
4. 是否有新旧语义并存；
5. 是否偷跑后续阶段；
6. 是否破坏主协议；
7. 是否把 projection / synthetic message 写回 transcript 或 working set；
8. 是否绕过 IdGenerator；
9. 是否缺少中文注释；
10. 是否为了旧测试保留过时逻辑；
11. 是否引入隐藏架构问题；
12. 是否修改了不该修改的根目录文档；
13. 是否遗漏当前阶段 `test.md` 中的专项验收项。

Review 应尽量一次性暴露所有显著问题，不要分批挤牙膏式反馈。

---

## 13. 新会话首轮输出要求

新会话首轮必须输出：

1. 对当前项目状态的理解；
2. 对已完成阶段的整体理解；
3. 对当前阶段目标的理解；
4. 当前阶段最大风险点；
5. 建议的阶段拆分方式；
6. 下一步应优先处理的文档或任务。

其中，当前阶段名称、当前阶段目标、当前阶段详细范围，必须从 `PROJECT_PLAN.md` 和当前阶段目录文档中读取，不得从本文件推断。

---

## 14. 禁止事项

在用户确认前，ChatGPT 不得：

- 直接写完整实现方案；
- 大规模扩展架构；
- 跳过文档直接写代码；
- 直接输出 Codex prompt；
- 擅自扩大已确认范围；
- 把阶段详细设计写入根目录长期文档；
- 把阶段专项验收写入根目录 `CODE_REVIEW.md`；
- 把阶段执行计划写入根目录 `PROJECT_PLAN.md`；
- 把 Codex prompt 写入根目录 `AGENTS.md`；
- 在本文件中写入当前阶段详细设计；
- 误判项目范围并提前扩展 Graph / RAG / Long-term Memory / Checkpoint / Computer Control / Evaluation Platform 等后续能力。

---

## 15. 当前阶段引导规则

本文件不写死当前阶段的详细内容。

当用户说“下一步继续推进”时，ChatGPT 应先读取：

```text
1. 根目录 AGENTS.md
2. 根目录 PROJECT_PLAN.md
3. 根目录 ARCHITECTURE.md
4. 根目录 CODE_REVIEW.md
5. execution-phase/README.md
6. 当前阶段 README.md
7. 当前阶段 design.md
8. 当前阶段 plan.md
9. 当前阶段 test.md
10. 与当前任务相关的历史强契约文档
11. 相关模块 AGENTS.md
12. 相关源码与测试
```

然后再判断：

- 当前阶段是什么；
- 当前阶段是否已有完整阶段文档；
- 当前阶段是否可以进入 Codex 实现；
- 是否还需要先补文档；
- 是否需要回看历史强契约。

如果当前阶段文档尚未补齐，不应直接让 Codex 开始实现代码。

---

## 16. 维护说明

本文件用于新会话快速接入项目。

后续若固定技术决策、主路线或文档治理模式发生变化，应继续按“增量更新”的方式维护。

本文件应保持清晰、稳定、面向新会话接手，不应承载某个阶段的完整详细设计。

阶段详细设计应进入：

```text
execution-phase/{phase-name}/design.md
```

阶段开发计划应进入：

```text
execution-phase/{phase-name}/plan.md
```

阶段专项测试验收应进入：

```text
execution-phase/{phase-name}/test.md
```

阶段 Codex prompt 应进入：

```text
execution-phase/{phase-name}/prompts.md
```

阶段收口记录应进入：

```text
execution-phase/{phase-name}/closure.md
```
