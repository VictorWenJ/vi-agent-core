# AGENTS.md

> 更新日期：2026-04-15

## 1. 文档定位

本文件定义 `vi-agent-core-common` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `common` 模块负责什么
- `common` 模块不负责什么
- `common` 模块内包结构如何约定
- 在 `common` 模块开发时必须遵守哪些局部规则
- `common` 模块测试应如何建设

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

> **模块**：`vi-agent-core-common` — 最小公共能力模块  
> **模块定位**：承载跨模块共享的基础异常、ID 生成器、无状态工具类  
> **当前目标**：服务于基础建设 / Phase 1 骨架阶段  
> **核心约束**：必须保持轻量、低耦合、无业务编排；禁止把 runtime、tool、provider、persistence 流程塞进工具类  
> **包结构重点**：`exception`、`id`、`util`

---

## 2. 模块定位

`vi-agent-core-common` 是整个 `vi-agent-core` 系统的**最小公共能力模块**，为所有上层模块（`app`、`runtime`、`infra`、`model`）提供统一异常体系、运行时标识生成与无状态通用工具能力。

模块目标：
- 定义统一的基础异常基类 `AgentRuntimeException` 及标准错误码 `ErrorCode`，确保全系统异常处理的一致性；
- 提供线程安全或可安全复用的全局 ID 生成工具（如 `TraceIdGenerator`、`RunIdGenerator`、`SessionIdGenerator`）；
- 封装 JSON 序列化、字符串处理、参数校验等通用无状态工具方法，避免各模块重复造轮子；
- 为其他模块提供最小公共支撑，而不反向侵入业务边界；
- 不包含业务状态，不依赖其他业务模块，不承载 Spring Web / 持久化 / Runtime 编排逻辑。

模块在整体依赖链中的位置：

`common` 被 `model`、`runtime`、`infra`、`app` 共同依赖，处于依赖树的**最底层公共能力层**。

因此，`common` 模块是：
- **最小基础能力模块**
- **公共异常与工具能力模块**
- **低耦合共享模块**

但 `common` 模块不是：
- 业务逻辑模块
- Runtime 编排模块
- Tool 路由模块
- Provider 适配模块
- Persistence 访问模块
- “什么都往里塞”的杂物间

换句话说：

**`common` 模块只负责“公共基础能力”，不负责“业务执行”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）基础异常体系
- `AgentRuntimeException`
- `ErrorCode`
- 少量明确语义的基础异常类型

#### 2）运行时标识工具
- `TraceIdGenerator`
- `RunIdGenerator`
- `SessionIdGenerator`
- 未来如需统一，可收敛为 `IdGenerator`，但当前阶段不强制

#### 3）公共无状态工具类
- `JsonUtils`
- `ValidationUtils`
- `StringUtils`
- 其他满足“无状态 + 通用 + 跨模块复用”原则的工具能力

---

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `common` 模块：

- Runtime 编排逻辑
- Tool 调用逻辑
- Provider 调用逻辑
- Repository / Store 访问逻辑
- Transcript / Memory / Context 构建逻辑
- 与具体业务域绑定的“专用工具类”
- 大而全的“万能 Utils”
- 依赖 Spring 容器生命周期的复杂行为
- 任何需要理解 session、tool call、provider、transcript 语义才能工作的流程控制

如果一个类必须理解会话状态、运行阶段或外部系统才能工作，那它就**不属于 `common` 模块**。

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
│   ├── ErrorCode.java
│   ├── ToolExecutionException.java
│   ├── ContextAssemblyException.java
│   └── ProviderException.java
├── id/
│   ├── TraceIdGenerator.java
│   ├── RunIdGenerator.java
│   └── SessionIdGenerator.java
└── util/
    ├── JsonUtils.java
    ├── ValidationUtils.java
    └── StringUtils.java
```

### 4.1 `exception/`
职责：
- 放项目统一异常基类
- 放少量公共错误码定义
- 为上层模块抛出标准化异常提供基础

约束：
- 不放业务模块特有异常
- 不把异常层做成复杂错误处理中心
- 错误码与异常语义必须清晰

建议至少包含：
- `AgentRuntimeException`
- `ErrorCode`

要求：
- 所有业务异常必须直接或间接继承 `AgentRuntimeException`
- 错误码推荐格式：`{模块缩写}-{三位数字}`，如 `COMMON-001`、`TOOL-002`
- 异常应至少支持：
    - `(ErrorCode errorCode)`
    - `(ErrorCode errorCode, String message)`
    - `(ErrorCode errorCode, String message, Throwable cause)`

---

### 4.2 `id/`
职责：
- 放运行时标识生成工具
- 为 `traceId`、`runId`、`sessionId` 等提供统一生成入口

约束：
- 保持无状态
- 不绑定 Web 请求或具体业务流程
- 不写持久化、注册或流程控制逻辑

建议至少包含：
- `TraceIdGenerator`
- `RunIdGenerator`
- `SessionIdGenerator`

要求：
- ID 生成器应为 `final`
- 优先提供静态方法
- 当前阶段可使用 `UUID` 简单实现；未来可替换为更高效的分布式 ID 方案，但调用方不应感知变化

---

### 4.3 `util/`
职责：
- 放公共、无状态、跨模块复用的工具类

约束：
- 只允许“纯公共工具”
- 禁止业务编排进入工具类
- 禁止把 `util` 变成模块边界绕行通道

当前阶段建议重点包含：
- `JsonUtils`
- `ValidationUtils`
- `StringUtils`

工具类必须满足：
1. 无状态
2. 通用
3. 跨模块复用
4. 不依赖业务流程
5. 不依赖 Spring 容器隐式注入

---

## 5. 模块局部开发约束

### 5.1 轻量原则
`common` 模块必须保持最小、稳定、低耦合。

任何新增类都必须先问自己：
- 它是否真的跨模块复用？
- 它是否真的无状态？
- 它是否不携带业务流程知识？

如果答案不是明确的“是”，就不要放进 `common`。

---

### 5.2 工具类边界规则
工具类只允许承载：
- 序列化 / 反序列化
- 基础字符串处理
- 基础集合处理
- 参数校验
- 基础 ID 生成
- 其他明确无状态公共逻辑

工具类禁止承载：
- Runtime 编排
- Tool 路由
- Provider 调用
- 数据库存取
- 会话状态变更
- 任何需要理解具体业务语义的流程控制

根目录治理规则已经明确禁止把业务编排写进 Utils；在 `common` 模块内这是**强约束**。

---

### 5.3 `JsonUtils` 规则
`JsonUtils` 是当前项目的标准 JSON 公共工具类。

#### 允许承载的能力
- `toJson`
- `jsonToBean`
- 少量明确的通用 JSON 辅助方法

#### 禁止承载的能力
- 业务对象装配
- 请求处理流程
- 错误恢复编排
- 依赖具体模块语义的 JSON 逻辑

#### 开发要求
- 保持无状态
- 不依赖测试库
- 统一异常风格
- 统一日志风格
- 不引入业务模块依赖
- 对 `null` 输入要有明确处理策略（返回默认值或抛出标准异常）

若项目中出现重复 JSON 转换逻辑，优先考虑收敛到 `JsonUtils`；但不要为了“统一”而把业务逻辑硬塞进去。

---

### 5.4 `ValidationUtils` / `StringUtils` 规则
若存在或新增这类工具类，必须满足：

- 方法是纯函数
- 输入相同则输出相同
- 不修改调用方对象
- 不依赖 Spring 上下文
- 不通过静态变量缓存业务状态

这类工具类的目标是减少重复代码，而不是成为业务逻辑容器。

---

### 5.5 Lombok 使用规则
`common` 模块允许使用 Lombok，但必须克制、按场景使用。

#### 适合优先使用 Lombok 的位置
- 简单异常类
- 简单错误码承载对象
- 简单工具辅助对象
- 简单 ID 对象（若后续出现）
- 需要日志的类：优先 `@Slf4j`

#### 建议使用
- `@Getter`
- `@Setter`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@Slf4j`

#### 使用边界
- 对简单对象，可优先用 Lombok 消除无参构造、全参构造、getter、setter 样板代码
- 对工具类，不要为了使用 Lombok 而破坏“工具类私有构造 + 静态方法”的基本形态
- 对异常类，优先关注语义清晰，不要为了减少几行代码损失可读性

---

### 5.6 日志规则
`common` 模块可以记录必要日志，但必须保持克制。

要求：
- 统一通过 SLF4J 输出
- 工具类如需日志，优先使用 `@Slf4j`
- 只记录真正有助于排查的异常或关键失败
- 不在工具类中滥打 `info` 日志
- `JsonUtils` 正常路径不打印日志，异常路径可记录 `warn` / `error`

禁止：
- `System.out.println`
- 为了“调试方便”在公共工具中长期保留噪音日志

---

### 5.7 异常处理规则
- 公共异常统一从 `AgentRuntimeException` 基线演进
- 不要在 `common` 模块中吞异常
- 工具类内部捕获异常后，必须转换为统一异常体系中的合理异常
- 不允许 `catch (Exception e) {}` 这种空处理
- 不允许直接向上抛出无语义的裸 `RuntimeException`

---

### 5.8 依赖管理规则
- `common` 模块的 `pom.xml` 必须保持精简
- 只允许引入真正必要的第三方库，如：
    - Jackson
    - SLF4J
    - Lombok
- 禁止引入：
    - Spring Web
    - Spring Data
    - LangChain4j
    - 持久化驱动
    - 与业务强相关的三方依赖

---

## 6. 当前阶段下的 `common` 模块约束

当前阶段内，`common` 模块允许存在：
- 最小异常基类
- 最小错误码定义
- 最小 ID 生成器
- 最小公共工具类（如 `JsonUtils`）

但不得提前引入：
- 复杂 DSL
- 复杂框架适配层
- 大量抽象工具基类
- “提前为未来设计”的重型公共能力

当前阶段内，`common` 模块的唯一目标是：

**为上层模块提供最小、稳定、通用的公共基础能力。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景

| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `AgentRuntimeException` 及子类 | 单元测试 | 验证异常构造器正确设置 `errorCode` 和 `message` |
| `JsonUtils` | 单元测试 | 覆盖正常序列化、反序列化、`null` 值与异常路径 |
| `ValidationUtils` | 单元测试 | 覆盖参数校验失败时正确抛出异常 |
| `TraceIdGenerator` / `RunIdGenerator` / `SessionIdGenerator` | 单元测试 | 覆盖非空生成、基本格式或唯一性预期 |

### 7.2 当前阶段测试目标
- `common` 模块工具类和基础异常测试可通过
- `mvn test` 可通过
- 不要求追求复杂覆盖率，但必须覆盖关键行为和异常路径

### 7.3 测试约束
- 优先写单元测试
- 不需要引入 Spring 上下文
- 不要为简单 getter / setter 机械补测试
- 要重点覆盖：
    - 异常路径
    - 边界输入
    - 工具类核心行为

---

## 8. 文档维护规则

1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `runtime` / `infra` / `app` / `model` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-common` 的职责是为整个 Agent Runtime 系统提供**最小、稳定、低耦合的公共基础能力**；它必须保持轻量，只承载异常、ID 与无状态工具，不得演变成承载业务编排的“万能公共模块”。
