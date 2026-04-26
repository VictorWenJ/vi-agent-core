# vi-agent-core

`vi-agent-core` 是一个基于 Java 17 + Spring Boot 3.x + WebFlux 的轻量 Agent Runtime Framework / Agent Runtime Kernel 项目。

本项目不是普通 chat backend，而是面向 Agent Runtime 的自研核心底座，目标是逐步沉淀以下能力：

- Model Provider Layer
- Context Engineering
- Prompt Engineering
- Memory Engineering
- Tool Runtime
- Workflow / Graph Orchestration
- Storage / Cache
- Safety / Permission
- Observability / Audit
- Evaluation / Testing
- RAG / Knowledge
- Checkpoint / Resume

当前项目采用 Maven 多模块结构，以单体工程方式先落地清晰的逻辑分层和运行时边界。

---

## 当前阶段

当前阶段为：

```text
P2-E：Prompt Engineering Governance
```

当前阶段文档目录：

```text
execution-phase/phase-P2-E-prompt-governance/
```

P2-A 到 P2-D 的历史强契约基线位于：

```text
execution-phase/phase-P2-context-memory/system-design-P2-v5.md
```

P2-A 到 P2-D 的历史开发计划基线位于：

```text
execution-phase/phase-P2-context-memory/system-design-P2-implementation-plan-v3.md
```

阶段状态与路线图以根目录 `PROJECT_PLAN.md` 为准。

---

## 文档入口

项目采用文档治理驱动开发。

根目录文档职责如下：

| 文档 | 职责 |
|---|---|
| `AGENTS.md` | 仓库级协作规则、通用开发规范、文档治理规则 |
| `ARCHITECTURE.md` | 长期架构分层、模块边界、依赖方向、核心调用关系 |
| `CODE_REVIEW.md` | 通用 code review 标准、拒绝项、测试门禁 |
| `PROJECT_PLAN.md` | 高层路线图、阶段状态、当前阶段索引 |
| `execution-phase/README.md` | 阶段文档治理规则 |
| `execution-phase/{phase-name}/` | 阶段详细设计、计划、测试、prompt、收口记录 |

阶段详细设计不写入根目录 README。  
当前阶段的详细内容请进入对应 `execution-phase/{phase-name}/` 查看。

---

## 模块说明

本仓库包含 5 个 Maven 模块：

| 模块 | 职责 |
|---|---|
| `vi-agent-core-app` | Spring Boot 启动、WebFlux 接入、SSE 适配、顶层配置装配，唯一可运行模块 |
| `vi-agent-core-runtime` | RuntimeOrchestrator、Agent Loop、Working Context、Prompt Governance、Tool Runtime、Memory Coordination |
| `vi-agent-core-infra` | Provider、Persistence、Cache、外部资源读取、Mock Integration 等基础设施实现 |
| `vi-agent-core-model` | 内部运行时模型、领域对象、值对象、枚举、跨层共享契约与 port |
| `vi-agent-core-common` | 公共异常、错误码、ID 生成器、无状态通用工具 |

标准依赖方向：

```text
common
model -> common
runtime -> model + common
infra -> model + common
app -> runtime + infra + model + common
```

核心规则：

- `runtime` 不依赖 `infra`
- `infra` 不依赖 `runtime`
- `model` 不依赖 `runtime / infra / app`
- `app` 是唯一装配 runtime 与 infra 的上层模块
- `common` 保持最小、稳定、无业务语义

---

## 快速启动

使用 Maven 启动应用模块：

```bash
mvn -pl vi-agent-core-app spring-boot:run
```

默认配置文件：

```text
vi-agent-core-app/src/main/resources/application.yml
```

---

## 运行测试

运行全部测试：

```bash
mvn test
```

运行指定模块测试：

```bash
mvn -pl vi-agent-core-model test
mvn -pl vi-agent-core-runtime test
mvn -pl vi-agent-core-infra test
mvn -pl vi-agent-core-app test
```

带依赖模块一起构建测试：

```bash
mvn -pl vi-agent-core-runtime -am test
```

---

## Docker 一键启动

本项目可使用 Docker Compose 启动 MySQL、Redis 和应用。

### 1. 复制环境变量模板

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

Windows CMD：

```cmd
copy .env.example .env
```

复制后，根据本地环境填写 `.env` 中的 provider key、model、MySQL、Redis 等配置。

---

### 2. IDEA 内一键启动

在 IDEA 中新建 Run Configuration：

```text
Docker -> Docker Compose
```

配置：

- Compose file：项目根目录 `docker-compose.yml`
- 启用 `.env` file：项目根目录 `.env`
- 点击 Run 启动

启动后会同时启动：

```text
mysql + redis + app
```

---

### 3. 手工命令启动

```bash
docker compose --env-file .env up -d --build
```

查看日志：

```bash
docker compose logs -f app
```

停止服务：

```bash
docker compose down
```

说明：

- 第一次启动会自动运行 Flyway 完成数据库初始化 / 迁移
- MySQL 与 Redis 由 Docker Compose 管理
- 应用容器端口默认映射为 `8080:8080`

---

## Docker 最新代码启动

如果希望每次启动都使用最新代码重新构建并部署，可执行：

```cmd
scripts\docker-up-latest.cmd
```

该脚本等价于：

```bash
docker compose up -d --build --force-recreate
```

IDEA 推荐方式：

- 新建 Run Configuration，类型选择 `Shell Script`
- Script path 选择 `scripts/docker-up-latest.cmd`
- 点击 Run 即可按最新代码重建并启动

---

## 基本验证

应用启动后，可访问健康检查接口：

```text
GET http://localhost:8080/health
```

同步聊天接口和流式聊天接口以 `vi-agent-core-app` 中的 controller 定义为准。

---

## 开发约束摘要

开发前必须阅读：

```text
1. 根目录 AGENTS.md
2. 根目录 PROJECT_PLAN.md
3. 根目录 ARCHITECTURE.md
4. 根目录 CODE_REVIEW.md
5. execution-phase/README.md
6. 当前阶段 README.md
7. 当前阶段 design.md
8. 当前阶段 plan.md
9. 当前阶段 test.md
10. 与当前任务相关的历史强契约文档
11. 相关模块 AGENTS.md
12. 相关源码与测试
```

关键约束：

- 不允许破坏 Maven 模块依赖方向
- 不允许绕过 `RuntimeOrchestrator` 另写主编排链路
- 不允许把 provider / persistence 实现写入 `runtime`
- 不允许把 runtime 主逻辑写入 `app`
- 不允许把业务领域对象下沉到 `common`
- 不允许把阶段详细设计长期堆叠到根目录文档
- 不允许为了兼容旧测试保留过时逻辑
- 不允许将 debug / audit / internal task / evidence 污染主 chat response 或 stream event
- 业务 ID / 审计 ID / projection ID / snapshot ID / block ID / internal task ID 必须通过对应语义的 IdGenerator 生成

---

## 当前核心设计口径

当前 P2 系列已经确立以下长期口径：

```text
WorkingContext ≠ Transcript
Memory ≠ Message
ToolExecution ≠ ToolMessage
MySQL = 事实源
Redis = snapshot cache
```

并且：

- `SessionWorkingSet` 只能来自 MySQL completed raw transcript
- `WorkingContextProjection.modelMessages` 只用于 provider 调用
- synthetic runtime / state / summary message 不写回 raw transcript
- internal task / audit / evidence 不暴露到主 chat response
- post-turn memory update 失败不能影响主聊天成功返回

---

## 一句话总结

`vi-agent-core` 是一个以 Java 工程方式自研的轻量 Agent Runtime Kernel。

项目当前重点不是堆业务功能，而是逐步把 Provider、Context、Prompt、Memory、Tool、Storage、Audit、Evaluation 等 Agent 核心能力沉淀为清晰、可测试、可审计、可扩展的运行时基础设施。
