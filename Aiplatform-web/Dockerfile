# =============================================================================
# 凌点 AI 平台 - 前端 Dockerfile (多阶段: Node 构建 + Nginx 部署)
# 构建: docker build -t lingdian-frontend ./Aiplatform-web
# =============================================================================
# ---- Stage 1: 构建 ----
FROM node:20-alpine AS builder
WORKDIR /build

COPY package*.json ./
RUN npm install --registry=https://registry.npmmirror.com

COPY . .
RUN npm run build

# ---- Stage 2: Nginx 部署 ----
FROM nginx:alpine
ENV TZ=Asia/Shanghai

# 拷贝构建产物
COPY --from=builder /build/dist /usr/share/nginx/html
# 拷贝 nginx 配置(API 代理 + SPA 路由)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
