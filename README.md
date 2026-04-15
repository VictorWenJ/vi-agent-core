# vi-agent-core

`vi-agent-core` 是一个基于 Java 17 + Spring Boot 3.x + WebFlux + LangChain4j 的 Java Agent Runtime Framework。

## 当前阶段

- 当前为 **Phase 1：核心闭环 + 最小状态底座**
- 本仓库已初始化为 **Maven 多模块项目**：`app / runtime / infra / model / common`

## 快速启动

```bash
mvn -pl vi-agent-core-app spring-boot:run
```

默认启动配置：`vi-agent-core-app/src/main/resources/application.yml`。

## 运行测试

```bash
mvn test
```

## 模块说明

- `vi-agent-core-app`：Spring Boot 启动与 WebFlux 接入层
- `vi-agent-core-runtime`：RuntimeOrchestrator 与 Agent Loop 核心编排骨架
- `vi-agent-core-infra`：Provider / Persistence / Observability 基础设施骨架
- `vi-agent-core-model`：内部运行时模型（Message、Transcript、RunState 等）
- `vi-agent-core-common`：公共异常与 ID 生成等轻量公共能力
