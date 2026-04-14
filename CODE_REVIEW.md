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

---

## 3. 通用审查清单

每次 Review 必须逐项确认以下检查点：

| 类别 | 检查项 | 说明 |
| :--- | :--- | :--- |
| **分层边界** | Controller 是否包含业务逻辑？ | Controller 只能做请求解析、参数校验、响应封装。 |
| | Service 是否直接操作了 Repository？ | Service 应通过 Core 层或基础设施层接口访问数据。 |
| | Core 层是否依赖了 Web 层对象？ | `core` 包不得导入 `HttpServletRequest`、`@RestController` 等。 |
| **依赖注入** | 是否存在 `@Autowired` 字段注入？ | 一律使用**构造器注入**，字段注入直接拒绝。 |
| **工具调用** | 是否绕过 `ToolGateway` 直接执行工具？ | 所有工具调用必须经由 `ToolGateway.route()`。 |
| **异常处理** | 是否捕获 `Exception` 后仅打印堆栈？ | 必须转换为 `AgentRuntimeException` 并抛出或记录后降级。 |
| | 是否抛出了非标准异常？ | 业务异常统一继承 `AgentRuntimeException`。 |
| **日志规范** | 是否存在 `System.out.println`？ | 必须使用 Slf4j，且关键节点记录 `traceId`。 |
| | 异常日志是否包含完整堆栈？ | 使用 `log.error("msg", e)`，不得只打印 `e.getMessage()`。 |
| **流式处理** | 返回 `Flux` 的接口是否处理了取消信号？ | 应使用 `Sinks.Many` 或 `Flux.create` 并监听客户端断开。 |
| **文档同步** | 代码改动是否更新了相关文档？ | 架构变更必须同步更新 `ARCHITECTURE.md`。 |
| **阶段合规** | 是否包含 Phase 2+ 的实现？ | Phase 1 不得出现记忆裁剪、子代理委派、RAG 等逻辑。 |

---

## 4. Phase 1 专项审查清单

针对当前阶段的核心模块，需额外检查以下内容：

### 4.1 AgentLoopEngine
- [ ] 循环是否设置了 `MAX_ITERATIONS` 并正确处理超限终止？
- [ ] 工具调用失败（如超时、返回错误）时，是否有明确的终止或重试策略？
- [ ] 工具执行结果是否正确回填到 `messages` 列表（`ToolExecutionResultMessage`）？
- [ ] 流式和非流式路径是否共享了相同的循环逻辑？
- [ ] 是否记录了每次迭代的 `trace` 信息（迭代次数、工具名称、耗时）？

### 4.2 ToolGateway
- [ ] 新增工具是否使用了统一的 `@Tool` 注解并正确描述了 Schema？
- [ ] `ToolRegistry` 是否在启动时自动扫描并注册了所有工具？
- [ ] 工具执行是否有超时控制（如 `@Timeout` 或 `Future.get(timeout)`）？
- [ ] 工具返回结果是否经过了标准化处理（如统一包装为 `ToolResult`）？

### 4.3 接口预留
- [ ] `ContextAssembler`、`MemoryService`、`DelegationService` 是否**仅定义了接口签名**，而未包含任何 Phase 2+ 的具体实现？
- [ ] 这些预留接口是否被正确放置在 `core` 层，供未来扩展？

### 4.4 持久化
- [ ] `ConversationRepository` 是否正确区分了 `transcript`（全量历史）与 `working context`（裁剪后）的存储？
- [ ] 实体类字段是否都有中文注释？

---

## 5. 应直接拒绝的改动类型

以下情况一经发现，Review 必须打回，不得合并：

1. **破坏架构边界**：
    - Controller 直接调用 Repository
    - Service 中包含 Agent Loop 编排逻辑
    - Core 层依赖 Web 层对象

2. **绕过工具网关**：
    - 在 `AgentLoopEngine` 中直接实例化具体工具类并调用

3. **违规依赖注入**：
    - 使用 `@Autowired` 字段注入

4. **裸写 SSE 输出**：
    - 未使用 Spring WebFlux `Flux`，手动操作 `HttpServletResponse.getOutputStream()`

5. **无测试覆盖**：
    - 修改 `AgentLoopEngine` 核心逻辑但未补充或更新单元测试

6. **删除必要注释**：
    - 删除 `record`/`@Data` 类中的中文字段注释

7. **越界实现**：
    - 在 Phase 1 中实现了 Phase 2+ 的功能（如 Token 计数裁剪、记忆提炼、子代理委派）

8. **吞噬异常**：
    - `try { ... } catch (Exception e) { }` 空处理

---

## 6. 测试要求

以下改动**必须**补充对应的单元测试或集成测试：

| 改动类型 | 测试要求 |
| :--- | :--- |
| 新增或修改 `AgentLoopEngine` 循环逻辑 | 必须测试最大迭代终止、工具调用成功/失败路径 |
| 新增 `@Tool` 工具类 | 必须测试工具执行的正常返回和异常处理 |
| 修改 `ToolGateway` 路由逻辑 | 必须测试工具查找、参数解析、超时场景 |
| 调整 API 接口契约 | 必须更新对应的 `@WebMvcTest` 或集成测试 |
| 修改持久化实体 | 必须测试 Repository 的 CRUD 操作 |

**Phase 1 目标覆盖率**：核心模块（`runtime`、`tool`）单元测试覆盖率 > 60%。

---

## 7. 文档模板冻结规则

本文件属于项目治理模板资产，受以下冻结规则约束：

1. **基线规则**：必须以当前内容为基线进行增量更新，不涉及变动的内容不得改写。
2. **冻结范围**：未经明确确认，不得改变本文件的布局、排版、标题层级、写法、风格、章节顺序。
3. **允许的修改**：
    - 在原有章节内补充新的审查项
    - 新增当前阶段确实需要的子章节
    - 更新日期、阶段信息
    - 删除已废弃的检查项
4. **禁止事项**：将本文件整体改写成另一种风格，或混入其他文档的职责内容。

---

## 8. 一句话总结

`CODE_REVIEW.md` 是 `vi-agent-core` 的代码质量守门员。它确保每一次代码提交都符合分层架构、依赖规范与阶段边界，让项目在从 Phase 1 到 Phase 4 的演进中始终保持可维护、可扩展的健康状态。