# AGENTS.md

> 更新日期：2026-04-16

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

> **模块**：`vi-agent-core-common` — 轻量公共能力模块  
> **模块定位**：承载公共异常、运行时标识生成器、`JsonUtils` / `ValidationUtils` 等无状态工具  
> **当前目标**：服务于 Phase 1 主链路补齐阶段，重点支撑运行时 ID 体系与公共工具复用  
> **核心约束**：必须保持轻量；只能承载异常、ID 与无状态通用工具；禁止写业务编排、Provider 调用、Tool 路由、Redis 持久化逻辑  
> **包结构重点**：`exception`、`id`、`util`

---

## 2. 模块定位

`vi-agent-core-common` 是整个 `vi-agent-core` 系统的**最小公共能力层**，负责为 `app`、`runtime`、`infra`、`model` 提供轻量、低耦合、跨模块复用的基础能力。

模块目标：
- 提供统一的异常体系；
- 提供运行时标识生成器；
- 提供少量无状态、通用、可复用的公共工具类；
- 保持 `common` 模块足够轻量，避免演化为“万能公共模块”。

模块在整体依赖链中的位置：

`common` 尽量零依赖，被所有其他模块复用。

因此，`common` 模块是：
- **轻量公共基础层**
- **异常与 ID 统一入口**
- **无状态工具收口点**

但 `common` 模块不是：
- 业务编排层
- Runtime 模块
- Provider 模块
- Persistence 模块
- Web DTO 模块

换句话说：

**`common` 模块负责“通用基础能力”，不负责“业务流程”。**

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容

#### 1）基础异常体系
- `AgentRuntimeException`
- `ErrorCode`
- 与标准异常语义相关的最小公共能力

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
- 必要时的最小字符串 / 集合辅助工具

### 3.2 本模块明确不负责的内容

以下内容**禁止**写入 `common` 模块：

- Runtime 编排
- Agent Loop
- Tool 路由
- Provider 调用
- Redis / MySQL 存储逻辑
- Web DTO
- Spring Bean / Controller / Service / Repository
- 复杂业务工具类

如果一个类需要：
- 决定运行流程
- 依赖 Spring 生命周期
- 调用外部系统
- 直接操作 Redis / MySQL

那它就**不属于 `common` 模块**。

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
- 统一定义公共异常与错误码
- 为上层模块提供一致的异常语义

约束：
- 这里只定义异常与错误码，不承载业务流程
- 不在这里放 Web 响应 DTO 或 provider 专属异常细节

### 4.2 `id/`
职责：
- 承载运行时标识生成器
- 为 provider / runtime / redis transcript / logging 链路提供统一 ID 生成能力

约束：
- 生成器必须保持无状态或可控状态
- 命名必须清晰表达用途
- 不在这里放 TraceContext / MDC 绑定逻辑，那属于 `infra.observability`
- 当前 Phase 1 重点补齐：`conversationId`、`turnId`、`messageId`、`toolCallId`

### 4.3 `util/`
职责：
- 承载无状态、通用、跨模块复用工具
- 当前标准 JSON 工具类基线为 `JsonUtils`

约束：
- 只允许无状态通用逻辑
- 不允许把业务编排、provider 请求构造、tool routing、transcript mapping 塞到 util
- 不允许出现“为了方便”而把复杂流程静态化的写法

---

## 5. 模块局部开发约束

### 5.1 轻量原则
- `common` 必须保持轻量
- 只承载所有模块都会复用的基础能力
- 不能为了少写几个包，就把上层职责压进 `common`

### 5.2 工具类边界规则
- 工具类只能承载无状态、通用、跨模块复用逻辑
- 不得把 Redis key 拼装规则、provider 请求构造、tool routing、runtime 编排塞进 util
- 若某逻辑只服务单一模块，应优先放回对应模块，而不是沉到 `common`

### 5.3 `JsonUtils` 规则

#### 允许承载的能力
- 通用 JSON 序列化
- 通用 JSON 反序列化
- 简单、通用的 JSON 节点辅助能力

#### 禁止承载的能力
- Transcript 业务映射流程
- Provider 请求体构造流程
- Tool 参数业务解析
- 带业务语义的 JSON 包装器

#### 开发要求
- `JsonUtils` 作为当前项目标准 JSON 工具类继续沿用
- 其他模块有 JSON 需求时优先复用，不重复造轮子
- 若需增强能力，应保持无状态、通用、低耦合

### 5.4 `ValidationUtils` / `StringUtils` 规则
- 只放最小通用校验能力
- 不放业务规则
- 不放运行时特定判断
- 能不用就不用，避免 `common` 工具类膨胀

### 5.5 Lombok 使用规则
`common` 模块允许并鼓励使用 Lombok，但要保持公共基础模块的清晰度。

#### 适合优先使用 Lombok 的位置
- 简单异常对象 / 配置值对象：`@Getter`、`@AllArgsConstructor`
- 需要日志的极少数公共类：`@Slf4j`

#### 建议使用
- 对简单生成器或轻量对象，可使用 `@RequiredArgsConstructor`
- 对纯数据对象可使用适度 Lombok 消除样板代码

#### 使用边界
- 不要为了省事在异常体系或关键基础对象上滥用 `@Data`
- 公共基础对象的语义要清晰，优先可读性

### 5.6 日志规则
- `common` 模块一般不是日志重点模块
- 仅在极少数需要日志的公共组件中使用 `@Slf4j`
- 禁止 `System.out.println`
- 不要把日志写进工具类以替代调用方的业务日志

### 5.7 异常处理规则
- 公共异常以 `AgentRuntimeException` 为统一基线
- `ErrorCode` 应保持清晰、稳定
- 不要在 `common` 层制造大量业务特定异常

### 5.8 依赖管理规则
- `common` 尽量保持零依赖或极少依赖
- 不引入 Web、数据库、模型 SDK、Redis 等重依赖
- 引入依赖必须能证明其跨模块公共价值

---

## 6. 当前阶段下的 `common` 模块约束
当前阶段内，`common` 模块允许存在：
- 最小异常体系
- 最小运行时标识体系
- `JsonUtils` / `ValidationUtils` 等通用工具

但不得提前引入：
- Runtime 相关上下文对象
- Provider / Transcript / Tool 专属工具集
- 大量字符串 / JSON / Bean 操作万能工具类
- 复杂公共状态中心

当前阶段内，`common` 模块的唯一目标是：

**为 Runtime Core、infra 与 app 提供最小、稳定、低耦合的公共基础能力。**

---

## 7. 测试要求

### 7.1 必须覆盖的测试场景
| 测试目标 | 测试类型 | 覆盖要求 |
| :--- | :--- | :--- |
| `AgentRuntimeException` / `ErrorCode` | 单元测试 | 验证错误码与异常语义 |
| ID 生成器 | 单元测试 | 验证唯一性、格式、最小约束 |
| `JsonUtils` | 单元测试 | 验证基本序列化/反序列化行为 |
| `ValidationUtils` | 单元测试 | 验证关键校验分支 |

### 7.2 当前阶段测试目标
- `traceId`、`runId`、`conversationId`、`turnId`、`messageId`、`toolCallId` 等生成器测试通过
- `JsonUtils` 基础测试通过
- `mvn test` 可通过

### 7.3 测试约束
- 优先写单元测试
- 不引入 Spring 上下文
- 不要为没有行为的简单常量类机械补测试
- 重点覆盖：异常语义、ID 唯一性/格式、JSON 基础能力

---

## 8. 文档维护规则
1. 本文件属于模块级治理文档，受根目录 `AGENTS.md` 的冻结规则约束。
2. 当模块内包结构、核心类职责、局部约束发生变更时，必须同步更新本文件对应章节。
3. 新增子包或核心类后，必须在第 4 节包结构说明中补充。
4. 未经确认，不得改变本文件的布局、排版、标题层级与章节顺序。
5. 不得把 `runtime` / `infra` / `app` / `model` 模块的细节写成本模块说明。

---

## 9. 一句话总结

`vi-agent-core-common` 的职责是为整个 Agent Runtime 系统提供**最小、稳定、低耦合的公共基础能力**；它必须保持轻量，只承载异常、运行时 ID 与无状态工具，不得演变成承载业务编排、Provider 调用或持久化细节的“万能公共模块”。
