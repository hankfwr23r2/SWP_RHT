# Stage 1: Build ứng dụng React với Node.js
FROM node:22-alpine AS builder
WORKDIR /app
ARG VITE_TURNSTILE_SITE_KEY
ENV VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY
# Chỉ đặt khi chạy thử trên máy cá nhân
ARG VITE_LOCAL_DEMO
ENV VITE_LOCAL_DEMO=$VITE_LOCAL_DEMO
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Chạy production bằng Nginx siêu nhẹ
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
