#!/bin/bash
# =============================================================================
# 本地构建 linux/amd64 镜像 (运行时专用, 复用预构建的 JAR / dist, 无需 QEMU 编译)
# 产物: aiplatform-backend:prod.tar  /  aiplatform-frontend:prod.tar
# =============================================================================
set -e
cd "$(dirname "$0")/.."

STAGE=/tmp/ld-build
rm -rf "$STAGE" && mkdir -p "$STAGE/backend" "$STAGE/frontend"

echo "===== 1. 准备后端构建上下文 ====="
cp Aiplatform-demo/target/ai-gateway-platform.jar "$STAGE/backend/app.jar"
cat > "$STAGE/backend/Dockerfile" <<'EOF'
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
# 安装 docker CLI (后端通过 docker.sock 控制其他容器, 仅需 CLI 即可; 这里改用 JDK 内 docker-java SDK, 无需 CLI)
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
EOF
echo "  JAR 大小: $(du -h "$STAGE/backend/app.jar" | cut -f1)"

echo "===== 2. 准备前端构建上下文 ====="
cp -R Aiplatform-web/dist "$STAGE/frontend/dist"
cp Aiplatform-web/nginx.conf "$STAGE/frontend/nginx.conf"
cat > "$STAGE/frontend/Dockerfile" <<'EOF'
FROM nginx:alpine
ENV TZ=Asia/Shanghai
COPY dist/ /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
EOF
echo "  dist 大小: $(du -sh "$STAGE/frontend/dist" | cut -f1)"

echo "===== 3. 构建后端 amd64 镜像 ====="
docker buildx build --platform linux/amd64 -t aiplatform-backend:prod \
  -f "$STAGE/backend/Dockerfile" "$STAGE/backend" --load
echo "  -> 后端镜像构建完成"

echo "===== 4. 构建前端 amd64 镜像 ====="
docker buildx build --platform linux/amd64 -t aiplatform-frontend:prod \
  -f "$STAGE/frontend/Dockerfile" "$STAGE/frontend" --load
echo "  -> 前端镜像构建完成"

echo "===== 5. 验证镜像架构 ====="
docker image inspect aiplatform-backend:prod --format 'backend: {{.Os}}/{{.Architecture}} {{.Size}}'
docker image inspect aiplatform-frontend:prod --format 'frontend: {{.Os}}/{{.Architecture}} {{.Size}}'

echo "===== 6. 导出镜像为 tar ====="
docker save aiplatform-backend:prod -o "$STAGE/aiplatform-backend:prod.tar"
docker save aiplatform-frontend:prod -o "$STAGE/aiplatform-frontend:prod.tar"
ls -lh "$STAGE"/*.tar
echo "===== 完成! tar 文件位于 $STAGE ====="
