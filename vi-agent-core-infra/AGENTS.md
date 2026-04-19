# AGENTS.md

> 更新日期：2026-04-19

## 1. 文档定位

本文件定义 `vi-agent-core-infra` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `infra` 模块负责什么
- `infra` 模块不负责什么
- `infra` 模块内包结构如何约定
- 在 `infra` 模块开发时必须遵守哪些局部规则
- `infra` 模块测试与依赖应如何建设

---

## 2. 模块定位

`vi-agent-core-infra` 是整个 `vi-agent-core` 系统的**基础设施实现层**。

标准依赖链位置：
`infra -> model + common`

但 `infra` 模块不是：
- 主编排层
- Web 接入层
- 共享契约定义层
- 运行时指标抽象层
- 业务工具箱

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容
- Provider 实现：`DeepSeekChatProvider`、`OpenAIChatProvider`、`DoubaoChatProvider`、`OpenAICompatibleChatProvider`、HTTP 执行器等
- Persistence 实现：Transcript / State / Summary 的 Redis / MySQL repository、mapper、adapter
- Prompt 存储实现：`MarkdownPromptRepository`（按模板 key 读取 markdown 内容）
- Integration：当前以 mock 为主
- 配置模型：provider `config/`、persistence `config/`

### 3.2 本模块明确不负责的内容
以下内容禁止写入 `infra` 模块：
- `RuntimeOrchestrator` 主循环
- Agent Loop 流程控制
- WebFlux Controller / SSE 协议适配
- Web DTO
- 根 POM 的聚合与插件总配置
- 运行时指标抽象与内部 LLM 任务协作接口

### 3.3 抽象与实现边界
- 删除 `LlmProvider`；统一实现 `model.port.LlmGateway`。
- 共享契约应放在 `model`，不长期留在 `infra`。
- 不允许在 `infra` 中保留误导性仓储类或未启用占位主类。

---

## 4. 模块内包结构约定

当前 `infra` 模块包结构固定为：

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

### 4.1 包规则
- transcript Redis 相关实现统一进入 `persistence/cache/transcript/*`。
- `MysqlPromptTemplateRepository` 等未进入正式主链路的误导性仓储类不保留。
- Prompt 模板文本维护在 `prompts/`，`infra` 只提供读取实现，不在 provider/runtime 中硬编码提示词正文。
- provider 协议对象只留在 provider 内部，不泄漏到 runtime / app。

### 4.2 Provider 规则
- `OpenAICompatibleChatProvider` 要向 request mapper / response mapper / stream parser / tool call assembler 拆分。
- provider 负责厂商协议请求、响应、流解析与 tool call 分片组装。
- provider 异常必须映射为项目标准异常。

### 4.3 Persistence 规则
- Transcript / State / Summary 均遵循 Redis（热态层）+ MySQL（事实源）双层治理。
- transcript MySQL 写入必须具备增量 / 幂等语义。
- MySQL 持久化默认采用 MyBatis-Plus Lambda Wrapper 链式函数写法表达查询、更新、删除条件（`Wrappers.lambdaQuery(...)` / `Wrappers.lambdaUpdate(...)`）。
- `insert(entity)` 作为标准新增写法允许保留。
- 只有 SQL 复杂到链式写法明显不可读时，才允许 XML / 字符串 SQL / 注解 SQL。
- `TranscriptStoreAdapter` 负责把 persistence 实现适配为 runtime 可用接口。
- entity / mapper / repository 与 `model` 对象必须保持分层。

---

## 5. 模块局部开发约束

### 5.1 POM 与依赖规则
- `infra` 只依赖 `model` + `common`。
- 严禁 `infra -> runtime`。
- 模块 POM 只保留直接依赖，不重复写受父 POM 管理的版本。

### 5.2 工具优先规则
- 字符串判空优先 `StringUtils`。
- 集合判空优先 `CollectionUtils`。
- Map 判空与默认值处理保持统一风格。

### 5.3 Lombok 规则
- Spring Bean 优先 `@RequiredArgsConstructor`。
- 需要日志的类优先 `@Slf4j`。
- Provider / persistence 核心实现不滥用 `@Data`。

### 5.4 预留能力规则
- 默认不保留未启用主类占位代码。
- 预留能力如需保留，必须有文档依据与清晰包归属。

---

## 6. 测试要求
- Provider 变更必须覆盖同步、流式、tool call 分片聚合与异常路径。
- Persistence 变更必须覆盖保存、读取、双层回源、增量/幂等写入。
- transcript 双层治理与 MySQL 增量写入必须有专项测试。

---

## 7. 一句话总结

`infra` 模块的核心要求是：让它始终只做实现与适配，不演化成第二编排层，同时把 provider 与 persistence 的目录、抽象、职责和双层治理口径全部收干净。

## 8. 会话元数据与 Transcript 事实源补充

- `SessionMetadataStore` / `MysqlSessionRepository` 负责 conversation-session 关系事实源管理。
- transcript 底层存储维度保持 `sessionId`；`conversationId` 来源于 session metadata 正式记录。
- 禁止在 transcript adapter 中拼接或推断伪 `conversationId`。
- Transcript 读路径：优先 Redis 热态，miss 时 MySQL 回源并回填 Redis。
- Transcript 写路径：先 MySQL，再刷新 Redis 热态。
