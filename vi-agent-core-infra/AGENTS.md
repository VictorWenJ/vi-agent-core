# AGENTS.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core-infra` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：

- `infra` 模块负责什么
- `infra` 模块不负责什么
- `infra` 模块内包结构如何约定
- 在 `infra` 模块开发时必须遵守哪些局部规则
- `infra` 模块测试与依赖应如何建设

本文件不负责：

- 仓库级协作规则与通用开发规范（见根目录 `AGENTS.md`）
- 项目高层路线图、阶段状态与当前阶段索引（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准与通用测试门禁（见根目录 `CODE_REVIEW.md`）
- 阶段详细设计、阶段开发计划、阶段专项测试验收、Codex prompt、阶段收口记录（见 `execution-phase/{phase-name}/`）
- `app` / `runtime` / `model` / `common` 模块内部职责细节（见对应模块 `AGENTS.md`）

执行 `infra` 模块相关任务前，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. `execution-phase/README.md`
6. 当前阶段目录下的 `README.md`
7. 当前阶段目录下的 `design.md`
8. 当前阶段目录下的 `plan.md`
9. 当前阶段目录下的 `test.md`
10. 与当前任务相关的历史强契约文档
11. 相关模块 `AGENTS.md`
12. 相关源码与测试

---

## 2. 模块定位

`vi-agent-core-infra` 是整个 `vi-agent-core` 系统的**基础设施实现层**。

标准依赖链位置：

```text
infra -> model + common
```

因此，`infra` 模块是：

- Provider 实现层
- Persistence 实现层
- Cache 实现层
- 外部资源读取实现层
- Mock Integration 实现层
- 基础设施对象映射层

但 `infra` 模块不是：

- 主编排层
- Agent Loop 控制层
- Web 接入层
- 共享契约定义层
- 运行时指标抽象层
- Prompt Governance 运行时治理层
- 业务工具箱

`infra` 模块的核心定位是：

**只做外部系统、存储系统、模型厂商协议和基础设施能力的具体实现与适配，不接管 runtime 主流程。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

`infra` 模块负责以下内容：

#### 1）Provider 实现

- DeepSeek / OpenAI / Doubao 等模型 provider 实现；
- OpenAI-compatible 协议实现；
- HTTP 执行器；
- provider request mapper；
- provider response mapper；
- provider stream parser；
- tool call fragment assembler；
- 厂商协议异常到项目标准异常的转换。

#### 2）Persistence 实现

- Transcript / Session Metadata / State / Summary / Evidence / Internal Task 的 MySQL repository、mapper、entity；
- Transcript / State / Summary 等 Redis snapshot cache 实现；
- MySQL 与 Redis 对象映射；
- repository 对外契约适配；
- cache miss 后 MySQL 回源与 Redis 回填。

#### 3）外部资源读取实现

- prompt template 文件读取实现；
- classpath / file resource repository implementation；
- 将外部资源内容转换为 `model` 或 `runtime` 可使用的契约对象。

#### 4）Integration 实现

- mock integration；
- 开发期外部系统替身；
- 基础设施层测试辅助实现。

#### 5）基础设施配置模型

- provider `config/`；
- persistence `config/`；
- cache 相关配置；
- HTTP client 基础配置。

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `infra` 模块：

- `RuntimeOrchestrator` 主循环；
- Agent Loop 流程控制；
- Working Context 构建策略；
- Prompt Registry / Prompt Renderer / Prompt Output Schema 校验逻辑；
- parser allowlist 业务校验逻辑；
- post-turn memory update 主协调逻辑；
- Tool Routing / Tool Runtime 主路由；
- WebFlux Controller / SSE 协议适配；
- Web DTO；
- 根 POM 的聚合与插件总配置；
- 运行时指标抽象与内部 LLM 任务协作接口；
- 共享领域模型定义；
- 阶段详细设计或阶段专项测试规则。

如果一段代码需要：

- 决定 Runtime 主链路如何执行；
- 决定 Agent Loop 何时继续或结束；
- 决定 WorkingContext 如何被选择、裁剪和投影；
- 决定 Prompt 模板如何注册、渲染、校验 output schema；
- 决定 State / Summary / Evidence 的业务合并规则；
- 定义 runtime 与 infra 共同依赖的接口或领域对象；
- 暴露 HTTP / SSE 协议；

那它就不属于 `infra` 模块。

### 3.3 抽象与实现边界

- 共享契约必须放在 `model`，不得长期留在 `infra`。
- `infra` 实现 `model.port` 中定义的 port / gateway / repository 契约。
- `infra` 不得定义需要被 `runtime` 依赖的接口。
- `infra` 不得反向依赖 `runtime`。
- `infra` 不得接管 run 生命周期。
- `infra` 不得用具体实现绕开 `model.port`。
- `infra` 中不得保留误导性仓储类或未启用占位主类。
- `infra` 中命名带 `Mysql`、`Redis`、`File`、`Classpath`、`OpenAi`、`DeepSeek` 等实现细节的类，必须与真实职责一致。

---

## 4. 模块内包结构约定

当前 `infra` 模块包结构建议保持为：

```text
com.vi.agent.core.infra
├── integration/
│   └── mock/
├── persistence/
│   ├── adapter/
│   ├── cache/
│   │   ├── state/
│   │   ├── summary/
│   │   └── transcript/
│   ├── config/
│   └── mysql/
│       ├── entity/
│       ├── mapper/
│       └── repository/
└── provider/
    ├── base/
    ├── config/
    ├── factory/
    ├── http/
    ├── mapper/
    ├── parser/
    └── protocol/
```

如 P2-E 或后续阶段正式引入 prompt template 外部资源读取实现，可在阶段设计明确后使用清晰包名，例如：

```text
com.vi.agent.core.infra.prompt/
```

或：

```text
com.vi.agent.core.infra.resource/prompt/
```

具体包名必须以当前阶段 `design.md` 为准，不得由实现阶段自行推断。

### 4.1 包规则

- 包名必须全小写。
- provider 协议对象只留在 provider 内部，不泄漏到 runtime / app。
- persistence entity / mapper / repository 与 `model` 对象必须保持分层。
- transcript Redis 相关实现统一进入 `persistence/cache/transcript/*`。
- state Redis 相关实现统一进入 `persistence/cache/state/*`。
- summary Redis 相关实现统一进入 `persistence/cache/summary/*`。
- MySQL entity 只表达数据库表结构，不得混入领域行为。
- MySQL mapper 只表达数据库访问能力，不得混入业务规则。
- MySQL repository 负责把 mapper / entity / JSON 映射为 `model.port` 需要的契约语义。
- adapter 负责把基础设施实现适配为上层可用接口，不承载主流程编排。
- integration mock 只服务开发期或测试期，不得进入正式主链路语义。
- 未进入正式主链路的误导性仓储类不保留。
- Prompt 模板文本或外部资源内容不得在 provider / runtime 中硬编码。

---

## 5. Provider 实现规则

### 5.1 Provider 基本边界

- Provider 负责厂商协议请求、响应、流解析与 tool call 分片组装。
- Provider 不做 Runtime 主流程编排。
- Provider 不做 transcript / memory / audit 持久化。
- Provider 不做 Working Context 选择、裁剪、投影。
- Provider 不做 Prompt Registry / Prompt Renderer 逻辑。
- Provider 不把厂商协议对象泄漏到 runtime / app。
- Provider 只返回 `model.provider` 或 `model.port` 约定的对象。

### 5.2 OpenAI-compatible 实现规则

`OpenAICompatibleChatProvider` 应逐步按职责拆分为：

- request mapper；
- response mapper；
- stream parser；
- tool call assembler；
- error mapper；
- HTTP executor。

拆分目标不是制造类数量，而是避免 provider 基类变成协议、HTTP、parser、assembler、异常处理全部混合的大类。

### 5.3 Provider 异常规则

- provider 异常必须映射为项目标准异常。
- 异常中必须保留必要上下文，例如 provider name、model、请求动作、响应状态。
- 不得把厂商原始异常体直接透出到 app response。
- 不得吞掉 provider 异常后返回伪成功。
- 流式异常必须能被 runtime / app 正确转成标准失败事件。

---

## 6. Persistence 实现规则

### 6.1 MySQL 与 Redis 双层治理

Transcript / Session Metadata / State / Summary / Evidence / Internal Task 等持久化对象必须遵循：

```text
MySQL = 事实源
Redis = snapshot cache
```

基本原则：

- Redis 是热态缓存，不是事实源；
- cache miss 不改变事实源语义；
- 写入事实数据时，以 MySQL 成功写入为准；
- Redis 刷新失败可以降级，但不能伪造 MySQL 成功；
- Redis 中不得保存与 MySQL 事实源冲突的长期语义。

### 6.2 Transcript 规则

- transcript 底层存储维度保持 `sessionId`。
- `conversationId` 来源于 session metadata 正式记录。
- 禁止在 transcript adapter 中拼接或推断伪 `conversationId`。
- Transcript 读路径：优先 Redis 热态，miss 时 MySQL 回源并回填 Redis。
- Transcript 写路径：先 MySQL，再刷新 Redis 热态。
- transcript MySQL 写入必须具备增量 / 幂等语义。
- transcript 只能存 raw user / assistant / tool 事实消息。
- synthetic runtime / state / summary message 不得写入 transcript。
- `WorkingContextProjection.modelMessages` 不得回刷 transcript。

### 6.3 Session Metadata 规则

- `SessionMetadataStore` / `MysqlSessionRepository` 负责 conversation-session 关系事实源管理。
- conversation-session 关系必须以正式 session metadata 记录为准。
- 不得在 transcript、working context、projection 或 app DTO 中反向推断 session metadata。
- `lastRunId`、`currentPhase`、`status` 等字段必须按领域语义更新。
- repository 查询缺失时必须用 `Optional.empty()` 表达，不得向外透出 `null`。

### 6.4 State / Summary / Evidence / Internal Task 规则

- State / Summary 写入成功后，可以刷新 Redis snapshot cache。
- Evidence 保存失败只能 degraded，不得回滚已经成功写入的 state / summary。
- Internal Task audit 必须独立存储，不得混入主 chat response。
- State / Summary / Evidence / Internal Task repository 只负责持久化实现，不负责业务抽取、merge、prompt 调用或失败策略决策。
- JSON 映射必须严格遵守对应领域模型契约，不得吞未知字段后伪成功。
- `MemoryJsonMapper` 或同类 JSON mapper 不得无限膨胀为泛化 JSON 大杂烩，应按对象类型逐步拆分。

### 6.5 Repository 查询契约

- Repository 实现必须遵守 `model.port` 的 Optional 查询契约。
- 对外单实体查询统一返回 `Optional<T>`。
- mapper / Redis miss / 解析失败应在 repository 边界转换为 `Optional.empty()` 或标准异常。
- 不得把 `null` 透出到 repository 边界。
- 无对应 `model.port` 的 concrete repository 若暴露 public 单实体查询，也必须按 `Optional<T>` 处理。
- 调用方不得再包一层 `Optional.ofNullable(repository.findXxx(...))`。

### 6.6 MyBatis / SQL 规则

- MySQL 持久化默认采用 MyBatis-Plus Lambda Wrapper 链式函数写法表达查询、更新、删除条件。
- 推荐使用 `Wrappers.lambdaQuery(...)` / `Wrappers.lambdaUpdate(...)`。
- `insert(entity)` 作为标准新增写法允许保留。
- 只有 SQL 复杂到链式写法明显不可读时，才允许 XML / 字符串 SQL / 注解 SQL。
- 不得为了省事散落硬编码 SQL。
- 不得在 repository 中拼接不安全 SQL。
- entity 字段与数据库字段映射必须清晰，必要时用注释说明。

---

## 7. Prompt 外部资源实现规则

P2-E 及后续 Prompt Engineering 治理中，`infra` 只允许承担 prompt 外部资源读取或 repository implementation 职责。

`infra` 可以负责：

- 从 classpath 读取 prompt template；
- 从文件读取 prompt template；
- 将外部模板资源转换为 `model.prompt` 契约对象；
- 实现 `model.port` 中定义的 prompt repository port；
- 处理文件缺失、路径错误、读取失败等基础设施异常。

`infra` 不负责：

- Prompt Registry；
- Prompt Renderer；
- Prompt Version 决策；
- Prompt Variable 校验规则；
- Prompt Output Schema 业务校验；
- parser allowlist 对齐检查；
- Prompt audit metadata 组装；
- LLM prompt 调用；
- 将 deterministic audit 改造成 LLM prompt。

如果 P2-E 阶段尚未正式定义 prompt repository port 或资源加载方案，不得提前新增 DB prompt 管理、prompt UI、prompt 热更新平台或复杂模板中心。

---

## 8. 模块局部开发约束

### 8.1 POM 与依赖规则

- `infra` 只依赖 `model` + `common`。
- 严禁 `infra -> runtime`。
- 严禁 `infra -> app`。
- 模块 POM 只保留直接依赖。
- 不重复写受父 POM 管理的版本。
- 测试依赖只能用于测试作用域。
- 不引入与当前阶段无关的大型框架、中间件或平台依赖。
- 不得通过新增依赖绕开模块边界。
- 新增 provider SDK、数据库驱动、Redis 客户端、HTTP client 等依赖时，必须有明确阶段文档依据。

### 8.2 工具优先规则

- 字符串判空优先使用 `StringUtils`。
- 集合判空优先使用 `CollectionUtils`。
- Map 判空与默认值处理保持统一风格。
- 对象判空优先使用 JDK `Objects` 相关 API。
- JSON 处理优先复用项目统一 mapper / utils，不重复手写 ObjectMapper 模板。
- 工具类只用于无状态辅助，不得承载 provider / persistence 业务流程。

### 8.3 Lombok 规则

- Spring Bean 优先使用 `@RequiredArgsConstructor`。
- 需要日志的类优先使用 `@Slf4j`。
- Provider / persistence 核心实现不滥用 `@Data`。
- entity / config / 简单 DTO 可以合理使用 Lombok。
- 值对象语义明确的对象优先不可变。
- Lombok 不得掩盖构造约束、默认值来源或对象合法性规则。

### 8.4 预留能力规则

- 默认不保留未启用主类占位代码。
- 预留能力如需保留，必须有文档依据与清晰包归属。
- 无文档、无调用、无明确归属的占位类必须删除。
- 不得因为未来可能使用，就在 infra 中提前加入平台级能力。
- 不得在 P2-E 未要求时提前实现 prompt DB、RAG repository、workflow storage、checkpoint storage。

### 8.5 注释规则

- 新增 entity、repository、mapper、adapter、provider、config、DTO、record-like value object 必须有中文类注释。
- 新增字段必须有中文字段注释。
- 关键基础设施边界必须说明失败策略、降级策略和数据一致性假设。
- provider 协议映射、stream parser、tool call assembler 等复杂点必须说明边界。
- persistence 写路径、cache refresh、回源逻辑必须说明事实源与缓存语义。
- 注释必须与代码同步维护，不得保留过时说明。

---

## 9. 测试要求

### 9.1 必须覆盖的测试场景

| 测试目标 | 测试要求 |
|---|---|
| Provider 同步调用 | 覆盖 request mapping、response mapping、异常路径 |
| Provider 流式调用 | 覆盖 delta 解析、finish reason、错误事件、资源释放 |
| Tool call 分片聚合 | 覆盖 fragment 聚合、缺失字段、异常片段 |
| Provider error mapper | 覆盖 HTTP status、厂商错误体、网络异常、超时异常 |
| Transcript persistence | 覆盖保存、读取、Redis miss、MySQL 回源、Redis 回填 |
| Transcript 增量 / 幂等写入 | 覆盖重复写、顺序写、缺失 turn、恢复场景 |
| Session Metadata | 覆盖 conversation-session 关系保存、查询、状态更新 |
| State / Summary cache | 覆盖 MySQL 读取、Redis cache refresh、cache miss |
| Evidence persistence | 覆盖保存成功、保存失败 degraded、查询边界 |
| Internal Task persistence | 覆盖任务记录、状态更新、失败记录、audit 字段 |
| Repository Optional 契约 | 覆盖 miss 返回 `Optional.empty()`，不向外透出 `null` |
| JSON mapper | 覆盖序列化、反序列化、未知字段、契约字段完整性 |
| Prompt resource repository | 覆盖模板存在、模板缺失、读取失败、key/version 映射 |
| POM / dependency | 覆盖模块依赖未破坏，关键测试可运行 |

### 9.2 测试约束

- Provider 测试不得真实依赖外部模型 API，除非是明确的集成测试。
- Persistence 单元测试应隔离 mapper / Redis client，集成测试才连接真实基础设施。
- Repository 测试必须覆盖 Optional 查询契约。
- Redis cache 测试必须覆盖 miss / hit / refresh 三类场景。
- MySQL 写入测试必须覆盖增量 / 幂等语义。
- JSON mapper 测试必须覆盖字段契约，不得只测“能序列化”。
- Prompt resource repository 测试只验证资源读取与映射，不测试 Prompt Renderer 业务逻辑。
- 旧测试与新架构冲突时，更新或删除旧测试，不为了旧测试保留过时逻辑。

---

## 10. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 文档治理规则约束。
2. 当 provider、persistence、cache、integration、prompt resource repository 等包结构或核心职责发生长期变化时，必须同步更新本文件。
3. 阶段性详细设计、阶段开发计划、阶段专项验收、Codex prompt 不写入本文件，应写入当前阶段 `execution-phase/{phase-name}/`。
4. 若根目录文档中出现了本模块过细实现内容，应回收到本文件。
5. 若阶段文档中产生了长期稳定的 infra 模块边界，完成阶段收口后可以晋升到本文件。
6. 未经确认，不得改变本文件的布局、标题层级与章节顺序。
7. 不得把 `app` / `runtime` / `model` / `common` 的实现细节长期写成本模块文档。
8. 不得把某个阶段的临时实现细节固化为 infra 模块长期规则。
9. 历史阶段补充规则如果已经成为长期边界，应归并到对应章节，不应继续放在“一句话总结”之后。

---

## 11. 一句话总结

`infra` 模块的核心要求是：始终只做基础设施实现与适配，不演化成第二编排层。

它负责 provider、persistence、cache、外部资源读取和 mock integration 的具体实现，但不负责 Runtime 主循环、Prompt Governance 运行时治理、Agent Loop、Web 协议、共享契约定义或阶段详细设计。
