# AGENTS.md

> 更新日期：2026-04-15

## 1. 文档定位

本文件定义 `vi-agent-core` 的仓库级协作与治理规则、开发约束、文档维护规则。

根目录四个文件各自承担不同职责：

- `AGENTS.md`：仓库级协作规则、治理规则、文档维护规则与开发约束（本文件）
- `PROJECT_PLAN.md`：阶段规划与路线图
- `ARCHITECTURE.md`：总体架构、分层职责与依赖方向
- `CODE_REVIEW.md`：项目级审查标准与验收检查点

执行任何实现任务时，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 目标模块 `AGENTS.md`（如果存在）

---

## 1.1 AI 代理必读速览

> **项目**：`vi-agent-core` — Java Agent Runtime Framework  
> **技术栈**：Java 17 + Spring Boot 3.x + LangChain4j + WebFlux + Maven  
> **当前阶段**：Phase 1（核心闭环 + 最小状态底座）  
> **核心约束**：构造器注入（禁止 `@Autowired` 字段注入）、所有工具必须通过 `ToolGateway`、禁止 `static` 可变全局状态、`RuntimeOrchestrator` 是唯一编排入口。  
> **文档体系**：本文件定义协作规则，`ARCHITECTURE.md` 定义分层边界，`PROJECT_PLAN.md` 定义阶段任务，`CODE_REVIEW.md` 定义审查门禁。

---

## 2. 项目定位

`vi-agent-core` 是一个基于 Java 和 Spring Boot 的 Agent Runtime Framework 核心实现。

项目目标：
- 构建一套可交互、可记忆、可执行、可扩展的 Agent 运行时底座。
- 为后续的企业级 AI 工作台或垂直领域 Agent 产品提供稳定的后端核心。

当前阶段聚焦（详见 `PROJECT_PLAN.md`）：
- Phase 1：核心闭环 + 最小状态底座

项目设计同时吸收三类来源的经验，但不照搬任何单一实现：
- **Claude Code / Agent SDK 思想**：强调 Agent Loop、Tool Use、Skills、Subagents、Runtime Kernel。
- **OpenClaw 思想**：强调 Gateway、Context Engine、Session/Memory、Streaming、Authoritative Runtime Path。
- **旧项目工程经验**：强调多模块边界、中心编排、注册表式扩展、同步/异步链路分离、配置驱动、运行时监控。

因此，本项目不是普通聊天应用后端，而是：
- 以 `runtime` 为唯一执行总线的 Agent Runtime Framework；
- 以逻辑分布式边界设计、单体先行落地的 Java 平台型工程；
- 以后可装配为企业级 AI 工作台、顾问工作台等产品形态的核心后端。

### 2.1 当前落地模块结构（Phase 1 初始化）

当前仓库已按 Maven 多模块方式落地为以下 5 个模块：

- `vi-agent-core-app`：顶层 Spring Boot 装配与 WebFlux 入口，**唯一可运行模块**
- `vi-agent-core-runtime`：Runtime 核心编排（含 `RuntimeOrchestrator`、Agent Loop、ToolGateway、ContextAssembler 等骨架）
- `vi-agent-core-infra`：provider / persistence / observability / integration 基础设施骨架
- `vi-agent-core-model`：内部运行时模型（message / tool / transcript / runtime / artifact）
- `vi-agent-core-common`：公共异常、ID 生成器、少量无状态工具

当前强制依赖方向（已落地）：

- `common`：尽量零依赖
- `model` → `common`
- `runtime` → `model` + `common`
- `infra` → `runtime` + `model` + `common`
- `app` → `runtime` + `infra` + `model` + `common`

---

## 3. 技术栈与开发约束速查表

以下规则必须在每次代码生成时严格遵守：

### 3.1 技术栈
- **语言**：Java 17
- **框架**：Spring Boot 3.x, Spring WebFlux
- **AI 集成**：LangChain4j（仅作为模型调用与工具集成基础设施，不主导 Runtime Core 的分层和抽象设计）
- **构建工具**：Maven
- **数据库**：MySQL / H2 (开发期)
- **容器**：Docker Compose
- **构建形态**：Maven 多模块（`app / runtime / infra / model / common`）

### 3.2 开发约束
- **命名规范**：类名 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`。
- **包结构**：基础包 `com.vi.agent.core`，子包按职责划分（`runtime`、`context`、`tool`、`memory`、`provider` 等）。
- **依赖注入**：一律使用**构造器注入**，禁止 `@Autowired` 字段注入。
- **日志**：统一使用 Slf4j，关键路径必须记录结构化日志（携带 `traceId`、`runId`、`sessionId`）。
- **异常处理**：业务异常统一继承 `AgentRuntimeException`，不允许吞没 `Exception`。
- **公共接口类型签名**：所有 public API 必须使用明确、稳定的类型签名；禁止在公共接口层使用语义不清的 `Object`、原始 `Map`、原始 `List` 作为逃逸类型。
- **注释**：所有 `record` 组件、DTO 字段、Entity 字段必须有中文注释，说明含义与约束。
- **测试**：核心逻辑（Agent Loop、ToolGateway、RuntimeOrchestrator）必须有单元测试覆盖。

### 3.3 必须继承的工程经验（强约束）
- **先分层，再实现**：接口层、模型层、核心运行时层、基础设施层必须清晰分离。
- **入口层必须薄**：Controller / Provider / Gateway 只做接入、参数解析、响应封装，不承载核心业务。
- **中心编排唯一**：复杂流程必须由单一 `RuntimeOrchestrator` 统一驱动，不允许多个子模块横向乱调。
- **扩展优先注册**：新增 Tool、Skill、Provider、Agent、Strategy 时，优先通过注册表/策略接口扩展，不允许不断膨胀 `if/else`。
- **同步与异步分离**：交互主链路、后台任务链路、长任务链路必须明确区分，不能混在一个 Service 中。
- **配置驱动**：运行时参数、线程池、限流、超时、开关应外部化配置，不允许大量写死。
- **可观测性前置**：关键链路必须具备日志、错误计数、耗时、状态标识，而不是后补。

### 3.4 明确禁止沿用的旧习惯
以下做法来自旧项目中的可识别问题，在新项目中禁止继续扩散：
- **禁止大量 static 可变全局状态**：不得用全局静态变量承载运行态业务数据、配置快照或注册表。
- **禁止 `SpringContextUtils.getBean(...)` 式绕容器调用**：必须通过 Spring 正常依赖注入完成装配。
- **禁止把业务编排写进 Utils**：工具类只能放无状态通用逻辑，不能承载核心业务流程。
- **禁止直接在 Runtime 中 new 具体外部依赖**：所有模型、工具、存储、远程调用必须通过网关或适配层进入。
- **禁止用“手工日志调试”替代测试**：关键链路新增或修改后必须补测试。

---

## 4. 文档模板冻结规则（强约束）

根目录四个核心文档、模块级 `AGENTS.md` 均属于项目治理模板资产。
后续任何更新都必须遵守以下规则：

### 4.1 基线规则
- 必须以当前文件内容为基线进行增量更新。
- 不涉及变动的内容不得改写。
- 未经明确确认，不得重写文件整体风格。

### 4.2 冻结规则
未经明确确认，不得擅自改变：
- 布局、排版、标题层级、写法、风格、章节顺序。

### 4.3 允许的修改范围
- 在原有章节内补充当前阶段内容。
- 新增当前阶段确实需要的新章节。
- 更新日期、阶段、默认基线等必要信息。
- 删除已明确确认废弃且必须移除的旧约束。

### 4.4 禁止事项
- 把原文档整体改写成另一种风格。
- 把原本职责清晰的文件改写成职责混乱的综合说明书。

---

## 5. 全局依赖方向（原则声明）

详细依赖方向见 `ARCHITECTURE.md`，此处仅强调原则：

- **Controller** 只做接入、校验、转发，不承载业务逻辑。
- **Service** 作为 Facade，不包含核心 Agent Loop 编排。
- **App 模块** 是唯一运行入口，负责装配 runtime + infra，不反向侵入 runtime 核心边界。
- **Core** 包（`runtime`、`tool`、`context`）是系统核心，不依赖 Web 层。
- **Provider** 层负责屏蔽模型厂商差异，不反向依赖业务层。
- **Runtime Core** 是唯一主链路编排中心，`interaction`、`context`、`tool`、`memory`、`provider` 等均围绕它协作。
- **Tool**、**Provider**、**Persistence**、**External Client** 必须通过网关/适配层接入，不得由业务模块直接散落调用。
- **Transcript**、**Memory**、**Working Context**、**Artifact** 必须概念分离，禁止混用或相互替代。
- **Sync Runtime** 与 **Async Task** 必须边界明确；长链路、后台链路、未来委派链路需预留独立演进空间。

---

## 6. 模块 `AGENTS.md` 规范

当某个模块（如 `runtime`、`tool`）足够复杂时，应在其根目录下创建独立的 `AGENTS.md`。
模块 `AGENTS.md` 负责：
- 模块职责与边界
- 模块内包结构约定
- 局部开发约束
- 测试要求

重点模块建议优先补齐模块级 `AGENTS.md`：
- `runtime`
- `context`
- `tool`
- `memory`
- `provider`
- `persistence`
- `skill`
- `observability`

---

## 7. 当前阶段约束（Phase 1）

当前处于 Phase 1：核心闭环阶段。必须遵守：
- 所有代码必须符合 Phase 1 的范围定义，不得提前实现 Phase 2+ 的功能（如子代理委派、RAG、长期记忆）。
- 为后续阶段预留接口时，只能定义接口签名，不实现具体逻辑。
- Phase 1 的唯一目标是跑通“用户输入 → Runtime Core → Agent Loop → Tool Calling → 输出结果 → 最小状态落盘”的闭环。
- Phase 1 必须补齐最小状态底座：至少包括 `traceId`、`runId`、最小 Transcript、工具调用记录。
- Phase 1 的工具能力以只读工具为主；写操作工具和审批能力属于后续阶段。
- Phase 1 中 `ContextAssembler` 的简单实现应直接返回全量历史消息，不进行 Token 计数或裁剪；裁剪能力属于 Phase 2。
- 其他预留接口（`MemoryService`、`DelegationService`、`SkillRegistry`）保持空实现或最小桩实现。

---

## 8. 一句话总结

`AGENTS.md` 的职责是让任何开发者或 AI 代理在阅读后，都能准确理解本项目的协作规则与技术约束，从而产出风格一致、边界清晰、可演进的 Java Agent Runtime 代码。
