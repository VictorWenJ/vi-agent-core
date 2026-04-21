FROM m.daocloud.io/docker.io/library/maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
COPY vi-agent-core-common/pom.xml ./vi-agent-core-common/pom.xml
COPY vi-agent-core-model/pom.xml ./vi-agent-core-model/pom.xml
COPY vi-agent-core-runtime/pom.xml ./vi-agent-core-runtime/pom.xml
COPY vi-agent-core-infra/pom.xml ./vi-agent-core-infra/pom.xml
COPY vi-agent-core-app/pom.xml ./vi-agent-core-app/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl vi-agent-core-app -am -DskipTests dependency:go-offline

COPY vi-agent-core-common ./vi-agent-core-common
COPY vi-agent-core-model ./vi-agent-core-model
COPY vi-agent-core-runtime ./vi-agent-core-runtime
COPY vi-agent-core-infra ./vi-agent-core-infra
COPY vi-agent-core-app ./vi-agent-core-app

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl vi-agent-core-app -am -DskipTests package

FROM m.daocloud.io/docker.io/library/eclipse-temurin:17-jre-alpine

WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /workspace/vi-agent-core-app/target/vi-agent-core-app-0.1.0-SNAPSHOT-exec.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
