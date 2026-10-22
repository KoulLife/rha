
# 1. React 빌드 환경
FROM node:24-alpine AS builder

WORKDIR /app/frontend

# npm 의존성 설치
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# 프론트엔드 소스 복사
COPY frontend/ ./

# 로고 이미지 2개 복사
COPY docs/R.ha.png docs/R.ha_logo_2.png /app/docs/

# TypeScript 검사 및 Vite 빌드
RUN npm run build

# 2. Nginx 실행 환경
FROM nginx:stable-alpine

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /app/frontend/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
