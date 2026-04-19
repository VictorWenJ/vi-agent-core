# AGENTS.md

> 更新日期：2026-04-19

## 1. 文档定位

本文件定义 `vi-agent-core-common` 模块的职责边界、包结构约定、局部开发约束与测试要求。

本文件只负责回答：
- `common` 模块负责什么
- `common` 模块不负责什么
- `common` 模块内包结构如何约定
- 在 `common` 模块开发时必须遵守哪些局部规则
- `common` 模块测试与依赖应如何建设

---

## 2. 模块定位

`vi-agent-core-common` 是整个 `vi-agent-core` 系统的**最底层公共能力模块**。

因此，`common` 模块是：
- 基础异常层
- 基础 ID 层
- 无状态通用工具层

但 `common` 模块不是：
- Runtime SPI 寄存地
- Provider / Repository 抽象层
- 业务工具箱
- Spring Bean 装配层

---

## 3. 模块职责与边界

### 3.1 本模块负责的内容
- 基础异常体系：`AgentRuntimeException`、`ErrorCode`
- 运行时标识工具：`IdGenerator` 与各类 ID 生成器
- 公共无状态工具类：`JsonUtils`、`ValidationUtils`

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

---

## 4. 模块内包结构约定

当前 `common` 模块包结构固定为：

```text
com.vi.agent.core.common
├── exception/
├── id/
└── util/
```

### 4.1 包规则
- 包名必须全小写。
- `exception/` 只放项目标准异常与错误码。
- `id/` 只放基础 ID 生成器。
- `util/` 只放无状态、跨模块可复用、非业务编排工具类。

### 4.2 错误码规则
- `ErrorCode` 命名必须单义、可扩展。
- 错误码粒度按业务对象区分，不允许 transcript / state / summary 共用模糊错误码。
- HTTP 状态映射属于 `app` 层 advice，不属于 `common`。

### 4.3 工具类规则
- 字符串判空优先 `StringUtils`。
- 集合判空优先 `CollectionUtils`。
- Map 判空保持统一风格。
- `JsonUtils` 只负责 JSON 序列化 / 反序列化，不演化出业务编排能力。
- `ValidationUtils` 只负责基础参数校验，不承载复杂业务规则。

---

## 5. 模块局部开发约束

### 5.1 轻量原则
- `common` 必须保持最小、稳定、纯基础。
- 新增能力前先判断：它是不是整个仓库都能复用的纯基础能力？

### 5.2 POM 与依赖规则
- `common` 不能依赖 `model` / `runtime` / `infra` / `app`。
- 禁止引入 Spring Boot starter、Redis、HTTP 客户端、LangChain4j 等运行时依赖。
- 根 POM 统一管理版本，`common` POM 不重复写版本策略。

### 5.3 内部类与占位类规则
- 生产代码中的 domain object / dto / command / event 默认独立成类。
- `common` 不保留未启用占位类。

---

## 6. 测试要求
- `ErrorCode` / 异常体系要有基础行为测试。
- ID 生成器要有格式与唯一性基础测试。
- `JsonUtils` / `ValidationUtils` 要有基础单元测试。

---

## 7. 一句话总结

`common` 模块的核心要求是：继续保持“最底层、最小、最稳定”的公共基线，不为上层工程治理收口制造新的杂糅入口。
