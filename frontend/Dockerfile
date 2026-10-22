
# 빌드 환경
FROM node:24-alpine AS builder

WORKDIR /app

# 의존성 설치
COPY package.json package-lock.json ./
RUN npm ci

# 소스 복사
COPY . .

# TypeScript 검사 및 Vite 빌드
RUN npm run build

# 실행 환경
FROM nginx:stable-alpine

# Nginx 설정
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 빌드된 정적 파일 복사
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
