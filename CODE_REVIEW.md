# CODE_REVIEW.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core` 的代码审查标准、通用检查清单、拒绝项与质量要求。

本文件用于：

- 人工代码审查时的标准化检查表
- 指导 AI 代理（如 Codex）以 Reviewer 角色审查代码
- 统一仓库级通用 review 口径
- 阻断模块边界漂移、依赖方向漂移、对象语义漂移、测试缺失和阶段范围失控

本文件只负责回答：

- Review 时应该重点检查什么
- 哪些改动会被直接拒绝
- 哪些改动必须补充测试
- 哪些问题属于通用阻塞项
- 阶段专项验收应该去哪里读取

本文件不负责：

- 架构设计说明（见 `ARCHITECTURE.md`）
- 仓库级开发规范（见 `AGENTS.md`）
- 阶段状态、当前阶段索引与高层路线图（见 `PROJECT_PLAN.md`）
- 阶段详细系统设计（见 `execution-phase/{phase-name}/design.md`）
- 阶段开发计划（见 `execution-phase/{phase-name}/plan.md`）
- 阶段专项测试验收（见 `execution-phase/{phase-name}/test.md`）
- Codex 执行 prompt（见 `execution-phase/{phase-name}/prompts.md`）
- 阶段完成收口记录（见 `execution-phase/{phase-name}/closure.md`）
- 模块内包结构与类职责（见各模块 `AGENTS.md`）

本文件是通用 review 门禁。  
某一阶段的专项验收清单必须放在对应阶段的：

```text
execution-phase/{phase-name}/test.md
```

---

## 2. 审查原则

1. **边界优先**：先检查模块边界、包职责、依赖方向，再检查业务逻辑。
2. **质量优先**：先确认阻塞项是否被解决，再看局部实现是否优雅。
3. **可测试性**：核心逻辑没有测试，Review 不予通过。
4. **一致性**：代码、POM、根目录文档、模块文档、阶段文档必须一致。
5. **范围合规**：未更新当前阶段文档前，不得擅自扩展超出已确认范围的能力。
6. **治理优先**：除了功能，还必须检查依赖治理、POM 标准化、目录语义、对象构造、文档职责边界与阶段范围边界。
7. **长期规范不可回退**：根 `AGENTS.md` 中的通用开发规范属于长期约束，不得因为“这次先改功能”而绕开。
8. **阶段契约不可绕过**：当前阶段 `design.md / plan.md / test.md` 中声明的契约、范围和验收标准必须被执行。
9. **旧测试不绑架新架构**：旧测试与新架构冲突时，应更新或删除旧测试，不为了旧测试保留过时逻辑。
10. **一次性暴露问题**：Review 应尽量一次性指出所有显著问题，避免分批挤牙膏式反馈。

---

## 3. 仓库级硬规则检查表（长期有效）

### 3.1 总体分层检查

- [ ] `common` 是否仍只放共享异常、ID、基础工具、纯通用常量？
- [ ] `model` 是否仍只放领域模型、值对象、枚举、端口接口？
- [ ] `runtime` 是否仍只做运行时编排、业务规则、上下文工程、prompt governance、tool orchestration（工具编排）？
- [ ] `infra` 是否仍只做外部系统适配实现、provider、DB / Redis repository、cache、external repository implementation？
- [ ] `app` 是否仍只做 controller、application service、Spring 配置与装配？
- [ ] 是否存在任何反向依赖，尤其是 `runtime -> infra`？
- [ ] 是否存在 `infra -> runtime`？
- [ ] 是否存在 `model -> runtime / infra / app`？
- [ ] 是否存在 `common` 依赖业务模块？
- [ ] Prompt Engineering / Prompt Registry / Prompt Renderer 是否仍位于 `runtime` 与 `model` 契约边界内，而不是上移到 `app` 层拼装？
- [ ] Provider / Persistence / Cache 是否仍由 `infra` 实现，而不是被 `runtime` 直接实现或调用具体实现？
- [ ] `app` 是否仍是唯一装配 runtime 与 infra 的上层模块？

---

### 3.2 包与目录检查

- [ ] 包名是否全小写、反向域名风格？
- [ ] 目录语义是否与职责一致？
- [ ] `port` 包是否只保留接口？
- [ ] 值对象、结果对象、DTO、command、record 是否被错误放入 `port`？
- [ ] DTO、Entity、VO、Command、Query、Event 等对象是否按职责分目录管理？
- [ ] 是否存在 `util`、`helper`、`misc`、`temp`、`test2` 等语义模糊目录长期承载正式代码？
- [ ] 是否把无明确归属的对象下沉到 `common` 形成垃圾场？
- [ ] 包位置是否表达真实职责？
- [ ] 类名与包位置是否存在语义冲突？
- [ ] 生产代码中的 entity / dto / command / event / domain object 是否默认独立成类，而不是长期以内置类形式存在？
- [ ] 预留能力是否进入 `reserved` / `future` / `extension` 包，或有文档依据？
- [ ] 是否还保留无文档、无调用、无明确归属的占位类？

---

### 3.3 Maven / POM 检查

- [ ] 根 POM 是否仍是聚合父 POM，并统一声明 `<modules>`？
- [ ] 所有第三方依赖版本是否都由根 POM `dependencyManagement` 管理？
- [ ] 公共构建插件版本是否由根 POM `pluginManagement` 管理？
- [ ] 子模块是否擅自写依赖版本？
- [ ] 子模块是否显式写默认 `<scope>compile</scope>`？
- [ ] 是否只有应用模块使用 `spring-boot-maven-plugin` 或其他可执行打包插件？
- [ ] 所有模块是否遵守 Maven 标准目录布局？
- [ ] 模块 POM 是否只表达直接依赖？
- [ ] 是否通过“顺手加依赖”破坏分层边界？
- [ ] 是否长期依赖传递依赖而未显式声明？
- [ ] 测试依赖是否仅存在于测试作用域？
- [ ] 是否引入与当前阶段无关的大型框架或中间件？
- [ ] 是否残留 SNAPSHOT、临时本地包、未稳定版本依赖？
- [ ] POM 是否准确表达模块职责、依赖方向、构建边界与治理规则？

---

### 3.4 命名检查

- [ ] 类名是否为名词或名词短语且语义完整？
- [ ] 接口名是否体现能力边界，而非与实现重名或混淆？
- [ ] 实现类命名是否准确体现实现语义？
- [ ] 方法名是否为动词或动宾短语并准确表达动作？
- [ ] 布尔字段和布尔方法是否直接表达真假语义？
- [ ] 常量名是否全部大写加下划线？
- [ ] 枚举名和枚举项是否表达稳定业务语义？
- [ ] 错误码命名是否单义、稳定、可扩展？
- [ ] 异常类命名是否体现业务对象与异常原因？
- [ ] 测试类和测试方法命名是否能直接表达被测对象、场景和期望？
- [ ] 是否仍存在 `InvalidFailed`、`StoreFailed` 这类双重语义或模糊命名？
- [ ] 是否存在 `New`、`Old`、`Temp`、`Tmp`、`Bak`、`Final2`、`ManagerImpl2` 等临时性或历史残留命名？

---

### 3.5 类设计检查

- [ ] 类职责是否单一？
- [ ] 超过 300 行或承担 4 类以上职责的类是否已评估拆分？
- [ ] 编排类是否仍混入 mapper 细节？
- [ ] repository 是否仍混入业务决策？
- [ ] provider 是否承担跨层流程编排？
- [ ] registry 是否承担与注册无关的宽职责？
- [ ] 类内方法是否按功能分组？
- [ ] 类的公开接口是否收敛？
- [ ] 是否把内部实现细节以 `public` 形式泄漏？
- [ ] 一个类中的字段是否服务于同一主职责？
- [ ] 是否存在“万能基类”“超大上下文父类”“统一结果父对象”掩盖建模问题？
- [ ] 领域对象、配置对象、持久化对象、协议对象、运行时对象是否按职责分开建模？
- [ ] 工具类是否仍保持无状态、纯辅助性质？
- [ ] 值对象是否优先不可变？
- [ ] 对象构造完成后是否处于合法、自洽状态？
- [ ] 是否存在类名、包位置、字段集合、方法集合明显语义冲突但未整改的情况？
- [ ] 重复逻辑出现 2 处及以上时，是否已评估抽取公共方法、公共组件或公共协作者？

---

### 3.6 方法设计检查

- [ ] 一个方法是否只负责一个清晰职责？
- [ ] 方法名、参数、返回值和副作用是否一致？
- [ ] 单方法参数超过 5 个时，是否已评估 Command / Request / Builder？
- [ ] 静态工厂方法超过 6 个参数时，是否已改 Builder？
- [ ] 是否长期用 `Object`、`Map<String, Object>`、裸 `List` 替代正式领域对象？
- [ ] 公共方法是否显式做必要参数校验？
- [ ] 是否仍通过多个布尔参数、位置参数或 `null` 组合表达复杂业务语义？
- [ ] 查询方法与修改方法是否严格区分？
- [ ] 返回值是否混用 `null`、魔法值、整数状态码、字符串标记表达不同语义？
- [ ] 方法副作用是否可预期？
- [ ] 嵌套过深、分支过多、循环体过重的方法是否已拆分？
- [ ] 转换类方法是否只做转换？
- [ ] 异常处理边界是否明确？
- [ ] 是否吞异常、空 `catch`、只记录日志后继续按成功路径执行？
- [ ] 事务边界是否放在合适方法层级？

---

### 3.7 可变性检查

- [ ] 核心领域对象是否优先不可变？
- [ ] 对外暴露的集合是否返回只读视图？
- [ ] 核心上下文对象是否仍使用类级别 `@Setter`？
- [ ] 是否通过显式意图方法控制状态变化，而不是一组裸 setter？
- [ ] 核心对象的必要字段是否在构建时补齐？
- [ ] 是否允许“半初始化对象”长期在主链路中流转？
- [ ] `record` 是否只用于语义明确、数据承载型、浅不可变的小对象？
- [ ] 可变实体对象的可变边界是否显式收紧？

---

### 3.8 Lombok 检查

- [ ] 简单 entity / dto / config properties 是否合理使用 Lombok？
- [ ] 核心领域对象是否滥用 `@Data`？
- [ ] 是否优先使用 `@Getter` + `@Builder`？
- [ ] 对于值对象语义明确的对象，是否评估使用不可变建模方式？
- [ ] 对于可被 Lombok 完整替代的 getter / setter / constructor / builder，是否仍存在手写重复实现？
- [ ] 是否把有业务语义的对象变成了“只有 Lombok 入口、没有约束”的松散对象？
- [ ] 使用 Lombok 后，对象创建路径、默认值来源、构造约束是否仍然清晰？

---

### 3.9 工具类与 API 使用检查

- [ ] 字符串判空是否优先 `StringUtils.isBlank / isNotBlank`？
- [ ] 集合判空是否优先 `CollectionUtils.isEmpty / isNotEmpty`？
- [ ] Map 判空是否使用统一风格？
- [ ] 对象判空、默认值提供、非空约束是否优先使用 JDK `Objects` 相关 API？
- [ ] 对可由成熟工具类或现成 API 等价替代的模板代码，是否仍存在手写重复实现？
- [ ] MySQL 持久化的查询 / 更新 / 删除是否默认采用 MyBatis-Plus Lambda Wrapper 链式写法（`Wrappers.lambdaQuery(...)` / `Wrappers.lambdaUpdate(...)`）？
- [ ] `insert(entity)` 是否仅作为标准新增写法保留，而未继续扩散可由链式写法替代的 MyBatis 逻辑？
- [ ] 是否仍残留可被链式函数写法替代的 XML / 字符串 SQL / 注解 SQL？
- [ ] `Optional` 是否只在提升可读性时使用？
- [ ] Repository 层单实体查询方法是否统一返回 `Optional<T>`？
- [ ] Repository 调用方是否直接使用 `orElse(...)` / `orElseThrow(...)` / `map(...)` / `flatMap(...)`，而不是再包 `Optional.ofNullable(...)`？
- [ ] `Stream` 是否只在确实更清晰时使用？
- [ ] 时间处理是否优先使用 `java.time`？
- [ ] 金额语义是否避免使用 `double` / `float`？

---

### 3.10 占位 / 预留能力检查

- [ ] 预留接口或预留类是否有文档依据？
- [ ] 未启用的占位类是否已删除、归位或显式标记预留？
- [ ] 是否存在越过已确认范围提前引入平台级能力的情况？
- [ ] 预留能力是否突破当前阶段边界？
- [ ] 是否存在“先塞进来以后再说”的宽松设计？
- [ ] 如果某项能力暂不启用，是否优先删除，确有需要时再加？

---

### 3.11 异常与错误码检查

- [ ] `ErrorCode` 命名是否单义、可扩展？
- [ ] 错误码粒度是否按业务对象区分？
- [ ] transcript / state / summary / evidence / prompt / provider / tool 等对象是否共用模糊错误码？
- [ ] 统一异常转换是否仍只在 `app` 层 advice？
- [ ] 业务异常是否继承项目标准异常基类？
- [ ] 是否随处 `throw new RuntimeException(...)` 充当正式错误边界？
- [ ] 异常与错误码是否能帮助定位对象、动作、失败原因？
- [ ] 是否存在捕获 `Exception` 后只打印日志不转换标准异常的情况？
- [ ] 记录异常日志时是否保留必要上下文？

---

### 3.12 文档与阶段治理检查

- [ ] 根目录 `AGENTS.md` 是否只承载仓库级协作规则、通用开发规范、文档治理规则和长期约束？
- [ ] 根目录 `ARCHITECTURE.md` 是否只承载长期架构分层、模块边界、依赖方向与核心调用关系？
- [ ] 根目录 `PROJECT_PLAN.md` 是否只承载高层路线图、阶段状态和当前阶段索引？
- [ ] 根目录 `CODE_REVIEW.md` 是否只承载通用 review 门禁和通用检查项？
- [ ] 当前阶段详细设计是否放入 `execution-phase/{phase-name}/design.md`？
- [ ] 当前阶段开发计划是否放入 `execution-phase/{phase-name}/plan.md`？
- [ ] 当前阶段专项测试验收是否放入 `execution-phase/{phase-name}/test.md`？
- [ ] 当前阶段 Codex prompt 是否记录到 `execution-phase/{phase-name}/prompts.md`？
- [ ] 阶段完成时是否填写 `execution-phase/{phase-name}/closure.md`？
- [ ] 模块 `AGENTS.md` 是否只承载模块长期职责、包边界、本模块开发约束？
- [ ] 是否把阶段详细设计、阶段完整计划、阶段专项验收或 Codex prompt 混入根目录文档？
- [ ] 是否存在新旧阶段语义混写在同一个历史设计文档中的情况？
- [ ] 是否在没有明确文档优先级的情况下处理冲突？
- [ ] 是否用工程习惯替代冻结文档中的 class 契约？

---

### 3.13 ID 与审计标识检查

- [ ] 业务 ID、审计 ID、projection ID、snapshot ID、block ID、internal task ID 是否全部通过对应语义的 IdGenerator 集中生成？
- [ ] 是否在业务类、repository、factory、service 中手写 `"prefix-" + UUID.randomUUID()`？
- [ ] 新增 ID 是否有清晰语义和生成边界？
- [ ] runId / traceId / sessionId / conversationId / turnId / messageId / toolCallId 等链路标识是否保持职责清晰？
- [ ] debug / audit / internal task / evidence 相关标识是否没有污染主协议？

---

### 3.14 注释与契约说明检查

- [ ] 新增实体类、领域类、命令类、结果类、配置类、DTO、record-like value object 是否添加中文类注释？
- [ ] 新增字段是否添加中文字段注释？
- [ ] 公共接口、公共方法、对外契约对象、关键枚举与复杂规则点是否补充正式说明？
- [ ] 注释是否解释“为什么这样做”或“边界 / 约束 / 风险是什么”？
- [ ] 注释是否与代码同步维护？
- [ ] 是否存在长期无上下文、无责任人、无明确归属的 TODO / 临时注释？
- [ ] 是否通过注释长期补救命名不清或职责不清的问题？

---

### 3.15 主协议防污染检查

- [ ] `ChatResponse` 是否没有混入 debug / audit / internal task / evidence 细节？
- [ ] `ChatStreamEvent` 是否没有混入 debug / audit / internal task / evidence 细节？
- [ ] runtime event 与 app SSE 协议是否保持边界？
- [ ] provider delta / tool fragment 是否没有直接泄漏到 app 层？
- [ ] internal task / audit / evidence 是否仍通过独立模型和未来独立查询接口承载？
- [ ] 当前阶段未明确要求时，是否没有新增 debug API？
- [ ] 主聊天协议是否没有因阶段内部治理而发生隐性变更？

---

## 4. 阶段专项验收入口

阶段专项验收不在本文件中长期堆叠。

每个阶段必须在当前阶段目录中维护专项验收标准：

```text
execution-phase/{phase-name}/test.md
```

Review 当前阶段改动时，必须同时读取：

```text
1. 根目录 AGENTS.md
2. 根目录 PROJECT_PLAN.md
3. 根目录 ARCHITECTURE.md
4. 根目录 CODE_REVIEW.md
5. execution-phase/README.md
6. 当前阶段 README.md
7. 当前阶段 design.md
8. 当前阶段 plan.md
9. 当前阶段 test.md
10. 与当前任务相关的历史强契约文档
11. 相关模块 AGENTS.md
12. 相关源码与测试
```

阶段专项验收应至少覆盖：

- 本阶段是否严格实现 `design.md`；
- 本阶段是否严格遵守 `plan.md`；
- 本阶段是否通过 `test.md` 中定义的专项检查；
- 是否存在新旧语义并存；
- 是否偷跑后续阶段；
- 是否破坏主协议；
- 是否绕过 IdGenerator；
- 是否缺少中文注释；
- 是否为了旧测试保留过时逻辑；
- 是否引入隐藏架构问题；
- 是否需要将长期规则晋升到根目录文档。

---

## 5. 应直接拒绝的改动类型

以下情况一经发现，Review 必须打回。

### 5.1 破坏模块依赖方向

- 新增或保留 `runtime -> infra`
- 新增或保留 `infra -> runtime`
- 让 `common` 依赖业务模块
- 让 `model` 依赖 `runtime` / `infra` / `app`
- 让 provider / persistence 实现反向接管 runtime 主流程
- 让 app 绕过 runtime 直接拼装主业务链路

---

### 5.2 继续制造目录与包语义污染

- 在 `port` 包放值对象、结果对象、DTO、command、record
- 用通用目录名承载正式业务对象或单一技术对象实现
- 让未启用占位类长期滞留主代码
- 把无明确归属的代码下沉到 `common`
- 包名、类名、职责明显冲突但不整改
- 新增临时目录、临时类、临时命名并进入主链路

---

### 5.3 伪流式实现

- `/chat/stream` 入口看似流式，实际仍调用同步 generate
- runtime 没有 token / delta 级事件，只有粗粒度事件
- app 层自己拼接 provider 分片逻辑
- provider stream delta 直接泄漏到 app
- tool fragment / provider fragment 的解析被放到 controller 或 application service 中

---

### 5.4 WebFlux 阻塞写法

- 在 WebFlux 事件线程直接执行同步 Redis / Provider / Tool 阻塞调用
- controller 中用同步式 `try/catch/finally` 包裹 `Mono/Flux`
- 在 reactive 链路中隐藏阻塞调用且没有调度隔离
- 流式链路没有清晰的取消、失败和资源释放边界

---

### 5.5 粗暴错误映射

- 所有运行时异常统一映射为 400
- 捕获 `Exception` 后只打印日志不转换标准异常
- 业务异常和系统异常不区分
- 错误码无法定位对象、动作和失败原因
- 在各层自行做 HTTP 协议转换，而不是统一交给 app 层 advice

---

### 5.6 绕过统一工具边界

- 在 orchestrator / app 中直接 new 具体工具并执行
- mock 工具绕过 `ToolRegistry` / `ToolGateway.execute(...)`
- 工具执行记录与模型协议 tool message 混为一谈
- 工具权限、工具审计、工具结果摘要没有独立边界
- 为未来工具能力提前污染当前主协议

---

### 5.7 对象构造与可变性失控

- 继续通过大量重载构造器适配新字段
- 核心对象继续暴露类级别 `@Setter`
- 通过可变集合直接篡改核心上下文对象内部状态
- 允许半初始化对象长期进入主链路
- 值对象没有不可变语义且缺乏构造约束
- 正式领域对象使用 `Map<String, Object>` 承载核心结构

---

### 5.8 POM 失控

- 子模块重复写受根 POM 管理的版本
- 模块内显式写默认 `compile` scope
- 在库模块挂启动插件
- 乱引与当前范围无关的大型框架或中间件
- 测试依赖进入生产作用域
- 长期保留 SNAPSHOT、临时本地包、未稳定版本依赖
- 通过依赖绕开模块分层

---

### 5.9 阶段范围失控

- 未建立或未更新当前阶段 `design.md / plan.md / test.md` 就直接改代码
- 把阶段详细设计混入根目录 `ARCHITECTURE.md`
- 把阶段完整计划混入根目录 `PROJECT_PLAN.md`
- 把阶段专项验收混入根目录 `CODE_REVIEW.md`
- 把 Codex prompt 混入根目录 `AGENTS.md`
- 当前阶段未要求却提前实现后续阶段能力
- 没有 `closure.md` 收口记录就宣布阶段完成

---

### 5.10 强契约被绕过

- 不按冻结文档中的 class 片段实现字段名、类型、数量和语义
- 用工程习惯自行推断、改名、扩展字段
- 只检查顶层类，不递归检查字段引用的嵌套对象
- prompt schema、parser allowlist、模型契约三者不一致
- 通过修改测试或兼容旧逻辑掩盖契约不一致
- 通过修改文档迁就错误代码，而不是先确认契约升级

---

### 5.11 主协议污染

- 把 debug / audit / evidence / internal task 信息塞入 `ChatResponse`
- 把 debug / audit / evidence / internal task 信息塞入 `ChatStreamEvent`
- 把 synthetic context message 写回 raw transcript
- 把 projection / workingMessages 回刷到 transcript / working set
- 把 ToolExecution 当作 ToolMessage 持久化
- 把 memory 派生结果伪装成 raw message

---

### 5.12 ID 与注释规则被绕过

- 业务 ID / 审计 ID / projection ID / snapshot ID / block ID / internal task ID 手写 `"prefix-" + UUID.randomUUID()`
- 新增实体类、领域类、命令类、结果类、配置类、DTO、record-like value object 缺少中文类注释
- 新增字段缺少中文字段注释
- 关键边界、失败策略、线程安全假设没有说明
- 旧注释与新代码语义不一致

---

## 6. 测试要求

以下改动必须补测试。

| 改动类型 | 测试要求 |
| :--- | :--- |
| 修改 `RuntimeOrchestrator` / `AgentLoopEngine` | 覆盖单轮、工具回填、多轮结束、最大迭代超限、流式事件行为 |
| 修改 Working Context / Context Projection | 覆盖 block 构建、预算裁剪、projection、synthetic message 不回写 transcript |
| 修改 Prompt Governance / Prompt Renderer / Prompt Registry | 覆盖 template 注册、version、variable 必填校验、render 结果、output schema、parser allowlist 对齐 |
| 修改 ToolRegistry / ToolGateway | 覆盖注册、查找、执行、缺失工具、异常工具、工具结果边界 |
| 修改 Provider / streaming 解析 | 覆盖同步回复、流式回复、tool call 分片聚合、异常路径、厂商协议对象不泄漏 |
| 修改 Transcript / Redis / MySQL 映射 | 覆盖保存、读取、覆盖、恢复、`turnId` 恢复、增量/幂等写入 |
| 修改 State / Summary / Evidence / Internal Task | 覆盖序列化、反序列化、merge、cache refresh、audit、失败降级 |
| 修改 MemoryJsonMapper 或对象 JSON 映射 | 覆盖目标对象序列化、反序列化、未知字段处理、契约字段完整性 |
| 修改 IdGenerator 或 ID 生成路径 | 覆盖生成格式、语义前缀、唯一性、调用方不手写 UUID |
| 修改 GlobalExceptionHandler | 覆盖 `ErrorCode -> HttpStatus` 分类映射 |
| 修改 `/chat` / `/chat/stream` | 覆盖 WebFlux 行为、SSE 基础输出、异常出口、主协议不污染 |
| 修改 POM / 模块依赖 | 至少做一次聚合构建与关键模块测试回归 |
| 修改阶段强契约对象 | 覆盖 class contract、嵌套对象递归契约、旧语义防回退 |
| 删除或更新旧测试 | 必须说明旧测试为何不再适配新架构，并补充新的有效测试 |

当前基线最低覆盖要求：

- runtime、infra provider、infra persistence、tool registry / gateway、app WebFlux 入口五类测试不可缺；
- context、memory、prompt、tool、provider、persistence 等核心链路必须有契约测试或回归测试保护；
- 涉及 state / summary / evidence / prompt schema / parser allowlist 的改动必须有防回退测试；
- 可以接受开发期未做日志治理测试，但不能没有主闭环行为测试；
- 旧测试与新架构冲突时，必须更新或删除旧测试，不允许为了旧测试保留过时逻辑。

---

## 7. 文档治理与 Review 关系

与 `AGENTS.md` 第 4 节保持一致，本文件同样受文档治理规则约束。

Review 时必须检查：

- 本轮改动是否读取了当前阶段文档；
- 本轮改动是否遵守当前阶段 `design.md / plan.md / test.md`；
- 本轮改动是否需要更新当前阶段 `prompts.md`；
- 本轮改动是否需要在阶段完成时进入 `closure.md`；
- 本轮改动是否错误修改了根目录文档；
- 本轮改动是否应该晋升为长期规则；
- 本轮改动是否破坏历史强契约；
- 本轮改动是否存在文档更新遗漏。

文档更新原则：

- 只做增量更新，不改变整体风格与章节结构；
- 根目录文档只承载长期稳定规则；
- 阶段详细内容优先下沉到 `execution-phase/{phase-name}/`；
- 模块内长期边界下沉到模块 `AGENTS.md`；
- 架构、依赖、POM、契约方向变更必须先更新对应文档，再落代码；
- 不得用文档回写掩盖代码实现错误；
- 不得通过修改文档迁就未经确认的代码偏差。

---

## 8. Review 输出要求

Reviewer 输出结论时，应至少包含：

1. 验收结论：通过 / 不通过 / 条件通过；
2. 本轮读取的关键文档；
3. 本轮检查的模块范围；
4. 阻塞问题清单；
5. 非阻塞改进建议；
6. 测试覆盖情况；
7. 是否存在阶段范围漂移；
8. 是否存在主协议污染；
9. 是否存在强契约不一致；
10. 是否需要补充阶段文档；
11. 建议执行的验证命令。

如果是阶段验收，还必须补充：

1. 是否符合当前阶段 `design.md`；
2. 是否符合当前阶段 `plan.md`；
3. 是否符合当前阶段 `test.md`；
4. 是否需要追加 Codex prompt 到 `prompts.md`；
5. 是否可以进入 `closure.md` 收口。

---

## 9. 一句话总结

`CODE_REVIEW.md` 的职责，是把“明确阻塞项没有解决、模块边界继续漂移、POM 继续失控、对象构造继续恶化、目录语义继续污染、阶段范围继续失控、强契约继续被绕过、主协议继续被污染”这些问题拦在合并前。

阶段专项验收清单不长期堆叠在本文件中，应进入对应阶段的 `execution-phase/{phase-name}/test.md`。
