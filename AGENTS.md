# AGENTS.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core` 的仓库级协作与治理规则、开发约束、文档维护规则。

根目录四个文件各自承担不同职责：

- `AGENTS.md`：仓库级协作规则、治理规则、文档维护规则与开发约束（本文件）
- `PROJECT_PLAN.md`：阶段规划、当前阶段任务、里程碑与验收口径
- `ARCHITECTURE.md`：总体架构、分层职责、依赖方向与核心调用关系
- `CODE_REVIEW.md`：项目级审查标准、拒绝项与测试门禁

执行任何实现任务时，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 目标模块 `AGENTS.md`

---

## 1.1 AI 代理必读速览

> **项目**：`vi-agent-core` — Java Agent Runtime Framework  
> **技术栈**：Java 17 + Spring Boot 3.3.x + WebFlux + Maven + Redis  
> **当前阶段**：Phase 1 收口阶段（真正流式输出、依赖治理、POM 标准化、消息/状态链路补齐）  
> **本轮重点**：真实 provider streaming、同步 `/chat` 阻塞隔离、错误码到 HTTP 状态映射、`turnId` 贯穿消息模型、移除 `infra -> runtime` 反向依赖、根/模块文档职责重整  
> **核心约束**：`RuntimeOrchestrator` 是唯一主链路编排入口；Controller/Application 轻薄；所有工具必须通过统一注册与 `ToolGateway`；禁止 `@Autowired` 字段注入；禁止让 `infra` 长期依赖 `runtime`。  
> **文档体系**：本文件定义仓库级规则；包结构、类职责、局部约束下沉到各模块 `AGENTS.md`；总体架构只看 `ARCHITECTURE.md`；阶段任务只看 `PROJECT_PLAN.md`；审查门禁只看 `CODE_REVIEW.md`。

---

## 2. 项目定位

`vi-agent-core` 是一个基于 Java 的 Agent Runtime Framework 实现，不是普通聊天后端。

项目目标：
- 构建一套可交互、可执行、可扩展、可评估、可审计的 Agent 运行时底座；
- 以 Java 工程方式沉淀 Runtime、Tool、Provider、Transcript、Observability 等稳定边界；
- 为后续企业级 AI 工作台、留学/移民顾问工作台等产品形态提供核心后端。

当前阶段聚焦（详见 `PROJECT_PLAN.md`）：
- Phase 1：核心闭环 + 最小状态底座
- 当前主目标不是继续扩模块，而是在既有五模块边界内收口主链路正确性、依赖方向、POM 标准化与文档职责边界

设计思想明确借鉴：
- **Claude Code / Agent SDK**：Agent Loop、Tool Use、受控扩展点、Runtime Kernel
- **OpenClaw**：Gateway、Context Engine、Session / Memory / Streaming / Provider 边界
- **传统企业后端工程经验**：中心编排、模块治理、依赖倒置、配置驱动、可测试性优先

### 2.1 当前落地模块结构（Phase 1）

当前仓库固定为 5 个 Maven 模块：

- `vi-agent-core-app`：顶层 Spring Boot 启动、WebFlux 接入、配置装配、SSE 适配，**唯一可运行模块**
- `vi-agent-core-runtime`：Runtime 主链路编排、Loop、Context、Tool 协调、运行时事件
- `vi-agent-core-infra`：Provider、Persistence、Observability、Mock Integration 等基础设施实现
- `vi-agent-core-model`：内部运行时模型与跨层共享契约的承载层
- `vi-agent-core-common`：异常、ID、通用无状态工具等轻量公共能力

**标准目标依赖方向**：

- `common`：最底层，保持轻量
- `model` → `common`
- `runtime` → `model` + `common`
- `infra` → `model` + `common`
- `app` → `runtime` + `infra` + `model` + `common`

**当前代码中的历史例外**：
- 历史上的 `infra -> runtime` 反向依赖已在本轮治理中移除
- `LlmGateway`、`TranscriptStore`、`@AgentTool`、`ToolBundle` 已下沉到 `model`
- 当前依赖方向已收口到：`runtime/infra -> model/common`

---

## 3. 技术栈与开发约束速查表

以下规则必须在每次代码生成时严格遵守：

### 3.1 技术栈
- **语言**：Java 17
- **框架**：Spring Boot 3.3.x、Spring WebFlux
- **构建工具**：Maven 多模块
- **状态底座**：Redis（Phase 1 Transcript 短期存储）
- **长期存储规划**：MySQL（后续阶段）
- **日志实现**：SLF4J + Log4j2（统一由 `vi-agent-core-app/src/main/resources/log4j2-spring.xml` 管理）
- **代码简化**：Lombok（按场景使用）
- **AI / Provider 接入**：当前以 DeepSeek 为 Phase 1 主实现；OpenAI / Doubao 保留扩展位

### 3.2 开发约束
- **依赖注入**：一律构造器注入，禁止 `@Autowired` 字段注入
- **主编排入口**：`RuntimeOrchestrator` 是唯一执行总线
- **WebFlux 边界**：Controller/Application 不做同步式 `try/catch/finally` 包裹 `Mono/Flux`；同步阻塞调用必须显式做线程隔离
- **工具边界**：所有工具统一经 `ToolRegistry` + `ToolGateway` 进入 Runtime
- **状态边界**：`Transcript`、`Working Context`、`Memory`、`Artifact` 必须分离
- **异常边界**：业务异常统一继承 `AgentRuntimeException`；HTTP 状态映射留在 `app` advice 层，不混入 `common`
- **命名边界**：当前项目统一使用 `@AgentTool` 命名，不再保留历史 `@Tool` 双命名体系
- **文档边界**：根目录文档只写仓库级规则、架构、阶段、审查；类级/包级细节下沉到模块 `AGENTS.md`
- **核心模型对象构造**：禁止通过持续追加重载构造器来适配新字段；核心模型最多保留一个完整主构造器，对外创建优先使用语义化静态工厂方法；运行时新建与持久化恢复必须使用不同命名入口，不得通过近似签名构造器混合表达语义

### 3.3 Maven / POM 标准化原则
- 根 `pom.xml` 负责：
  - 模块聚合
  - 统一 `dependencyManagement`
  - 统一 `pluginManagement`
  - Java / 编码 / 测试 / 插件版本基线
- 子模块 POM 负责：
  - 仅声明本模块直接依赖
  - 不重复声明已由根 POM 管理的版本
  - 不引入与当前阶段无关的重型依赖
- 依赖方向必须表达架构边界：
  - 禁止 `infra -> runtime`
  - 禁止 `common` 依赖业务模块
  - 禁止 `model` 依赖 `runtime` / `infra` / `app`
- 启动插件只属于 `app` 模块；其余模块保持普通库模块
- `starter` 依赖优先收敛在 `app`；库模块优先依赖更细粒度的基础库
- 测试依赖、编译插件、Enforcer 规则统一由根 POM 管理
- POM 的职责不只是“让项目能编译”，还要明确表达模块职责和依赖边界

### 3.4 必须继承的工程经验（强约束）
- **先分层，再实现**：入口、运行时、基础设施、模型、公共能力必须清晰分层
- **入口必须轻**：Controller/Application 只做接入、转发、适配、异常出口
- **中心编排唯一**：复杂流程必须由 `RuntimeOrchestrator` 统一驱动
- **扩展优先注册**：新增 Tool / Provider / Strategy 优先走注册机制，不堆 `if/else`
- **抽象与实现分离**：跨层共享契约应放在 `model`（少量基础能力放 `common`），不能长期挂在 `runtime`
- **配置驱动**：模型、Redis、线程隔离、运行时开关、超时等必须外部化

### 3.5 明确禁止！
- 禁止大量 static 可变全局状态
- 禁止 `SpringContextUtils.getBean(...)` 式绕容器调用
- 禁止把业务编排写进 `Utils`
- 禁止在 Runtime / App 中直接 `new` 外部客户端
- 禁止用 in-memory Transcript 冒充当前阶段正式实现
- 禁止让 `infra` 长期依赖 `runtime`
- 禁止把根目录文档写成“大杂烩说明书”
- 禁止把模块细节长期堆在根目录文档而不下沉到模块 `AGENTS.md`

### 3.6 公共工具类与日志规则
- 公共工具类只能承载无状态、跨模块复用、非业务编排能力
- `JsonUtils` 是当前统一 JSON 工具基线
- 关键类日志优先使用 Lombok `@Slf4j`
- 当前开发阶段允许为排障保留较详细日志，但日志治理、脱敏、瘦身不作为本轮阻塞项
- 日志实现与格式策略统一由 `log4j2-spring.xml` 管理，不在代码中分散配置

---

## 4. 文档模板冻结规则（强约束）

根目录四个核心文档、模块级 `AGENTS.md` 均属于项目治理模板资产。
后续任何更新都必须遵守以下规则：

### 4.1 基线规则
- 必须以当前文件内容为基线进行增量更新
- 不涉及变动的内容不得改写
- 未经确认，不得重写文件整体风格

### 4.2 冻结规则
未经明确确认，不得擅自改变：
- 布局、排版、标题层级、写法、风格、章节顺序

### 4.3 允许的修改范围
- 在原有章节内补充当前阶段内容
- 新增当前阶段确实需要的新章节
- 更新日期、阶段、默认基线等必要信息
- 删除已明确确认废弃且必须移除的旧约束
- 在职责重整确有必要时，将不属于根文档的细节下沉到模块 `AGENTS.md`

### 4.4 禁止事项
- 把原文档整体改写成另一种风格
- 把原本职责清晰的文件改写成职责混乱的综合说明书
- 把应该写在模块文档中的类/包细节长期保留在根目录文档中

---

## 5. 全局依赖方向（原则声明）

详细架构与调用关系见 `ARCHITECTURE.md`，此处只保留仓库级原则：

- `app` 是唯一运行入口，只做装配、接入、SSE 适配与异常出口
- `runtime` 是唯一主链路编排中心
- `infra` 只做实现与适配，不做流程主导
- `model` 承载内部运行时模型与跨层共享契约
- `common` 只承载轻量公共能力
- Runtime 内部事件可以向 `app` 输出；SSE / Reactor 类型不得反向侵入 `runtime`
- 若某个契约需要被 `runtime` 与 `infra` 同时依赖，应下沉到 `model`（少量纯基础契约才可放 `common`）

---

## 6. 模块 `AGENTS.md` 规范

当前仓库已固定存在以下模块级治理文档：

- `vi-agent-core-app/AGENTS.md`
- `vi-agent-core-common/AGENTS.md`
- `vi-agent-core-infra/AGENTS.md`
- `vi-agent-core-model/AGENTS.md`
- `vi-agent-core-runtime/AGENTS.md`

模块 `AGENTS.md` 负责：
- 模块职责与边界
- 模块内包结构约定
- 局部开发约束
- 测试要求
- 依赖与 POM 的模块级规则

根目录文档与模块文档的职责边界必须固定：
- 根目录文档：仓库级规则、阶段、总体架构、审查门禁
- 模块文档：模块内包结构、类职责、局部约束、模块测试与依赖规则

---

## 7. 当前阶段约束（Phase 1）

当前处于 Phase 1 收口阶段。必须遵守：

- Phase 1 当前主目标仍是“用户输入 → Runtime Core → Agent Loop → Tool Calling → 输出结果 → 最小状态落盘”闭环
- 本轮必须优先补齐：
  - 真正的 provider streaming
  - `/chat` 阻塞隔离
  - 错误码到 HTTP 状态映射
  - `turnId` 贯穿消息模型与 transcript
  - `infra -> runtime` 反向依赖消除
  - POM 标准化
  - 根/模块文档职责重整
- Phase 1 主 Provider 为 `DeepSeekChatProvider`
- 工具能力当前以 `@AgentTool` + `ToolRegistry` + mock 只读工具跑通闭环
- Transcript 当前正式实现为 Redis Hash；Redis 裁剪 / TTL / MySQL 长期存储升级不属于本轮
- 当前开发阶段日志暴露问题不作为本轮阻塞项
- 不得提前实现 Phase 2+ 的完整上下文裁剪、长期记忆、多代理、RAG、治理平台能力

---

## 8. 一句话总结

`AGENTS.md` 的职责是把仓库级规则、阶段边界、依赖方向、POM 原则和文档职责边界定死，让任何实现者都能在不误解模块边界的前提下推进 `vi-agent-core` 的 Phase 1 收口工作。
