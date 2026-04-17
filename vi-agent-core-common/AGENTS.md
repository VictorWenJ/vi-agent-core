# AGENTS.md

> 更新日期：2026-04-17

## 1. 文档定位

本文件定义 `vi-agent-core-common` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `common` 模块负责什么
- `common` 模块不负责什么
- `common` 模块内包结构如何约定
- 在 `common` 模块开发时必须遵守哪些局部规则
- `common` 模块测试与依赖应如何建设

本文件不负责：
- 仓库级协作规则（见根目录 `AGENTS.md`）
- 项目阶段规划（见根目录 `PROJECT_PLAN.md`）
- 总体架构与依赖方向（见根目录 `ARCHITECTURE.md`）
- 全局审查标准（见根目录 `CODE_REVIEW.md`）

执行 `common` 模块相关任务前，必须先读：

1. 根目录 `AGENTS.md`
2. 根目录 `PROJECT_PLAN.md`
3. 根目录 `ARCHITECTURE.md`
4. 根目录 `CODE_REVIEW.md`
5. 本文件 `vi-agent-core-common/AGENTS.md`

---

## 1.1 AI 代理必读速览

> **模块**：`vi-agent-core-common` — 轻量公共能力模块  
> **模块定位**：承载公共异常、ID 生成器、JSON/校验等无状态基础能力  
> **当前目标**：继续保持轻量，不膨胀为运行时 SPI 或业务工具箱  
> **本轮重点**：保证 `ErrorCode` 语义足以支撑 app 层状态映射；保持 ID 体系稳定；为 POM 标准化提供最小依赖基线  
> **核心约束**：只放轻量公共能力；不承载运行时共享契约中带强业务语义的接口

---

## 2. 模块定位

`vi-agent-core-common` 是整个 `vi-agent-core` 系统的**最底层公共能力模块**。

模块目标：
- 提供统一异常体系；
- 提供最小运行时 ID 生成器；
- 提供少量无状态、跨模块复用的工具能力；
- 为上层模块提供不依赖 Spring、Provider、Redis、Runtime 的纯基础能力。

模块在整体依赖链中的位置：

`common` 位于最底层，可被 `model`、`runtime`、`infra`、`app` 依赖。

因此，`common` 模块是：
- **基础异常层**
- **基础 ID 层**
- **无状态通用工具层**

但 `common` 模块不是：
- Runtime SPI 寄存地
- Provider / Repository 抽象层
- 业务工具箱
- Spring Bean 装配层

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）基础异常体系
- `AgentRuntimeException`
- `ErrorCode`

用于承载项目级标准异常与错误码语义。

#### 2）运行时标识工具
- `IdGenerator`
- `TraceIdGenerator`
- `RunIdGenerator`
- `ConversationIdGenerator`
- `TurnIdGenerator`
- `MessageIdGenerator`
- `ToolCallIdGenerator`

#### 3）公共无状态工具类
- `JsonUtils`
- `ValidationUtils`

### 3.2 本模块明确不负责的内容

以下内容禁止写入 `common` 模块：

- `LlmGateway`、`TranscriptStore` 这类运行时共享契约
- `@AgentTool`、`ToolBundle` 这类强运行时语义注解与契约
- Web DTO
- Provider 调用
- Redis / MySQL 访问
- Runtime 编排
- Tool Routing
- Spring 配置与 Bean

如果一个类型需要：
- 被 `runtime` 与 `infra` 用作运行时协作契约
- 明确表达 Tool / Transcript / Provider 语义
- 依赖 Spring 生命周期

那它通常不属于 `common`，而应进入 `model` 或对应业务模块。

---

## 4. 模块内包结构约定

当前 `common` 模块包结构固定为：

```text
com.vi.agent.core.common
├── exception/
├── id/
└── util/
```

推荐类分布示例：

```text
com.vi.agent.core.common/
├── exception/
│   ├── AgentRuntimeException.java
│   └── ErrorCode.java
├── id/
│   ├── IdGenerator.java
│   ├── TraceIdGenerator.java
│   ├── RunIdGenerator.java
│   ├── ConversationIdGenerator.java
│   ├── TurnIdGenerator.java
│   ├── MessageIdGenerator.java
│   └── ToolCallIdGenerator.java
└── util/
    ├── JsonUtils.java
    └── ValidationUtils.java
```

### 4.1 `exception/`
职责：
- 定义项目标准异常与错误码

约束：
- `ErrorCode` 必须表达清晰语义，但不直接绑定 HTTP 状态码
- HTTP 状态码映射属于 `app` 层 advice 责任
- 不在这里放 Web 层响应对象

### 4.2 `id/`
职责：
- 提供基础运行时标识生成器

约束：
- ID 生成器必须无状态、可复用
- `turnId`、`messageId`、`toolCallId` 的存在，应服务于 runtime / transcript / provider 链路传递
- 不在这里承载 session/replay 业务逻辑

### 4.3 `util/`
职责：
- 提供统一 JSON 与校验等通用无状态能力

约束：
- `JsonUtils` 只负责序列化 / 反序列化
- `ValidationUtils` 只负责参数校验
- 不得演化出“流程工具类”或“provider 帮助类”

---

## 5. 模块局部开发约束

### 5.1 轻量原则
- `common` 必须保持最小、稳定、纯基础
- 新增能力前先问：这是不是整个仓库都能复用的纯基础能力？
- 不能因为多个模块都用到，就把强业务语义接口硬塞进 `common`

### 5.2 工具类边界规则
- 工具类只能承载无状态、通用、跨模块复用能力
- 禁止把 provider 请求构造、tool routing、runtime 编排、Redis key 策略塞进 util
- 若逻辑带有明显 Message / Tool / Transcript 语义，应优先考虑放 `model`

### 5.3 `JsonUtils` 规则
#### 允许承载的能力
- JSON 序列化 / 反序列化
- 常用对象转换封装

#### 禁止承载的能力
- Provider 协议拼装
- Transcript 业务映射
- Tool 参数路由
- Runtime 调试流程

#### 开发要求
- 仓库内如需通用 JSON 转换，优先复用 `JsonUtils`
- 不要在各模块重复 new `ObjectMapper`

### 5.4 `ValidationUtils` 规则
- 只承载基础参数校验
- 不承载复杂业务规则
- 校验失败统一抛标准异常

### 5.5 Lombok 使用规则
#### 适合优先使用 Lombok 的位置
- 简单异常辅助对象
- 简单配置 / 结果对象（若未来出现）

#### 建议使用
- 当前 `common` 规模很小，能不用就不用，保持清晰直接

#### 使用边界
- 不为省几行代码引入复杂 Lombok 组合
- 保持公共基础类的可读性

### 5.6 日志规则
- `common` 不是日志重点模块
- 如确实需要日志，统一使用 SLF4J
- 不要在公共工具类中大量打印日志

### 5.7 异常处理规则
- `AgentRuntimeException` 是业务标准异常基类
- `ErrorCode` 要覆盖当前阶段需要的 provider、tool、transcript、runtime、argument 等场景
- 当前阶段要支持 app 层做更细粒度的 HTTP 状态映射

### 5.8 依赖管理规则
- `common` 只允许依赖最小公共库，如 Jackson、SLF4J、Lombok、JUnit
- 禁止依赖 `model` / `runtime` / `infra` / `app`
- 禁止引入 Spring Boot starter、Redis、HTTP 客户端、LangChain4j 等运行时依赖
- 根 POM 统一管理版本，`common` POM 不重复写版本策略

---

## 6. 当前阶段下的 `common` 模块约束

当前阶段内，`common` 模块允许存在：
- 项目标准异常与错误码
- 最小 ID 生成器
- `JsonUtils`
- `ValidationUtils`

但不得提前引入：
- Runtime 共享契约
- Provider 抽象
- Transcript / Tool / Message 业务模型
- Spring Bean
- 数据访问能力

当前阶段内，`common` 模块的唯一目标是：

**为上层模块提供稳定、轻量、无状态的公共基础能力，不制造新的耦合源。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `ErrorCode` / `AgentRuntimeException` | 单元测试 | 验证错误码与异常构造行为 |
| 各类 `IdGenerator` | 单元测试 | 验证生成格式、非空与基本唯一性 |
| `JsonUtils` | 单元测试 | 验证基础序列化与反序列化 |
| `ValidationUtils` | 单元测试 | 验证空值 / 非法值场景 |

### 7.2 当前阶段测试目标
- `common` 模块基础行为可测
- 不追求复杂覆盖率，但基础能力必须稳定

### 7.3 测试约束
- 优先写纯单元测试
- 不引入 Spring 上下文
- 不为了测试方便扩展模块职责

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 冻结规则约束。
2. 当 `exception/`、`id/`、`util/` 包结构或职责变化时，必须同步更新本文件。
3. 若根目录文档中出现了本模块过细内容，应回收到本文件。
4. 未经确认，不得改变本文件的布局与章节顺序。
5. 不得把强运行时语义的共享契约长期写进本文件。

---

## 9. 一句话总结

`vi-agent-core-common` 的职责，是把异常、ID、JSON 与基础校验这类真正的公共能力沉淀下来，同时坚决不让 `common` 演变成“什么都能放”的杂糅层。
