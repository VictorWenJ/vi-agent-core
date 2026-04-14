# CODE_REVIEW.md

> 更新日期：2026-04-15

## 1. 文档定位

本文件定义 `vi-agent-core` 的代码审查标准、检查清单与验收要求。

本文件用于：
- 人工代码审查时的标准化检查表
- 指导 AI 代理（如 Codex）以 Reviewer 角色审查代码

本文件只负责回答：
- Review 时应该重点检查什么
- 哪些改动会被直接拒绝
- 哪些改动必须补充测试

本文件不负责：
- 架构设计说明（见 `ARCHITECTURE.md`）
- 开发规范与协作规则（见 `AGENTS.md`）
- 阶段规划与路线图（见 `PROJECT_PLAN.md`）

---

## 2. 审查原则

1. **边界优先**：先检查分层调用关系是否越界，再检查代码逻辑是否正确。
2. **可测试性**：新增或修改的核心逻辑若无对应测试，Review 不予通过。
3. **一致性**：代码实现必须与 `ARCHITECTURE.md`、`AGENTS.md` 中的约定保持一致。
4. **阶段合规**：不得包含超出当前阶段（Phase 1）范围的功能实现。
5. **编排优先**：先检查是否维护了 Runtime Core 的单一编排中心，再检查具体子模块实现。
6. **经验继承 + 反模式规避**：保留旧项目中“中心编排、注册扩展、运行时监控”的优点，禁止继续扩散 static 全局状态、容器绕路取 Bean、业务工具类化等旧问题。

---

## 3. 通用审查清单

每次 Review 必须逐项确认以下检查点。**加粗项为高频违规，需特别留意**。

| 类别 | 检查项 | 说明 |
| :--- | :--- | :--- |
| **分层边界** | Controller 是否包含业务逻辑？ | Controller 只能做请求解析、参数校验、响应封装。 |
| | Service 是否直接操作了 Repository？ | Service 应通过 Core 层或基础设施层接口访问数据。 |
| | Core 层是否依赖了 Web 层对象？ | `core` 包不得导入 `HttpServletRequest`、`@RestController` 等。 |
| | **Runtime 是否成为唯一主链路编排入口？** | 复杂流程不得分散到多个 Service 横向乱调。 |
| **依赖注入** | **是否存在 `@Autowired` 字段注入？** | 一律使用**构造器注入**，字段注入直接拒绝。 |
| | **是否通过 `SpringContextUtils.getBean(...)` 获取核心依赖？** | 必须通过正常依赖注入装配，绕容器调用直接拒绝。 |
| **扩展机制** | 新增 Tool / Skill / Provider / Strategy 是否通过注册机制接入？ | 不允许在 Runtime 核心里不断追加 `if/else`。 |
| | 是否破坏了已有注册表契约？ | 新扩展应新增实现，不应随意改坏中心路由。 |
| **工具调用** | **是否绕过 `ToolGateway` 直接执行工具？** | 所有工具调用必须经由 `ToolGateway.route()`。 |
| **异常处理** | 是否捕获 `Exception` 后仅打印堆栈？ | 必须转换为 `AgentRuntimeException` 并抛出或记录后降级。 |
| | 是否抛出了非标准异常？ | 业务异常统一继承 `AgentRuntimeException`。 |
| **日志规范** | **是否存在 `System.out.println`？** | 必须使用 Slf4j，且关键节点记录 `traceId` / `runId`。 |
| | 异常日志是否包含完整堆栈？ | 使用 `log.error("msg", e)`，不得只打印 `e.getMessage()`。 |
| | 关键路径是否具备 count / error / latency 观测点？ | 运行时核心、工具网关、外部依赖调用必须可观测。 |
| **状态边界** | **是否混淆了 transcript、working context、memory、artifact？** | 四者职责必须清晰，不得互相替代。 |
| | 是否把大对象、原始工具结果直接塞入上下文？ | 应通过摘要、引用或 Artifact 机制处理。 |
| **流式处理** | 返回 `Flux` 的接口是否处理了取消信号？ | 应使用 `Sinks.Many` 或 `Flux.create` 并监听客户端断开。 |
| **文档同步** | 代码改动是否更新了相关文档？ | 架构变更必须同步更新 `ARCHITECTURE.md`。 |
| **阶段合规** | 是否包含 Phase 2+ 的实现？ | Phase 1 不得出现长期记忆、子代理委派、RAG 等完整逻辑。 |

---

## 4. Phase 1 专项审查清单

针对当前阶段的核心模块，需额外检查以下内容：

### 4.1 RuntimeOrchestrator
- [ ] 循环是否设置了 `MAX_ITERATIONS` 并正确处理超限终止？
- [ ] 工具调用失败（如超时、返回错误）时，是否有明确的终止或重试策略？
- [ ] 工具执行结果是否正确回填到 `messages` 列表（如 `ToolExecutionResultMessage`）？
- [ ] 流式和非流式路径是否共享了相同的循环逻辑？
- [ ] 是否记录了每次迭代的 `trace` 信息（迭代次数、工具名称、耗时、runId）？
- [ ] **Runtime 是否承担了唯一编排职责**，而不是把流程分散到 Controller / Service / Tool 内部？

### 4.2 ToolGateway
- [ ] 新增工具是否使用了统一的 `@Tool` 注解并正确描述了 Schema？
- [ ] `ToolRegistry` 是否在启动时自动扫描并注册了所有工具？
- [ ] 工具执行是否有超时控制（如 `@Timeout` 或 `Future.get(timeout)`）？
- [ ] 工具返回结果是否经过了标准化处理（如统一包装为 `ToolResult`）？
- [ ] 是否保留了扩展点模式，而不是在网关里写死工具分支？

### 4.3 接口预留
- [ ] `ContextAssembler` 的 Phase 1 实现是否仅返回全量历史消息，未包含 Token 计数或裁剪逻辑？
- [ ] `MemoryService`、`DelegationService`、`SkillRegistry` 是否**仅定义了接口签名**，而未包含任何 Phase 2+ 的完整实现？
- [ ] 这些预留接口是否被正确放置在 `core` 层，供未来扩展？

### 4.4 持久化与状态
- [ ] Phase 1 是否至少具备最小 Transcript（用户消息、助手回复、工具调用记录）和 `traceId` / `runId`？
- [ ] 如当前阶段已引入 Working Context 持久化，是否与 Transcript 严格分离？
- [ ] 实体类字段是否都有中文注释？
- [ ] **是否把运行时数据错误地做成 static 全局状态？**

### 4.5 旧经验继承检查
- [ ] 入口层是否保持轻薄，类似旧项目中 API Provider 只做转发？
- [ ] 复杂逻辑是否落在中心编排器，而不是分散到多个工具类？
- [ ] 新增扩展是否优先采用“注册表 + 接口”而不是“中心类膨胀”？
- [ ] 关键路径是否具备运行时监控，而不是只依赖日志排查？

---

## 5. 应直接拒绝的改动类型

以下情况一经发现，Review 必须打回，不得合并：

1. **破坏架构边界**：
   - Controller 直接调用 Repository
   - Service 中包含 Agent Loop 编排逻辑
   - Core 层依赖 Web 层对象

2. **绕过工具网关**：
   - 在 `RuntimeOrchestrator` 中直接实例化具体工具类并调用

3. **违规依赖注入**：
   - 使用 `@Autowired` 字段注入
   - 使用 `SpringContextUtils.getBean(...)` 绕过正常依赖注入

4. **裸写 SSE 输出**：
   - 未使用 Spring WebFlux `Flux`，手动操作 `HttpServletResponse.getOutputStream()`

5. **无测试覆盖**：
   - 修改 `RuntimeOrchestrator` 核心逻辑但未补充或更新单元测试

6. **删除必要注释**：
   - 删除 `record` 组件、DTO 字段、Entity 字段中的中文注释

7. **越界实现**：
   - 在 Phase 1 中实现了 Phase 2+ 的功能（如 Token 计数裁剪、长期记忆提炼、子代理委派、RAG）

8. **吞噬异常**：
   - `try { ... } catch (Exception e) { }` 空处理

9. **扩散旧项目反模式**：
   - 新增 static 可变共享状态
   - 把业务编排塞进 `Utils`
   - 在核心链路里直接 `new` 外部客户端
   - 用全局工具类替代网关/注册表

---

## 6. 测试要求

以下改动**必须**补充对应的单元测试或集成测试：

| 改动类型 | 测试要求 |
| :--- | :--- |
| 新增或修改 `RuntimeOrchestrator` 循环逻辑 | 必须测试最大迭代终止、工具调用成功/失败路径 |
| 新增 `@Tool` 工具类 | 必须测试工具执行的正常返回和异常处理 |
| 修改 `ToolGateway` 路由逻辑 | 必须测试工具查找、参数解析、超时场景 |
| 调整 API 接口契约 | 必须更新对应的 `@WebFluxTest` 或集成测试 |
| 修改持久化实体 | 必须测试 Repository 的 CRUD 操作 |
| 新增注册表型扩展点 | 必须测试注册、查找、缺失类型与异常路径 |
| 新增运行时状态字段 | 必须测试 Transcript / Trace / RunState 的写入与读取 |

**Phase 1 目标覆盖率**：核心模块（`runtime`、`tool`）单元测试覆盖率 > 60%。

同时要求：
- 关键路径优先补**行为测试**，不要只补简单 getter/setter 测试；
- 工具调用、异常路径、超时路径、空注册表路径都必须覆盖；
- 未来接入委派、记忆、RAG 时，必须为接口边界补最小回归测试。

---

## 7. 文档模板冻结规则

与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：
- 只做增量更新，不改变整体风格与章节结构。
- 任何架构变更必须先更新本文档，再修改代码。

---

## 8. 一句话总结

`CODE_REVIEW.md` 是 `vi-agent-core` 的代码质量守门员。它不仅要保证分层架构、依赖规范与阶段边界，还要确保项目真正继承旧项目里有效的工程经验，并主动规避旧项目中已经暴露出的反模式。
