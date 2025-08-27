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
WORKDIR /app
# 用通配符拷贝构建产物（避免固定文件名）
COPY --from=build /workspace/target/*.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
