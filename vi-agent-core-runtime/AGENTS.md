# AGENTS.md

> 更新日期：2026-04-16

## 1. 文档定位

本文件定义 `vi-agent-core-runtime` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `runtime` 模块负责什么
- `runtime` 模块不负责什么
- `runtime` 模块内包结构如何约定
- 在 `runtime` 模块开发时必须遵守哪些局部规则
- `runtime` 模块测试应如何建设

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
> **模块定位**：承载 `RuntimeOrchestrator`、Agent Loop、Context、Tool、Memory、Skill、Delegation 的核心接口与编排骨架  
> **当前目标**：服务于 Phase 1 主链路闭环实现阶段  
> **核心约束**：`RuntimeOrchestrator` 是唯一主链路编排中心；运行时只做编排和协作，不直接承载 Web 协议和基础设施实现细节  
> **包结构重点**：`orchestrator`、`engine`、`context`、`tool`、`memory`、`skill`、`delegation`

---

## 2. 模块定位

`vi-agent-core-runtime` 是整个 `vi-agent-core` 系统的**核心运行时层**，也是 Agent 执行逻辑的唯一编排中心。

模块目标：
- 以 `RuntimeOrchestrator` 为唯一执行总线，统一调度一次 run 的完整生命周期；
- 承载 Agent Loop 的真实执行逻辑，而不再停留在骨架占位；
- 负责上下文装配、工具调用协作、最小状态写回协调；
- 定义并维护 `ToolGateway` 作为工具调用的统一运行时边界；
- 为后续 `MemoryService`、`DelegationService`、`SkillRegistry` 等能力提供稳定扩展点；
- 保证上层 `app` 只做接入和输出，下层 `infra` 只做实现和适配。

模块在整体依赖链中的位置：

`app` → `runtime` ← `infra`  
`runtime` 同时依赖 `model` + `common`

因此，`runtime` 模块是：
- **唯一主链路编排模块**
- **Agent Runtime 核心模块**
- **上下文、工具、状态协作中心**

但 `runtime` 模块不是：
- Web 接入模块
- Provider 实现模块
- Repository 实现模块
- 公共工具类模块
- 持久化实体模块

换句话说：

**`runtime` 模块负责“编排和执行协调”，不负责“协议接入”和“基础设施实现”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）主链路编排
- `RuntimeOrchestrator`
- 一次 run 的生命周期驱动
- 输入 → 上下文组装 → 模型推理 → 工具调用 → 再推理 → 结果输出 → 状态写回的主链路协调

#### 2）Agent Loop 执行骨架
- `AgentLoopEngine`
- `StreamAgentLoopEngine`
- 同步与流式执行链路的内部运行机制

当前阶段要求：
- Default loop 必须真正支持“推理 → 工具 → 再推理”
- 支持明确的终止条件与 `MAX_ITERATIONS`
- sync / stream 共享同一套 loop 语义

#### 3）上下文装配骨架
- `ContextAssembler`
- `SimpleContextAssembler`
- 当前阶段的最小上下文拼装逻辑

#### 4）工具网关骨架
- `ToolGateway`
- `ToolRegistry`
- `@AgentTool`
- 工具统一注册、路由和结果回填的运行时边界

#### 5）扩展接口预留
- `MemoryService`
- `SkillRegistry`
- `DelegationService`

这些能力当前阶段可只保留接口或最小桩，但位置必须清晰。

#### 6）运行时状态协调
- run 标识协调
- loop 迭代状态
- 最小 transcript / tool call / tool result 协作写回触发
- 为后续 replay / evaluation / harness 能力预留最小结构位

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `runtime` 模块：

- Controller / HTTP / SSE 协议处理
- Provider SDK 具体实现
- Repository / Store 具体实现
- 数据库、Redis、向量库等访问细节
- 通用 JSON / String / Validation 工具
- 持久化 Entity / Record
- 复杂产品化 UI 逻辑

严格来说，`runtime` 模块不能：
- 直接 `new` 外部客户端
- 直接操作 Web 请求对象
- 直接承担持久化实现细节
- 直接把基础设施实现写死在编排逻辑里

如果一个类需要：
- 处理 HTTP 协议
- 调用 OpenAI / DeepSeek SDK 细节
- 直接访问数据库客户端
- 直接写日志配置文件策略

那它就**不属于 `runtime` 模块**。

---

## 4. 模块内包结构约定

当前 `runtime` 模块包结构固定为：

```text
com.vi.agent.core.runtime
├── context/
├── delegation/
├── engine/
├── memory/
├── orchestrator/
├── skill/
└── tool/
```

推荐类分布示例：

```text
com.vi.agent.core.runtime/
├── context/
│   ├── ContextAssembler.java
│   └── SimpleContextAssembler.java
├── delegation/
│   ├── DelegationService.java
│   └── NoOpDelegationService.java
├── engine/
│   ├── AgentLoopEngine.java
│   ├── DefaultAgentLoopEngine.java
│   ├── StreamAgentLoopEngine.java
│   └── DefaultStreamAgentLoopEngine.java
├── memory/
│   ├── MemoryService.java
│   └── NoOpMemoryService.java
├── orchestrator/
│   └── RuntimeOrchestrator.java
├── skill/
│   ├── SkillRegistry.java
│   └── NoOpSkillRegistry.java
└── tool/
    ├── ToolGateway.java
    ├── DefaultToolGateway.java
    ├── ToolRegistry.java
    ├── ToolExecutor.java
    └── annotation/
        └── AgentTool.java
```

说明：
- 当前阶段以**最小但真实可运行的闭环**为主，不强制一开始就拆成“接口 + Impl”双文件模式；
- 若后续某个能力需要明确接口与实现分离，可在不破坏整体结构的前提下演进；
- 当前项目中对外运行时边界更重要，文件数量不是目标；
- 如需引入 `LlmGateway`、`TranscriptStore`、runtime stream listener / event 等端口或对象，应放在不破坏当前结构与职责边界的前提下补齐。

### 4.1 `orchestrator/`
职责：
- 放唯一主编排中心
- 统一驱动 run 生命周期
- 负责把 Context / Loop / Tool / Transcript 等能力串起来

约束：
- `RuntimeOrchestrator` 是唯一主链路编排中心
- 不允许再出现并列的“第二编排器”
- 不允许 controller / service / tool executor 绕过 orchestrator 自行组织主流程
- orchestrator 负责总调度、transcript load/save、结果汇总；但不应深入实现 provider / persistence 细节

### 4.2 `engine/`
职责：
- 承载 Agent Loop 的执行骨架
- 区分同步执行与流式执行的内部机制

约束：
- `engine` 只负责 loop 运行，不负责 Web 输出协议
- `AgentLoopEngine` 不应变成“大而全 chat service”
- `StreamAgentLoopEngine` 不负责 SSE 序列化，它只负责运行时流式结果的产生
- 必须有明确终止条件、最大迭代次数与异常路径
- 当前阶段的默认实现必须真正支持：
    - 第一次推理
    - tool call 识别
    - tool 执行
    - tool result 回填
    - 再次推理形成最终回答

### 4.3 `context/`
职责：
- 承载 working context 的构建接口与当前阶段最小实现
- 表达“真正送进模型的上下文”，而不是完整 transcript

约束：
- `ContextAssembler` 是运行时上下文构建边界
- 当前阶段 `SimpleContextAssembler` 可只做最小实现
- 不允许把完整 Transcript / Memory / RAG 逻辑硬塞进 `SimpleContextAssembler`
- 未来裁剪、summary、memory recall 可以扩展，但当前阶段不要偷跑完整实现

当前阶段装配顺序建议保持：
- system prompt
- 历史消息
- 当前用户消息
- 当前阶段允许全量历史简单拼接，不做 Token 裁剪

### 4.4 `tool/`
职责：
- 承载工具注册、工具路由、工具调用回填的运行时边界
- 统一让工具接入 Agent Loop

约束：
- `ToolGateway` 是运行时内部统一工具边界
- `ToolRegistry` 负责注册与发现，不负责真实外部调用实现
- `ToolExecutor` 负责最小执行调度，但不承担主流程编排
- `@AgentTool` 是项目运行时工具声明注解，不等同于外部框架默认注解
- 不允许 controller / provider / repository 直接替代 ToolGateway 做工具调度
- 当前阶段必须从手工注册过渡到统一注册机制；mock 工具也必须走统一注册与 gateway

### 4.5 `memory/`
职责：
- 当前阶段承载记忆服务接口预留
- 为后续长期记忆、会话摘要、偏好召回预留稳定入口

约束：
- 当前阶段可只放接口和 NoOp 实现
- 不要提前实现完整长期记忆体系
- 不要把 Transcript 当 Memory
- 不要在 Memory 接口层混入持久化实现

### 4.6 `skill/`
职责：
- 当前阶段承载技能注册接口预留
- 为后续 skill 注入和技能配置提供清晰边界

约束：
- 当前阶段可只放接口和 NoOp 实现
- 不要提前做复杂 skill 发布、版本管理、页面化配置
- SkillRegistry 只作为能力入口，不承载控制面平台逻辑

### 4.7 `delegation/`
职责：
- 当前阶段承载受控委派接口预留
- 为后续子代理运行、受控委派、最小上下文隔离预留边界

约束：
- 当前阶段可只放接口和 NoOp 实现
- 不要提前实现多代理 swarm 或复杂 child run 管理
- DelegationService 只预留受控委派边界，不提前做完整自治系统

---

## 5. 模块局部开发约束

### 5.1 唯一执行总线原则
`RuntimeOrchestrator` 是全系统唯一主链路编排中心。

强约束：
- 任何一次用户请求都必须通过 `RuntimeOrchestrator`
- 不允许 `ChatService`、`ToolGateway`、`Provider`、`Repository` 自己拼出一条主流程
- 不允许出现“临时方便”的旁路执行逻辑
- 任何后续新增能力，都必须先思考它在 orchestrator 链路中的位置

### 5.2 编排与执行分离
- `RuntimeOrchestrator` 负责创建 run、管理 run 生命周期、协调各组件调用顺序
- `AgentLoopEngine` 负责具体循环内的模型调用与工具交互
- 两者职责不可混淆
- `RuntimeOrchestrator` 可以协调 loop 启停、transcript load/save、结果汇总，但不应把 loop 细节和 provider/persistence 细节揉成一个类
- `AgentLoopEngine` 不得越权管理完整状态落盘和外部输出协议

也就是说：

**orchestrator 负责总调度，engine 负责 loop 内核。**

### 5.3 Runtime 只编排，不实现基础设施细节
`runtime` 模块可以依赖接口和抽象协作对象，但不能自己落实现有基础设施细节。

例如：
- 可以调用 `LlmProvider` / `LlmGateway`
- 可以调用 `TranscriptStore`
- 可以调用 `RuntimeMetricsCollector`

但：
- 不应该自己 new DeepSeek / OpenAI SDK
- 不应该自己写 Redis / MySQL / 文件存储
- 不应该直接承担日志配置策略

### 5.4 Agent Loop 规则
`AgentLoopEngine` 的职责是统一执行最小 loop。

当前阶段要求：
- 支持最小同步执行链路
- 为流式执行保留明确边界
- 至少能清晰表达：
    - 第一次推理
    - 工具调用识别
    - 工具结果回流
    - 再次推理
    - 最终结果形成

实现约束：
- 必须设置 `MAX_ITERATIONS` 或等价最大迭代限制
- 工具调用失败时应有明确终止或异常策略
- 流式与非流式应尽量共享同一套 loop 语义
- 不得把 Loop 逻辑散落到 `ChatService`、`ToolGateway` 或 provider 中
- 当前阶段应明确区分 planning assistant message 与最终 assistant message

### 5.5 Context 规则
- `context` 负责 working context，不等于 transcript
- 当前阶段可简单，但边界必须清楚
- 不要把 transcript 全量堆到任何地方都叫 context
- 当前阶段即便不做 token 裁剪，也必须保留未来升级的演进位

未来可以升级的方向：
- token budget
- history truncation
- summary
- memory injection
- skill injection
- retrieval evidence injection

但当前阶段不要提前把这些重型能力做进 `SimpleContextAssembler`

### 5.6 Tool Gateway 规则
- 所有工具调用必须统一经过 `ToolGateway`
- `ToolRegistry` 负责工具发现与注册
- 真实调用实现可依赖下层能力，但运行时入口必须统一
- 工具结果应标准化成内部 `ToolResult`
- 工具调用记录必须能进入 transcript / run 记录链路

Phase 1 约束：
- `ToolRegistry` 启动时应基于注解扫描或统一注册机制形成最小工具表
- `@AgentTool` 至少应具备 `name`、`description`
- mock 只读工具是 Phase 1 正式闭环验证手段，但不是旁路临时代码
- `ToolGateway` 和 `ToolRegistry` 不能越界承担 orchestrator 职责

禁止：
- controller/service 直接执行工具
- provider 直接冒充工具调用器
- tool executor 绕过 runtime 直接写会话状态

### 5.7 Memory / Skill / Delegation 预留规则
当前阶段这三块主要做**边界预留**，不是完整实现。

要求：
- 接口位置必须清晰
- 语义必须准确
- 当前阶段可提供 NoOp 实现，便于装配和测试
- 不要因为当前未实现就乱写占位类
- 不要把完整控制面 / 多代理 / 长期记忆系统偷跑进来

也就是说：
- `MemoryService`：只定义最小记忆读写边界
- `SkillRegistry`：只定义最小技能查询边界
- `DelegationService`：只定义最小委派边界

### 5.8 Lombok 使用规则
`runtime` 模块允许并鼓励使用 Lombok 消除样板代码，但必须保持运行时边界清晰。

#### 适合优先使用 Lombok 的位置
- Spring Bean：`@RequiredArgsConstructor`
- 需要日志的类：`@Slf4j`
- 简单状态对象：`@Getter`、`@Builder`

#### 使用边界
- 不要为了省事在 orchestrator、engine 这类核心对象上粗暴使用 `@Data`
- 核心编排对象优先可读性和封装清晰度

### 5.9 日志与可观测性规则
`runtime` 模块是日志重点模块之一。

要求：
- 统一通过 SLF4J 输出
- 关键类优先使用 `@Slf4j`
- 关键日志应覆盖：
    - run 开始 / 结束
    - `traceId` / `runId` / `sessionId` / `conversationId`
    - context 组装关键节点
    - tool call 识别 / 执行回流
    - loop 结束条件
    - 关键异常路径
- 每轮 loop 至少应能追踪：
    - iteration 次数
    - 是否触发 tool call
    - 是否终止
    - 是否异常

禁止：
- `System.out.println`
- 把日志写成业务判断
- 无节制打印全量消息历史或大对象

日志要服务于：
- 排查
- 回放基础
- 最小可观测性
- 后续 harness 工程接入

### 5.10 公共工具类使用规则
`runtime` 模块允许使用 `common.util` 下的公共工具类，例如：
- `JsonUtils`
- 校验工具
- ID 工具

但：
- 不得在 `runtime` 模块里新增承载业务流程的工具类
- 若出现跨类复用的无状态逻辑，应优先评估是否应沉淀到 `common`
- `runtime` 里不应该出现“大量静态工具类拼流程”的情况

### 5.11 依赖管理规则
`runtime` 模块依赖必须克制。

允许依赖：
- `model`
- `common`
- 基础接口依赖
- 当前阶段最小运行所需框架依赖

禁止随意引入：
- 具体模型厂商 SDK
- Redis / MySQL 驱动
- Web 协议层依赖
- 与当前阶段无关的大型 workflow / queue / policy / vector 系统

原则：
- `runtime` 依赖抽象，不依赖实现细节
- 保持模块纯度，避免被基础设施细节污染

---

## 6. 当前阶段下的 `runtime` 模块约束
当前阶段内，`runtime` 模块允许存在：
- `RuntimeOrchestrator`
- 最小但真实可运行的 `AgentLoopEngine`
- 最小 `StreamAgentLoopEngine`
- 最小 `ContextAssembler`
- 最小 `ToolGateway` / `ToolRegistry`
- `MemoryService` / `SkillRegistry` / `DelegationService` 接口预留与 NoOp 实现

但不得提前引入：
- 完整长期记忆系统
- 完整 skill 配置与发布平台
- 完整多代理 child run 系统
- Replay / Evaluation / Approval / Policy 完整治理能力
- RAG 在线 / 离线完整系统

当前阶段内，`runtime` 模块的唯一目标是：

**构建清晰、稳定、可扩展的核心运行时主闭环，并在不偷跑高阶段功能的前提下完成 Phase 1 真正的 sync/stream 主链路。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `RuntimeOrchestrator` | 单元测试 / 集成测试 | 覆盖主链路协调、transcript load/save、异常路径、最小状态写回触发 |
| `AgentLoopEngine` | 单元测试 | 覆盖无工具调用时正常返回、有工具调用时回填再推理、结束条件、最大迭代限制 |
| `ToolRegistry` | 单元测试 | 覆盖工具注册、查找、注解元数据提取 |
| `ToolGateway` / `ToolExecutor` | 单元测试 | 覆盖最小路由、执行、结果标准化、缺失工具与异常路径 |
| `SimpleContextAssembler` | 单元测试 | 覆盖最小上下文组装行为 |
| `StreamAgentLoopEngine` | 单元测试 / 响应式测试 | 覆盖最小流式行为、取消信号处理 |

### 7.2 当前阶段测试目标
- `runtime` 模块核心主闭环测试可通过
- `mvn test` 可通过
- 不追求形式化覆盖率数字，但必须覆盖：
    - 主链路协调
    - Loop 最小行为
    - Tool Gateway 最小行为
    - Context 最小行为
    - 关键异常路径
    - 超最大轮次保护

### 7.3 测试约束
- 优先写单元测试
- 除非必要，不引入完整 Spring 上下文
- 涉及 `LlmProvider`、Transcript 持久化等外部协作时，应使用 mock / stub / fake
- 对当前阶段的 NoOp / fake / mock 协作要明确说明其阶段定位
- 不要为简单 getter / setter 机械补测试
- 响应式测试优先使用 `StepVerifier`

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `app` / `infra` / `model` / `common` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-runtime` 的职责是为整个 Agent Runtime 系统提供**唯一、稳定、可扩展的核心执行总线与运行时骨架**；当前 Phase 1 的首要任务是把 orchestrator、agent loop、context 与统一工具管道真正做成可运行闭环，而不是继续停留在骨架或预留位层面。
