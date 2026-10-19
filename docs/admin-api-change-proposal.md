# 관리자 화면 API 변경 검토안

- 작성일: 2026-10-19
- 상태: **1차 검토용. 구현 범위와 필드명, 처리 기준 모두 피드백 후 확정한다.**
- 기준: [API 계약](../api-spec/openapi.yaml), 현재 프론트 화면 및 백엔드 구현
- 목적: 화면 동작에 필요한 API 호출 순서와 기존 계약의 변경점을 함께 검토한다.
- 이 문서는 변경 제안이다. `openapi.yaml`, 생성 코드, DB 및 서비스 구현은 변경하지 않았다.

## 1. 검토 범위

| 항목 | 현재 가능한 것 | 검토할 변경 |
| --- | --- | --- |
| 강사 색상 | 브라우저에 색상 저장 | 서버에 색상 식별자 저장, 목록·시간표에서 조회, 색상 기준표 관리 방식 |
| 수업 인계 | 미래 수업 즉시 인계, 인계 건수 및 활동 이력 조회 | 인계받을 강사 미리 지정·진행 상태 관리가 이번 범위에 필요한지 |
| 상품 판매 중지 | 상품 조회·수정·삭제 | 판매 여부, 판매 중지 상품의 신규 발급 차단 |
| 룸 비활성 | 룸 조회·수정·삭제 | 활성 여부, 비활성 룸 배정 차단, 예정 수업 처리 기준 |
| 출결 정정 | 출석·노쇼 변경, 변경 전후 이력 | 정정 사유 저장·조회 |
| 기본 통계 | 기존 목록과 건수를 이용한 계산 | 현재 화면 기준 신규 API 불필요 |

반복 수업 생성·일괄 수정, 예약 승인·대기자·자동 승급, 특별 휴무·이용권 일괄 보상은 팀에서 제외한 범위이므로 이번 요청에 포함하지 않는다. 출결 정정의 별도 승인 단계도 이번 제안에 포함하지 않는다.

### 공통 표기

- 아래 URL은 실제 요청 주소 기준으로 `/v1`을 포함한다. OpenAPI의 `paths`에는 `/v1` 없이 선언되어 있고, `servers.url`에 `/v1`이 포함된다.
- 목록 응답은 기존 `{ content, pageInfo }` 형식을 유지한다. 페이지는 1부터 시작한다.
- JSON 응답 예시는 **관련 필드만 발췌**했다. 생략한 기존 필드를 삭제하거나 응답 구조를 바꾸자는 의미가 아니다.
- 기존 API의 인증·권한·오류 처리 기준은 유지한다. 변경하는 규칙만 각 항목에 적었다.
- 오류 응답은 기존 `ErrorResponse` 형식을 사용한다. 신규 에러 코드는 제안이며, 확정 시 [에러 코드 문서](error-codes.md)와 계약에 함께 반영한다.
- 시각은 UTC `date-time`, 날짜만 있는 값은 센터 현지(Asia/Seoul) `date`를 사용한다.

## 2. 강사 색상 구분

### 화면과 목적

관리자 → 강사관리 → 강사목록 → 색상 선택.

관리자가 지정한 색상으로 시간표의 해당 강사 수업을 표시한다. 새로고침하거나 다른 기기로 접속해도 같은 색상 선택이 유지되어야 한다.

### 피드백과 선택할 방식

처음에는 강사별 `colorCode: "#4A90E2"` 저장을 제안했다. 동익님 피드백에 따라, 서비스의 공통 색상 톤을 바꿀 때 강사별 데이터를 일괄 수정하지 않아도 되는 방식을 함께 검토한다.

| 방식 | 강사별 저장 값 | 색상 톤 변경 방법 | 고려할 점 |
| --- | --- | --- | --- |
| A. 실제 색상값 직접 저장 | `#AC6A40` | 같은 계열을 함께 바꾸려면 강사별 값 변경 필요 | 자유로운 색상 선택 가능. 개별 사용자 색상 보존이 목적이면 사용할 수 있음 |
| B. 식별자 저장 + 프론트 기준표 | `INSTRUCTOR_2` | 프론트 CSS 토큰의 색상값 수정 후 배포 | 별도 색상 조회 API 불필요. 허용 식별자는 프론트·백엔드가 함께 관리 |
| C. 식별자 저장 + 서버 기준표 | 색상 ID 또는 키 | 공통 색상 테이블의 해당 항목 수정 | 여러 클라이언트가 같은 기준표를 조회 가능. 기준표 조회 API 및 관리 방식 필요 |

**검토 우선안은 B이다.** 현재 프론트에 강사 기본 색상 토큰 6개가 이미 있고, 운영자가 색상 기준표 자체를 편집해야 한다는 요구사항은 아직 없다. 배포 없이 공통 색상을 바꾸거나 여러 클라이언트의 기준표를 서버에서 관리해야 한다면 C를 검토한다. A가 항상 잘못된 방식이라는 의미는 아니다.

아래 API 예시는 **B를 선택했을 때의 제안**이다. 기존 제안의 `colorCode`와 새 제안의 `colorKey`를 동시에 추가하지 않는다.

### 색상 기준표 예시

아래 식별자 이름은 신규 제안이며, CSS 토큰과 색상값은 현재 코드 기준이다. HEX 값은 프론트 `globals.css`에 유지한다.

| 저장할 식별자 | 프론트 CSS 토큰 | 현재 표시 색상 |
| --- | --- | --- |
| `INSTRUCTOR_0` | `--instructor-0` | `#39775B` |
| `INSTRUCTOR_1` | `--instructor-1` | `#3C70AD` |
| `INSTRUCTOR_2` | `--instructor-2` | `#AC6A40` |
| `INSTRUCTOR_3` | `--instructor-3` | `#8D5A9D` |
| `INSTRUCTOR_4` | `--instructor-4` | `#397C88` |
| `INSTRUCTOR_5` | `--instructor-5` | `#777B35` |

예를 들어 주황색 톤을 바꾸면 `--instructor-2`의 값만 수정한다. `INSTRUCTOR_2`를 선택한 강사들의 DB 값은 유지된다. 기준표 변경은 그 색상을 선택한 모든 강사에게 적용된다.

### 작업 순서와 API

#### ① 강사 목록 진입 — 기존 조회 API 수정

- `GET /v1/members?role=INSTRUCTOR` — `getMembers`, `MemberAdmin`
- `GET /v1/members/{memberId}` — `getMember`, `MemberAdmin`
- `Member` 응답에 nullable `colorKey` 추가.

목록 응답 예시:

```json
{
  "content": [
    { "id": 2, "name": "김강사", "colorKey": "INSTRUCTOR_2" }
  ],
  "pageInfo": { "page": 1, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

#### ② 색상 선택 후 저장 — 기존 수정 API 확장

- `PATCH /v1/members/{memberId}` — `updateMember`, `MemberAdmin`
- **`MemberAdminUpdateRequest`**에 선택 필드 `colorKey` 추가. 본인 정보 수정용 `MemberUpdateRequest`와 구분한다.

요청:

```json
{ "colorKey": "INSTRUCTOR_2" }
```

응답: 기존과 동일하게 `200`, 수정된 `Member` 반환.

```json
{ "id": 2, "name": "김강사", "colorKey": "INSTRUCTOR_2" }
```

#### ③ 시간표 조회 — 기존 조회 API 수정

- `GET /v1/lessons?from={시작시각}&to={종료시각}` — `getLessons`, `Lesson`
- `GET /v1/lessons/{lessonId}` — `getLesson`, `Lesson`
- `InstructorSummary`에 nullable `colorKey` 추가. 이 스키마를 사용하는 `GET /v1/instructors` 응답에도 포함된다.

```json
{
  "content": [
    {
      "id": 101,
      "instructor": { "id": 2, "name": "김강사", "colorKey": "INSTRUCTOR_2" }
    }
  ],
  "pageInfo": { "page": 1, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

프론트는 `colorKey`를 CSS 토큰에 연결하여 수업 블록을 표시한다. 저장 성공 후 강사 목록·상세 및 수업 목록·상세의 조회 캐시를 갱신한다. 다른 기기는 다음 조회 시 저장된 값을 반영하며, 실시간 동기화는 별도 요구사항으로 보지 않는다.

### 처리 기준 제안

- 활성 관리자만 변경 가능. 색상은 강사 계정에만 적용한다.
- 허용 값은 위 식별자 6개 또는 `null`. 알 수 없는 키는 `400 INVALID_INPUT`.
- 요청에서 생략하면 기존 값 유지. `null`이면 지정 색상 해제.
- `colorKey`가 `null`이면 현재처럼 강사 ID에 따른 기본 색상 사용.
- 기존 데이터는 `null`. 현재 브라우저에 저장된 임의 HEX 값은 자동 변환하지 않고 팔레트에서 다시 선택하도록 제안한다.
- 강사가 아닌 계정에 색상 필드를 지정하면 `400 INVALID_INPUT`으로 제안한다. 역할과 색상을 함께 수정한다면 변경 후 역할을 기준으로 검증한다.
- 강사를 다른 역할로 변경하면 저장된 색상 식별자는 해제하는 방향으로 제안한다.
- 읽기 응답은 `null`을 포함해 필드를 반환한다. 구버전 응답에 필드가 없거나 구버전 프론트가 알 수 없는 키를 받으면 기본 색상으로 표시하도록 한다.
- DB에는 실제 HEX 대신 nullable 식별자를 저장한다. 정확한 테이블·컬럼 이름은 백엔드 검토로 결정한다.
- 허용 키를 추가할 때는 계약의 허용 값과 프론트 기준표를 함께 반영한다. 사용 중인 키의 삭제·이름 변경은 별도 호환성 검토가 필요하다.

### 프론트 변경과 검토 질문

현재 UI는 `<input type="color">`로 임의의 RGB 색상을 고른다. B 또는 C를 선택하면 **정해진 색상 목록에서 고르는 UI**로 변경해야 한다.

- 자유로운 색상이 필요한가, 정해진 6개 색상이면 충분한가?
- 기준표를 프론트에 둘 것인가, 서버에서 관리할 것인가?
- 기존 수정 API 확장 방식으로 갈 것인가, 색상 전용 API를 분리할 것인가? 이 문서는 기존 API 확장을 제안한다.

## 3. 퇴사 강사 수업 인계

### 작업 순서와 기존 API 재사용

1. `GET /v1/members?role=INSTRUCTOR`로 강사 및 `resignationDate` 조회.
2. `GET /v1/instructors`로 인계받을 활동 중인 강사 조회. 본인은 선택에서 제외.
3. `POST /v1/members/{memberId}/lesson-reassignments`로 즉시 인계.
4. 결과 건수를 표시하고 수업 목록 및 활동 이력 재조회.

인계 요청 (`reassignLessons`, `Instructor`):

```json
{ "toInstructorId": 5 }
```

응답: `200`.

```json
{ "reassignedCount": 12 }
```

이력 조회:

```text
GET /v1/activity-logs?targetType=MEMBER&targetId={memberId}&action=LESSONS_REASSIGNED
```

기존 API는 시작 전이고 취소되지 않은 수업을 즉시 넘기며, 회원 예약은 유지한다. 수업 하나라도 새 강사에게 배정할 수 없으면 전체 실패(`409 REASSIGNMENT_FAILED`)이다. 선택 입력인 `from`은 인계할 수업의 시작 시각 기준이며 자동 실행 예약 시각이 아니다.

이력에서 실행자·시각·이전/이후 강사를 확인할 수 있고 건수는 요약에 포함된다. 현재 인계 기록에는 개별 수업 ID 목록이 저장되지 않는다. 이용권에 지정된 담당 강사도 이 작업으로 변경되지 않는다.

### 별도 범위 결정 필요

화면의 ‘인계 대상 등록’은 저장 버튼이 비활성화되어 있다. 인계 대상을 미리 저장하고 진행 상태를 관리하는 API는 없다.

- 즉시 인계만 사용한다면 위 API를 그대로 재사용한다.
- 미리 지정할 필요가 있다면, ‘대상만 저장하고 관리자가 나중에 실행’인지 ‘정해진 시각에 자동 실행’인지부터 결정한다.
- 진행 상태가 필요한 경우 상태의 의미와 완료 기준을 정해야 한다. 담당 미래 수업이 0건이라는 이유만으로 인계 완료로 판정하지 않는다.
- 이 요구사항이 확정되기 전에는 예약 실행 API나 상태 필드를 임의로 추가하지 않는다.

## 4. 상품 판매 중지·재개

**이번 개발에 포함할지 미확정. 아래는 포함할 경우의 제안이다.**

### 작업 순서와 API 변경

1. 상품 관리 진입: `GET /v1/pass-products` 및 `GET /v1/pass-products/{passProductId}`.
2. 판매 중지·재개: `PATCH /v1/pass-products/{passProductId}`.
3. 이용권 발급 화면: 판매 가능한 상품만 선택.
4. `POST /v1/passes`에서 서버도 판매 가능 여부 검증.

`PassProduct` 응답과 `PassProductUpdateRequest`에 `onSale` 추가를 제안한다. 기존 `getPassProducts`, `getPassProduct`, `updatePassProduct`를 확장한다.

상품 객체 예시:

```json
{ "id": 1, "name": "필라테스 10회권", "onSale": true }
```

판매 중지 요청:

```json
{ "onSale": false }
```

응답: `200`, 기존 `PassProductUpdateResponse` 구조 유지.

```json
{
  "passProduct": { "id": 1, "name": "필라테스 10회권", "onSale": false },
  "affectedPassCount": 0
}
```

### 처리 기준 제안

- 관리자만 변경 가능. 기존·신규 상품은 기본 `true`.
- PATCH에서 필드 생략 시 기존 값 유지. `null`은 허용하지 않음. `true`로 다시 보내면 판매 재개.
- 상품 관리 목록에는 판매 중지 상품도 표시한다. 발급 화면에서만 `onSale: true`인 상품을 선택하도록 한다.
- 이미 발급한 이용권과 기존 예약은 그대로 유지한다. 판매 상태만 수정하는 경우 `affectedPassCount`는 0.
- 판매 중지 상품의 신규 발급 요청은 `409 PASS_PRODUCT_NOT_ON_SALE`로 거절하는 방향을 제안한다. **신규 코드**이다.
- 목록에서 숨기는 것만으로 끝내지 않고 서버 발급 시점에도 검증한다.

검토 사항: 이번 범위에 포함할지, `onSale` 필드명과 신규 발급 차단 기준에 동의하는지.

## 5. 룸 비활성·재활성

**이번 개발에 포함할지 미확정. 아래는 포함할 경우의 제안이다.**

### 작업 순서와 API 변경

1. 룸 관리 진입: `GET /v1/rooms` 및 `GET /v1/rooms/{roomId}`.
2. 사용 중지·재개: `PATCH /v1/rooms/{roomId}`.
3. 수업 등록·수정 시 활성 룸만 선택.
4. `POST /v1/lessons`, `PATCH /v1/lessons/{lessonId}`에서 서버도 룸 배정 가능 여부 검증.

`Room` 응답과 `RoomUpdateRequest`에 `active` 추가를 제안한다. 기존 `getRooms`, `getRoom`, `updateRoom`을 확장한다.

요청:

```json
{ "active": false }
```

응답: `200`, 기존 `Room` 구조에 필드 추가.

```json
{ "id": 1, "name": "A룸", "capacity": 6, "active": false }
```

### 처리 기준 제안

- 관리자만 변경 가능. 기존·신규 룸은 기본 `true`.
- PATCH에서 생략하면 기존 값 유지. `null`은 허용하지 않음.
- 비활성 룸도 목록·상세 조회가 가능하고 `active: true`로 재활성화할 수 있다. 삭제와 구분한다.
- **시작 전이고 취소되지 않은 수업이 있으면 비활성화할 수 없도록 제안한다.** 해당 수업을 다른 룸으로 변경하거나 취소한 뒤 중지한다.
- 이 경우 기존 삭제용 `409 ROOM_IN_USE`의 적용 범위를 비활성화까지 확장하는 방향을 제안한다.
- 비활성 룸으로 신규 수업을 개설하거나 기존 수업의 룸을 변경하려 하면 `409 ROOM_INACTIVE`로 거절하는 방향을 제안한다. **신규 코드**이다.
- 과거 수업 기록은 유지한다. 과거 수업의 기존 룸을 그대로 두고 제목 등 다른 필드만 수정하는 요청까지 일괄 차단하지 않는다.
- 수업 인계 등 다른 경로에서 공통 배정 검증을 사용할 때도 기존 룸을 유지하는 상황과 새로 배정하는 상황을 구분해야 한다.

검토 사항: 예정 수업이 있으면 비활성화를 막는 기준에 동의하는지, 진행 중 수업도 차단 대상에 포함할지. 이 기준을 결정한 뒤 구현한다.

## 6. 출결 정정 사유 저장·조회

### 현재 상태

강사 화면은 정정 사유를 입력받지만 출결 API에는 `status`만 전달한다. 사유는 화면 메모리에만 남는다. 관리자 정정 화면은 현재 사유를 입력받지 않는다.

### 작업 순서와 API 변경

#### ① 출결 조회 — 기존 API 사용

`GET /v1/lessons/{lessonId}/reservations`로 예약 ID와 현재 출결 상태를 조회한다.

#### ② 상태와 사유 저장 — 기존 API 확장

`PUT /v1/reservations/{reservationId}/attendance` — `confirmAttendance`, `Reservation`.

`AttendanceRequest`에 `reason` 추가:

```json
{ "status": "ATTENDED", "reason": "출석 체크 누락으로 정정" }
```

응답: 기존대로 `200`, 수정된 `Reservation` 반환.

```json
{ "id": 101, "status": "ATTENDED" }
```

사유는 예약 객체의 마지막 값 하나로 덮어쓰는 대신 각 출결 변경 이력에 보관한다.

#### ③ 관리자가 이력 조회 — 기존 API 확장

```text
GET /v1/activity-logs?targetType=RESERVATION&targetId=101&action=ATTENDANCE_RECORDED
```

`ActivityLog`에 nullable `reason` 추가를 제안한다. 기존 이력과 사유가 없는 행위는 `null`로 반환한다.

```json
{
  "content": [
    {
      "occurredAt": "2026-10-19T01:00:00Z",
      "action": "ATTENDANCE_RECORDED",
      "actor": { "id": 2, "name": "김강사", "role": "INSTRUCTOR" },
      "changes": [
        { "field": "status", "before": "NO_SHOW", "after": "ATTENDED" }
      ],
      "reason": "출석 체크 누락으로 정정"
    }
  ],
  "pageInfo": { "page": 1, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

### 처리 기준 제안

- 출결 변경 권한은 기존대로 관리자 또는 해당 수업 담당 강사.
- 최초 `RESERVED → ATTENDED/NO_SHOW` 처리에는 사유 선택, `ATTENDED ↔ NO_SHOW` 정정에는 사유 필수.
- 사유를 입력하면 앞뒤 공백 제거 후 1~500자. 정정 시 누락·null·공백만 입력하거나 길이 제한을 넘기면 `400 INVALID_INPUT`.
- 같은 상태를 다시 보내는 요청은 기존 허용 범위를 유지하며, 새로운 정정으로 보아 사유를 강제하지 않는 방향을 제안한다.
- 사유를 포함한 이력과 출결 변경은 같은 트랜잭션에서 저장한다. 이전 정정 사유는 덮어쓰지 않는다.
- 취소된 예약, 시작 전 수업, 정산에 포함된 수업에 대한 기존 제한은 유지한다.
- 활동 이력 조회는 기존대로 관리자 전용. 강사가 과거 정정 이력을 조회해야 한다면 별도 권한 범위와 조회 계약이 필요하며, 관리자 전용 API를 그대로 강사에게 열지 않는다.
- 별도 승인 단계 없이 변경 즉시 반영한다. 승인 필요 여부는 별도 논의한다.
- 실제 DB 저장 위치는 기존 활동 이력 구조를 고려해 백엔드에서 결정한다.

### 호환성

정정 사유를 필수로 만들면 기존의 `{ "status": "ATTENDED" }`만 보내는 정정 요청은 실패한다. **동작상 breaking change**이므로 관리자·강사 화면을 함께 수정해야 한다. 배포 순서를 위해 처음에는 선택 입력으로 받을지, 프론트·백엔드를 함께 전환할지 결정한다.

## 7. 기본 통계

현재 화면의 기본 통계는 신규 API 없이 기존 조회를 재사용한다.

| 순서/항목 | API | 계산 기준 |
| --- | --- | --- |
| 기간 선택 | 프론트에서 센터 현지 기간 계산 | 시각 필터는 UTC로 변환 |
| 신규 회원 | `GET /v1/members?role=MEMBER` | 전체 페이지 조회 후 `createdAt` 기간 필터 |
| 이용권 발급 | `GET /v1/passes?createdFrom={시작}&createdTo={끝}` | `pageInfo.totalElements` |
| 수업 | `GET /v1/lessons?from={시작}&to={끝}` | `pageInfo.totalElements` |
| 출석·노쇼 | `GET /v1/reservations?lessonFrom={시작}&lessonTo={끝}&status={상태}` | 각 상태의 `pageInfo.totalElements` |
| 매출 | `GET /v1/payments?paidFrom=2026-10-01&paidTo=2026-10-31&refunded=false` | 모든 페이지의 `amount` 합계 |

- 노쇼율: `노쇼 / (출석 + 노쇼) × 100`. 분모가 0이면 화면에 `—` 표시.
- 매출 비교는 기간을 바꿔 지난달·작년 같은 달을 조회한다. 비교 기간 매출이 0원이면 증감률은 `—` 표시.
- 출결 통계는 선택 기간에 시작하는 수업의 현재 예약 상태 기준이다. 취소가 발생한 날짜별 건수와는 다르다.
- 재등록 통계, 신청일·취소일 기준 통계는 지표 정의가 확정되지 않아 이번 신규 API 요청에 포함하지 않는다.
- 데이터가 커지면 서버 집계 API를 별도로 검토할 수 있으나 현재 기능을 위해 먼저 추가하지 않는다.

## 8. 스펙 변경 없이 수정할 프론트 항목

| 항목 | 현재 문제 | 수정 방향 |
| --- | --- | --- |
| 인계 이력 | 프론트가 `INSTRUCTOR_LESSONS_REASSIGNED`로 조회하지만 서버 행위 코드는 `LESSONS_REASSIGNED` | 기존 계약에 맞게 조회 값 수정. 수업별 개별 이력이 있다는 화면 설명도 실제 응답 범위에 맞춤 |
| 매출 | 월별 결제를 100건만 조회하고, 여러 페이지면 금액을 표시하지 않음 | 기존 생성 훅을 이용해 전체 페이지 합산 |

이 문서에서는 위 코드 수정도 수행하지 않았다.

## 9. 변경 영향과 결정할 내용

| 제안 | 기존 계약 영향 | 결정할 내용 |
| --- | --- | --- |
| 강사 `colorKey` | 선택 요청 필드·응답 필드 추가로 기존 호출 유지 가능 | 자유 색상/고정 팔레트, 기준표 소유 위치, 키 이름·목록 |
| 즉시 인계 | 계약 변경 없음 | 미리 지정·상태 관리도 필요한지 |
| 상품 `onSale` | 필드 추가. 판매 중지된 상품 발급이 새롭게 실패하므로 동작 영향 있음 | 범위 포함 여부, 에러 코드 |
| 룸 `active` | 필드 추가. 비활성화·배정 제한에 따른 동작 영향 있음 | 범위 포함 여부, 예정·진행 중 수업 처리 기준 |
| 정정 `reason` | 사유 필수화는 기존 정정 호출과 호환되지 않음 | 필수 적용 시점, 이력 응답 필드, 강사 이력 조회 필요 여부 |
| 기본 통계 | 계약 변경 없음 | 추가 지표는 별도 정의 |

피드백으로 범위를 확정하고 승인받은 뒤, API 하나와 화면 하나씩 `openapi.yaml → 생성 코드 → 프론트·백엔드 구현 → 검증` 순서로 진행한다. 기존 operationId를 유지하고, 신규 경로를 선택하는 경우 operationId·tags·example을 별도로 정의한다. 스펙 변경 PR에는 변경 내용과 호환성 영향을 적는다.

## 10. 연동 및 테스트 상태에 대한 설명

이 문서의 확인 범위는 코드와 계약 대조이다. **문서 작성 중 실제 서버 E2E 테스트는 실행하지 않았다.** 기존 API 연결 여부와 전체 업무 흐름의 E2E 통과 여부를 구분한다.

개발 서버 공동 테스트에서는 실제 API 모드, DB와 테스트 계정 준비 후 기존 업무 흐름부터 확인한다. 아직 제안 단계인 기능은 현재 E2E 완료 범위에 포함하지 않는다.

변경 구현 후 확인할 주요 시나리오:

- 색상: 저장 → 재조회·다른 브라우저 확인 → 색상 해제 → 기준표의 표시 색상 변경 시 강사 저장 값 유지.
- 인계: 정상 인계와 시간 충돌 시 전체 실패, 예약 유지, 올바른 활동 이력 조회.
- 상품: 중지 상품의 발급 차단과 기존 이용권 사용 유지, 판매 재개.
- 룸: 예정 수업이 있는 룸 중지 거절, 비활성 룸 배정 차단, 재활성화.
- 출결: 최초 기록, 사유 포함 정정, 사유 없는 정정 거절, 관리자 이력 조회, 기존 정산 제한 유지.
- 통계: 결제 기록이 100건을 넘는 월의 합계, 비교 기준이 0원인 경우.

## 11. 확인한 코드

- [강사 목록·색상 선택](../frontend/src/features/admin/MemberPages.tsx)
- [브라우저 색상 저장과 기본 색상 계산](../frontend/src/features/instructor/colors.ts)
- [색상 토큰](../frontend/src/globals.css)
- [시간표](../frontend/src/features/timetable/TimetablePage.tsx)
- [퇴사·수업 인계](../frontend/src/features/admin/InstructorPages.tsx)
- [상품·룸 설정](../frontend/src/features/admin/SettingsPages.tsx)
- [강사 출결 처리](../frontend/src/features/instructor/InstructorLessonRoster.tsx)
- [출결 사유의 화면 메모리 저장](../frontend/src/app/App.tsx)
- [통계](../frontend/src/features/admin/StatisticsPage.tsx)
- [수업 인계 백엔드](../backend/src/main/java/com/rha/lesson/LessonService.java)
- [출결 처리 백엔드](../backend/src/main/java/com/rha/reservation/ReservationService.java)
