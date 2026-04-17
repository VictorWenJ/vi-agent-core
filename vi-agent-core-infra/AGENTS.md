# AGENTS.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core-infra` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `infra` 模块负责什么
- `infra` 模块不负责什么
- `infra` 模块内包结构如何约定
- 在 `infra` 模块开发时必须遵守哪些局部规则
- `infra` 模块测试与依赖应如何建设

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
> **模块定位**：承载 Provider、Persistence、Observability、Mock Integration 等具体实现  
> **当前目标**：服务于 Phase 1 收口阶段的真实 provider streaming、Redis Transcript、依赖治理与 POM 标准化  
> **本轮重点**：DeepSeek streaming 解析、Redis Transcript 保持最小正式实现、移除 `infra -> runtime` 反向依赖、保持 `MockReadOnlyTools` 通过统一注册链路接入  
> **核心约束**：`infra` 只做实现与适配，不做主链路编排；共享契约不得长期依赖 `runtime`

---

## 2. 模块定位

`vi-agent-core-infra` 是整个 `vi-agent-core` 系统的**基础设施实现层**。

模块目标：
- 提供 Provider 具体实现；
- 提供 Transcript 存储相关的 repository / mapper / adapter；
- 提供 observability 的最小骨架；
- 提供当前阶段的 mock integration 能力；
- 保持对 runtime 的实现支撑，但不主导运行流程。

模块在整体依赖链中的标准位置：

`infra` → `model` + `common`

**当前代码中的历史例外**：
- 历史上的 `infra -> runtime` 反向依赖已移除
- 共享契约已统一下沉到 `model`，`infra` 当前仅依赖 `model/common`

因此，`infra` 模块是：
- **Provider 实现层**
- **Persistence 实现层**
- **Observability 骨架层**
- **Mock Integration 层**

但 `infra` 模块不是：
- 主编排层
- Web 接入层
- 共享契约定义层
- 业务工具箱

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）Provider 实现
- `DeepSeekChatProvider`
- `OpenAIChatProvider`
- `DoubaoChatProvider`
- `OpenAICompatibleChatProvider`
- `LlmHttpExecutor` / `JdkLlmHttpExecutor`

#### 2）Persistence 实现
- `TranscriptRepository`
- `RedisTranscriptRepository`
- `InMemoryTranscriptRepository`（当前只可作为过渡或测试辅助，不再视为正式实现）
- `RedisTranscriptMapper`
- `TranscriptStoreAdapter`

#### 3）Observability 实现
- `TraceContext`
- `RuntimeMetricsCollector`
- `NoopRuntimeMetricsCollector`

#### 4）Integration（当前以 mock 为主）
- `MockReadOnlyTools`

#### 5）配置模型
- provider `config/`
- persistence `config/`

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `infra` 模块：

- `RuntimeOrchestrator` 主循环
- Agent Loop 流程控制
- WebFlux Controller / SSE 协议适配
- Web DTO
- 根 POM 的聚合与插件总配置
- 运行时共享契约的长期定义位置

如果一个类型需要：
- 决定 run 生命周期
- 直接参与 Runtime 编排
- 被 `runtime` 与 `infra` 同时作为抽象契约依赖

那它不应该长期留在 `infra`，也不应继续挂在 `runtime`，而应下沉到 `model`（少量基础能力才可放 `common`）。

---

## 4. 模块内包结构约定

当前 `infra` 模块包结构固定为：

```text
com.vi.agent.core.infra
├── integration/
│   └── mock/
├── observability/
├── persistence/
│   ├── adapter/
│   ├── config/
│   ├── entity/
│   ├── mapper/
│   └── repository/
└── provider/
    ├── base/
    ├── config/
    ├── factory/
    ├── http/
    └── protocol/
        └── openai/
```

推荐类分布示例与当前代码现实一致：

```text
com.vi.agent.core.infra/
├── integration/mock/
│   └── MockReadOnlyTools.java
├── observability/
│   ├── TraceContext.java
│   ├── RuntimeMetricsCollector.java
│   └── NoopRuntimeMetricsCollector.java
├── persistence/
│   ├── adapter/
│   │   └── TranscriptStoreAdapter.java
│   ├── config/
│   │   └── RedisTranscriptProperties.java
│   ├── entity/
│   │   └── TranscriptEntity.java
│   ├── mapper/
│   │   └── RedisTranscriptMapper.java
│   └── repository/
│       ├── TranscriptRepository.java
│       ├── RedisTranscriptRepository.java
│       └── InMemoryTranscriptRepository.java
└── provider/
    ├── base/
    │   └── OpenAICompatibleChatProvider.java
    ├── config/
    │   ├── DeepSeekProperties.java
    │   ├── DoubaoProperties.java
    │   └── OpenAIProperties.java
    ├── factory/
    │   └── DefaultLlmProviderFactory.java
    ├── http/
    │   ├── HttpRequestOptions.java
    │   ├── LlmHttpExecutor.java
    │   └── JdkLlmHttpExecutor.java
    ├── protocol/openai/
    │   └── ...
    ├── DeepSeekChatProvider.java
    ├── DoubaoChatProvider.java
    ├── OpenAIChatProvider.java
    └── LlmProvider.java
```

### 4.1 `provider/`
职责：
- 对接模型厂商接口
- 构造请求、执行 HTTP、解析响应
- 屏蔽不同厂商差异

约束：
- provider streaming 分片解析属于本包职责
- app 不解析 provider 协议，runtime 不依赖厂商协议细节
- `LlmProvider` 作为共享抽象统一依赖 `model.port.LlmGateway`

### 4.2 `persistence/`
职责：
- Transcript 的 Redis repository、entity、mapper、adapter
- 将 `ConversationTranscript` 与 Redis Hash 相互转换

约束：
- 只负责存取与映射
- 不做 Working Context 裁剪
- 不做 Memory / Artifact 语义替代
- Redis 裁剪 / TTL / MySQL 升级不属于本轮

### 4.3 `observability/`
职责：
- 提供最小 Trace / Metrics 骨架

约束：
- 不承载主流程编排
- 可保持简单或 noop
- 不在本轮扩成完整治理平台

### 4.4 `integration/`
职责：
- 当前阶段承载 mock tools / mock integration

约束：
- 当前阶段仅保留最小 mock 接缝
- mock 工具必须仍通过统一注册与 gateway 链路接入
- 不要把 integration 写成大杂烩适配层

---

## 5. 模块局部开发约束

### 5.1 基础设施只做实现，不做主导
- `infra` 只实现 runtime 需要的能力，不决定 runtime 主流程
- 不得在 provider / persistence 中偷偷拼 loop 或编排流程
- 不允许 `infra` 反客为主成为“第二 runtime”

### 5.2 依赖注入与 Bean 暴露规则
- 一律使用构造器注入
- Spring Bean 优先使用 `@RequiredArgsConstructor`
- 配置对象使用 `@ConfigurationProperties`
- 不在实现类内部手工 new 外部依赖客户端（测试桩除外）

### 5.3 LLM Provider 实现规范
- 当前主 Provider 以 `DeepSeekChatProvider` 为准
- 同步路径与 streaming 路径都属于当前阶段正式目标
- `OpenAICompatibleChatProvider` 负责公共协议复用，但不应把厂商差异泄漏到 runtime/app
- provider 异常必须映射为标准异常
- tool call / tool fragment 解析属于 provider 层职责

### 5.4 持久化规范
- 当前 Transcript 主实现为 Redis Hash
- Redis 负责最小恢复，不等于长期审计存储
- `TranscriptStoreAdapter` 负责把 persistence 实现适配为运行时可用接口
- `InMemoryTranscriptRepository` 不再作为当前阶段正式实现口径
- entity / mapper / repository 与 `model` 对象必须保持分层

### 5.5 可观测性规则
- `infra` 是 provider / persistence / observability 日志重点落点之一
- 允许开发期保留较详细日志辅助排查
- 日志治理与脱敏不作为本轮阻塞项
- 但 observability 不应因此演变成编排逻辑承载地

### 5.6 Lombok 使用规则
#### 适合优先使用 Lombok 的位置
- Spring Bean：`@RequiredArgsConstructor`
- 需要日志的类：`@Slf4j`
- 简单配置对象：`@Getter`、`@Setter`、`@Data`

#### 使用边界
- Provider / persistence 核心实现中，不滥用 `@Data`
- 对需要封装与保护的对象，优先更细粒度 Lombok 组合

### 5.7 日志规则
- 统一使用 SLF4J
- 重点日志落在 provider 调用开始/结束/失败、persistence 存取失败、mapping 异常等位置
- 当前开发阶段不把日志暴露问题作为阻塞项
- 但不要把日志当成业务状态存储替代品

### 5.8 配置管理规则
- 使用 `config/` + `@ConfigurationProperties` 按 provider / persistence 分组
- 敏感配置（如 API Key）只从环境变量或外部配置读取
- 不提供真实密钥默认值

### 5.9 公共工具类使用规则
- 允许使用 `common.util.JsonUtils`
- 不在 provider / persistence 内重复造 JSON 样板轮子
- 不在 `infra` 内新建承载业务流程的工具类

### 5.10 依赖管理规则
- `infra` 的标准依赖方向是：`infra -> model + common`
- 当前依赖方向已收口为 `infra -> model + common`
- LangChain4j、Redis、HTTP 执行器等依赖只应出现在 `infra` / `app` 合理边界内
- 不引入与当前阶段无关的 queue / workflow / vector / policy 等重型依赖
- 版本与插件由根 POM 统一管理，模块 POM 只声明直接依赖

---

## 6. 当前阶段下的 `infra` 模块约束

当前阶段内，`infra` 模块允许存在：
- DeepSeek / OpenAI / Doubao 等 provider 实现
- Redis Transcript 最小正式实现
- mock tools
- 最小 observability 骨架

但不得提前引入：
- MySQL Transcript 主实现
- 长期记忆存储系统
- RAG 向量库接入
- Approval / Replay / Evaluation 平台
- 复杂真实外部系统集成

当前阶段内，`infra` 模块的唯一目标是：

**为 Runtime Core 提供最小、稳定、可替换的实现支撑，并完成依赖方向治理。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `DeepSeekChatProvider` | 单元测试 / 隔离测试 | 普通回复解析、流式回复、tool call 分片聚合、异常路径 |
| `OpenAICompatibleChatProvider` | 单元测试 | 公共协议构造与解析 |
| `RedisTranscriptRepository` / `TranscriptStoreAdapter` | 单元测试 / 集成测试 | 保存、读取、覆盖、恢复 |
| `RedisTranscriptMapper` | 单元测试 | Redis Hash 与 Transcript 的映射 |
| `MockReadOnlyTools` | 单元测试 | 作为 ToolBundle 的基础行为 |
| `RuntimeMetricsCollector` | 单元测试 | 最小 noop / 默认行为 |

### 7.2 当前阶段测试目标
- provider 同步/流式关键路径可测
- Redis Transcript 最小恢复可测
- `infra -> runtime` 依赖清除后测试仍稳定

### 7.3 测试约束
- 优先使用 mock / fake 隔离外部依赖
- 不为了测试方便回退依赖方向
- 不依赖 app 层 WebFlux 测试来替代 provider / persistence 测试

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 冻结规则约束。
2. 当 `provider/`、`persistence/`、`integration/`、`observability/` 包结构或职责变化时，必须同步更新本文件。
3. 若根目录文档中出现了本模块过细实现内容，应回收到本文件。
4. 未经确认，不得改变本文件的布局与章节顺序。
5. 不得把主编排逻辑或 Web 协议细节长期写进本模块文档。

---

## 9. 一句话总结

`vi-agent-core-infra` 的职责，是把 Provider、Transcript、Observability 与 mock integration 的实现做扎实，并通过移除 `infra -> runtime` 反向依赖，把基础设施层真正收回到它该在的位置。
