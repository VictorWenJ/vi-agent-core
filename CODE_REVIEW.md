# CODE_REVIEW.md

> 更新日期：2026-04-16

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
7. **主闭环优先**：当前 Review 重点已从“骨架是否存在”升级为“主闭环是否真实成立”，尤其要检查 DeepSeek、ToolRegistry、Redis Transcript 与 WebFlux 流式边界是否真的落地。

---

## 3. 通用审查清单
每次 Review 必须逐项确认以下检查点。**加粗项为高频违规，需特别留意**。

| 类别 | 检查项 | 说明 |
| :--- | :--- | :--- |
| **分层边界** | Controller 是否包含业务逻辑？ | Controller 只能做请求解析、参数校验、响应封装。 |
| | Service 是否直接操作了 Repository？ | Service 应通过 Core 层或基础设施层接口访问数据。 |
| | Core 层是否依赖了 Web 层对象？ | `core` 包不得导入 WebFlux/SSE 协议对象。 |
| | **Runtime 是否成为唯一主链路编排入口？** | 复杂流程不得分散到多个 Service 横向乱调。 |
| | **Maven 模块依赖是否符合 `app → runtime/infra/model/common`、`infra → runtime/model/common`？** | 禁止 `runtime` 反向依赖 `app`，禁止 `common` 依赖 `model`。 |
| **依赖注入** | **是否存在 `@Autowired` 字段注入？** | 一律使用**构造器注入**，字段注入直接拒绝。 |
| | **是否通过 `SpringContextUtils.getBean(...)` 获取核心依赖？** | 必须通过正常依赖注入装配，绕容器调用直接拒绝。 |
| **扩展机制** | 新增 Tool / Skill / Provider / Strategy 是否通过注册机制接入？ | 不允许在 Runtime 核心里不断追加 `if/else`。 |
| | 是否破坏了已有注册表契约？ | 新扩展应新增实现，不应随意改坏中心路由。 |
| **工具调用** | **是否绕过 `ToolGateway` 直接执行工具？** | 所有工具调用必须经由 `ToolGateway.route()`。 |
| | **mock 工具是否也走统一注册与网关？** | 当前阶段允许 mock，但不允许旁路 mock。 |
| **Provider / State** | 当前主 Provider 是否以 DeepSeek 为准？ | `OpenAiProvider` 可保留，但不能替代本轮主实现目标。 |
| | 当前 Transcript 正式实现是否为 Redis Hash？ | 不允许继续把 in-memory store 冒充正式 Phase 1 实现。 |
| **WebFlux** | Controller 是否仍用同步式 `try/catch/finally` 包裹 `Mono/Flux`？ | 这种写法直接判定为边界错误。 |
| **异常处理** | 是否捕获 `Exception` 后仅打印堆栈？ | 必须转换为 `AgentRuntimeException` 并抛出或记录后降级。 |
| | 是否抛出了非标准异常？ | 业务异常统一继承 `AgentRuntimeException`。 |

---

## 4. Phase 1 专项审查清单
针对当前阶段的核心模块，需额外检查以下内容：

### 4.1 RuntimeOrchestrator
- [ ] 是否存在真正的 while loop，而不是仅做单次推理？
- [ ] 循环是否设置了 `MAX_ITERATIONS` 并正确处理超限终止？
- [ ] 是否先加载 Redis Transcript，再组装 working messages？
- [ ] 是否区分 planning assistant message 与最终 assistant message？
- [ ] 工具调用失败（如超时、返回错误）时，是否有明确的终止或重试策略？
- [ ] 工具执行结果是否正确回填到 `messages` 列表与 transcript？
- [ ] 工具结果回填后，是否再次调用 LLM 完成最终回答？
- [ ] 流式和非流式路径是否共享了相同的 loop 语义？
- [ ] 是否记录了每次迭代的关键 `trace` 信息（迭代次数、工具名称、耗时、runId）？
- [ ] **Runtime 是否承担了唯一编排职责**，而不是把流程分散到 Controller / Service / Tool 内部？

### 4.2 ToolGateway
- [ ] 新增工具是否使用了统一的 `@AgentTool` 注解并正确描述了最小元数据？
- [ ] `ToolRegistry` 是否通过统一注册机制构建，而不是长期保留手工写死工具表？
- [ ] mock 工具是否与真实工具共享统一 `ToolDefinition` / `ToolCall` / `ToolResult` 表达？
- [ ] 工具执行是否有清晰的超时 / 异常策略？
- [ ] 工具返回结果是否经过了标准化处理（统一包装为 `ToolResult`）？
- [ ] 是否保留了扩展点模式，而不是在网关里写死工具分支？

### 4.3 接口预留
- [ ] `ContextAssembler` 的 Phase 1 实现是否仅返回全量历史消息，未包含 Token 计数或裁剪逻辑？
- [ ] `MemoryService`、`DelegationService`、`SkillRegistry` 是否**仅定义了接口签名**，而未包含任何 Phase 2+ 的完整实现？
- [ ] 这些预留接口是否被正确放置在 `runtime` 层，供未来扩展？

### 4.4 持久化与状态
- [ ] Phase 1 是否至少具备最小 Transcript（用户消息、助手回复、工具调用记录）和 `traceId` / `runId`？
- [ ] 当前 Transcript 正式实现是否为 Redis Hash，而不是 in-memory 临时实现？
- [ ] Redis key / field / value 方案是否稳定、清晰、可恢复？
- [ ] `conversationId` / `turnId` / `messageId` / `toolCallId` 是否已进入建模与链路传递？
- [ ] Working Context 与 Transcript 是否仍然严格分离？
- [ ] 实体类字段是否都有中文注释？
- [ ] **是否把运行时数据错误地做成 static 全局状态？**

### 4.5 旧经验继承检查
- [ ] 入口层是否保持轻薄，类似旧项目中 API Provider 只做转发？
- [ ] 复杂逻辑是否落在中心编排器，而不是分散到多个工具类？
- [ ] 新增扩展是否优先采用“注册表 + 接口”而不是“中心类膨胀”？
- [ ] 关键路径是否具备运行时监控，而不是只依赖日志排查？
- [ ] Maven 多模块边界是否清晰（`app` 负责装配与启动，`runtime` 保持独立）？

### 4.6 Lombok、日志与公共工具专项检查
- [ ] 是否已在需要日志的类中优先使用 Lombok `@Slf4j`，而不是重复手写 `LoggerFactory.getLogger(...)`？
- [ ] 是否已在合适对象上使用 Lombok 注解简化样板代码，同时避免在领域核心对象上滥用 `@Data`？
- [ ] 日志输出是否仍满足结构化字段要求，且未因引入 Lombok 而弱化日志规范？
- [ ] 是否存在标准 `vi-agent-core-app/src/main/resources/log4j2-spring.xml` 配置文件，并由其统一管理日志级别、格式、Appender 与滚动策略？
- [ ] 是否优先复用了 `JsonUtils` 等现有公共工具类，而不是重复实现相同逻辑？
- [ ] 新增工具类是否仍保持无状态、通用、可复用边界，未承载业务编排？
- [ ] WebFlux controller 是否已去除同步式 `try/catch/finally` 包裹 `Mono/Flux` 的错误写法？
- [ ] runtime / infra 关键日志是否至少带 `traceId`、`runId`、`sessionId`，并尽量带 `conversationId`、`turnId`、`toolCallId`？

---

## 5. 应直接拒绝的改动类型
以下情况一经发现，Review 必须打回，不得合并：

1. **破坏架构边界**：
   - Controller 直接调用 Repository
   - Service 中包含 Agent Loop 编排逻辑
   - Core 层依赖 Web 层对象

2. **绕过工具网关**：
   - 在 `RuntimeOrchestrator` 中直接实例化具体工具类并调用
   - mock 工具绕过 `ToolRegistry` / `ToolGateway`

3. **违规依赖注入**：
   - 使用 `@Autowired` 字段注入
   - 使用 `SpringContextUtils.getBean(...)` 绕过正常依赖注入

4. **错误的 WebFlux 写法**：
   - 未使用 Spring WebFlux `Flux` / `Mono` 语义，手动操作 `HttpServletResponse.getOutputStream()`
   - 在 controller 中用同步式 `try/catch/finally` 包裹 `Mono/Flux` 记录结果或异常

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
   - 在存在 `JsonUtils` 等公共工具的前提下继续散落重复实现同类通用逻辑

10. **破坏既定模块方案**：
   - 新增与本阶段无关的 `contract/api` 模块
   - 将 `runtime` 与 `app` 形成反向依赖
   - 让 `common` 依赖 `model`

11. **破坏当前阶段技术收口**：
   - 用 OpenAI 替代当前主 DeepSeek 实现作为 Phase 1 完成口径
   - 继续把 in-memory Transcript 作为正式实现
   - 在 `RuntimeBeanConfig` 中长期硬编码正式工具注册表
   - 把 MySQL Transcript 持久化提前做成当前阶段的主交付物

---

## 6. 测试要求
以下改动**必须**补充对应的单元测试或集成测试：

| 改动类型 | 测试要求 |
| :--- | :--- |
| 新增或修改 `RuntimeOrchestrator` 循环逻辑 | 必须测试最大迭代终止、工具调用成功/失败路径、二次推理路径 |
| 新增 `@AgentTool` 工具类 | 必须测试工具注册、正常返回和异常处理 |
| 修改 `ToolGateway` / `ToolRegistry` 路由逻辑 | 必须测试工具查找、参数解析、缺失工具与超时场景 |
| 新增或修改 `DeepSeekProvider` | 必须测试普通回复解析、tool call 解析、异常路径 |
| 引入 Redis Transcript 存储 | 必须测试保存、读取、覆盖、空值与不存在场景 |
| 调整 API 接口契约 | 必须更新对应的 `@WebFluxTest` 或集成测试 |
| 新增运行时状态字段 | 必须测试 Transcript / Trace / RunState 的写入与读取 |

**Phase 1 目标覆盖要求**：
- 关键路径优先补**行为测试**，不要只补简单 getter/setter 测试；
- `runtime`、`tool`、`provider`、`transcript`、`app` 的关键路径都必须覆盖；
- 工具调用、异常路径、超时路径、空注册表路径、Redis 恢复路径都必须覆盖；
- 未来接入委派、记忆、RAG 时，必须为接口边界补最小回归测试。

---

## 7. 文档模板冻结规则
与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：
- 只做增量更新，不改变整体风格与章节结构。
- 任何架构变更必须先更新本文档，再修改代码。

---

## 8. 一句话总结

`CODE_REVIEW.md` 是 `vi-agent-core` 在当前 Phase 1 补齐期的代码质量守门员。它不仅要保证分层架构、依赖规范与阶段边界，还要确保 DeepSeek、统一 ToolRegistry、Redis Transcript、正确的 WebFlux 流式边界与最小可恢复主闭环真正落地，而不是停留在骨架或占位状态。
