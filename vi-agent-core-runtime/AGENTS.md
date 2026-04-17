# AGENTS.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core-runtime` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `runtime` 模块负责什么
- `runtime` 模块不负责什么
- `runtime` 模块内包结构如何约定
- 在 `runtime` 模块开发时必须遵守哪些局部规则
- `runtime` 模块测试与依赖应如何建设

本文件不负责：
- 仓库级协作规则（见根目录 `AGENTS.md`）
- 项目阶段规划（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准（见根目录 `CODE_REVIEW.md`）

执行 `runtime` 模块相关任务前，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 本文件 `vi-agent-core-runtime/AGENTS.md`

---

## 1.1 AI 代理必读速览

> **模块**：`vi-agent-core-runtime` — 核心运行时模块  
> **模块定位**：承载 `RuntimeOrchestrator`、Agent Loop、Context、Tool 协调、Runtime Event 与最小扩展接口  
> **当前目标**：服务于 Phase 1 收口阶段的真实 sync/stream 主闭环  
> **本轮重点**：真正流式输出语义、`turnId` 贯穿消息链路、跨层共享契约从 runtime 下沉、保持 `RuntimeOrchestrator` 唯一编排中心  
> **核心约束**：runtime 只做编排和运行时协作；不直接承载 Web 协议、Provider 实现、Redis 访问细节

---

## 2. 模块定位

`vi-agent-core-runtime` 是整个 `vi-agent-core` 系统的**核心运行时层**，也是 Agent 执行逻辑的唯一编排中心。

模块目标：
- 以 `RuntimeOrchestrator` 为唯一执行总线，统一调度一次 run 的生命周期；
- 承载 Agent Loop 的同步与流式执行语义；
- 负责上下文装配、工具调用协作、Transcript 协调写回；
- 通过 Runtime Event 向 `app` 暴露内部流式结果；
- 为后续 Memory / Skill / Delegation 保留最小接口位。

模块在整体依赖链中的标准位置：

`runtime` → `model` + `common`

**当前代码中的历史治理结果**：
- `runtime` 不再承载跨层共享契约
- `LlmGateway`、`TranscriptStore`、`@AgentTool`、`ToolBundle` 已下沉到 `model`
- `infra -> runtime` 反向依赖已移除

因此，`runtime` 模块是：
- **唯一主链路编排模块**
- **Agent Loop 运行时模块**
- **Context / Tool / Event 协调中心**

但 `runtime` 模块不是：
- Web 接入层
- Provider 实现层
- Persistence 实现层
- 公共工具类模块
- 共享 SPI 永久寄存层

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）主链路编排
- `RuntimeOrchestrator`
- run 生命周期驱动
- Transcript 加载 / 写回协调
- sync / stream 主入口

#### 2）Agent Loop 执行
- `AgentLoopEngine`
- `SimpleAgentLoopEngine`

当前阶段要求：
- 支持真正的“推理 → 工具 → 再推理”
- 支持同步与流式两种执行语义
- 支持明确终止条件与 `MAX_ITERATIONS`

#### 3）上下文装配
- `ContextAssembler`
- `SimpleContextAssembler`

#### 4）工具运行时边界
- `ToolGateway`
- `DefaultToolGateway`
- `ToolRegistry`
- `ToolExecutor`

#### 5）流式事件
- `RuntimeEvent`
- `RuntimeEventType`

#### 6）扩展接口预留
- `ConversationMemoryService`
- `SkillCatalog`
- `DelegationCoordinator`

#### 7）运行结果
- `AgentExecutionResult`

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `runtime` 模块：

- Controller / HTTP / SSE 协议处理
- DeepSeek / OpenAI / Redis 等基础设施实现
- Redis / MySQL repository / mapper / entity
- Web DTO
- 通用 JSON / 校验工具
- 长期作为 `infra` 共享契约的承载地

如果一个类型需要：
- 被 `infra` 作为共享抽象实现
- 表达跨层共享契约而不是 Runtime 主循环逻辑
- 被 app/runtime/infra 多方依赖

那它不应长期留在 `runtime`，而应进入 `model`（少量纯基础能力才放 `common`）。

---

## 4. 模块内包结构约定

当前 `runtime` 模块包结构固定为：

```text
com.vi.agent.core.runtime
├── context/
├── delegation/
├── engine/
├── event/
├── memory/
├── orchestrator/
├── result/
├── skill/
└── tool/
```

推荐类分布示例与当前代码现实一致：

```text
com.vi.agent.core.runtime/
├── context/
│   ├── ContextAssembler.java
│   └── SimpleContextAssembler.java
├── delegation/
│   └── DelegationCoordinator.java
├── engine/
│   ├── AgentLoopEngine.java
│   └── SimpleAgentLoopEngine.java
├── event/
│   ├── RuntimeEvent.java
│   └── RuntimeEventType.java
├── memory/
│   └── ConversationMemoryService.java
├── orchestrator/
│   └── RuntimeOrchestrator.java
├── result/
│   └── AgentExecutionResult.java
├── skill/
│   └── SkillCatalog.java
└── tool/
    ├── DefaultToolGateway.java
    ├── ToolExecutor.java
    ├── ToolGateway.java
    └── ToolRegistry.java
```

**本轮治理结果**：
- `runtime` 已保持“只负责编排与运行时协作”的职责边界
- 跨层共享契约统一在 `model` 承载

### 4.1 `orchestrator/`
职责：
- 放唯一主编排中心
- 统一驱动 run 生命周期
- 协调 Context / Loop / Tool / Transcript / Event

约束：
- `RuntimeOrchestrator` 是唯一主链路编排中心
- 不允许 controller / application / tool executor 绕过 orchestrator 自行组织主流程
- orchestrator 负责协调，不负责实现 provider / Redis 细节

### 4.2 `engine/`
职责：
- 承载 Agent Loop 执行骨架

约束：
- `AgentLoopEngine` 负责 loop 语义，不负责 Web 输出协议
- 当前阶段必须真正支持：
  - 首次推理
  - tool call 识别
  - tool 执行
  - tool result 回填
  - 再次推理
  - 流式 token / delta 输出
- 不能把 streaming 入口包装后仍走同步 generate

### 4.3 `context/`
职责：
- 组装 working context

约束：
- 当前 `SimpleContextAssembler` 保持最小实现
- 不偷跑 Token 裁剪、Memory、RAG
- 本轮重点不是上下文治理，而是保持与 Transcript 的边界清晰

### 4.4 `tool/`
职责：
- 统一工具注册、路由与执行边界

约束：
- `ToolRegistry` 负责注册与发现
- `ToolGateway` 负责运行时路由
- `ToolExecutor` 负责最小执行适配
- 当前 mock 工具也必须走统一链路
- `ToolBundle` 由 `model.tool.ToolBundle` 统一承载

### 4.5 `memory/`
职责：
- 当前只保留最小接口位

约束：
- 不在 Phase 1 扩成完整记忆系统

### 4.6 `skill/`
职责：
- 当前只保留最小接口位

约束：
- 不在 Phase 1 扩成 Skill 配置平台

### 4.7 `delegation/`
职责：
- 当前只保留最小接口位

约束：
- 不在 Phase 1 实现完整子代理委派

---

## 5. 模块局部开发约束

### 5.1 唯一执行总线原则
- 所有主流程必须由 `RuntimeOrchestrator` 统一驱动
- 不允许出现第二套并列编排器
- 不允许 app / infra 旁路主链路

### 5.2 编排与执行分离
- orchestrator 负责总调度
- engine 负责 loop 语义
- tool gateway 负责工具边界
- event 负责运行时事件表达
- result 负责对 app 输出的运行结果表达

### 5.3 Runtime 只编排，不实现基础设施细节
- 不直接操作 Redis repository
- 不直接解析 DeepSeek 协议
- 不直接处理 HTTP / SSE 协议对象
- 不让 runtime 被基础设施实现细节污染

### 5.4 Agent Loop 规则
- 必须有真正 while loop
- 必须有 `MAX_ITERATIONS`
- 必须支持工具回填再推理
- streaming 模式下必须有 token / delta 级别事件
- 工具失败、provider 失败、超限终止要有清晰异常路径

### 5.5 Context 规则
- ContextAssembler 只负责 working context
- Transcript 是完整记录，不等于工作上下文
- 当前阶段不做 Token 裁剪与摘要治理

### 5.6 Tool Gateway 规则
- 所有工具统一经 `ToolRegistry` + `ToolGateway`
- 当前 mock 工具也不例外
- 注解命名统一使用 `@AgentTool`
- `@Tool` 历史命名不再保留

### 5.7 Memory / Skill / Delegation 预留规则
- 当前只保留最小接口签名
- 不扩成完整系统
- 只要位置清晰即可

### 5.8 Lombok 使用规则
#### 适合优先使用 Lombok 的位置
- 需要日志的运行时类：`@Slf4j`
- 简单结果对象：`@Builder`、`@Getter`
- Bean 类：`@RequiredArgsConstructor`（若运行时类进入 Spring Bean 装配场景）

#### 使用边界
- 不对带复杂状态与集合保护语义的对象粗暴使用 `@Data`

### 5.9 日志与可观测性规则
- runtime 是关键日志落点之一
- 日志应服务于链路排查、状态追踪、回放基础
- 当前开发阶段不把日志暴露问题作为阻塞项
- 但日志不能替代状态建模与 Transcript 记录

### 5.10 公共工具类使用规则
- 可以使用 `common.util.JsonUtils`、`ValidationUtils`、ID 工具
- 不在 `runtime` 内新建承载业务流程的工具类
- 一旦逻辑成为跨层共享契约，应优先下沉到 `model`

### 5.11 依赖管理规则
- `runtime` 标准依赖方向为 `runtime -> model + common`
- 不依赖 Web 协议层
- 不依赖 Provider SDK、Redis / MySQL 驱动
- 当前滞留在 `runtime` 的共享契约，本轮需下沉到 `model`
- 版本与插件统一由根 POM 管理

---

## 6. 当前阶段下的 `runtime` 模块约束

当前阶段内，`runtime` 模块允许存在：
- `RuntimeOrchestrator`
- 最小但真实可运行的 `AgentLoopEngine`
- 最小 `ContextAssembler`
- 最小 `ToolGateway` / `ToolRegistry`
- Runtime Event
- Memory / Skill / Delegation 接口预留

但不得提前引入：
- 完整长期记忆系统
- 完整 Skill 配置与发布平台
- 完整多代理 child run 系统
- Replay / Evaluation / Approval / Policy 完整治理能力
- RAG 在线 / 离线完整系统

当前阶段内，`runtime` 模块的唯一目标是：

**构建清晰、稳定、可扩展的核心运行时主闭环，并把 streaming 语义、消息链路与共享契约边界收口正确。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `RuntimeOrchestrator` | 单元测试 | 主链路协调、transcript load/save、异常路径、状态写回 |
| `AgentLoopEngine` | 单元测试 | 无工具单轮、有工具多轮、最大迭代、流式事件行为 |
| `ToolRegistry` | 单元测试 | 注解扫描、注册、查找、元数据提取 |
| `ToolGateway` / `ToolExecutor` | 单元测试 | 路由、执行、异常路径 |
| `RuntimeEvent` 语义 | 单元测试 | token / tool / complete / error 事件输出 |
| 共享契约迁移后回归 | 单元测试 | 编译期依赖关系与基本类型可用性 |

### 7.2 当前阶段测试目标
- Runtime 主闭环测试可通过
- streaming 语义有可验证测试
- 共享契约迁移不破坏主闭环

### 7.3 测试约束
- 优先写单元测试
- 除非必要，不引入完整 Spring 上下文
- Provider / Transcript 等外部协作通过 mock / fake 隔离

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 冻结规则约束。
2. 当 `runtime` 包结构、核心类职责、共享契约归属发生变化时，必须同步更新本文件。
3. 若根目录文档中出现了本模块过细实现细节，应回收到本文件。
4. 未经确认，不得改变本文件的布局与章节顺序。
5. 不得把 Web 协议、Provider 实现、Redis 细节长期写进本模块文档。

---

## 9. 一句话总结

`vi-agent-core-runtime` 的职责，是用 `RuntimeOrchestrator` 把 sync/stream 主闭环真正跑起来，同时把共享契约从 runtime 身上剥离，让运行时层重新回到“只负责编排与协作”的正确位置。
