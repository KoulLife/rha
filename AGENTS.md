# AGENTS.md

## 프로젝트 개요
- 서비스: {서비스 한 줄 설명}
- 개발 방식: **스펙 중심(Contract-First)** — `/api-spec/openapi.yaml`이 프론트·백엔드의 유일한 기준

## 폴더 구조
```
/api-spec/openapi.yaml   API 계약 (기준)
/docs/                   ERD, 에러 코드, 결정 기록
/frontend/               React + TypeScript (Vite)
/backend/                Spring Boot
/e2e/                    Playwright FE+BE 통합 E2E (배포 서버 대상, 비즈니스 코드와 독립)
```

## 핵심 원칙
1. API 변경은 반드시 `openapi.yaml`부터 수정한다. 코드부터 바꾸지 않는다.
2. 스펙 변경이 있으면 PR 설명에 변경 내용을 명시한다.
   - 구현 중 스펙의 추가·수정·삭제가 필요해지면 코드를 먼저 고치지 말고 멈춘다. 변경안(추가/수정/삭제 대상, 이유, breaking 여부)을 사용자에게 보고하고 승인을 받은 뒤 `openapi.yaml`부터 수정한다.
   - `openapi.yaml` 수정 시 Slack 알림(hook)과 PR의 oasdiff 코멘트가 자동으로 발송된다. 설정은 `/docs/api-spec-alerts.md` 참고.
3. 생성 코드(`frontend/src/api/generated/`)는 직접 수정하지 않는다.
4. YAGNI: 요청받지 않은 기능·추상화를 추가하지 않는다. 기존 라이브러리와 기본 기능을 우선 사용한다.
5. 작업은 작은 단위로: "API 하나 + 화면 하나"

## 기능 개발 워크플로우
1. **스펙 정의**: `openapi.yaml`에 API 추가 (operationId, tags, example 필수)
2. **코드 생성**: `{orval 생성 명령어}` 실행
3. **프론트**: MSW 목 응답으로 화면 개발 (백엔드 완성 대기 불필요)
4. **백엔드**: 스펙에 맞춰 구현, ERD 변경 시 `/docs/erd.dbml` 함께 수정
5. **연동**: MSW 끄고 실제 API로 확인
6. **검증**: 타입체크·테스트 통과 확인 후 PR

## API 스펙 규칙
- 작성 규칙은 `openapi.yaml`의 `info.description` 참고
- URL: 복수형 명사 + kebab-case
- 목록 응답: `{ content, pageInfo }`, 페이지는 1부터 시작
- 에러: RFC 7807 형식, `components`의 `ErrorResponse` 재사용
- 에러 코드 목록: `/docs/error-codes.md` (새 코드 추가 시 함께 갱신)
- 인증 불필요 API는 `security: []` 명시
- 날짜/시간: ISO 8601. 시각(`date-time`)은 UTC, 날짜만 있는 필드(`date`)는 센터 현지(Asia/Seoul) 날짜

## 프론트엔드 규칙
- 모든 UI는 `/docs/shadcn-ui-component-guide.md`의 컴포넌트·variant·스타일 기준을 따른다.
- UI: shadcn/ui + Tailwind, 폰트 Pretendard, `word-break: keep-all`
- 색상·radius·간격은 `globals.css` 토큰으로만 지정
- 반복되는 스타일은 className 대신 컴포넌트 variant로 추가
- API 호출은 생성된 훅만 사용 (직접 fetch 금지)

## 백엔드 규칙
- 에러는 `@RestControllerAdvice`에서 `ProblemDetail` 반환, `code`·`timestamp`는 확장 필드로
- 페이징: `one-indexed-parameters=true`, 응답 `pageInfo.page`는 1부터

## 명령어
- 프론트 실행: `{명령어}`
- 백엔드 DB 실행: `cd backend && docker compose up -d`
- 백엔드 실행: `cd backend && ./gradlew bootRun` (JDK 21 필요, `JAVA_HOME` 지정)
- 백엔드 테스트: `cd backend && ./gradlew test` (Testcontainers 사용, Docker 필요)
- API 코드 생성: `{명령어}`
- 프론트 타입체크/테스트: `{명령어}`
- E2E 테스트: `cd e2e && npm test` (Node 20 이상, 대상 서버·계정은 `e2e/.env`에 지정)

## 결정 기록
- 주요 결정은 `/docs/`에 마크다운으로 남긴다.
- 같은 실수가 반복되면 이 파일에 규칙을 한 줄 추가한다.

## 커밋 규칙

### 형식
```
<type>(<scope>): <요약>
```
- 요약은 한국어 가능, 50자 이내, 마침표 없음
- 예: `feat(api): 코스 목록 조회 API 스펙 추가`

### type
- `feat`: 새 기능
- `fix`: 버그 수정
- `refactor`: 동작 변화 없는 코드 개선
- `style`: UI 스타일·토큰 변경 (로직 변화 없음)
- `test`: 테스트 추가·수정
- `docs`: 문서 (ERD, 에러 코드, AGENTS.md 등)
- `chore`: 설정, 의존성, 빌드

### scope
- `api`: `/api-spec/openapi.yaml` 및 생성 코드
- `fe`: `/frontend`
- `be`: `/backend`
- `docs`: `/docs`
- `e2e`: `/e2e`
- scope가 여러 개면 커밋을 나눈다

### 쪼개는 기준
1. **스펙 변경은 단독 커밋**: `openapi.yaml` 수정 + 코드 재생성 결과를 함께 커밋 (`feat(api): ...`)
2. **프론트와 백엔드는 커밋을 분리**: 한 커밋에 `fe`와 `be`를 섞지 않는다
3. **ERD·DB 변경은 백엔드 구현과 함께**: `/docs/erd.dbml` 수정은 해당 `be` 커밋에 포함
4. **한 커밋 = 한 가지 의도**: 기능 추가와 리팩터링을 섞지 않는다
5. **커밋 시점마다 빌드·타입체크가 통과해야 한다**

### 기능 하나의 커밋 순서 예시
```
feat(api): 코스 목록 조회 API 스펙 추가
feat(fe): 코스 목록 화면 구현 (MSW 목 연동)
feat(be): 코스 목록 조회 API 구현
fix(fe): 코스 목록 빈 상태 처리
```
