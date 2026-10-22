
#!/usr/bin/env bash
set -euo pipefail

REGION="ap-northeast-2"
PARAMETER="/rha/dev/postgres/password"
DIR="/opt/rha"
COMPOSE_FILE="$DIR/compose.yaml"

echo "[1/4] Retrieve DB password"

POSTGRES_PASSWORD=$(aws ssm get-parameter \
  --region "$REGION" \
  --name "$PARAMETER" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text)

if [ -z "$POSTGRES_PASSWORD" ]; then
  echo "DB password not found"
  exit 1
fi

export POSTGRES_PASSWORD

# DB 단독 배포 시 Compose 변수 검증용
# Backend / Frontend 컨테이너는 생성하지 않음
export BACKEND_IMAGE="rha-backend:db-only"
export FRONTEND_IMAGE="rha-frontend:db-only"
export JWT_SECRET="db-only-placeholder"

echo "[2/4] Validate Docker Compose"

docker compose \
  -f "$COMPOSE_FILE" \
  config --quiet

echo "[3/4] Ensure PostgreSQL is running"

# 기존 컨테이너를 재생성하지 않음
docker compose \
  -f "$COMPOSE_FILE" \
  up -d --no-deps --no-recreate postgres

echo "[4/4] Verify PostgreSQL"

HEALTH=""

for i in $(seq 1 40); do
  HEALTH=$(docker inspect \
    --format '{{.State.Health.Status}}' \
    rha-postgres 2>/dev/null || true)

  if [ "$HEALTH" = "healthy" ]; then
    break
  fi

  if [ "$HEALTH" = "unhealthy" ]; then
    echo "PostgreSQL unhealthy"
    exit 1
  fi

  sleep 3
done

test "$HEALTH" = "healthy"

RESULT=$(docker exec rha-postgres \
  psql -U rha -d rha -tAc "SELECT 1")

test "$RESULT" = "1"

echo "PostgreSQL deployment SUCCESS"
docker ps --filter name=rha-postgres
