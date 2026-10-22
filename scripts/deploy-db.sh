
#!/usr/bin/env bash
set -euo pipefail

REGION="ap-northeast-2"
PARAMETER="/rha/dev/postgres/password"
DIR="/opt/rha"

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

echo "[2/4] Deploy PostgreSQL"

docker compose \
  -f "$DIR/postgres.compose.yaml" \
  up -d postgres

unset POSTGRES_PASSWORD

echo "[3/4] Check PostgreSQL health"

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
    docker logs --tail 30 rha-postgres
    exit 1
  fi

  sleep 3
done

if [ "$HEALTH" != "healthy" ]; then
  echo "PostgreSQL health timeout"
  exit 1
fi

echo "[4/4] Verify database"

RESULT=$(docker exec rha-postgres \
  psql -U rha -d rha -tAc "SELECT 1")

test "$RESULT" = "1"

echo "PostgreSQL deployment SUCCESS"
docker ps --filter name=rha-postgres
