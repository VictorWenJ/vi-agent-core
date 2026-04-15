# AGENTS.md

> 更新日期：2026-04-15

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

> **模块**：`vi-agent-core-app` — 顶层 Spring Boot 装配与 WebFlux 接入模块  
> **模块定位**：唯一可运行模块，负责启动、装配、接入、异常出口与响应输出  
> **当前目标**：服务于当前基础建设 / Phase 1 骨架阶段  
> **核心约束**：`controller` / `service facade` 必须保持轻薄；不得承载 Runtime 主链路编排；不得绕过 `RuntimeOrchestrator`、`ToolGateway`、`LlmProvider` 等核心边界  
> **包结构重点**：`controller`、`controller.dto`、`service`、`config`、`advice`、`resources`

---

## 2. 模块定位

`vi-agent-core-app` 是整个 `vi-agent-core` 系统的**应用入口与装配层**，也是当前项目中**唯一可运行的 Maven 模块**。

模块目标：
- 作为 Spring Boot 启动模块，负责加载全局配置并装配下层模块能力
- 暴露 REST / SSE 接口，处理 HTTP 与流式输出协议
- 作为 Facade 层，将请求委托给 `runtime` 模块的核心编排能力
- 提供全局异常处理、响应标准化与运行配置入口
- 承担应用级日志配置文件与 Spring 配置文件落点

模块在整体依赖链中的位置：

`app` → `runtime` + `infra` + `model` + `common`

因此，`app` 模块是：
- **唯一启动入口**
- **唯一 Web 接入入口**
- **唯一顶层装配入口**

但 `app` 模块不是：
- Runtime 主链路编排中心
- Agent Loop 执行层
- Tool 路由与执行中心
- LLM Provider 调用中心
- Transcript / Memory / RAG 主逻辑承载层

换句话说：

**`app` 模块只负责“接入 + 装配 + 输出”，不负责“核心执行”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）启动与装配
- Spring Boot 启动类
- 顶层 Bean 装配
- 模块间依赖连接
- 应用级配置文件加载

#### 2）Web 接入
- 同步聊天接口
- 流式聊天接口
- 请求 DTO / 响应 DTO
- 参数绑定、校验、响应封装

#### 3）异常出口
- 全局异常处理
- Web 层统一错误响应
- 异常到标准响应 DTO 的映射

#### 4）资源配置
- `application.yml`
- `application-dev.yml`
- `application-test.yml`
- `log4j2-spring.xml`

---

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `app` 模块：

- Agent Loop 核心执行逻辑
- `RuntimeOrchestrator` 的主编排逻辑
- Tool 注册、Tool 路由、Tool 执行逻辑
- LLM Provider 具体实现
- Transcript / Memory / Artifact / RAG 的核心实现
- 上下文裁剪、记忆提炼、子代理委派等运行时主逻辑
- 业务流程编排
- Phase 2+ 的长期记忆、Delegation、Skill、RAG 等完整实现

如果这些逻辑出现在 `controller`、`service`、`config` 中，说明 `app` 模块已经越界。

---

## 4. 模块内包结构约定

当前 `app` 模块包结构固定为：

```text
com.vi.agent.core.app
├── ViAgentCoreApplication.java
├── advice/
├── config/
├── controller/
│   └── dto/
└── service/
```

资源目录要求：

```text
src/main/resources
├── application.yml
├── application-dev.yml
├── application-test.yml
└── log4j2-spring.xml
```

### 4.1 `advice/`
职责：
- 全局异常处理
- Web 层统一错误出口
- 响应式接口异常映射

约束：
- 只做异常到响应的转换
- 不做业务补偿与流程判断
- 不写 Runtime 主链路逻辑

---

### 4.2 `config/`
职责：
- Spring Bean 装配
- 顶层运行时连接
- 外部配置绑定
- 模块集成 wiring

约束：
- 只做装配，不做业务执行
- 允许连接 `runtime`、`infra` 的实现类
- 不允许在配置类中写复杂流程判断
- 若当前阶段存在手工注册（如工具注册），必须明确标注为当前阶段临时实现，不得演变成长期业务逻辑容器

---

### 4.3 `controller/`
职责：
- 暴露 HTTP API
- 请求参数绑定、校验
- 调用 facade service
- 返回 `Mono` / `Flux` 响应

约束：
- 必须保持轻薄
- 不得直接调用 Repository / Provider / ToolExecutor
- 不得写 Agent Loop、Tool Calling、Transcript 写回逻辑
- 不得因为日志或工具类使用而引入业务编排

---

### 4.4 `controller/dto/`
职责：
- Web 层 request / response DTO
- 错误响应 DTO
- 流式响应 DTO

约束：
- DTO 仅服务于 Web 协议层
- 不得承担内部 Runtime 模型职责
- 不得与 `model` 层对象混用
- 对简单 DTO，应优先使用 Lombok 消除无参构造、全参构造、getter、setter 等样板代码
- 字段必须保留中文注释

建议优先使用：
- `@Data`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@Builder`

---

### 4.5 `service/`
职责：
- 作为 `controller` 与 `runtime` 之间的 facade
- 负责 request DTO → runtime 参数转换
- 负责 runtime 结果 → response DTO 转换
- 返回 WebFlux 所需的 `Mono` / `Flux`

约束：
- 只做 facade，不做核心编排
- 不直接操作 ToolGateway / Provider / TranscriptStore 的细节
- 不直接写数据库
- 不得演变成“大 service”
- 依赖注入优先使用 `@RequiredArgsConstructor`

---

## 5. 模块局部开发约束

### 5.1 Web 层轻薄原则
- `controller` 只做接入、校验、转发、响应封装
- `service facade` 只做 DTO 与 runtime 结果的转换
- `app` 模块不承担核心 Agent 行为逻辑

### 5.2 依赖注入规则
- 一律使用构造器注入
- Spring Bean 优先使用 Lombok `@RequiredArgsConstructor`
- 禁止 `@Autowired` 字段注入

### 5.3 Lombok 使用规则
在 `app` 模块内，Lombok 不是只限于 `@Slf4j`，而是要按场景合理使用。

#### 适合优先使用 Lombok 的位置
- `controller` / `service` / `advice` / `config` 中的 Spring Bean：优先 `@RequiredArgsConstructor`
- 需要日志的类：优先 `@Slf4j`
- DTO：优先使用
    - `@Getter`
    - `@Setter`
    - `@Data`
    - `@NoArgsConstructor`
    - `@AllArgsConstructor`
    - `@Builder`

#### 使用边界
- 对简单 DTO，应尽量使用 Lombok 消除样板代码
- 不要为了使用 Lombok 而改变类职责
- `app` 模块中一般不存在复杂领域行为类；若未来引入带显式行为与封装边界的对象，不得机械使用 `@Data`

---

### 5.4 日志规则
- `app` 模块统一通过 **SLF4J** 输出日志
- 日志实现统一由 `log4j2-spring.xml` 配置
- 关键链路日志应尽量带上：
    - `traceId`
    - `runId`
    - `sessionId`
- 重点记录：
    - 请求进入
    - 参数校验失败
    - 关键响应返回
    - 异常出口

禁止：
- `System.out.println`
- 在日志中无节制打印大对象
- 把日志逻辑写成业务流程控制

---

### 5.5 `log4j2-spring.xml` 规则
`log4j2-spring.xml` 是 `app` 模块资源目录下的**标准日志配置文件**。

要求：
- 必须存在于 `src/main/resources/`
- 统一管理 Console / File / Rolling 策略
- pattern 中应兼容：
    - 时间
    - 级别
    - 线程
    - logger
    - 消息
    - `traceId`
    - `runId`
    - `sessionId`
- 不允许在 Java 代码中硬编码日志输出策略替代配置文件

---

### 5.6 工具类使用规则
- `app` 模块允许使用 `common.util` 下的公共工具类
- 如 `JsonUtils`、校验工具、ID 生成工具等
- `app` 模块不得新增承载业务流程的工具类
- 若出现跨类复用的无状态通用逻辑，应优先沉淀到 `common` 模块，而不是在 `app` 模块内创建临时工具类

#### `JsonUtils` 使用边界
- 可用于统一 JSON 序列化 / 反序列化
- 不得承担请求处理流程、异常控制流程
- 不得代替 DTO / model 映射职责
- 不得让 `controller` / `service` 因过度依赖 `JsonUtils` 而侵蚀边界

---

### 5.7 异常处理规则
- Web 层异常出口统一收敛到 `GlobalExceptionHandler`
- `controller` 不应自行吞异常
- 不应直接抛出无语义的裸 `RuntimeException`
- 业务异常应优先转换为统一异常体系中的标准异常
- 全局异常处理应保持轻量，不承载业务补偿逻辑

---

## 6. 当前阶段下的 `app` 模块约束

当前阶段内，`app` 模块允许存在：
- 最小可运行接入链路
- 最小 Bean 装配
- 同步聊天与流式聊天接口骨架
- 最小 DTO 与全局异常处理
- 最小日志与配置文件骨架

但不得提前引入：
- 前端页面
- 鉴权体系
- 多租户接入
- 文件上传完整产品能力
- 长期记忆完整接入
- RAG 页面化能力
- Delegation / Skill / Policy / Replay 等完整控制面能力

当前阶段内，`app` 模块的唯一目标是：

**把请求稳定交给 Runtime Core，并把运行结果稳定返回给调用方。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景

| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `ChatController` 同步端点 | WebFlux 切片测试 / 集成测试 | 验证 `/chat` 接口能正确接收请求并返回标准响应 |
| `StreamController` 流式端点 | WebFlux 切片测试 / 集成测试 | 验证 `/chat/stream` 能正确建立流式连接并返回事件 |
| `GlobalExceptionHandler` | 单元测试 / 集成测试 | 验证标准异常能被正确转换为统一错误响应 |
| `RuntimeBeanConfig` 装配 | Spring 上下文测试 | 验证必要 Bean 能成功装配 |

### 7.2 当前阶段测试目标
- `app` 模块核心接口测试通过
- 启动类 `ViAgentCoreApplication` 能成功加载 Spring 上下文
- `mvn test` 可通过

### 7.3 测试约束
- WebFlux 项目优先使用 **WebFlux 风格测试**
- 不要使用 MVC 风格测试替代 WebFlux 测试
- Controller 层测试应 mock 下层 facade service，避免重复验证 runtime 内部细节
- 不得为了测试通过而在 `controller` 中增加仅用于测试的代码分支

---

## 8. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `runtime` / `infra` / `model` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-app` 的职责是作为整个 Agent Runtime 系统的**唯一运行入口与 Facade 层**，稳定完成 **启动、装配、接入、异常出口与响应输出**；它必须保持轻薄，绝不侵入 `RuntimeOrchestrator` 所主导的核心执行边界。
