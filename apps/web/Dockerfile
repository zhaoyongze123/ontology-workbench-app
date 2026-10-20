# docker-compose up --build -d

# 使用node:18-alpine作为基础镜像
# FROM node:18-alpine AS builder
FROM docker.1ms.run/library/node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制package.json和package-lock.json文件
COPY package.json package-lock.json ./

# 安装依赖
RUN npm install

# 复制项目文件
COPY . .

# 构建应用
RUN npm run build

# 使用nginx:stable-alpine作为基础镜像
# FROM nginx:alpine
FROM docker.1ms.run/library/nginx:latest
RUN mkdir -p /app/config
WORKDIR /usr/share/nginx/html
RUN rm -rf ./* && \
    rm -rf /etc/nginx/conf.d/*

COPY nginx.conf.template /etc/nginx/templates/nginx.conf.template
COPY entrypoint.sh /entrypoint.sh
# Windows 环境下可能产生 CRLF，导致容器内脚本无法执行（报 no such file or directory）
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh
# 从builder阶段复制构建好的应用文件
COPY --from=builder /app/dist /usr/share/nginx/html/

RUN ln -sf /app/config/nginx.conf /etc/nginx/nginx.conf

ENTRYPOINT ["/entrypoint.sh"]