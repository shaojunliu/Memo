# 只用运行阶段镜像（JRE 21）
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre

# 建一个非 root 用户
RUN useradd -m appuser
WORKDIR /app

# 拷贝你上传的 jar
COPY app.jar /app/app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]