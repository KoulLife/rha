#!/usr/bin/env bash
# PostToolUse hook: api-spec/openapi.yaml이 수정되면 Slack으로 알림을 보낸다.
# SLACK_WEBHOOK_URL이 없으면 조용히 건너뛴다. 알림 실패가 작업을 막지 않는다.
set -u

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')

case "$file" in
  */api-spec/openapi.yaml) ;;
  *) exit 0 ;;
esac

[ -z "${SLACK_WEBHOOK_URL:-}" ] && exit 0

cd "$(git -C "$(dirname "$file")" rev-parse --show-toplevel)" || exit 0
branch=$(git branch --show-current)
stat=$(git diff --stat -- api-spec/openapi.yaml | tail -1)
paths=$(git diff -U0 -- api-spec/openapi.yaml | grep -E '^[+-]  /[a-z]' | head -20)

text=":memo: *API 스펙 변경 (AI 작업 중)*\n브랜치: \`${branch}\`\n${stat:-변경 내역 없음}"
[ -n "$paths" ] && text="${text}\n\`\`\`$(printf '%s' "$paths" | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}')\`\`\`"

curl -fsS -m 10 -X POST -H 'Content-type: application/json' \
  --data "{\"text\":\"${text}\"}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
exit 0
