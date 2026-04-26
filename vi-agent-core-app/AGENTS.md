# AGENTS.md

> 更新日期：2026-04-26

## 1. 文档定位

本文件定义 `vi-agent-core-app` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：

- `app` 模块负责什么
- `app` 模块不负责什么
- `app` 模块内包结构如何约定
- 在 `app` 模块开发时必须遵守哪些局部规则
- `app` 模块测试与依赖应如何建设

本文件不负责：

- 仓库级协作规则与通用开发规范（见根目录 `AGENTS.md`）
- 项目高层路线图、阶段状态与当前阶段索引（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准与通用测试门禁（见根目录 `CODE_REVIEW.md`）
- 阶段详细设计、阶段开发计划、阶段专项测试验收、Codex prompt、阶段收口记录（见 `execution-phase/{phase-name}/`）
- `runtime` / `infra` / `model` / `common` 模块内部职责细节（见对应模块 `AGENTS.md`）

执行 `app` 模块相关任务前，必须先读：

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

`vi-agent-core-app` 是整个 `vi-agent-core` 系统的**唯一运行模块**。

模块目标：

- 启动 Spring Boot 应用；
- 暴露 WebFlux 同步与流式接口；
- 提供 Facade 层，把请求委托给 `RuntimeOrchestrator`；
- 承接顶层 Bean 装配，把 runtime 与 infra 连接起来；
- 作为 HTTP / SSE 的唯一协议出口，承接错误响应与流式适配。

模块在整体依赖链中的位置：

```text
app -> runtime + infra + model + common
```

因此，`app` 模块是：

- **唯一运行入口**
- **WebFlux 接入层**
- **顶层装配层**
- **SSE 协议适配层**

但 `app` 模块不是：

- Runtime 编排层
- Provider 实现层
- Persistence 实现层
- 通用工具层
- 内部模型定义层
- 阶段详细设计承载层

换句话说：

**`app` 模块负责“接进来、装起来、转出去”，不负责“在里面跑完整业务”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）启动与装配

- `ViAgentCoreApplication`
- `config/` 下的顶层 Bean 装配
- `config/properties/` 下的配置绑定对象
- 预算参数、Provider 配置、Prompt 相关配置、Redis / MySQL 持久化配置统一加载

#### 2）Web 接入

- `api/controller/`
- `/api/chat`
- `/api/chat/stream`
- `/health`

#### 3）Application Facade

- `ChatApplicationService`
- `ChatStreamApplicationService`

#### 4）异常出口

- `api/advice/GlobalExceptionHandler`
- API 错误响应 DTO

#### 5）Web 协议 DTO

- `api/dto/request/`
- `api/dto/response/`

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `app` 模块：

- `RuntimeOrchestrator` 主循环逻辑
- Agent Loop 执行逻辑
- ToolRegistry / ToolGateway 运行时路由逻辑
- Provider 协议实现与 streaming 分片解析
- Redis / MySQL repository 实现
- Transcript / State / Summary / Evidence 持久化实现
- 运行时共享契约定义
- 公共 JSON / 校验 / ID 工具
- Transcript / Message / Tool 内部模型定义
- Prompt Registry / Prompt Renderer 的运行时治理逻辑
- Internal Task / Audit / Evidence 的业务处理逻辑
- 阶段 debug API 的提前实现

如果一段代码需要：

- 决定 Agent Loop 如何执行；
- 直接解析模型厂商流式响应；
- 直接操作 Redis / MySQL repository 或 mapper；
- 被 `runtime` 与 `infra` 同时依赖；
- 定义跨层共享领域模型；
- 决定 post-turn memory update 策略；
- 处理 prompt schema 与 parser allowlist 一致性；

那它就不属于 `app` 模块。

---

## 4. 模块内包结构约定

当前 `app` 模块包结构固定为：

```text
com.vi.agent.core.app
├── ViAgentCoreApplication.java
├── api/
│   ├── advice/
│   ├── controller/
│   └── dto/
│       ├── request/
│       └── response/
├── application/
└── config/
    └── properties/
```

推荐类分布示例：

```text
com.vi.agent.core.app/
├── ViAgentCoreApplication.java
├── api/
│   ├── advice/
│   │   └── GlobalExceptionHandler.java
│   ├── controller/
│   │   ├── ChatController.java
│   │   ├── ChatStreamController.java
│   │   └── HealthController.java
│   └── dto/
│       ├── request/
│       │   └── ChatRequest.java
│       └── response/
│           ├── ApiErrorResponse.java
│           ├── ChatResponse.java
│           └── ChatStreamEvent.java
├── application/
│   ├── ChatApplicationService.java
│   └── ChatStreamApplicationService.java
└── config/
    ├── IdGeneratorConfig.java
    ├── ObservabilityConfig.java
    ├── PersistenceConfig.java
    ├── ProviderConfig.java
    ├── RuntimeCoreConfig.java
    ├── ToolConfig.java
    └── properties/
        ├── ProviderRoutingProperties.java
        └── RuntimeProperties.java
```

### 4.1 `api/advice/`

职责：

- 统一承接 Web 层异常出口；
- 将标准异常转换为 API 错误响应；
- 负责 `ErrorCode -> HttpStatus` 映射。

约束：

- 只做错误响应映射；
- 不补业务兜底流程；
- 不承载 Runtime 决策逻辑；
- 不吞异常后伪造成功响应；
- 不输出 internal task / audit / evidence 的内部细节。

### 4.2 `config/`

职责：

- 负责 runtime / infra / app 的顶层接线；
- 绑定配置项；
- 装配 Provider、Repository、ToolRegistry、IdGenerator、Runtime 协作者等 Bean。

约束：

- 只做装配，不做业务编排；
- `ToolConfig` 负责工具集合 Bean 与统一注册装配，不负责工具执行逻辑；
- `RuntimeCoreConfig` 负责 Runtime 相关 Bean 装配，不在这里写流程代码；
- `reserved_output_tokens`、`reserved_tool_loop_tokens`、`safety_margin_tokens` 等预算参数统一在 `app/config` 绑定与加载；
- POM 与启动职责统一收口到 `app`，`spring-boot-maven-plugin` 只属于本模块；
- Prompt 相关配置只做顶层配置绑定与 Bean 接线，不在 `app` 层实现 prompt render / registry / schema check 逻辑；
- 不在 `config` 中手写业务 ID / 审计 ID / projection ID / snapshot ID / block ID / internal task ID。

### 4.3 `api/controller/`

职责：

- 暴露 WebFlux 接口；
- 承接 HTTP / SSE 请求与响应。

约束：

- controller 必须保持轻薄；
- 只做参数接收、DTO 转换、调用 Application Service、返回 `Mono/Flux`；
- 禁止同步式 `try/catch/finally` 包裹 `Mono/Flux`；
- 不得直接调用 Provider / Redis / MySQL / ToolRegistry；
- 不得直接调用 Prompt Registry / Prompt Renderer；
- 不得直接处理 runtime event 内部细节；
- 不得直接拼装 provider messages；
- 不得新增未被当前阶段文档允许的 debug / audit 查询接口。

### 4.4 `api/dto/`

职责：

- 定义 Web 请求 / 响应 DTO；
- 承载 API 协议字段与中文注释。

约束：

- DTO 不等于 `model` 内部运行时对象；
- request / response 分目录维护；
- DTO 可以使用 Lombok，但字段语义必须清晰；
- DTO 字段必须有中文字段注释；
- DTO 不承载 runtime 内部状态；
- DTO 不暴露 provider 厂商协议对象；
- DTO 不暴露 internal task / audit / evidence 内部模型；
- 主 chat response / stream event 不承载 debug / audit / evidence / internal task 细节。

### 4.5 `application/`

职责：

- 作为 app 层 Facade；
- 承接 controller 请求并委托给 `RuntimeOrchestrator`；
- 将 runtime 返回结果适配为 Web DTO；
- 将 runtime event 适配为 SSE event。

约束：

- `ChatApplicationService` 负责同步请求转发与轻量映射；
- `ChatApplicationService` 若调用阻塞 runtime，必须显式做线程隔离；
- `ChatStreamApplicationService` 负责把 runtime event 适配为 SSE；
- application 层不负责 provider 协议解析；
- application 层不负责 Redis / MySQL 存取；
- application 层不负责 Agent Loop 执行；
- application 层不负责 Prompt Governance 规则判断；
- application 层不把 internal task / audit / evidence 信息塞入主协议。

---

## 5. 模块局部开发约束

### 5.1 Web 层轻薄原则

- Controller 和 Application 只做接入、映射、转发、适配；
- 所有主流程继续通过 `RuntimeOrchestrator`；
- 不在 `app` 层写第二套编排器；
- 不在 `app` 层绕过 runtime 直接调用 provider / tool / persistence；
- 不在 `app` 层承接阶段详细设计职责。

### 5.2 依赖注入规则

- 一律使用构造器注入；
- Spring Bean 优先使用 `@RequiredArgsConstructor`；
- 禁止使用 `@Resource` 字段注入核心依赖；
- 禁止在 controller / application / config 中手工 `new` 核心依赖；
- 禁止通过静态单例绕开 Spring 装配边界；
- 核心协作者必须由配置类清晰装配，便于测试替换。

### 5.3 Lombok 使用规则

#### 适合优先使用 Lombok 的位置

- Controller / Application / Config：`@RequiredArgsConstructor`
- 需要日志的类：`@Slf4j`
- DTO / 简单配置对象：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

#### 使用边界

- 对带封装语义的对象，不粗暴使用 `@Data`；
- DTO 可使用 Lombok，但字段注释与 API 语义必须完整；
- 配置对象使用 Lombok 后，默认值来源和绑定语义必须清晰；
- 不得为了省代码牺牲协议对象的可读性。

### 5.4 日志规则

- `app` 模块日志聚焦：请求进入、异常出口、stream 建立 / 终止；
- 不依赖 controller `finally` 记录响应结果；
- 统一使用 SLF4J；
- 关键类优先使用 `@Slf4j`；
- 不在 app 层打印 provider 原始协议对象；
- 不在主协议响应中返回日志链路信息；
- `traceId / runId / sessionId / conversationId / turnId` 等链路标识由 runtime / observability 协调，app 只按协议需要输出允许字段。

### 5.5 `log4j2-spring.xml` 规则

- `vi-agent-core-app/src/main/resources/log4j2-spring.xml` 是统一日志配置文件；
- 日志级别、格式、Appender、滚动策略统一在此管理；
- 不在业务代码中定义日志实现策略；
- 不在业务代码中硬编码彩色日志、日志格式或 appender 策略。

### 5.6 工具类使用规则

#### `JsonUtils` 使用边界

- DTO 与内部对象做 JSON 转换时优先复用 `JsonUtils`；
- 不在 `app` 模块重复手写 `ObjectMapper` 样板逻辑；
- `JsonUtils` 不承载业务编排或协议流程；
- JSON 转换失败必须转换为清晰的标准异常，不得吞异常。

### 5.7 异常处理规则

- Web 层异常统一通过 `GlobalExceptionHandler` 输出；
- `GlobalExceptionHandler` 负责把 `ErrorCode` 映射为合理的 HTTP 状态码；
- controller 不做吞异常 + 返回默认值的伪兜底；
- 响应式链路错误必须显式转换为标准异常或标准错误响应；
- 错误响应不得泄漏 provider 原始异常体、内部堆栈、internal task、audit、evidence 明细；
- 业务异常与系统异常必须区分处理。

### 5.8 依赖与 POM 管理规则

- `app` 是唯一可以挂 `spring-boot-maven-plugin` 的模块；
- `app` 可以依赖 `spring-boot-starter-*`；
- `app` 只声明自己直接使用的内部模块依赖；
- 不在 `app` POM 中重复声明由根 POM 已统一管理的版本；
- 不在 `app` 中引入与已确认范围无关的重量级前端、工作流、向量库依赖；
- 配置型预算参数、Provider 配置、Prompt 配置与 Redis / MySQL 配置统一在本模块收口，不得让 runtime / infra 自行读散落配置；
- 不得通过新增依赖绕开模块边界；
- 新增依赖必须符合当前阶段文档范围。

---

## 6. `app` 模块补充约束

`app` 模块允许存在：

- 同步 `/chat` 与流式 `/chat/stream` 入口；
- `ChatApplicationService` 与 `ChatStreamApplicationService`；
- 顶层配置装配；
- 统一异常出口；
- SSE 适配逻辑；
- 预算参数、Provider 配置、Prompt 配置、Redis / MySQL 配置统一加载。

但不得提前引入：

- Runtime Loop；
- Tool Routing；
- Provider streaming 解析；
- Redis / MySQL 持久化实现细节；
- Prompt Registry / Prompt Renderer 运行时治理逻辑；
- Internal Task / Audit / Evidence 查询接口；
- 前端页面与工作台逻辑；
- Graph Workflow；
- RAG / Knowledge；
- Long-term Memory；
- Computer / Mobile control。

`app` 模块的核心目标是：

**把接入、装配、错误出口和 SSE 适配做对，并把预算参数与顶层配置统一收口到 `app/config`，同时保证同步路径不阻塞 WebFlux 事件线程。**

---

## 7. 主协议适配规则

### 7.1 `ChatRequest`

`ChatRequest` 固定语义：

- `conversationId`
- `sessionId`
- `requestId`
- `message`

约束：

- app 层不做会话 pair 解析；
- app 层不在 controller / application 中硬编码会话恢复策略；
- app 层不根据 request 字段自行构造 runtime memory；
- app 层不把 request DTO 当作 runtime 内部模型长期传递。

### 7.2 `ChatResponse`

`ChatResponse` 对外返回：

- `conversationId`
- `sessionId`
- `requestId`
- `runId`
- `turnId`
- `assistant`
- `usage`

约束：

- 不返回 `traceId`；
- 不返回 internal task；
- 不返回 audit；
- 不返回 evidence；
- 不返回 prompt render debug 信息；
- 不返回 WorkingContextProjection；
- 不返回 synthetic runtime / state / summary message。

### 7.3 `ChatStreamEvent`

`ChatStreamEvent` 对外返回：

- `conversationId`
- `sessionId`
- `requestId`
- `runId`
- `turnId`
- `messageId`
- `delta`
- `finishReason`
- `error`

约束：

- 不返回 `traceId`；
- 不返回 internal task；
- 不返回 audit；
- 不返回 evidence；
- 不返回 provider 原始 delta；
- 不返回 tool fragment 内部结构；
- 不返回 prompt governance debug 信息。

---

## 8. 测试要求

### 8.1 必须覆盖的测试场景

| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `ChatController` | `@WebFluxTest` / 集成测试 | 验证请求绑定、响应状态码、错误出口 |
| `ChatStreamController` | `@WebFluxTest` / 响应式测试 | 验证 SSE 响应类型、最小事件输出、主协议不污染 |
| `GlobalExceptionHandler` | `@WebFluxTest` / 单元测试 | 验证 `ErrorCode -> HttpStatus` 映射 |
| `ChatApplicationService` | 单元测试 | 验证同步转发、轻量映射与阻塞隔离 |
| `ChatStreamApplicationService` | 单元测试 / 响应式测试 | 验证 runtime event 到 SSE 的适配 |

### 8.2 测试目标

- `/chat`、`/chat/stream` 关键入口测试通过；
- 错误状态映射测试通过；
- `ChatApplicationService` 的阻塞隔离行为可验证；
- WebFlux 风格测试通过；
- 主协议不暴露 debug / audit / internal task / evidence；
- controller / application 不侵入 runtime 主逻辑。

### 8.3 测试约束

- WebFlux 项目测试按 WebFlux 口径写；
- 不要为了测试通过侵入 runtime 逻辑；
- controller 测试只验证协议与响应式行为；
- 流式测试优先使用 `StepVerifier`；
- 旧测试与新架构冲突时，更新或删除旧测试；
- 不为了旧测试保留过时 app 层兼容逻辑。

---

## 9. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 文档治理规则约束。
2. 当 `api/`、`application/`、`config/` 包结构或核心类职责发生长期变化时，必须同步更新本文件。
3. 阶段性详细设计、阶段开发计划、阶段专项验收、Codex prompt 不写入本文件，应写入当前阶段 `execution-phase/{phase-name}/`。
4. 若根目录文档中出现了本模块过细实现内容，应回收到本文件。
5. 若阶段文档中产生了长期稳定的 app 模块边界，完成阶段收口后可以晋升到本文件。
6. 未经确认，不得改变本文件的布局、标题层级与章节顺序。
7. 不得把 `runtime` / `infra` / `model` 的实现细节长期写成本模块文档。
8. 不得把某个阶段的临时实现细节固化为 app 模块长期规则。

---

## 10. 一句话总结

`vi-agent-core-app` 的职责，是作为整个 Agent Runtime 系统的唯一运行入口与 Facade 层，把 WebFlux 接入、SSE 适配、错误出口与顶层装配做正确，而不是把运行时主循环、Provider 协议解析、Persistence 实现、Prompt Governance 运行时逻辑或 Memory 派生链路写进 Web 层。
