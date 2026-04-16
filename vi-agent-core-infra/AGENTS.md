# AGENTS.md

> 更新日期：2026-04-16

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
> **模块定位**：承载 provider、persistence、observability、integration 等基础设施实现与适配  
> **当前目标**：服务于 Phase 1 基础设施真实接入阶段，重点完成 `DeepSeekProvider`、Redis Transcript 与最小可观测性贯穿  
> **核心约束**：只做实现与适配，不做主链路编排；不反客为主变成“第二 runtime”  
> **包结构重点**：`provider`、`persistence`、`observability`、`integration`

---

## 2. 模块定位

`vi-agent-core-infra` 是整个 `vi-agent-core` 系统的**基础设施实现层**，负责承接 `runtime` 所依赖的模型调用、持久化、可观测性与外部系统适配实现。

模块目标：
- 为 `runtime` 提供最小、稳定、可替换的 Provider 实现；
- 为 `runtime` 提供 Transcript 存储与最小恢复能力；
- 为全链路提供最小日志、Trace、Metrics 承接位；
- 为未来外部系统和第三方能力接入预留清晰的 integration 边界。

模块在整体依赖链中的位置：

`app` → `infra` ← `runtime`  
`infra` 依赖 `runtime` + `model` + `common`

因此，`infra` 模块是：
- **基础设施实现层**
- **Provider / Persistence / Observability 承接层**
- **外部依赖适配层**

但 `infra` 模块不是：
- Runtime 主编排层
- Web 接入层
- 公共工具类层
- 内部模型定义层

换句话说：

**`infra` 模块负责“实现与适配”，不负责“决定主流程”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）Provider 实现
- `DeepSeekProvider`
- `OpenAiProvider`（保留扩展位）
- Provider 配置绑定与 SDK 适配
- 同步 / 流式模型调用实现

#### 2）Persistence 实现
- `TranscriptStore` 的 Redis 实现
- Transcript 序列化 / 反序列化映射
- 最小 Artifact / Repository 预留位

#### 3）Observability 实现
- `TraceContext`
- `RuntimeMetricsCollector`
- 最小日志上下文字段承接
- 为后续 Micrometer / tracing 体系预留接缝

#### 4）Integration 预留
- 未来外部系统与第三方服务接入适配点
- 当前阶段只保留边界，不做复杂集成

#### 5）配置管理
- DeepSeek / Redis / observability 等配置对象
- 配置绑定、默认值与环境变量接入

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `infra` 模块：

- Runtime 主编排逻辑
- Agent Loop while 循环控制
- Controller / WebFlux 接口
- Web DTO
- 公共无状态工具类
- 业务流程决策

严格来说，`infra` 模块不能：
- 直接决定“是否继续下一轮推理”
- 直接承担 `RuntimeOrchestrator` 的职责
- 直接把 Web 协议对象拉进 provider / persistence 实现
- 在 provider / persistence 内拼出一条完整业务主流程

如果一个类需要：
- 管理 run 生命周期
- 决定 tool use / 二次推理
- 处理 HTTP / SSE 协议细节

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
│   ├── DeepSeekProvider.java
│   ├── OpenAiProvider.java
│   └── config/
│       └── DeepSeekProperties.java
├── persistence/
│   ├── RedisTranscriptStore.java
│   ├── TranscriptRedisMapper.java
│   └── config/
│       └── RedisTranscriptProperties.java
├── observability/
│   ├── TraceContext.java
│   └── RuntimeMetricsCollector.java
└── integration/
    └── placeholder/
```

### 4.1 `provider/`
职责：
- 承载模型厂商适配与 Provider 实现
- 屏蔽 DeepSeek / OpenAI 等厂商差异

约束：
- 当前主 Provider 为 `DeepSeekProvider`
- `OpenAiProvider` 保留但不是当前主验收目标
- provider 只负责模型调用与结果解析，不做 tool routing / transcript persistence / runtime loop
- 同步调用先打通，流式能力随后服务于 streaming 主链路

### 4.2 `persistence/`
职责：
- 承载 Transcript / Artifact / Repository 等最小持久化实现
- 当前阶段重点为 Redis Transcript 短期存储

约束：
- Phase 1 正式短期方案为 Redis Hash
- 当前主推荐口径：
    - key：`transcript:{sessionId}`
    - field：`conversationId`、`traceId`、`runId`、`messages`、`toolCalls`、`toolResults`、`updatedAt`
    - value：标量字段存字符串；复杂字段存 JSON 字符串
- Redis 负责近期上下文与最小恢复；MySQL 作为未来长期持久化层，不在本轮落地
- persistence 负责映射与存取实现，不负责 run 流程决策

### 4.3 `observability/`
职责：
- 承载最小日志上下文、trace、metrics 适配
- 为 provider / persistence / runtime 协作链路提供最小观测支撑

约束：
- observability 负责上下文字段承接与统计，不主导业务流程
- provider / persistence 异常路径应尽量带上 `traceId`、`runId`、`sessionId`、`conversationId`
- MDC 绑定等实现细节放在 observability，不放到 `common.id`

### 4.4 `integration/`
职责：
- 为未来第三方服务、MCP、企业内部系统接入预留边界

约束：
- 当前阶段只保留最小接缝，不做复杂真实系统集成
- 不要把 integration 写成“大杂烩适配层”

---

## 5. 模块局部开发约束

### 5.1 基础设施只做实现，不做主导
- `infra` 只实现 `runtime` 需要的能力，不决定 `runtime` 主流程
- 不得在 provider / persistence 中偷偷拼 loop 或编排流程
- 不允许 `infra` 反客为主成为“第二 runtime”

### 5.2 依赖注入与 Bean 暴露规则
- 一律使用构造器注入
- Spring Bean 优先使用 `@RequiredArgsConstructor`
- 通过配置类或 `@ConfigurationProperties` 暴露 Provider / Redis 相关配置
- 不在实现类内部手工 new 外部依赖客户端

### 5.3 LLM Provider 实现规范
- 当前主 Provider 以 DeepSeek API 为准
- 要覆盖普通 assistant 回复解析与 tool call 解析
- 配置必须通过配置对象与环境变量注入，不写真实密钥默认值
- Provider 异常必须映射为标准异常，不直接向上抛裸 SDK 异常
- `OpenAiProvider` 可保留占位或扩展位，但不要把本轮交付重点转移到 OpenAI

### 5.4 持久化规范
- 当前 Transcript 主实现为 Redis Hash
- Redis 负责短期上下文与最小恢复，不等于长期审计存储
- 未来 MySQL 负责长期持久化、治理、审计、可恢复数据
- entity / record 与 `model` 内部对象必须严格分离
- 不要把 working context 落成 transcript 的替代品

### 5.5 可观测性规则
- `infra` 是日志重点落点之一
- Provider 调用开始 / 结束 / 失败、Redis 关键存取失败、上下文注入异常都应有明确日志
- 不要打印完整敏感请求或完整 transcript
- 允许 `RuntimeMetricsCollector` 保持简单或 noop，但接口位置必须清晰
- 后续对接 Micrometer / tracing 系统时，应优先在 `infra.observability` 扩展，而不是散落到各层

### 5.6 Lombok 使用规则
`infra` 模块允许并鼓励使用 Lombok 消除样板代码，但必须按场景使用。

#### 适合优先使用 Lombok 的位置
- Spring Bean：`@RequiredArgsConstructor`
- 需要日志的实现类：`@Slf4j`
- 简单配置对象：`@Getter`、`@Setter`、`@Data`

#### 使用边界
- Provider / persistence 核心实现中，不要为省事滥用 `@Data`
- 对有封装和保护需求的对象，优先选择更细粒度的 Lombok 组合

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

### 5.8 配置管理规则
- 使用 `@ConfigurationProperties` 将基础设施配置分组
- 配置类应放置在对应子包的 `config/` 目录下
- 敏感配置（如 API Key）必须从环境变量或安全配置读取，不得在代码中提供真实默认值
- 当前阶段即便使用简单配置方式，也必须为后续分组化配置保留演进空间

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

### 5.10 依赖管理规则
`infra` 模块可以依赖必要三方库，但必须克制。

允许引入：
- 模型 SDK
- Redis 客户端
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
- `DeepSeekProvider`
- `OpenAiProvider` 扩展位
- Redis Transcript 最小实现
- 最小 observability 骨架
- 最小 integration 预留位

但不得提前引入：
- 真实复杂多模型路由
- MySQL Transcript 持久化主实现
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
| `DeepSeekProvider` | 单元测试 + 隔离集成测试 | 验证普通回复解析、tool call 解析、异常路径 |
| `OpenAiProvider` 扩展位 | 单元测试 | 验证最小占位行为或配置加载 |
| `RedisTranscriptStore` | 单元测试 / 集成测试 | 验证保存、读取、覆盖、空值与不存在场景 |
| `TranscriptRedisMapper` | 单元测试 | 验证 Redis Hash 与 Transcript 的映射 |
| `TraceContext` / `RuntimeMetricsCollector` | 单元测试 | 验证最小行为、noop 稳定性、上下文承接基本能力 |
| 配置类绑定 | 单元测试 | 验证 DeepSeek、Redis 等配置项能正确绑定 |

### 7.2 当前阶段测试目标
- `DeepSeekProvider` 通过测试
- Redis Transcript 最小存取实现通过测试
- Observability 最小骨架行为通过测试
- `mvn test` 可通过

### 7.3 测试约束
- 优先写单元测试
- 除非必要，不引入完整 Spring 上下文
- 对真实外部 API 的测试应做隔离，不让 CI 强依赖密钥与外部服务
- Redis 测试应明确使用嵌入式、容器化或可替代方案，避免测试不稳定
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

`vi-agent-core-infra` 的职责是为整个 Agent Runtime 系统提供**最小、稳定、可替换的基础设施实现能力**；当前 Phase 1 以 `DeepSeekProvider`、Redis Transcript 和最小 observability 为主落地对象，但绝不主导 `RuntimeOrchestrator` 所负责的主链路执行边界。
