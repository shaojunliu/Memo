# ======== Build stage ========
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
# 打包
RUN mvn -q -B -DskipTests package
# 可选：查看产物，便于排错
# RUN ls -lah target

# ======== Runtime stage ========
FROM eclipse-temurin:21-jre

# 常用：用于 healthcheck/诊断（很多 compose/探针会用 curl）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 时区（可按需修改）
ENV TZ=Asia/Shanghai

# 以非 root 用户运行
RUN useradd -m -u 10001 appuser
WORKDIR /app

# 用通配符拷贝构建产物（避免固定文件名）
COPY --from=build /workspace/target/*.jar ./app.jar
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar ./app.jar"]
