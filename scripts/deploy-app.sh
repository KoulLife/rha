
#!/usr/bin/env bash
set -euo pipefail

# ==========================================
# RHA Frontend / Backend 공통 배포
# GitHub Actions -> SSM -> EC2
# ==========================================

SERVICE="${1:?Service required}"
NEW_IMAGE="${2:?Image required}"
COMPOSE_FILE="${3:?Compose file required}"

REGION="ap-northeast-2"
ECR_REGISTRY="592193418739.dkr.ecr.ap-northeast-2.amazonaws.com"

DB_PARAMETER="/rha/dev/postgres/password"
JWT_PARAMETER="/rha/dev/jwt/secret"

case "$SERVICE" in
  backend)
    CONTAINER="rha-backend"
    ;;
  frontend)
    CONTAINER="rha-frontend"
    ;;
  *)
    echo "Invalid service: $SERVICE"
    exit 1
    ;;
esac

test -f "$COMPOSE_FILE"

case "$NEW_IMAGE" in
  "$ECR_REGISTRY/r.ha_dev:$SERVICE-"*) ;;
  *)
    echo "Invalid ECR image: $NEW_IMAGE"
    exit 1
    ;;
esac

# ==========================================
# 1. Parameter Store 비밀정보 조회
# ==========================================

echo "[1/6] Read secrets"

POSTGRES_PASSWORD=$(aws ssm get-parameter \
  --region "$REGION" \
  --name "$DB_PARAMETER" \
  --with-decryption \
  --query 'Parameter.Value' \
  --output text)

JWT_SECRET=$(aws ssm get-parameter \
  --region "$REGION" \
  --name "$JWT_PARAMETER" \
  --with-decryption \
  --query 'Parameter.Value' \
  --output text)

test -n "$POSTGRES_PASSWORD"
test -n "$JWT_SECRET"

export POSTGRES_PASSWORD JWT_SECRET

# ==========================================
# 2. 현재 컨테이너 이미지 확인
# ==========================================

current_image() {
  docker inspect \
    --format '{{.Config.Image}}' \
    "$1" 2>/dev/null || true
}

PREVIOUS_IMAGE=$(current_image "$CONTAINER")

BACKEND_IMAGE=$(current_image rha-backend)
FRONTEND_IMAGE=$(current_image rha-frontend)

export BACKEND_IMAGE="${BACKEND_IMAGE:-rha-backend:bootstrap-unused}"
export FRONTEND_IMAGE="${FRONTEND_IMAGE:-rha-frontend:bootstrap-unused}"

set_target_image() {
  if [ "$SERVICE" = "backend" ]; then
    export BACKEND_IMAGE="$1"
  else
    export FRONTEND_IMAGE="$1"
  fi
}

set_target_image "$NEW_IMAGE"

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

# ==========================================
# 3. 서비스 정상 동작 확인
# ==========================================

probe() {
  local running health ip http_code

  running=$(docker inspect \
    --format '{{.State.Running}}' \
    "$CONTAINER" 2>/dev/null || true)

  [ "$running" = "true" ] || return 1

  health=$(docker inspect \
    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
    "$CONTAINER" 2>/dev/null || true)

  [ "$health" != "unhealthy" ] || return 1

  if [ "$SERVICE" = "backend" ]; then

    ip=$(docker inspect \
      --format '{{with index .NetworkSettings.Networks "rha-network"}}{{.IPAddress}}{{end}}' \
      "$CONTAINER" 2>/dev/null || true)

    [ -n "$ip" ] || return 1

    http_code=$(curl -s -o /dev/null \
      -w '%{http_code}' \
      --connect-timeout 2 \
      --max-time 4 \
      "http://$ip:8080/v1/" || true)

    [[ "$http_code" =~ ^[0-9]{3}$ ]] || return 1

    # 401/403/404도 서버가 응답한 것이므로 허용
    (( 10#$http_code >= 200 && 10#$http_code < 500 ))

  else

    http_code=$(curl -s -o /dev/null \
      -w '%{http_code}' \
      --connect-timeout 2 \
      --max-time 4 \
      http://127.0.0.1:8080/ || true)

    [ "$http_code" = "200" ]
  fi
}

wait_for_ready() {
  for i in $(seq 1 60); do
    if probe; then
      echo "$SERVICE is ready"
      return 0
    fi
    sleep 3
  done

  return 1
}

# ==========================================
# 4. 실패 시 이전 이미지 롤백
# ==========================================

rollback() {
  echo "Deployment failed. Rolling back..."

  if [ -n "$PREVIOUS_IMAGE" ]; then

    set_target_image "$PREVIOUS_IMAGE"

    if compose up -d \
      --no-deps \
      --force-recreate \
      "$SERVICE" && wait_for_ready; then

      echo "Rollback SUCCESS: $PREVIOUS_IMAGE"
    else
      echo "Rollback FAILED"
    fi

  else
    # 최초 배포 실패 시 해당 앱만 제거
    compose stop "$SERVICE" || true
    compose rm -sf "$SERVICE" || true
  fi
}

# ==========================================
# 5. Compose 검증 및 이미지 비교
# ==========================================

echo "[2/6] Validate Compose"
compose config --quiet

echo "[3/6] Compare image"
echo "Previous: ${PREVIOUS_IMAGE:-none}"
echo "New: $NEW_IMAGE"

if [ "$PREVIOUS_IMAGE" = "$NEW_IMAGE" ] && probe; then
  echo "SKIPPED: Same image and healthy"
  exit 0
fi

# ==========================================
# 6. AWS ECR 로그인 및 이미지 Pull
# ==========================================

echo "[4/6] ECR Login and Pull"

aws ecr get-login-password --region "$REGION" |
  docker login \
    --username AWS \
    --password-stdin "$ECR_REGISTRY" >/dev/null

docker pull "$NEW_IMAGE"

# ==========================================
# 7. 변경된 서비스만 재배포
# ==========================================

echo "[5/6] Deploy $SERVICE"

if ! compose up -d \
  --no-deps \
  --force-recreate \
  "$SERVICE"; then

  rollback
  exit 1
fi

# ==========================================
# 8. 배포 정상 여부 검증
# ==========================================

echo "[6/6] Verify deployment"

if ! wait_for_ready; then

  docker logs --tail 80 "$CONTAINER" || true

  rollback
  exit 1
fi

# 최신 Compose 파일 보관
if [ "$COMPOSE_FILE" != "/opt/rha/compose.yaml" ]; then
  install -m 600 "$COMPOSE_FILE" /opt/rha/compose.yaml
fi

echo "================================"
echo "Deployment SUCCESS"
echo "Service: $SERVICE"
echo "Image: $NEW_IMAGE"
echo "================================"

docker ps --filter "name=^/${CONTAINER}$"
