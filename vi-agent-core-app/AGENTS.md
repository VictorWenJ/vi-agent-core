# AGENTS.md

> 更新日期：2026-04-16

## 1. 文档定位

本文件定义 `vi-agent-core-app` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `app` 模块负责什么
- `app` 模块不负责什么
- `app` 模块内包结构如何约定
- 在 `app` 模块开发时必须遵守哪些局部规则
- `app` 模块测试应如何建设

本文件不负责：
- 仓库级协作规则（见根目录 `AGENTS.md`）
- 项目阶段规划（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准（见根目录 `CODE_REVIEW.md`）

执行 `app` 模块相关任务前，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 本文件 `vi-agent-core-app/AGENTS.md`

---

## 1.1 AI 代理必读速览

> **模块**：`vi-agent-core-app` — 唯一运行入口与应用装配模块  
> **模块定位**：承载 Spring Boot 启动、WebFlux 接入、Facade Service、配置装配、异常出口与资源配置  
> **当前目标**：服务于 Phase 1 主链路补齐阶段，重点完成 sync/stream 接入适配与正确的 WebFlux 边界  
> **核心约束**：`app` 只负责启动、接入、装配、输出；不负责 Runtime Loop、工具路由、Provider 调用与 Redis Transcript 细节实现  
> **包结构重点**：`config`、`controller`、`controller/dto`、`service`、`advice`

---

## 2. 模块定位

`vi-agent-core-app` 是整个 `vi-agent-core` 系统的**顶层应用模块**，也是当前仓库中**唯一可运行模块**。

模块目标：
- 作为 Spring Boot 启动入口，负责装配 `runtime`、`infra`、`model`、`common`；
- 提供 WebFlux API 入口与 SSE 输出；
- 通过 Facade Service 把请求转发给 `RuntimeOrchestrator`；
- 提供统一异常出口、配置装配和资源文件承接；
- 保持 `app` 作为“接入与装配层”，不侵入运行时内核。

模块在整体依赖链中的位置：

`app` → `runtime` + `infra` + `model` + `common`

因此，`app` 模块是：
- **唯一启动模块**
- **唯一 Web 接入模块**
- **Facade 与装配模块**

但 `app` 模块不是：
- Runtime 主编排模块
- Provider 实现模块
- Transcript 持久化实现模块
- Tool 路由模块
- 公共工具类模块

换句话说：

**`app` 模块负责“把系统跑起来并对外暴露”，不负责“主流程怎么执行”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）启动与装配
- Spring Boot 启动类
- 顶层 Bean 装配
- `runtime` / `infra` 的接线与启动配置

#### 2）Web 接入
- `ChatController`
- `StreamController`
- 请求接收、参数绑定、响应输出
- SSE 连接暴露

#### 3）异常出口
- `GlobalExceptionHandler`
- 统一错误响应
- Web 层异常转换与日志出口

#### 4）资源配置
- `application.yml`
- `log4j2-spring.xml`
- `@ConfigurationProperties` 绑定
- 运行时环境变量装配

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `app` 模块：

- `RuntimeOrchestrator` 主编排逻辑
- Agent Loop while 循环逻辑
- `ToolGateway` / `ToolRegistry` 的执行细节
- Provider SDK 调用细节
- Redis Transcript 存取细节
- 运行时模型定义
- 通用工具类实现

如果一个类需要：
- 决定“是否继续下一轮推理”
- 直接调用 DeepSeek SDK
- 直接决定工具路由
- 直接操作 Redis Transcript

那它就**不属于 `app` 模块**。

---

## 4. 模块内包结构约定

当前 `app` 模块包结构固定为：

```text
com.vi.agent.core.app
├── advice/
├── config/
├── controller/
│   └── dto/
└── service/
```

推荐类分布示例：

```text
com.vi.agent.core.app/
├── advice/
│   └── GlobalExceptionHandler.java
├── config/
│   ├── AppBeanConfig.java
│   ├── RuntimeBeanConfig.java
│   ├── RedisConfig.java
│   └── DeepSeekProperties.java
├── controller/
│   ├── ChatController.java
│   ├── StreamController.java
│   └── dto/
│       ├── ChatRequest.java
│       ├── ChatResponse.java
│       └── ApiErrorResponse.java
└── service/
    ├── ChatService.java
    └── StreamingChatService.java
```

### 4.1 `advice/`
职责：
- 统一承接 Web 层异常出口
- 负责将标准异常转换为 API 友好响应

约束：
- 只负责异常转换与错误响应格式
- 不承载 Runtime 决策逻辑
- 不在这里补“业务兜底流程”

### 4.2 `config/`
职责：
- 装配运行所需 Bean
- 承接外部配置与环境变量
- 负责 `runtime` / `infra` / `app` 的顶层接线

约束：
- `config` 负责装配，不负责业务编排
- 当前若仍存在手工工具注册，只能视为临时过渡态，必须向统一 `ToolRegistry` 注册机制收口
- `RuntimeBeanConfig` 不应长期承载工具表硬编码
- Redis、DeepSeek 等配置对象应通过配置类绑定与 Bean 装配进入系统，而不是散落在业务代码里

### 4.3 `controller/`
职责：
- 暴露 WebFlux 接口
- 承接 HTTP / SSE 请求与响应

约束：
- controller 必须保持轻薄
- 只做参数接收、DTO 转换、调用 Facade Service、返回 `Mono/Flux`
- **禁止**使用同步式 `try/catch/finally` 包裹 `Mono/Flux` 来记录结果或异常
- 异常统一交由响应式链路和 `GlobalExceptionHandler` 处理
- 不得在 controller 中写 Runtime Loop、Tool 路由、Provider 调用逻辑

### 4.4 `controller/dto/`
职责：
- 定义 Web 层请求/响应 DTO
- 承载 API 协议字段与中文注释

约束：
- DTO 不等于 `model` 内部运行时对象
- `controller/dto` 不得直接替代 `Message`、`ToolCall`、`ConversationTranscript`
- DTO 可以使用 Lombok 简化样板代码，但字段语义必须清晰

### 4.5 `service/`
职责：
- 作为 app 层 Facade
- 承接 controller 的请求并委托给 `RuntimeOrchestrator`

约束：
- `ChatService` 负责同步请求转发与轻量映射
- `StreamingChatService` 负责把 runtime 流式结果适配为 WebFlux/SSE
- `service` 不负责 Runtime Loop、Tool 路由、Provider 调用、Redis Transcript 存取
- 不得在 `service` 层写出“第二套编排器”

---

## 5. 模块局部开发约束

### 5.1 Web 层轻薄原则
- Controller 和 Facade Service 只做接入、转发、映射、异常出口协作
- 不要把业务编排逻辑写进 `app` 层
- 所有主流程都必须继续通过 `RuntimeOrchestrator`

### 5.2 依赖注入规则
- 一律使用构造器注入
- Spring Bean 优先使用 `@RequiredArgsConstructor`
- 禁止 `@Autowired` 字段注入
- 禁止在 controller / service 内部手工 new 核心依赖

### 5.3 Lombok 使用规则
`app` 模块允许并鼓励使用 Lombok 消除样板代码，但必须按场景使用。

#### 适合优先使用 Lombok 的位置
- Controller / Service / Config Bean：`@RequiredArgsConstructor`
- 需要日志的类：`@Slf4j`
- DTO / 简单配置对象：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

#### 使用边界
- 对带封装语义的对象，不要粗暴使用 `@Data`
- DTO 可使用 Lombok，但字段注释与 API 语义必须完整

### 5.4 日志规则
- `app` 模块日志应聚焦：请求进入、关键响应出口、异常出口、stream 建立/终止
- 统一使用 SLF4J
- 关键类优先使用 `@Slf4j`
- controller 不应依赖 `finally` 来记录响应结果；响应式日志应在 Facade 或 runtime 关键节点处理
- 禁止 `System.out.println`

### 5.5 `log4j2-spring.xml` 规则
- `vi-agent-core-app/src/main/resources/log4j2-spring.xml` 是标准日志配置文件
- 日志格式、级别、Appender、滚动策略统一在此维护
- 不在业务代码中分散定义日志策略

### 5.6 工具类使用规则

#### `JsonUtils` 使用边界
- DTO 与内部对象做 JSON 转换时，优先复用 `JsonUtils`
- 不要在 `app` 模块重复手写 `ObjectMapper` 样板逻辑
- 但 `JsonUtils` 只用于通用转换，不用于承载业务编排或协议流程

### 5.7 异常处理规则
- Web 层异常统一通过 `GlobalExceptionHandler` 输出
- controller 不做“吞异常 + 返回默认值”的伪兜底
- 响应式链路中的错误必须显式转换为标准异常或标准错误响应
- 业务异常统一继承 `AgentRuntimeException`

---

## 6. 当前阶段下的 `app` 模块约束
当前阶段内，`app` 模块允许存在：
- 同步 `/chat` 与流式 `/chat/stream` 入口
- Facade Service
- `GlobalExceptionHandler`
- Redis / DeepSeek / Runtime 顶层装配配置
- WebFlux / SSE 适配逻辑

但不得提前引入：
- 在 `app` 层实现 Runtime Loop
- 在 `app` 层实现 Tool Routing
- 在 `app` 层实现 Redis Transcript 持久化细节
- 在 `app` 层实现 DeepSeek/OpenAI SDK 调用
- 前端页面与复杂工作台逻辑

当前阶段内，`app` 模块的唯一目标是：

**作为唯一运行入口，把同步/流式接入、配置装配、异常出口和响应适配做正确、做轻薄。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `ChatController` | `@WebFluxTest` / 集成测试 | 验证请求绑定、响应状态码、错误出口 |
| `StreamController` | `@WebFluxTest` / 响应式测试 | 验证 SSE 响应类型、最小流式输出 |
| `GlobalExceptionHandler` | `@WebFluxTest` / 单元测试 | 验证标准错误响应结构 |
| `ChatService` / `StreamingChatService` | 单元测试 | 验证 Facade 转发、SSE 适配、异常映射 |
| 配置绑定与装配 | 单元测试 / 轻量 Spring 测试 | 验证 DeepSeek、Redis 等配置能正确装配 |

### 7.2 当前阶段测试目标
- `/chat`、`/chat/stream` 关键入口测试通过
- 异常出口测试通过
- WebFlux 风格测试通过
- `mvn test` 可通过

### 7.3 测试约束
- WebFlux 项目测试按 WebFlux 口径写，不要写成 MVC 风格
- 不要为了测试通过侵入 runtime 逻辑
- Controller 测试重点验证协议与响应式行为，不要把 runtime 细节测成 app 层责任
- 流式测试优先使用 `StepVerifier`

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `runtime` / `infra` / `model` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-app` 的职责是作为整个 Agent Runtime 系统的**唯一运行入口与 Facade 层**，稳定完成 **启动、装配、接入、异常出口与同步/流式输出适配**；它必须保持轻薄，绝不侵入 `RuntimeOrchestrator` 所主导的核心执行边界。
