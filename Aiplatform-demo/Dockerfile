# =============================================================================
# 凌点 AI 平台 - 后端 Dockerfile (多阶段构建)
# 构建: docker build -t lingdian-backend ./Aiplatform-demo
# =============================================================================
# ---- Stage 1: Maven 构建 ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先拷贝 pom.xml 缓存依赖(加速后续构建)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源码并打包(跳过测试)
COPY src ./src
RUN mvn package -DskipTests -q \
    && cp target/*.jar app.jar

# ---- Stage 2: 运行时 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/app.jar app.jar

EXPOSE 8080

# JVM 参数可通过 JAVA_OPTS 环境变量覆盖
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
