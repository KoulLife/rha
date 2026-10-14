# API 스펙 변경 알림

스펙(`api-spec/openapi.yaml`)이 바뀌면 사람이 알 수 있도록 3단계로 구성한다.

| 단계 | 위치 | 동작 |
|---|---|---|
| 1. 규칙 | `AGENTS.md` | AI는 스펙 변경이 필요하면 멈추고 변경안을 보고, 승인 후 수정 |
| 2. CI | `.github/workflows/api-spec-diff.yml` | PR에서 oasdiff로 변경 요약 코멘트, breaking이면 실패, Slack 알림 |
| 3. Hook | `.claude/settings.json`, `.claude/hooks/notify-spec-change.sh` | AI가 로컬에서 스펙을 수정하는 즉시 Slack 알림 |

## 설정

1. Slack에서 Incoming Webhook URL 생성 (https://api.slack.com/messaging/webhooks)
2. GitHub: Settings > Secrets and variables > Actions > `SLACK_WEBHOOK_URL` 등록
3. 로컬 / Claude Code 클라우드 환경: 환경 변수 `SLACK_WEBHOOK_URL` 지정 (`jq`, `curl` 필요)

URL이 없으면 알림만 건너뛰고 나머지는 정상 동작한다. 웹훅 URL은 커밋하지 않는다.

## Breaking change 처리

CI가 실패하면 의도한 변경인지 확인하고, PR 설명에 근거와 프론트·백엔드 영향 범위를 적은 뒤 리뷰어 승인으로 진행한다.
