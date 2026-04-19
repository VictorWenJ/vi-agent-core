# CODE_REVIEW.md

> 更新日期：2026-04-19

## 1. 文档定位

本文件定义 `vi-agent-core` 的代码审查标准、检查清单与质量要求。

本文件用于：
- 人工代码审查时的标准化检查表
- 指导 AI 代理（如 Codex）以 Reviewer 角色审查代码

本文件只负责回答：
- Review 时应该重点检查什么
- 哪些改动会被直接拒绝
- 哪些改动必须补充测试
- 哪些问题属于阻塞项

本文件不负责：
- 架构设计说明（见 `ARCHITECTURE.md`）
- 仓库级开发规范（见 `AGENTS.md`）
- 执行清单类内容（见 `PROJECT_PLAN.md`，当前已清空）
- 模块内包结构与类职责（见各模块 `AGENTS.md`）

---

## 2. 审查原则
1. **边界优先**：先检查模块边界与依赖方向，再检查业务逻辑。
2. **质量优先**：先确认阻塞项是否被解决，再看局部实现是否优雅。
3. **可测试性**：核心逻辑没有测试，Review 不予通过。
4. **一致性**：代码、POM、文档三者必须一致。
5. **范围合规**：未更新文档前，不得擅自扩展超出已确认范围的能力。
6. **治理优先**：除了功能，还必须检查依赖治理、POM 标准化、目录语义、对象构造与文档职责边界。
7. **长期规范不可回退**：根 `AGENTS.md` 中的通用开发规范属于长期约束，不得因为“这次先改功能”而绕开。

---

## 3. 仓库级硬规则检查表（长期有效）

### 3.1 总体分层检查
- [ ] `common` 是否仍只放共享异常、ID、基础工具、纯通用常量？
- [ ] `model` 是否仍只放领域模型、值对象、枚举、端口接口？
- [ ] `runtime` 是否仍只做运行时编排、业务规则、上下文工程、tool orchestration（工具编排）？
- [ ] `infra` 是否仍只做外部系统适配实现、provider、DB/Redis repository、文件型 prompt repository？
- [ ] `app` 是否仍只做 controller、application service、Spring 配置与装配？
- [ ] 是否存在任何反向依赖，尤其是 `runtime -> infra`？
- [ ] Prompt Engine 是否仍归属 `runtime` 主链路，而非上移到 `app` 层拼装？

### 3.2 包与目录检查
- [ ] 包名是否全小写、反向域名风格？
- [ ] 目录语义是否与职责一致？
- [ ] `port` 包是否只保留接口？
- [ ] 值对象、结果对象、DTO、command、record 是否被错误放入 `port`？
- [ ] 预留能力是否进入 `reserved` / `future` / `extension` 包，或有文档依据？
- [ ] 是否还保留无文档、无调用、无明确归属的占位类？

### 3.3 Maven / POM 检查
- [ ] 根 POM 是否仍是聚合父 POM，并统一声明 `<modules>`？
- [ ] 所有第三方依赖版本是否都由根 POM `dependencyManagement` 管理？
- [ ] 子模块是否擅自写版本？
- [ ] 子模块是否显式写默认 `<scope>compile</scope>`？
- [ ] 是否只有应用模块使用 `spring-boot-maven-plugin`？
- [ ] 所有模块是否遵守 Maven 标准目录布局？
- [ ] 模块 POM 是否只表达直接依赖？

### 3.4 命名检查
- [ ] 类名是否为名词且语义完整？
- [ ] 接口名是否体现能力，而非与实现重名或混淆？
- [ ] 方法名是否为动词并准确表达动作？
- [ ] 常量名是否全部大写加下划线？
- [ ] 是否仍存在 `InvalidFailed`、`StoreFailed` 这类双重语义或模糊命名？

### 3.5 类设计检查
- [ ] 类职责是否单一？
- [ ] 超过 300 行或承担 4 类以上职责的类是否已评估拆分？
- [ ] 编排类是否仍混入 mapper 细节？
- [ ] repository 是否仍混入业务决策？
- [ ] provider 是否承担过多跨层解析或编排细节？
- [ ] 类内方法是否按功能分组？

### 3.6 方法设计检查
- [ ] 单方法参数超过 5 个时，是否已评估 Command / Request / Builder？
- [ ] 静态工厂方法超过 6 个参数时，是否已改 Builder？
- [ ] 是否长期用 `Map<String, Object>` 替代正式领域对象？
- [ ] 公共方法是否显式做参数校验？
- [ ] 是否仍通过大量 `null` 位置参数表达业务语义？

### 3.7 可变性检查
- [ ] 核心领域对象是否优先不可变？
- [ ] 对外暴露的集合是否返回只读视图？
- [ ] 核心上下文对象是否仍使用类级别 `@Setter`？
- [ ] 是否通过显式意图方法控制状态变化，而不是一组裸 setter？

### 3.8 Lombok 检查
- [ ] 简单 entity / dto / config properties 是否合理使用 Lombok？
- [ ] 核心领域对象是否滥用 `@Data`？
- [ ] 是否优先使用 `@Getter` + `@Builder`？
- [ ] 对于可被 Lombok 完整替代的 getter/setter/constructor/builder，是否仍存在手写重复实现？
- [ ] 是否把有业务语义的对象变成了“只有 Lombok 入口、没有约束”的松散对象？

### 3.9 工具类使用检查
- [ ] 字符串判空是否优先 `StringUtils.isBlank / isNotBlank`？
- [ ] 集合判空是否优先 `CollectionUtils.isEmpty / isNotEmpty`？
- [ ] Map 判空是否使用统一风格？
- [ ] 默认值是否优先使用清晰的 JDK 工具 API？
- [ ] 对可由成熟工具类或现成 API 等价替代的模板代码，是否仍存在手写重复实现？
- [ ] MySQL 持久化的查询 / 更新 / 删除是否默认采用 MyBatis-Plus Lambda Wrapper 链式写法（`Wrappers.lambdaQuery(...)` / `Wrappers.lambdaUpdate(...)`）？
- [ ] `insert(entity)` 是否仅作为标准新增写法保留，而未继续扩散可由链式写法替代的 MyBatis 逻辑？
- [ ] 是否仍残留可被链式函数写法替代的 XML / 字符串 SQL / 注解 SQL？
- [ ] `Optional` 是否只在提升可读性时使用？
- [ ] `Stream` 是否只在确实更清晰时使用？

### 3.10 占位 / 预留能力检查
- [ ] 预留接口或预留类是否有文档依据？
- [ ] 未启用的占位类是否已删除、归位或显式标记预留？
- [ ] 是否存在越过已确认范围提前引入平台级能力的情况？

### 3.11 异常与错误码检查
- [ ] `ErrorCode` 命名是否单义、可扩展？
- [ ] 错误码粒度是否按业务对象区分？
- [ ] transcript / state / summary 是否仍共用模糊错误码？
- [ ] 统一异常转换是否仍只在 `app` 层 advice？

### 3.12 文档与同步检查
- [ ] 根目录 `AGENTS.md` 是否只承载通用规范？
- [ ] `PROJECT_PLAN.md` 是否已按要求清空或保持占位？
- [ ] `ARCHITECTURE.md` 是否只承载架构边界与调用链？
- [ ] 模块 `AGENTS.md` 是否已同步更新局部规则？

---

## 4. 专项验收清单

专项验收内容已按要求清除。

## 5. 应直接拒绝的改动类型

以下情况一经发现，Review 必须打回：

1. **破坏模块依赖方向**
   - 新增或保留 `runtime -> infra`
   - 新增或保留 `infra -> runtime`
   - 让 `common` 依赖业务模块
   - 让 `model` 依赖 `runtime` / `infra` / `app`

2. **继续制造目录与包语义污染**
   - 在 `port` 包放值对象、结果对象、DTO
   - 用通用目录名承载单一技术对象的实现
   - 让未启用占位类长期滞留主代码

3. **伪流式实现**
   - `/chat/stream` 入口看似流式，实际仍调用同步 generate
   - runtime 没有 token / delta 级事件，只有粗粒度事件
   - app 层自己拼接 provider 分片逻辑

4. **WebFlux 阻塞写法**
   - 在 WebFlux 事件线程直接执行同步 Redis / Provider / Tool 阻塞调用
   - controller 中用同步式 `try/catch/finally` 包裹 `Mono/Flux`

5. **粗暴错误映射**
   - 所有运行时异常统一映射为 400
   - 捕获 `Exception` 后只打印日志不转换标准异常

6. **绕过统一工具边界**
   - 在 orchestrator / app 中直接 new 具体工具并执行
   - mock 工具绕过 `ToolRegistry` / `ToolGateway.execute(...)`

7. **对象构造与可变性失控**
   - 继续通过重载构造器适配新字段
   - 核心对象继续暴露类级别 `@Setter`
   - 通过可变集合直接篡改核心上下文对象内部状态

8. **POM 失控**
   - 子模块重复写受根 POM 管理的版本
   - 模块内显式写默认 `compile` scope
   - 在库模块挂启动插件
   - 乱引与当前范围无关的大型框架或中间件

9. **明确阻塞项被绕开而不是被解决**
   - `StateDelta` 仍无序列化闭环
   - Transcript 仍只有单边写入而无双层读写闭环
   - Transcript MySQL 仍通过全量重复 append 写入

---

## 6. 测试要求

以下改动必须补测试：

| 改动类型 | 测试要求 |
| :--- | :--- |
| 修改 `RuntimeOrchestrator` / `AgentLoopEngine` | 覆盖单轮、工具回填、多轮结束、最大迭代超限、流式事件行为 |
| 修改 `ToolRegistry` / `ToolGateway` | 覆盖注册、查找、执行、缺失工具、异常工具 |
| 修改 `OpenAICompatibleChatProvider` / streaming 解析 | 覆盖同步回复、流式回复、tool call 分片聚合、异常路径 |
| 修改 Transcript / Redis / MySQL 映射 | 覆盖保存、读取、覆盖、恢复、`turnId` 恢复、增量/幂等写入 |
| 修改 `StateDelta` / `MemoryJsonMapper` | 覆盖序列化、反序列化、merge 行为 |
| 修改 `GlobalExceptionHandler` | 覆盖 `ErrorCode -> HttpStatus` 分类映射 |
| 修改 `/chat` / `/chat/stream` | 覆盖 WebFlux 行为、SSE 基础输出、异常出口 |
| 修改 POM / 模块依赖 | 至少做一次聚合构建与关键模块测试回归 |

**当前基线最低覆盖要求**：
- runtime、infra provider、infra persistence、tool registry/gateway、app WebFlux 入口五类测试不可缺
- `StateDelta` 序列化与 Transcript 双层闭环必须有专项测试
- 可以接受开发期未做日志治理测试，但不能没有主闭环行为测试

---

## 7. 文档模板冻结规则
与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：
- 只做增量更新，不改变整体风格与章节结构
- 当根目录文档职责过细时，允许把细节下沉到模块 `AGENTS.md`
- 任何架构、依赖、POM 方向变更都必须先更新文档，再落代码

---

## 8. 一句话总结

`CODE_REVIEW.md` 的职责，是把“明确阻塞项没有解决、模块边界继续漂移、POM 继续失控、对象构造继续恶化、目录语义继续污染”这些问题拦在合并前。
