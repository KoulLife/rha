
#!/usr/bin/env bash
set -euo pipefail

# ==========================================
# RHA SSM 자동 배포
# GitHub Actions -> AWS SSM -> EC2
# ==========================================

SERVICE="${1:?Service required}"
IMAGE="${2:?ECR image required}"

REGION="ap-northeast-2"
INSTANCE="i-0d4ab82e506bfbac9"

# 1. 서비스 검증
case "$SERVICE" in
  backend|frontend)
    ;;
  *)
    echo "Invalid service: $SERVICE"
    exit 1
    ;;
esac

echo "=================================="
echo "RHA SSM Deployment"
echo "Service : $SERVICE"
echo "Image   : $IMAGE"
echo "Instance: $INSTANCE"
echo "=================================="

# 2. 배포 파일 Base64 인코딩
SCRIPT_B64=$(base64 -w0 scripts/deploy-app.sh)
COMPOSE_B64=$(base64 -w0 deploy/compose.yaml)

# 3. EC2에서 실행할 명령 생성
COMMAND="$(cat <<EOS
set -eu

install -d -m 700 /opt/rha

WORK_DIR=\$(mktemp -d /opt/rha/.deploy.XXXXXX)
trap 'rm -rf "\$WORK_DIR"' EXIT

printf '%s' '$SCRIPT_B64' | base64 -d > "\$WORK_DIR/deploy-app.sh"
printf '%s' '$COMPOSE_B64' | base64 -d > "\$WORK_DIR/compose.yaml"

chmod 700 "\$WORK_DIR/deploy-app.sh"

bash "\$WORK_DIR/deploy-app.sh" \
  '$SERVICE' \
  '$IMAGE' \
  "\$WORK_DIR/compose.yaml"
EOS
)"

# 4. SSM 요청 파라미터 생성
PARAMS=$(jq -nc \
  --arg cmd "$COMMAND" \
  '{commands: [$cmd], executionTimeout: ["1200"]}')

# 5. AWS SSM 명령 실행
echo "[1/3] Send SSM Command"

COMMAND_ID=$(aws ssm send-command \
  --region "$REGION" \
  --instance-ids "$INSTANCE" \
  --document-name "AWS-RunShellScript" \
  --parameters "$PARAMS" \
  --comment "RHA $SERVICE deploy" \
  --query "Command.CommandId" \
  --output text)

echo "SSM Command ID: $COMMAND_ID"

# 6. 배포 결과 대기
echo "[2/3] Wait for deployment"

STATUS="Pending"

for i in $(seq 1 130); do

  STATUS=$(aws ssm get-command-invocation \
    --region "$REGION" \
    --command-id "$COMMAND_ID" \
    --instance-id "$INSTANCE" \
    --query "Status" \
    --output text 2>/dev/null || echo "Pending")

  echo "Deployment Status: $STATUS"

  case "$STATUS" in
    Success|Failed|Cancelled|TimedOut)
      break
      ;;
  esac

  sleep 10
done

# 7. 실행 결과 출력
echo "[3/3] Deployment Result"

aws ssm get-command-invocation \
  --region "$REGION" \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE" \
  --query '{
    Status:Status,
    Output:StandardOutputContent,
    Error:StandardErrorContent
  }' \
  --output json || true

# 8. 성공 여부 판단
if [ "$STATUS" != "Success" ]; then
  echo "Deployment FAILED: $STATUS"
  exit 1
fi

echo "=================================="
echo "RHA $SERVICE Deployment SUCCESS"
echo "=================================="
