# AGENTS.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core-common` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：

- `common` 模块负责什么
- `common` 模块不负责什么
- `common` 模块内包结构如何约定
- 在 `common` 模块开发时必须遵守哪些局部规则
- `common` 模块测试与依赖应如何建设

本文件不负责：

- 仓库级协作规则与通用开发规范（见根目录 `AGENTS.md`）
- 项目高层路线图、阶段状态与当前阶段索引（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准与通用测试门禁（见根目录 `CODE_REVIEW.md`）
- 阶段详细设计、阶段开发计划、阶段专项测试验收、Codex prompt、阶段收口记录（见 `execution-phase/{phase-name}/`）
- `app` / `runtime` / `infra` / `model` 模块内部职责细节（见对应模块 `AGENTS.md`）

执行 `common` 模块相关任务前，必须先读：

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

`vi-agent-core-common` 是整个 `vi-agent-core` 系统的**最底层公共能力模块**。

因此，`common` 模块是：

- 基础异常层
- 基础错误码层
- 基础 ID 层
- 无状态通用工具层

但 `common` 模块不是：

- Runtime SPI 寄存地
- Provider / Repository 抽象层
- 业务工具箱
- Spring Bean 装配层
- Prompt Governance 承载层
- Memory / Context / Tool / Provider 领域模型承载层

`common` 模块的核心定位是：

**只提供整个仓库都能复用的轻量基础能力，不承载业务流程、不承载领域模型、不承载运行时编排。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

`common` 模块只负责以下内容：

- 基础异常体系：`AgentRuntimeException`、`ErrorCode`
- 基础 ID 生成能力：`IdGenerator` 与各类 ID 生成器
- 公共无状态工具类：`JsonUtils`、`ValidationUtils` 等
- 跨模块可复用且无业务流程语义的轻量基础能力

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `common` 模块：

- `LlmGateway`、`TranscriptStore` 这类运行时共享契约
- `@AgentTool`、`ToolBundle` 这类强运行时语义注解与契约
- `PromptTemplate`、`PromptOutputSchema`、`PromptRegistry` 等 Prompt Governance 对象或运行时逻辑
- Working Context / Session State / Conversation Summary / Evidence / Internal Task 等领域模型
- Web DTO
- Provider 调用
- Redis / MySQL 访问
- Runtime 编排
- Tool Routing
- Spring 配置与 Bean
- 业务 repository / mapper / entity
- 阶段详细设计或阶段专项测试规则

如果一段代码需要：

- 表达某个业务领域对象；
- 定义 runtime / infra 共同依赖的业务契约；
- 承载 provider、tool、context、memory、prompt 等领域语义；
- 操作 MySQL / Redis / 外部 API；
- 参与 Agent Loop 或 RuntimeOrchestrator 主链路；
- 依赖 Spring 容器或应用配置；

那它就不属于 `common` 模块。

---

## 4. 模块内包结构约定

当前 `common` 模块包结构固定为：

```text
com.vi.agent.core.common
├── exception/
├── id/
└── util/
```

### 4.1 包规则

- 包名必须全小写。
- `exception/` 只放项目标准异常与错误码。
- `id/` 只放基础 ID 生成器与 ID 生成相关的轻量无状态能力。
- `util/` 只放无状态、跨模块可复用、非业务编排工具类。
- 禁止在 `common` 中新增 `runtime`、`provider`、`repository`、`prompt`、`memory`、`context`、`tool` 等强业务语义包。
- 禁止为了“方便复用”把尚未明确归属的业务对象下沉到 `common`。

### 4.2 错误码规则

- `ErrorCode` 命名必须单义、可扩展。
- 错误码粒度按业务对象区分，不允许 transcript / state / summary / evidence / prompt / provider / tool 共用模糊错误码。
- 错误码应能帮助定位对象、动作与失败原因。
- HTTP 状态映射属于 `app` 层 advice，不属于 `common`。
- `common` 可以定义错误码枚举或基础异常，但不决定 Web 响应协议。
- 新增错误码必须有明确使用场景，不得为了预留大量添加。

### 4.3 ID 生成规则

- 业务 ID、审计 ID、projection ID、snapshot ID、block ID、internal task ID 等，不允许在业务类、repository、factory、service 中手写 `"prefix-" + UUID.randomUUID()`。
- ID 生成必须使用对应语义的 IdGenerator 集中生成。
- `common/id` 可以承载跨模块共享的轻量 ID 生成能力。
- ID 生成器只负责生成 ID，不负责业务判断、持久化、审计记录或运行时编排。
- 新增 ID 生成器必须具备清晰语义、稳定前缀、基础格式测试。
- 如果某个 ID 只属于特定领域对象，其语义归属必须先在对应阶段文档或模块文档中明确，再决定生成器放置位置。
- 不得在工具类中散落临时 ID 生成逻辑。

### 4.4 工具类规则

- 字符串判空优先使用 `StringUtils`。
- 集合判空优先使用 `CollectionUtils`。
- Map 判空保持统一风格。
- `JsonUtils` 只负责 JSON 序列化 / 反序列化，不演化出业务编排能力。
- `ValidationUtils` 只负责基础参数校验，不承载复杂业务规则。
- 工具类必须保持无状态。
- 工具类不得持有可变全局状态。
- 工具类不得偷偷读取 Spring 配置、系统环境或外部资源。
- 工具类不得访问 MySQL / Redis / Provider / Tool / Runtime。
- 如果某个工具方法开始承载业务流程，应迁移到对应模块的正式 service / coordinator / assembler 中。

---

## 5. 模块局部开发约束

### 5.1 轻量原则

- `common` 必须保持最小、稳定、纯基础。
- 新增能力前必须判断：它是不是整个仓库都能复用的纯基础能力？
- 不能因为多个模块都想用，就直接把业务能力下沉到 `common`。
- 不能把 `common` 当作“暂时没地方放”的缓冲区。
- 无明确跨模块基础价值的类不得进入 `common`。
- 无文档、无调用、无明确归属的占位类不得保留。

### 5.2 依赖规则

- `common` 不能依赖 `model` / `runtime` / `infra` / `app`。
- `common` 禁止依赖 Spring Boot starter。
- `common` 禁止依赖 Redis、MySQL、HTTP 客户端、Provider SDK、LangChain4j、向量数据库客户端等运行时依赖。
- `common` 禁止依赖任何需要 Spring 容器才能正常工作的能力。
- `common` 中的能力必须尽量保持纯 Java、可单测、可复用。
- 根 POM 统一管理版本，`common` POM 不重复写版本策略。
- 新增依赖必须证明其属于轻量公共基础能力，否则不得加入 `common`。

### 5.3 POM 管理规则

- `common` 是底层库模块，不允许使用 `spring-boot-maven-plugin`。
- `common` 不生成可执行包。
- `common` 只声明自己直接使用的依赖。
- 禁止通过传递依赖“蹭”其他模块能力。
- 禁止引入与当前阶段无关的大型框架或中间件。
- 测试依赖只能用于测试作用域。
- 子模块不得重复声明由根 POM 管理的依赖版本。

### 5.4 内部类与占位类规则

- 生产代码中的 domain object / dto / command / event 默认独立成类。
- `common` 不保留未启用占位类。
- `common` 不定义业务 domain object。
- `common` 不定义阶段预留业务对象。
- 如确实需要新增预留基础能力，必须在根文档、阶段文档或本文件中有明确依据。
- 临时实验代码不得进入 `common` 主代码目录。

### 5.5 Lombok 使用规则

- 简单基础对象可以使用 Lombok。
- 基础值对象优先不可变。
- 禁止为了省事使用 `@Data` 破坏对象约束。
- 日志类如需 logger，优先使用 `@Slf4j`。
- 使用 Lombok 后，不得降低代码可读性和对象语义清晰度。

### 5.6 注释规则

- 新增异常类、错误码、ID 生成器、工具类必须有中文类注释。
- 新增字段必须有中文字段注释。
- 公共方法应说明参数语义、返回语义和异常边界。
- 注释必须解释边界、约束或风险，不得机械重复代码字面含义。
- 注释必须与代码同步维护。
- 不得用注释长期补救命名不清或职责不清的问题。

---

## 6. 测试要求

### 6.1 必须覆盖的测试场景

| 测试目标 | 测试要求 |
|---|---|
| `ErrorCode` / 异常体系 | 覆盖错误码语义、异常构造、错误码携带 |
| ID 生成器 | 覆盖格式、前缀、唯一性、非空、基础并发安全假设 |
| `JsonUtils` | 覆盖序列化、反序列化、异常路径、未知字段策略 |
| `ValidationUtils` | 覆盖基础参数校验、异常路径 |
| 字符串 / 集合 / Map 工具类 | 覆盖空值、空集合、正常值、边界值 |
| 新增公共工具方法 | 覆盖正常路径、边界路径、异常路径 |

### 6.2 测试约束

- `common` 测试必须是轻量单元测试。
- 测试不依赖 Spring 容器。
- 测试不依赖 Redis / MySQL / Provider / HTTP 外部服务。
- 测试不通过启动 app 来验证 common 能力。
- ID 生成器测试不要求证明绝对全局唯一，但必须覆盖格式、前缀与基础唯一性。
- 旧测试与新架构冲突时，更新或删除旧测试，不为了旧测试保留过时逻辑。

---

## 7. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 文档治理规则约束。
2. 当 `exception/`、`id/`、`util/` 包结构或核心类职责发生长期变化时，必须同步更新本文件。
3. 阶段性详细设计、阶段开发计划、阶段专项验收、Codex prompt 不写入本文件，应写入当前阶段 `execution-phase/{phase-name}/`。
4. 若根目录文档中出现了本模块过细实现内容，应回收到本文件。
5. 若阶段文档中产生了长期稳定的 common 模块边界，完成阶段收口后可以晋升到本文件。
6. 未经确认，不得改变本文件的布局、标题层级与章节顺序。
7. 不得把 `app` / `runtime` / `infra` / `model` 的实现细节长期写成本模块文档。
8. 不得把某个阶段的临时实现细节固化为 common 模块长期规则。

---

## 8. 一句话总结

`common` 模块的核心要求是：继续保持“最底层、最小、最稳定”的公共基线。

它只承载异常、错误码、ID 和无状态通用工具，不为上层工程治理收口制造新的杂糅入口，也不承载任何运行时主链路、领域模型、外部实现或阶段详细设计。
