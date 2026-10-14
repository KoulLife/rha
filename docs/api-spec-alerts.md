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

CI는 포크에서 온 PR에도 secret과 코멘트 권한이 필요해 `pull_request_target`으로 실행한다. 이 이벤트는 base 저장소의 권한으로 돌기 때문에, 워크플로우에서 PR의 코드(빌드, 스크립트 등)를 실행하는 단계를 추가하지 않는다. 워크플로우 파일 자체는 base 브랜치의 것이 쓰이므로, 워크플로우 변경은 머지된 뒤의 PR부터 적용된다. `actions/checkout`은 이 이벤트에서 포크 PR 코드를 체크아웃하면 오류를 내므로, base 브랜치만 체크아웃하고 PR의 `openapi.yaml`은 `git fetch`로 한 파일만 읽어 온다.

## Breaking change 처리

CI가 실패하면 의도한 변경인지 확인하고, PR 설명에 근거와 프론트·백엔드 영향 범위를 적은 뒤 리뷰어 승인으로 진행한다.
