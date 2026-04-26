# AGENTS.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core-model` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：

- `model` 模块负责什么
- `model` 模块不负责什么
- `model` 模块内包结构如何约定
- 在 `model` 模块开发时必须遵守哪些局部规则
- `model` 模块测试与依赖应如何建设

本文件不负责：

- 仓库级协作规则与通用开发规范（见根目录 `AGENTS.md`）
- 项目高层路线图、阶段状态与当前阶段索引（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准与通用测试门禁（见根目录 `CODE_REVIEW.md`）
- 阶段详细设计、阶段开发计划、阶段专项测试验收、Codex prompt、阶段收口记录（见 `execution-phase/{phase-name}/`）
- `app` / `runtime` / `infra` / `common` 模块内部职责细节（见对应模块 `AGENTS.md`）

执行 `model` 模块相关任务前，必须先读：

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

`vi-agent-core-model` 是整个 `vi-agent-core` 系统的**内部运行时模型层**，同时也是 `runtime` 与 `infra` 共享契约的正式承载层。

标准依赖链位置：

```text
model -> common
```

因此，`model` 模块是：

- 领域模型层
- 值对象层
- 枚举与常量契约层
- command / result / snapshot / patch / ref 等跨层对象承载层
- `runtime` 与 `infra` 共同依赖的 port / gateway / repository 接口承载层

但 `model` 模块不是：

- 持久化实现层
- Provider 实现层
- Runtime 编排层
- Web DTO 层
- Spring Bean 装配层
- 未启用占位类寄存层
- 阶段详细设计承载层

`model` 模块的核心定位是：

**让跨层共享的模型和接口有稳定、清晰、可测试、可序列化、可审计的契约边界。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

`model` 模块负责以下内容：

#### 1）消息模型

- `AbstractMessage`
- `UserMessage`
- `AssistantMessage`
- `ToolExecutionMessage`
- 其他内部 message value object

约束：

- message 是 runtime 内部语义对象，不等于 app 层 Web DTO。
- message 不直接承载 HTTP / SSE 协议语义。
- synthetic runtime / state / summary message 不得被误建模为 raw transcript 事实消息。

#### 2）工具模型

- `ToolCall`
- `ToolResult`
- `ToolBundle`
- tool schema / tool execution 相关共享对象

约束：

- ToolExecution 不等于 ToolMessage。
- 工具执行记录、工具结果、模型协议中的 tool message 必须保持语义分离。
- tool model 不负责工具执行路由，也不负责反射调用。

#### 3）Transcript 模型

- `ConversationTranscript`
- raw transcript message 相关 value object
- transcript store 需要的共享契约对象

约束：

- Transcript 是原始事实记录。
- Transcript 不承载 WorkingContext projection。
- Transcript 不承载 post-turn memory 派生结论。
- transcript 底层读写维度保持 `sessionId`。

#### 4）Runtime 模型

- `AgentRunContext`
- run / turn / trace / request 相关共享对象
- runtime command / result / event 相关共享模型

约束：

- `AgentRunContext` 可以承载 runtime 主链路所需的基础上下文信息。
- `AgentRunContext` 不应膨胀为万能上下文大对象。
- `requestId` 用于支持 runtime 事件链路透传请求关联，不得替代 `runId / turnId / sessionId / conversationId` 的语义。

#### 5）Context 模型

- `WorkingContext`
- `WorkingContextProjection`
- `ContextBlock`
- `ContextBlockSet`
- `ContextBudget`
- `SessionWorkingSet`
- context planning / projection 相关 value object

约束：

- WorkingContext 不等于 Transcript。
- WorkingContextProjection 只用于 provider 调用。
- `WorkingContextProjection.modelMessages` 不得写回 transcript。
- `SessionWorkingSet` 只能来自 MySQL completed raw transcript，不得从 projection / workingMessages 回刷。

#### 6）Memory 模型

- `SessionStateSnapshot`
- `StateDelta`
- `ConversationSummary`
- `EvidenceRef`
- `EvidenceTarget`
- `EvidenceSource`
- `InternalLlmTaskRecord`
- state / summary / evidence / internal task 相关 snapshot、patch、record、result

约束：

- Memory 不等于 Message。
- state / summary / evidence 是 post-turn 派生 memory。
- `StateDelta` 必须保持领域补丁风格，不能回退到 `upsert/remove` 风格。
- `StateDelta` 及其嵌套对象必须具备正式序列化 / 反序列化闭环。
- evidence / internal task 不暴露到主 chat response。

#### 7）Prompt 模型

- prompt template 相关契约对象
- prompt version 相关契约对象
- prompt variable 相关契约对象
- prompt output schema 相关契约对象
- prompt template ref / render metadata 等跨层共享对象

约束：

- `model.prompt` 只承载 prompt 领域模型与值对象。
- Prompt Registry / Prompt Renderer / schema 对齐检查等运行时治理逻辑不属于 `model`。
- prompt 外部资源读取实现不属于 `model`。
- P2-E 或后续阶段新增 Prompt Governance 对象时，必须先在当前阶段 `design.md` 中声明契约，再实现到 `model.prompt`。

#### 8）Provider 结果对象

- `ModelResponse`
- `ModelUsage`
- provider response / usage 相关 value object

约束：

- `model.provider` 放统一 provider 调用结果对象与配套值对象。
- `model.provider` 不放 provider 实现逻辑。
- 厂商协议对象不得泄漏到 `model.provider` 作为正式共享契约。

#### 9）跨层共享契约

- `LlmGateway`
- `TranscriptStore`
- `SessionMetadataStore`
- state / summary / evidence / internal task repository port
- prompt repository port
- 其他真正需要被 `runtime` 与 `infra` 同时依赖的接口

约束：

- 跨层接口必须放在 `model.port`。
- `model.port` 只放接口。
- 值对象、结果对象、DTO、command、record 不得放入 `port`。

---

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `model` 模块：

- Redis / MySQL repository / mapper / entity 实现
- Provider HTTP 调用与协议解析
- Provider request mapper / response mapper / stream parser
- Runtime 主循环与业务编排
- Prompt Registry / Prompt Renderer 运行时实现
- parser allowlist 运行时校验流程
- Web 请求 / 响应 DTO
- Spring 配置与 Bean 装配
- 任意动态字段集合式 Session State
- 未启用占位类与未启用主代码
- 阶段详细设计或阶段专项测试规则

如果一段代码需要：

- 访问 MySQL / Redis / 外部 API；
- 解析模型厂商协议；
- 执行 Agent Loop；
- 执行 RuntimeOrchestrator 主流程；
- 处理 WebFlux / SSE 协议；
- 读取 classpath / file prompt template；
- 决定 prompt 如何注册、渲染、校验；
- 决定 state / summary / evidence 如何 merge 或持久化；

那它就不属于 `model` 模块。

---

## 4. 模块内包结构约定

当前 `model` 模块包结构固定为：

```text
com.vi.agent.core.model
├── annotation/
├── context/
├── memory/
├── message/
├── port/
├── prompt/
├── provider/
├── runtime/
├── tool/
└── transcript/
```

### 4.1 `annotation/`

职责：

- 放跨层共享的轻量注解；
- 放需要被 runtime 扫描、但不依赖 runtime 实现的注解契约。

约束：

- 注解不得依赖 Spring 容器语义。
- 注解不得承载执行逻辑。
- 注解不得访问配置、provider、repository 或 runtime 协作者。

### 4.2 `context/`

职责：

- 放 Working Context、Context Block、Context Budget、Projection、Working Set 等上下文工程模型。

约束：

- context 对象只表达上下文工程契约。
- context 对象不直接访问 transcript repository。
- projection 对象不持久化为 transcript。
- context 模型不得反向依赖 runtime builder / projector / validator 实现。

### 4.3 `memory/`

职责：

- 放 Session State、Conversation Summary、State Delta、Evidence、Internal Task 等 memory 模型。

约束：

- memory 对象必须保持领域语义清晰。
- patch / snapshot / record / ref / result 必须按语义拆分。
- 不得使用 `Map<String, Object>` 承载正式 memory schema。
- 不得回退到旧的 `upsert/remove` 式 StateDelta 语义。
- P2-A 到 P2-D 已有 memory 对象必须遵守历史强契约。

### 4.4 `message/`

职责：

- 放内部消息模型。
- 表达 user / assistant / tool execution 等 runtime 内部消息语义。

约束：

- 不放 Web DTO。
- 不放 provider 厂商协议对象。
- 不放 transcript repository entity。
- 不把 memory 派生结果伪装成 raw message。

### 4.5 `port/`

职责：

- 只放跨层共享接口。
- 放 `runtime` 调用、`infra` 实现的 port / gateway / repository 接口。

约束：

- `port/` 只放接口。
- 值对象、结果对象、DTO、command、record、builder 不得进入 `port`。
- Repository 类接口的单实体查询方法必须返回 `Optional<T>`。
- 列表查询返回 `List<T>`。
- 存在性判断返回 `boolean`。
- 写入 / 更新 / 删除保持语义化返回或 `void`。
- 接口命名必须体现能力边界，不得与实现类混淆。
- 如果一个接口只被单一实现内部使用，不应放入 `model.port`。

### 4.6 `prompt/`

职责：

- 放 prompt template、prompt version、prompt variable、prompt output schema 等提示词治理相关模型。
- 放 prompt ref、prompt render metadata 等跨层共享值对象。

约束：

- `prompt/` 不放 Prompt Registry 实现。
- `prompt/` 不放 Prompt Renderer 实现。
- `prompt/` 不放 prompt resource repository 实现。
- `prompt/` 不放 prompt 文件读取逻辑。
- `prompt/` 不放 parser allowlist 校验流程。
- `prompt/` 中对象字段必须以当前阶段 `design.md` 的 class 契约为准。
- prompt output schema 与 parser allowlist 的一致性测试应在对应阶段 `test.md` 中定义。

### 4.7 `provider/`

职责：

- 放 LLM 调用结果对象与配套值对象。
- 放 provider usage、finish reason、tool call result 等统一抽象结果。

约束：

- 不放 provider HTTP 实现。
- 不放 provider request mapper / response mapper。
- 不放 stream parser。
- 不放厂商 SDK 协议对象。
- 不向 runtime / app 泄漏厂商协议字段。

### 4.8 `runtime/`

职责：

- 放 runtime 主链路所需的共享模型。
- 放 run context、runtime command、runtime result、runtime event 等模型对象。

约束：

- 不放 RuntimeOrchestrator 实现。
- 不放 Agent Loop 实现。
- 不放 runtime service / coordinator。
- 不放 Spring Bean。
- 不放 MySQL / Redis / Provider 调用逻辑。

### 4.9 `tool/`

职责：

- 放工具调用、工具结果、工具 bundle、工具 schema 等共享模型。

约束：

- 不放工具执行路由。
- 不放反射调用实现。
- 不放 tool registry 实现。
- 不把 ToolExecution 和 ToolMessage 混为一个模型。

### 4.10 `transcript/`

职责：

- 放 transcript 领域模型。
- 放 conversation transcript、transcript message、session transcript 相关共享对象。

约束：

- transcript 只表达 raw transcript 事实记录。
- transcript 对象不负责 MySQL / Redis 实现。
- transcript 对象不从 projection 反向推断 conversation / session metadata。
- transcript 读写契约保持 `sessionId` 维度。

---

## 5. 强契约与递归契约规则

### 5.1 历史强契约

P2-A 到 P2-D 已有对象必须继续遵守历史强契约：

```text
execution-phase/phase-P2-context-memory/system-design-P2-v5.md
```

P2-A 到 P2-D 的历史开发计划基线为：

```text
execution-phase/phase-P2-context-memory/system-design-P2-implementation-plan-v3.md
```

涉及已有模型时，字段名、字段类型、字段数量、字段语义必须以历史强契约为准。  
不得用工程习惯自行推断、改名、扩展字段。

### 5.2 当前阶段新增契约

当前阶段新增对象必须以当前阶段 `design.md` 为新增契约源：

```text
execution-phase/{phase-name}/design.md
```

如果当前阶段新增 Prompt Governance 对象，则新增对象必须先在当前阶段 `design.md` 中定义 class 片段，再进入 `model.prompt` 或 `model.port`。

### 5.3 递归契约自检

修改、读取、序列化、反序列化、merge、render、audit、prompt 组装中涉及领域模型时，必须递归检查字段引用对象。

例如涉及 `StateDelta` 时，不得只检查 `StateDelta` 顶层字段，还必须检查：

- `ConfirmedFactRecord`
- `ConstraintRecord`
- `DecisionRecord`
- `OpenLoop`
- `ToolOutcomeDigest`
- `UserPreferencePatch`
- `PhaseStatePatch`

涉及 `SessionStateSnapshot` 时，也必须递归检查其引用的 state 子对象。

如果任一嵌套对象与强契约不一致，必须先修代码或先修正文档契约，再继续实现。

---

## 6. 模块局部开发约束

### 6.1 命名规则

- 类名必须是名词或名词短语，语义完整清晰。
- 接口名必须体现能力边界。
- 方法名必须是动词或动宾短语。
- 不允许在模型层保留双重语义或模糊语义命名。
- 不允许使用 `New`、`Old`、`Temp`、`Tmp`、`Bak`、`Final2` 等临时命名。
- DTO、Command、Result、Snapshot、Patch、Record、Ref、State、Summary 等后缀必须与真实职责一致。

### 6.2 对象构造规则

- 长参数静态工厂统一向 Builder 收口。
- 单个静态工厂超过 6 个参数时，默认视为待整改项。
- 不允许继续通过重载构造器层层堆新字段。
- 如果 Lombok `@Builder` 能完整等价替代手写 builder，必须优先使用 Lombok `@Builder`。
- 对值对象语义明确的对象，应优先使用不可变建模。
- 核心对象的必要字段应在构建时补齐，不允许半初始化对象进入主链路。

### 6.3 可变性规则

- 领域对象优先不可变。
- 对外集合返回只读视图。
- 核心上下文对象禁止类级别 `@Setter`。
- `AgentRunContext`、`ToolCall`、`ToolResult`、`WorkingContext`、`StateDelta` 等核心对象必须收紧可变性。
- `ToolCall` / `ToolResult` 的关键字段应在构建时补齐，构建后不再随意改写。
- 需要表达状态变化时，优先使用明确 patch / command / result 对象，而不是裸 setter。

### 6.4 工具优先规则

- 字符串判空优先使用 `StringUtils`。
- 集合判空优先使用 `CollectionUtils`。
- Map 判空使用统一风格，不在模型层散落多种写法。
- 对象判空优先使用 JDK `Objects` 相关 API。
- Lombok 注解、成熟工具类或现成 JDK API 能完整替代手写样板时，必须优先使用，不得新增重复实现。
- 不得在 model 层引入需要外部环境或 Spring 容器才能工作的工具能力。

### 6.5 内部类规则

- 生产代码中的 entity / dto / command / event / domain object 默认独立成类。
- 只有纯私有、不可复用、强绑定宿主类的 helper 才允许作为内部类。
- 跨层共享对象不得作为内部类长期存在。
- prompt schema、state patch、memory record 等正式契约对象必须独立成类。

### 6.6 预留能力规则

- 未启用的模型主类默认删除。
- 预留能力如需保留，必须有明确文档依据与归属。
- 不得在 model 层保留无文档、无调用、无明确归属的占位类。
- 不得为了未来阶段提前新增 Graph / RAG / Long-term Memory / Checkpoint 等模型对象。
- 未来能力必须等对应阶段文档明确后再进入 model。

### 6.7 注释规则

- 新增实体类、领域类、命令类、结果类、配置类、DTO、record-like value object 必须添加中文类注释。
- 新增字段必须添加中文字段注释。
- 公共接口、公共方法、对外契约对象、关键枚举与复杂规则点必须补充正式说明。
- 注释必须解释边界、约束或风险，不得机械重复代码字面含义。
- 注释必须与代码同步维护。
- 不得用注释长期补救命名不清或职责不清的问题。

---

## 7. 端口契约规则

### 7.1 `model.port` 基本规则

`model.port` 是跨层共享接口承载区。

允许放入：

- `runtime` 调用、`infra` 实现的 provider gateway；
- `runtime` 调用、`infra` 实现的 transcript store；
- `runtime` 调用、`infra` 实现的 session metadata store；
- `runtime` 调用、`infra` 实现的 state / summary / evidence / internal task repository port；
- `runtime` 调用、`infra` 实现的 prompt resource repository port。

禁止放入：

- DTO
- Entity
- Mapper
- Result object
- Command object
- Record-like value object
- Builder
- 实现类
- Spring 配置类

### 7.2 查询返回规则

- 单实体查询方法必须返回 `Optional<T>`。
- 列表查询方法返回 `List<T>`。
- 存在性判断方法返回 `boolean`。
- 写入 / 更新 / 删除方法按语义返回 `void`、`boolean` 或明确 result 对象。
- 不得用 `null` 表达查询缺失。
- 不得让调用方再包一层 `Optional.ofNullable(...)`。

### 7.3 会话元数据与 Transcript 事实源规则

- `SessionMetadataRecord` 作为 session 元数据读取结果对象，承载 conversation-session 映射信息。
- `SessionMetadataStore` 提供 pair 查询、session 存在性、创建与归档能力。
- `TranscriptStore` 保持按 `sessionId` 读写，不扩展为双 key 接口。
- `conversationId` 来源于 session metadata 正式记录，不从 transcript adapter 反向拼接或推断。
- `AgentRunContext` 中的 `requestId` 用于 runtime 事件链路透传请求关联。

---

## 8. 测试要求

### 8.1 必须覆盖的测试场景

| 测试目标 | 测试要求 |
|---|---|
| `StateDelta` | 覆盖 builder、不可变性、序列化、反序列化、`isEmpty()`、merge 相关输入 |
| `SessionStateSnapshot` | 覆盖序列化、反序列化、嵌套对象契约 |
| `ConversationSummary` | 覆盖序列化、反序列化、关键字段契约 |
| Evidence 相关对象 | 覆盖 ref / target / source 的构造、序列化、字段契约 |
| Internal Task 相关对象 | 覆盖 task type、status、audit metadata、序列化字段 |
| Working Context 相关对象 | 覆盖 block、projection、budget、working set 的构造与只读集合 |
| Prompt 相关对象 | 覆盖 template、version、variable、output schema、ref、metadata 等对象的构造与字段契约 |
| Provider 结果对象 | 覆盖 response、usage、finish reason 等对象构造与字段语义 |
| Tool 相关对象 | 覆盖 `ToolCall` / `ToolResult` / `ToolBundle` 的构造与不可变性 |
| Runtime 相关对象 | 覆盖 `AgentRunContext`、runtime event、command / result 的构造与关键字段 |
| Port 契约 | 覆盖 repository 单实体查询语义，确保契约使用 `Optional<T>` |

### 8.2 测试约束

- `model` 测试应以轻量单元测试和契约测试为主。
- 测试不依赖 Spring 容器。
- 测试不依赖 Redis / MySQL / Provider / HTTP 外部服务。
- JSON 序列化测试必须覆盖未知字段、缺失字段、嵌套对象和字段命名。
- 契约测试必须覆盖旧语义防回退。
- 修改强契约对象时，必须补充或更新对应 contract test。
- 修改 prompt schema 对象时，必须配合当前阶段 `test.md` 检查 parser allowlist 对齐。
- 旧测试与新架构冲突时，更新或删除旧测试，不为了旧测试保留过时逻辑。

---

## 9. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 文档治理规则约束。
2. 当 `annotation/`、`context/`、`memory/`、`message/`、`port/`、`prompt/`、`provider/`、`runtime/`、`tool/`、`transcript/` 包结构或核心职责发生长期变化时，必须同步更新本文件。
3. 阶段性详细设计、阶段开发计划、阶段专项验收、Codex prompt 不写入本文件，应写入当前阶段 `execution-phase/{phase-name}/`。
4. 若根目录文档中出现了本模块过细实现内容，应回收到本文件。
5. 若阶段文档中产生了长期稳定的 model 模块边界，完成阶段收口后可以晋升到本文件。
6. 未经确认，不得改变本文件的布局、标题层级与章节顺序。
7. 不得把 `app` / `runtime` / `infra` / `common` 的实现细节长期写成本模块文档。
8. 不得把某个阶段的临时实现细节固化为 model 模块长期规则。
9. 历史阶段补充规则如果已经成为长期边界，应归并到对应章节，不应继续放在“一句话总结”之后。

---

## 10. 一句话总结

`model` 模块的核心要求是：让接口只像接口、对象只像对象、结果对象只放在结果对象该在的位置。

它负责内部运行时模型与跨层共享契约，但不负责持久化实现、Provider 实现、Runtime 编排、Web DTO、Spring 装配或阶段详细设计。
