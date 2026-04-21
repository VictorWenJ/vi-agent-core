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
## Docker 一键启动（IDEA）

本项目可直接使用 Docker Compose 启动 MySQL、Redis 和应用。

1. 复制环境变量模板并填写密钥
   ```bash
   copy .env.example .env
   ```

2. IDEA 内一键启动
   - 新建 Run Configuration，类型选择 `Docker -> Docker Compose`
   - Compose file 选择项目根目录的 `docker-compose.yml`
   - 选择并启用 `Use .env file`，文件为根目录 `.env`
   - 点击 Run，即可一键启动 `mysql + redis + app`

3. 手工命令启动（同效果）
   ```bash
   docker compose --env-file .env up -d --build
   ```

4. 查看日志与停止
   ```bash
   docker compose logs -f app
   docker compose down
   ```

说明
- 第一次启动会自动运行 Flyway 完成数据库初始化/迁移
- MySQL 与 Redis 分别在 docker-compose 管理下独立启动
- 应用容器端口映射为 `8080:8080`

## Docker 最新代码启动

如果你希望每次启动都使用最新代码重新构建并部署，直接执行：

```bash
scripts\docker-up-latest.cmd
```

该脚本等价于：

```bash
docker compose up -d --build --force-recreate
```

IDEA 推荐方式：
- 新建 `Run Configuration` 类型为 `Shell Script`
- Script path 选择 `scripts/docker-up-latest.cmd`
- 之后点击 Run 即可一键按最新代码重建并启动
