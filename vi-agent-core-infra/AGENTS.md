# AGENTS.md

> 更新日期：2026-04-15

## 1. 文档定位

本文件定义 `vi-agent-core-infra` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `infra` 模块负责什么
- `infra` 模块不负责什么
- `infra` 模块内包结构如何约定
- 在 `infra` 模块开发时必须遵守哪些局部规则
- `infra` 模块测试应如何建设

本文件不负责：
- 仓库级协作规则（见根目录 `AGENTS.md`）
- 项目阶段规划（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准（见根目录 `CODE_REVIEW.md`）

执行 `infra` 模块相关任务前，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 本文件 `vi-agent-core-infra/AGENTS.md`

---

## 1.1 AI 代理必读速览

> **模块**：`vi-agent-core-infra` — 基础设施实现模块  
> **模块定位**：承载 provider、persistence、observability、integration 等外部能力实现  
> **当前目标**：服务于基础建设 / Phase 1 骨架阶段  
> **核心约束**：只提供实现与适配，不负责主链路编排；不得把业务流程、Agent Loop、上下文装配写进基础设施层  
> **包结构重点**：`provider`、`persistence`、`observability`、`integration`

---

## 2. 模块定位

`vi-agent-core-infra` 是整个 `vi-agent-core` 系统的**基础设施实现层**，负责封装所有与外部系统、第三方依赖、持久化存储、模型厂商交互的技术实现。

模块目标：
- 为上层 `runtime` 模块提供**稳定的、面向接口的**基础设施能力，包括 LLM 调用、持久化存储、可观测性等；
- 屏蔽不同模型厂商（如 OpenAI、DeepSeek 等）的 API 差异，通过 `LlmProvider` 统一抽象；
- 封装 H2 / MySQL、Redis、向量库等存储访问，对外暴露 Repository / Store 接口；
- 提供日志上下文、Trace、Metrics 收集等基础能力；
- 为未来邮件、日历、第三方 API、MCP 等外部系统集成预留实现落点；
- 不包含任何 Agent 核心编排逻辑（Agent Loop、工具调度、上下文装配等），这些属于 `runtime` 模块职责。

模块在整体依赖链中的位置：

`app` → `infra` → `runtime` + `model` + `common`

因此，`infra` 模块是：
- **基础设施实现模块**
- **外部系统适配模块**
- **持久化与观测接入模块**

但 `infra` 模块不是：
- Runtime 主编排模块
- Agent Loop 执行模块
- 上下文构建模块
- 业务流程模块
- Web 接入模块
- Facade 层

换句话说：

**`infra` 模块负责“实现和接线”，不负责“决策和编排”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）Provider 实现
- `LlmProvider` 的具体实现
- 模型调用请求组装
- 模型返回结果适配
- 当前阶段的 stub / fake / mock provider
- 后续真实 OpenAI / DeepSeek / 其他模型厂商接入

#### 2）Persistence 实现
- Transcript 最小存储实现
- Repository / Store 实现类
- 持久化实体与存储适配层
- 当前阶段的 InMemory 实现
- 后续 DB / 文件 / KV 等落盘实现

#### 3）Observability 实现
- 日志上下文字段承载
- 运行时基础指标收集器
- 与日志框架的基础对接
- 为后续 tracing / metrics / audit 预留实现位

#### 4）Integration 预留
- 与外部系统交互的适配实现位
- 当前阶段可为空或极简
- 未来接第三方服务、内部平台接口、MCP 客户端等能力的实现落点

#### 5）配置管理
- 基础设施相关配置项绑定
- Provider、Persistence、Observability、Integration 的配置装配入口
- 不同环境下的最小实现切换

---

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `infra` 模块：

- Runtime 主链路编排逻辑
- Agent Loop 循环控制
- Tool 选择与业务决策
- 上下文裁剪与记忆提炼
- Delegation / Skill / Policy 等控制面逻辑
- Controller / SSE 接入逻辑
- Facade Service 逻辑
- 任何“先调 provider，再自己判断业务分支”的业务流程
- 任何需要理解 session、tool call、provider、transcript 语义后才能做出的执行决策

如果一个类需要决定：
- 是否调用工具
- 何时停止 loop
- 何时写 memory
- 何时做子代理委派

那它就**不属于 `infra` 模块**。

---

## 4. 模块内包结构约定

当前 `infra` 模块包结构固定为：

```text
com.vi.agent.core.infra
├── provider/
├── persistence/
├── observability/
└── integration/
```

推荐类分布示例：

```text
com.vi.agent.core.infra/
├── provider/
│   ├── LlmProvider.java
│   ├── openai/
│   │   ├── OpenAiConfig.java
│   │   ├── OpenAiProvider.java
│   │   └── OpenAiStreamParser.java
│   ├── deepseek/
│   │   ├── DeepSeekConfig.java
│   │   └── DeepSeekProvider.java
│   └── stub/
│       └── StubLlmProvider.java
├── persistence/
│   ├── repository/
│   │   ├── ConversationRepository.java
│   │   └── TranscriptStore.java
│   ├── entity/
│   │   ├── ConversationRecord.java
│   │   ├── MessageRecord.java
│   │   └── ToolCallRecord.java
│   ├── impl/
│   │   ├── InMemoryTranscriptRepository.java
│   │   ├── JpaConversationRepository.java
│   │   └── RedisTranscriptStore.java
│   └── config/
│       └── PersistenceConfig.java
├── observability/
│   ├── TraceContext.java
│   ├── RuntimeMetricsCollector.java
│   ├── NoopRuntimeMetricsCollector.java
│   └── LoggingAdapter.java
└── integration/
    ├── email/
    ├── calendar/
    └── mcp/
```

### 4.1 `provider/`
职责：
- 承载模型调用相关接口与实现
- 负责把 runtime 给出的输入转换成模型调用请求
- 负责把模型响应转换成项目内部可用结果
- 未来可扩展 Embedding / Rerank 等模型能力，但当前阶段不强制落地

约束：
- Provider 只负责“调模型”和“适配返回”
- 不负责 Tool 选择、上下文裁剪、状态写回
- 不允许在 provider 中写业务条件分支来替代 runtime 决策
- 不允许在 provider 中写 Transcript / Memory / Context 主逻辑
- 厂商实现应下沉到对应子包中，对外暴露接口或配置装配，而不是让上层直接依赖具体类

当前阶段建议至少包含：
- `LlmProvider`
- 一个最小 stub 实现
- 后续真实厂商实现时，保留接口不变

若类名已叫 `OpenAiProvider`，但实际仍是 stub 行为，必须在代码与注释中清楚说明当前阶段定位，避免误导。

---

### 4.2 `persistence/`
职责：
- 承载持久化接口与实现
- 承载 Transcript 最小落盘实现
- 承载与存储介质相关的数据适配

约束：
- Persistence 只负责“存”和“取”
- 不负责 Runtime 编排
- 不负责 Working Context 裁剪
- 不负责业务状态迁移决策
- 不允许把 Repository 写成带复杂业务判断的“半 Service”

当前阶段建议重点包含：
- `ConversationRepository`
- `TranscriptStore`
- `InMemoryTranscriptRepository`
- `ConversationTranscriptEntity` / `ConversationRecord`
- `MessageRecord`
- `ToolCallRecord`

命名约束：
- `Repository`：表示底层持久化接口
- `Store` / `StoreService`：表示面向上层的轻量持久化服务封装
- `Entity` / `Record`：仅用于持久化映射对象，不等于 runtime model

要求：
- 上层模块不得直接使用 `JdbcTemplate` / `EntityManager` / 客户端 SDK
- 所有数据库访问必须通过 `infra` 暴露的接口完成
- 当前阶段允许 InMemory / H2 方案，后续可替换为 MySQL / Redis / 向量库实现，上层不应感知

---

### 4.3 `observability/`
职责：
- 承载最小运行时观测实现
- 管理 `traceId / runId / sessionId` 等上下文字段的承接
- 管理 metrics collector、结构化日志适配等基础骨架

约束：
- 只提供观测能力，不主导业务流程
- 不把 metrics / trace 逻辑写成业务编排
- 当前阶段允许存在 noop 实现
- 后续可扩展 tracing、cost、tool activity，但当前阶段不做重治理平台

当前阶段建议重点包含：
- `TraceContext`
- `RuntimeMetricsCollector`
- `NoopRuntimeMetricsCollector`
- `LoggingAdapter`

要求：
- `TraceContext` 必须线程安全或在当前模型下行为可控
- 若当前阶段只支持同步主链路，可先采用简单实现；后续进入更复杂的 Reactor 上下文透传时再升级
- 关键 provider 调用、持久化操作、异常路径应能挂接 trace 信息

---

### 4.4 `integration/`
职责：
- 预留未来外部系统适配的统一落点
- 用于后续接邮件、日历、第三方平台、MCP 等外部系统

约束：
- 当前阶段可以为空
- 不要为了“结构完整”硬塞无意义类
- 不要把 provider / persistence 该做的实现随意扔进 integration
- 集成客户端必须经过接口与适配层，不得散落在 `runtime` / `app` 中

---

## 5. 模块局部开发约束

### 5.1 基础设施只做实现，不做主导
`infra` 模块的所有类都必须遵守：

- **只提供能力**
- **不做编排决策**
- **不越级主导主链路**

也就是说：
- `provider` 不决定 loop
- `persistence` 不决定状态流转
- `observability` 不决定业务补偿
- `integration` 不决定 tool 选择

---

### 5.2 依赖注入与 Bean 暴露规则
- 所有对外暴露的 Bean 必须通过 `@Configuration` 配置类显式声明
- 推荐使用 `@Bean` 方法返回接口类型
- 具体实现类可以使用 `@Component`，但要控制可见性与依赖方向
- 必须使用构造器注入
- 禁止 `@Autowired` 字段注入

实现类设计建议：
- 对外以接口为边界
- 对内允许 package-private 实现类
- 避免上层直接注入具体实现

---

### 5.3 LLM Provider 实现规范
- 所有 LLM 能力必须实现 `LlmProvider`
- Provider 必须同时考虑同步调用与流式调用的扩展接口设计
- 若当前阶段暂未落地真实流式，可保留明确的接口位或最小占位实现
- Provider 内部应处理 API 调用异常，并统一转换为标准异常体系中的 Provider 相关异常
- API Key、超时、URL、模型名等配置必须通过配置体系注入，不得硬编码
- 敏感配置不入日志、不入数据库

禁止：
- 在 Provider 中直接做业务决策
- 在 Provider 中直接写 ToolGateway、TranscriptStore、MemoryService 逻辑
- 在 Provider 中绕开 runtime 主链路直接改状态

---

### 5.4 持久化规范
- Repository 接口命名应清晰、稳定、可表达意图
- 实体类字段必须有中文注释
- 当前阶段允许依赖 H2 / InMemory 快速验证
- 后续切换 MySQL / Redis / 向量库时，上层接口不应变化
- 如当前阶段尚未引入 Flyway / Liquibase，不强制提前接入，但持久化设计必须为后续演进预留空间

禁止：
- 在 Repository 中写复杂流程判断
- 在持久化层做 Working Context 构造
- 在 Store / Repository 中吞异常或偷偷降级

---

### 5.5 可观测性规则
- `traceId / runId / sessionId` 必须与运行时链路保持一致
- 所有关键基础设施操作至少应保留最小日志点：
    - provider 调用开始 / 结束 / 异常
    - persistence 存取失败
    - trace 上下文异常
- 当前阶段允许 `RuntimeMetricsCollector` 保持简单或 noop，但接口位置必须清晰
- 后续对接 Micrometer / tracing 系统时，应优先在 `infra.observability` 扩展，而不是散落到各层

---

### 5.6 Lombok 使用规则
`infra` 模块允许并鼓励使用 Lombok 消除样板代码，但必须按场景使用。

#### 适合优先使用 Lombok 的位置
- 实现类中的 logger：优先 `@Slf4j`
- Spring Bean：优先 `@RequiredArgsConstructor`
- 简单持久化对象 / 配置对象 / 轻量上下文对象：
    - `@Getter`
    - `@Setter`
    - `@NoArgsConstructor`
    - `@AllArgsConstructor`
    - `@Builder`
    - `@Data`（谨慎）

#### 使用边界
- 对简单承载对象，可优先让 Lombok 替代无参构造、全参构造、getter、setter
- 对带行为和封装边界的类，不要机械使用 `@Data`
- 对实现类，Lombok 只能简化样板，不能改变模块职责

---

### 5.7 日志规则
`infra` 模块是日志重点落点之一，但日志必须服务于排查。

要求：
- 统一使用 SLF4J
- 需要日志的实现类优先使用 `@Slf4j`
- 关键日志重点放在：
    - provider 调用开始 / 结束 / 失败
    - persistence 关键存取失败
    - observability 上下文异常
- 禁止使用 `System.out.println`

注意：
- 不要在 provider 中打印完整敏感请求
- 不要在 persistence 中无节制打印整段 transcript
- 日志要围绕排查、耗时、错误定位，而不是替代业务状态

---

### 5.8 配置管理规则
- 使用 `@ConfigurationProperties` 将基础设施配置分组
- 配置类应放置在对应子包的 `config/` 目录下
- 敏感配置（如 API Key）必须从环境变量或安全配置读取，不得在代码中提供真实默认值
- 当前阶段如仍使用简单配置方式，也必须为后续分组化配置保留演进空间

---

### 5.9 公共工具类使用规则
`infra` 模块允许使用 `common.util` 下的公共工具类，例如：
- `JsonUtils`
- 校验工具
- ID 生成工具

但：
- 不得在 `infra` 模块内部新建承载业务流程的工具类
- 若出现跨类复用的无状态逻辑，应优先评估是否应沉淀到 `common`
- JSON 转换优先统一到 `JsonUtils`
- 不要在 provider / persistence / observability 中重复手写 JSON 样板逻辑

---

### 5.10 依赖管理规则
`infra` 模块可以依赖必要三方库，但必须克制。

允许引入：
- 模型 SDK
- Jackson
- Lombok
- 日志相关基础库
- 最小持久化相关依赖

禁止随意引入：
- 与当前阶段无关的大型框架
- 重型 workflow / queue / vector / policy 系统
- 仅为未来预留但当前完全不用的复杂依赖

原则：
- 依赖必须服务于当前阶段可运行目标
- 不要为了“以后可能用”而提前堆依赖

---

## 6. 当前阶段下的 `infra` 模块约束

当前阶段内，`infra` 模块允许存在：
- 最小 provider 实现
- 最小 Transcript 存储实现
- 最小 observability 骨架
- 最小 integration 预留位

但不得提前引入：
- 真实复杂多模型路由
- 长期记忆存储系统
- RAG 向量库接入
- 审批 / 回放 / 评估平台
- 复杂外部系统集成

当前阶段内，`infra` 模块的唯一目标是：

**为 Runtime Core 提供最小、稳定、清晰的基础设施实现支撑。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景

| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `LlmProvider` stub / 最小实现 | 单元测试 + 集成测试 | 验证基本输入输出、最小流式接口行为或工具调用信号 |
| `ConversationRepository` / `TranscriptStore` | 单元测试 / 集成测试 | 验证保存、读取、覆盖、空值与不存在场景 |
| `TranscriptStoreService` | 单元测试 | 验证对 repository / store 的轻量封装行为 |
| `TraceContext` / `RuntimeMetricsCollector` | 单元测试 | 验证最小行为、noop 稳定性、上下文承接基本能力 |
| 配置类绑定 | 单元测试 | 验证配置项能正确绑定 |

### 7.2 当前阶段测试目标
- 至少一个 `LlmProvider` 最小实现通过测试
- Transcript 最小存取实现通过测试
- Observability 最小骨架行为通过测试
- `mvn test` 可通过

### 7.3 测试约束
- 优先写单元测试
- 除非必要，不引入完整 Spring 上下文
- 对真实外部 API 的测试应做隔离，不让 CI 强依赖密钥与外部服务
- 对 stub / fake / in-memory 实现，测试必须明确其当前阶段定位
- 不要为简单 getter / setter 机械补测试
- 要重点覆盖：
    - 异常路径
    - 边界输入
    - 存取行为
    - provider 最小行为
    - 观测骨架最小能力

---

## 8. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `runtime` / `app` / `model` / `common` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-infra` 的职责是为整个 Agent Runtime 系统提供**最小、稳定、可替换的基础设施实现能力**；它负责 provider、persistence、observability 与 integration 的实现，但绝不主导 `RuntimeOrchestrator` 所负责的主链路执行边界。
