# R.HA Pilates ERP

R.HA 필라테스 센터의 회원·강사·관리자 업무를 관리하는 ERP 서비스입니다.

## 프로젝트 기준

이 저장소는 프론트엔드와 백엔드가 하나의 API 계약을 기준으로 협업하는 구조입니다.

| 영역 | 기준 |
| --- | --- |
| API 계약 | [`api-spec/openapi.yaml`](api-spec/openapi.yaml) |
| 메뉴·화면 구조 | [`docs/menu-structure.md`](docs/menu-structure.md) |
| 와이어프레임·API 매핑 | [`docs/wireframes/`](docs/wireframes/) |
| 데이터 구조 | [`docs/erd.dbml`](docs/erd.dbml) |
| UI 컴포넌트·브랜드 토큰 | [`docs/shadcn-ui-component-guide.md`](docs/shadcn-ui-component-guide.md) |
| 에러 코드 | [`docs/error-codes.md`](docs/error-codes.md) |

기능을 추가하거나 변경할 때는 먼저 메뉴 구조와 API 계약을 확인합니다. API가 변경되면 `openapi.yaml`을 먼저 수정하고, 관련 문서와 생성 코드를 함께 갱신합니다.

## 저장소 구조

```text
api-spec/                    프론트·백엔드 공통 API 계약
docs/                        메뉴 구조, 와이어프레임, ERD, 결정 기록
frontend/                    React + TypeScript + Vite
  src/app/                   앱 셸, 라우팅 타입, 전역 화면 전환
  src/components/            여러 메뉴에서 공유하는 레이아웃·UI
  src/config/                역할·권한·메뉴 접근 정책
  src/features/              메뉴·업무 영역별 화면과 전용 컴포넌트
  src/mocks/                 API 연결 전 화면 확인용 목업 데이터
backend/                     Spring Boot API 서버
```

### 프론트엔드 기능 폴더 원칙

메뉴별 화면은 `frontend/src/features/{feature-name}/` 아래에서 관리합니다. 메뉴 전용 컴포넌트와 화면 로직은 해당 기능 폴더에 두고, 둘 이상의 메뉴에서 재사용하는 UI만 `frontend/src/components/`로 올립니다.

현재 구현된 메뉴 3 회원용 메뉴는 다음과 같이 분리되어 있습니다.

```text
frontend/src/features/member/pages/
  LessonsPage.tsx              수업 목록·상세·예약/대기 신청
  PassesPage.tsx               내 이용권·상세
  ReservationsPage.tsx         내 예약·출결·예약 상세
  AccountPage.tsx              내 계정·비밀번호 변경
  NotificationsPage.tsx        알림 목록·상세
```

홈·대시보드와 관리자·강사 영역은 각 담당 범위에 맞춰 별도 feature 폴더로 추가합니다. 한 팀원의 화면을 다른 팀원의 feature 폴더에 섞지 않습니다.

## 개발 환경

- Node.js 20 이상
- npm
- 백엔드 실행 시 JDK 21, Docker 필요

## 실행 방법

### 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

기본 주소는 `http://localhost:5173`입니다.

```bash
# 타입체크
npm run typecheck

# 프로덕션 빌드
npm run build
```

### 백엔드

```bash
cd backend
docker compose up -d
./gradlew bootRun
```

```bash
# 백엔드 테스트
cd backend
./gradlew test
```

## API·목업 데이터 사용 원칙

- 프론트엔드에서 API를 직접 `fetch`하지 않고 생성된 API 훅을 사용합니다.
- `frontend/src/mocks/`의 데이터는 화면 개발과 미리보기 전용입니다.
- 실제 연동 시 목업 데이터 대신 API 계약에 맞는 생성 훅과 응답 타입을 사용합니다.
- 생성 코드(`frontend/src/api/generated/`)는 직접 수정하지 않습니다.
- API 변경은 반드시 `api-spec/openapi.yaml`부터 시작합니다.

## 역할·권한

같은 메뉴라도 역할과 권한에 따라 메뉴 및 액션의 노출 여부가 달라질 수 있습니다. 프론트는 `frontend/src/config/access.ts`의 정책을 사용해 화면을 구성하고, 실제 보안 검증은 백엔드의 역할·권한 정책이 담당합니다.

메뉴를 숨기는 것만으로 권한 검증을 대체하지 않습니다. 새로운 권한이 필요하면 API 계약, 에러 코드, 화면 정책을 함께 검토합니다.

## UI 개발 규칙

- UI는 shadcn/ui 방식과 Tailwind 스타일 원칙을 따릅니다.
- 폰트는 Pretendard, 본문은 `word-break: keep-all`을 사용합니다.
- 색상·radius·간격은 디자인 토큰으로 관리합니다.
- 공통 헤더·풋터와 공통 UI는 `frontend/src/components/`에서 관리합니다.
- 화면 개발 후 타입체크와 빌드를 실행합니다.

## 협업·커밋 규칙

커밋 형식은 다음과 같습니다.

```text
<type>(<scope>): <요약>
```

예시:

```text
feat(api): 코스 목록 조회 API 스펙 추가
feat(fe): 회원 수업 목록 화면 구현
fix(fe): 예약 상세 반응형 레이아웃 수정
docs(docs): 메뉴 구조 문서 갱신
```

- `api`: `api-spec/openapi.yaml` 및 생성 코드
- `fe`: `frontend/`
- `be`: `backend/`
- `docs`: `docs/`, README 등 문서

프론트엔드와 백엔드 변경은 가능한 한 커밋을 분리합니다. 한 커밋에는 하나의 의도만 담고, 커밋 전 관련 타입체크·테스트·빌드를 확인합니다.

