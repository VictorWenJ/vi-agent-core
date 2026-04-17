# CODE_REVIEW.md

> 更新日期：2026-04-17

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
- 仓库级开发规范（见 `AGENTS.md`）
- 阶段任务与里程碑（见 `PROJECT_PLAN.md`）
- 模块内包结构与类职责（见各模块 `AGENTS.md`）

---

## 2. 审查原则
1. **边界优先**：先检查模块边界与依赖方向，再检查业务逻辑。
2. **主闭环优先**：先确认 Runtime 主闭环是否真实成立，再看局部实现是否优雅。
3. **可测试性**：核心逻辑没有测试，Review 不予通过。
4. **一致性**：代码、POM、文档三者必须一致。
5. **阶段合规**：不得超出当前 Phase 1 范围。
6. **治理优先**：当前阶段除了功能，还必须检查依赖治理、POM 标准化与文档职责边界。
7. **开发期宽严有别**：日志暴露与 Redis TTL/trim 不是本轮阻塞项，但假流式、反向依赖、错误码粗暴映射、消息链路不完整是阻塞项。

---

## 3. 通用审查清单

| 类别 | 检查项 | 说明 |
| :--- | :--- | :--- |
| **分层边界** | Controller 是否包含业务逻辑？ | Controller 只能做请求绑定、协议适配、异常出口协作。 |
| | Application / Facade 是否演化成第二编排器？ | Facade 只能做转发、映射、阻塞隔离、SSE 适配。 |
| | Runtime 是否仍是唯一主链路编排入口？ | 不允许出现并列编排器。 |
| | Infra 是否承担了流程主导？ | provider / persistence / integration 只能做实现与适配。 |
| **Maven / 依赖方向** | POM 是否符合 `app -> runtime/infra/model/common`、`runtime -> model/common`、`infra -> model/common`？ | 禁止 `infra -> runtime` 继续存在。 |
| | 根 POM 是否统一管理版本和插件？ | 子模块不应重复写版本与插件配置。 |
| | 启动插件是否只保留在 `app` 模块？ | 其余模块应保持普通 jar 模块。 |
| **共享契约** | `LlmGateway`、`TranscriptStore`、`@AgentTool`、`ToolBundle` 等是否继续滞留在 `runtime`？ | 跨层共享契约应下沉到 `model`（少量纯基础能力留在 `common`）。 |
| **工具调用** | 是否绕过 `ToolGateway` 直接执行工具？ | 所有工具调用必须经过统一注册和路由。 |
| | mock 工具是否也走统一注册链路？ | 当前阶段允许 mock，但不允许旁路。 |
| **Provider / Streaming** | streaming 是否真正走 provider streaming？ | 禁止流式入口最后仍调用同步 generate。 |
| | app 是否只做 SSE 适配？ | 禁止在 app 层解析 provider 协议分片。 |
| **WebFlux** | 同步 `/chat` 是否显式隔离阻塞调用？ | 禁止把 Redis / Provider / Tool 的阻塞逻辑直接跑在事件线程。 |
| **异常处理** | 是否按 `ErrorCode` 分类映射 HTTP 状态码？ | 不允许所有 `AgentRuntimeException` 一律返回 400。 |
| **状态链路** | `turnId` 是否进入主消息模型与 transcript？ | 需要支持按 turn 回放与排障。 |
| **核心模型对象构造** | 是否通过堆叠重载构造器适配新字段？ | 核心模型应优先使用“完整主构造器 + 语义化静态工厂”。 |
| **文档一致性** | 改动后根目录文档与模块文档是否仍职责清晰？ | 禁止把模块细节重新堆回根目录文档。 |

---

## 4. Phase 1 专项审查清单

### 4.1 RuntimeOrchestrator
- [ ] 是否仍然是唯一主链路编排中心？
- [ ] 是否存在真正的 while loop，而不是单轮调用？
- [ ] 是否正确加载 / 保存 Transcript？
- [ ] 是否在 tool result 回填后再次调用 LLM？
- [ ] streaming 与 sync 是否共享同一套 Runtime 语义？
- [ ] `executeStreaming(...)` 是否真正进入 streaming 语义，而不是包装后仍走同步 run？
- [ ] 是否能产生 token / delta 级别事件，而不是只有阶段事件？
- [ ] 是否记录了最小链路标识：`traceId`、`runId`、`sessionId`、`conversationId`、`turnId`？

### 4.2 ToolGateway / ToolRegistry
- [ ] 是否统一使用 `@AgentTool`？
- [ ] `ToolRegistry` 是否通过统一注册机制构建，而不是长期保留手工硬编码工具表？
- [ ] mock 工具是否与真实工具共享统一 `ToolDefinition` / `ToolCall` / `ToolResult`？
- [ ] 工具调用是否通过 `ToolGateway` 路由，而不是在 orchestrator/app 中写死？
- [ ] 缺失工具、执行异常、参数错误路径是否有明确处理？

### 4.3 共享契约与依赖治理
- [ ] `LlmGateway`、`TranscriptStore`、`@AgentTool`、`ToolBundle` 等跨层共享契约是否已下沉到 `model`？
- [ ] `infra/pom.xml` 是否已移除对 `runtime` 的依赖？
- [ ] `runtime` 是否只依赖抽象，不依赖 provider / Redis / Web 协议实现？
- [ ] `common` 是否仍保持轻量，没有被扩成运行时 SPI 杂糅层？

### 4.4 持久化与状态
- [ ] 当前 Transcript 正式实现是否仍为 Redis Hash？
- [ ] `turnId` 是否进入主消息模型与 transcript 恢复链路？
- [ ] `conversationId`、`messageId`、`toolCallId` 是否可在链路中传递与恢复？
- [ ] Redis 映射职责是否仍留在 `infra.persistence`，没有污染 `model`？
- [ ] 是否误把 Redis 裁剪 / TTL / MySQL 升级当成本轮必做项？（本轮不应因此阻塞）

### 4.5 WebFlux / 异常出口
- [ ] `/api/chat` 是否已对阻塞调用做调度隔离？
- [ ] Controller 是否继续保持轻薄，没有同步式 `try/catch/finally` 包裹 `Mono/Flux`？
- [ ] `GlobalExceptionHandler` 是否已按错误类型映射到更合理的 HTTP 状态码？
- [ ] `/api/chat/stream` 是否输出真实内容流而不是只有阶段流？

### 4.6 POM、文档与治理收口
- [ ] 根 POM 是否统一管理版本、测试依赖与插件？
- [ ] 模块 POM 是否只声明本模块直接依赖？
- [ ] 文档是否已完成职责重整：根目录文档只保留仓库级内容，模块细节下沉到模块 `AGENTS.md`？
- [ ] 代码结构变化是否已同步更新到对应模块文档？
- [ ] 根目录文档与模块文档是否仍使用统一命名，例如 `@AgentTool` 而不是旧 `@Tool`？

### 4.7 核心模型对象构造
- [ ] 是否为了兼容新字段而持续新增多个近似签名构造器？
- [ ] 是否存在多个相同类型参数导致调用语义不清的问题？
- [ ] 是否应改为“一个完整主构造器 + 少量语义化静态工厂方法”？
- [ ] 是否将“运行时新建”和“持久化恢复”错误地混在一组重载构造器中？
- [ ] 是否存在通过 `null` 位置参数表达业务语义的情况？

---

## 5. 应直接拒绝的改动类型

以下情况一经发现，Review 必须打回：

1. **破坏模块依赖方向**
   - 继续保留或新增 `infra -> runtime`
   - 让 `common` 依赖业务模块
   - 让 `model` 依赖 `runtime` / `infra` / `app`

2. **伪流式实现**
   - `/chat/stream` 入口看似流式，实际仍调用同步 generate
   - runtime 没有 token / delta 级事件，只有阶段事件
   - app 层自己拼接 provider 分片逻辑

3. **WebFlux 阻塞写法**
   - 在 WebFlux 事件线程直接执行同步 Redis / Provider / Tool 阻塞调用
   - controller 中用同步式 `try/catch/finally` 包裹 `Mono/Flux`

4. **粗暴错误映射**
   - 所有运行时异常统一映射为 400
   - 捕获 `Exception` 后只打印日志不转换标准异常

5. **绕过统一工具边界**
   - 在 orchestrator / app 中直接 new 具体工具并执行
   - mock 工具绕过 `ToolRegistry` / `ToolGateway`

6. **继续扩散旧反模式**
   - static 可变共享状态
   - 业务编排塞进 `Utils`
   - 在核心链路直接 `new` 外部客户端
   - 手工从容器拉 Bean

7. **POM 失控**
   - 子模块重复写受根 POM 管理的版本
   - 在库模块挂启动插件
   - 乱引与当前阶段无关的大型框架或中间件

8. **文档职责回潮**
   - 把模块内包结构、类职责、实现细节重新堆进根目录文档
   - 代码已经改名改包，但文档不更新
   - 同一概念同时保留两套命名（如 `@Tool` 与 `@AgentTool`）

9. **核心模型构造失控**
   - 通过不断新增重载构造器适配新字段
   - 用多个近似签名构造器混用“运行时新建”和“持久化恢复”语义
   - 通过 `null` 位置参数对外表达业务语义，而不是改为静态工厂模式

---

## 6. 测试要求

以下改动必须补测试：

| 改动类型 | 测试要求 |
| :--- | :--- |
| 修改 `RuntimeOrchestrator` / `AgentLoopEngine` | 覆盖单轮、工具回填、多轮结束、最大迭代超限、流式事件行为 |
| 修改 `ToolRegistry` / `ToolGateway` | 覆盖注册、查找、执行、缺失工具、异常工具 |
| 修改 `DeepSeekChatProvider` / streaming 解析 | 覆盖同步回复、流式回复、tool call 分片聚合、异常路径 |
| 修改 Transcript / Redis 映射 | 覆盖保存、读取、覆盖、恢复、`turnId` 恢复 |
| 修改 `GlobalExceptionHandler` | 覆盖 `ErrorCode -> HttpStatus` 分类映射 |
| 修改 `/chat` / `/chat/stream` | 覆盖 WebFlux 行为、SSE 基础输出、异常出口 |

**当前阶段最低覆盖要求**：
- 不再只有 controller 测试
- 至少要补齐 runtime、infra provider、infra persistence、tool registry/gateway、app WebFlux 入口五类测试
- 可以接受开发期未做日志治理测试，但不能没有主闭环行为测试

---

## 7. 文档模板冻结规则
与 `AGENTS.md` 第 4 节保持一致，本文件同样受模板冻结规则约束：
- 只做增量更新，不改变整体风格与章节结构
- 当根目录文档职责过细时，允许把细节下沉到模块 `AGENTS.md`
- 任何架构、依赖、POM 方向变更都必须先更新文档，再落代码

---

## 8. 一句话总结

`CODE_REVIEW.md` 当前的职责，是把“假流式、反向依赖、POM 混乱、错误码粗暴映射、消息链路不完整、文档职责漂移”这些会直接影响 Phase 1 收口质量的问题拦在合并前。
