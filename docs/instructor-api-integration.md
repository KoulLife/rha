# 강사 API 연동 (2026-10-22)

## 기준과 연결 범위

`api-spec/openapi.yaml`과 `backend/`의 기존 컨트롤러·서비스를 사용한다. 스펙과 백엔드 구현은 변경하지 않았다. Orval 설정에 수업별 예약자 infinite 훅 생성을 추가하고 `npm run generate:api`로 재생성했다. 생성 코드에 포함된 기존 스펙 설명의 갱신도 재생성 결과다.

| 화면 | 기존 API | 백엔드 |
| --- | --- | --- |
| 로그인·새로고침 세션 복원 | `/auth/login`, `/auth/refresh`, `/members/me` | 기존 인증 구현 재사용 |
| 강사 홈·시간표·수업 상세 | `GET /lessons`, `GET /lessons/{lessonId}` | `LessonController` |
| 예약자·오늘 출결 집계 | `GET /lessons/{lessonId}/reservations` | `ReservationController` |
| 출결 처리·직접 정정 | `PUT /reservations/{reservationId}/attendance` | `ReservationController` |
| 부재 목록·상세 표시 | `GET /absences` (목록 응답의 상세 필드 사용) | `AbsenceController` |
| 부재 신청·철회 | `POST /absences`, `POST /absences/{absenceId}/cancellations` | `AbsenceController` |
| 본인 알림·읽음 | 기존 `/notifications` 생성 훅 | 기존 알림 구현 재사용 |

모든 경로는 실행 시 `/v1` 아래로 요청한다. API 요청은 생성 훅과 공통 인증 클라이언트를 거친다. 강사 홈은 `MEMBER_READ`가 필요한 전체 `/reservations` 조회를 사용하지 않고 본인 담당 수업별 예약자를 조회한다.

## 동작

- 홈은 현재 서울 날짜의 담당 수업, 예약·출석·노쇼·종료 수업의 출결 대기, 본인 부재 승인 대기를 표시한다. 모의 모드에서는 기존 예시 기준 시각을 사용한다.
- 수업·부재·홈 집계는 필요한 모든 서버 페이지를 조회한다. 부재의 기존 사유 검색·기간 필터·정렬 및 화면 5개 단위 페이지는 전체 조회 결과에 적용한다. 수업별 예약자 업무 목록은 5개 단위 서버 페이지를 유지한다.
- 부재 신청은 서울 입력 시각을 UTC로 전달한다. 신청 사유는 기존 계약의 최대 200자에 맞춘다. 서버의 중복 기간, 상태, 권한 검증 오류를 표시한다. 신청·철회 중 중복 제출을 막고 실패 시 창과 입력을 유지한다.
- 부재 반려 사유와 처리 시각은 서버 응답을 사용한다. 신청·철회 후 일반 목록과 infinite 목록 캐시를 모두 무효화한다.
- 출결 저장 후 예약자 목록·출결 요약·홈 집계 캐시를 갱신한다. 임의의 예시 수업 ID로 실제 수업의 정산 여부를 판단하지 않는다. 서버가 정산 포함 여부 등 변경 제한을 최종 검증한다.
- 강사 홈과 부재는 별도 메모리 예시 데이터에서 제거했다. 모의 모드도 같은 생성 훅을 사용하고 MSW가 기존 계약에 맞춰 본인 조회·신청·철회를 처리한다.

## 현행 계약의 제한

출결의 `AttendanceRequest`에는 `status`만 있다. 정정 사유·별도 승인 API를 추가하지 않았고, 기존 사유 입력은 현재 로그인 세션의 화면 이력에만 보관한다. 입력 창과 이력 제목에 서버 미저장·새로고침 시 초기화를 명시한다. 영구 보관이 필요하면 AGENTS.md 절차에 따라 스펙 변경안을 승인받아야 한다.

## 실행

```bash
cd backend
docker compose up -d
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun

# 별도 터미널
cd frontend
npm run dev:api
```

백엔드에 준비된 실제 강사 계정으로 로그인한다. 미리보기의 `instructor@rha.example` 계정은 실제 DB 계정이 아니다. 다른 서버는 `frontend/.env.local`의 `API_PROXY_TARGET`으로 지정한다. `npm run dev`는 MSW 모의 모드다.

## 검증

- 프론트 타입 검사·프로덕션 빌드 통과. 번들 크기 경고는 남아 있다.
- 프론트 테스트 18개 통과: 인증/통신 회귀, 강사 필터와 서울 날짜 집계, 서버 부재 상세 변환, MSW 부재 신청·중복·본인 조회·철회·권한 검증 포함.
- 기존 백엔드 관련 테스트 207개 통과 (실패·오류·건너뜀 0): `com.rha.absence.*`, `com.rha.reservation.*`, `com.rha.lesson.GetLessonsTest`, `com.rha.auth.LoginTest`.
- 기존 DB와 분리한 임시 DB, 실제 Spring Boot 서버, MSW를 끈 Vite 프록시에서 브라우저 검증: 권한이 비어 있는 강사 로그인 → 본인 수업/예약자 조회 → 출석 저장 → 부재 신청 → 새로고침 후 세션/출석/부재 유지 → 부재 철회.
- 테스트 예약자 101명으로 두 번째 서버 페이지까지 홈 집계에 포함되는 것을 확인했다. 다른 강사 수업은 홈에서 제외된다.
- 신청 폼을 연 뒤 DB에 겹치는 신청을 추가하여 서버 409 응답을 검증했다. 서버 오류 문구가 표시되고 입력 내용과 폼이 유지된다.
