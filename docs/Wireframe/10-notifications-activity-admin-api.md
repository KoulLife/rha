# 메뉴 10 알림·활동 이력 — 관리자

기준: `docs/menu-structure.md` 10번 및 관리자 활동 이력 메뉴, `api-spec/openapi.yaml`, 첨부 브레인스토밍2/3.
기존 `wireframes.html`에 누적 추가. API·ERD 변경 없음. 실제 API 호출·외부 메시지 발송 없음.

## 화면과 확인 위치

| 위치 | 할 수 있는 일 |
| --- | --- |
| 상단 알림 버튼 또는 왼쪽 알림 | 내 알림·미확인 숫자, 읽지 않은 것만 조회, 5개씩 페이지, 전체 읽음 |
| 알림 제목·행·상세보기 | 읽음 처리 후 내용·분류·받은 시각·읽은 시각 표시 |
| 알림 상세 → 관련 화면 | relatedType/relatedId가 있고 조회 가능한 경우 부재·예약·수업 등으로 이동 |
| 왼쪽 활동 이력 | 기간·카테고리·행위·행위자/역할·영향받은 사람·대상 종류/ID별 필터, 정렬·페이지 |
| 활동 이력 상세 | 누가 무엇을 변경했는지, 변경 전후 필드, 관련 관리 화면 이동 |
| 상세 → 이 대상의 이력만 보기 | 동일 targetType/targetId 필터로 목록 이동 |
| 메뉴 9 정산 상세 → 처리·수정 이력 | ‘이 정산의 활동 이력 보기’로 활동 이력 목록에 연결 |

알림은 **관리자 본인의 받은 알림**이다. 관리자가 다른 회원의 알림을 읽거나 모두 읽음으로 변경하는 기능이 아니다. 활동 이력은 관리자 전용이며 알림과 별개의 데이터다.

## 1. 각 화면·버튼의 기존 API

### 내 알림

| 화면·버튼 | API / operationId | 연결 규칙 |
| --- | --- | --- |
| 내 알림 목록·페이지 | `GET /notifications` / getMyNotifications | page/size, 최신순, content/pageInfo. 로그인 본인 |
| 읽지 않은 알림만 | 동일 getMyNotifications | unread=true. 필터 바꾸면 page=1 |
| 상단 미확인 수 | `GET /notifications/unread-count` / getMyUnreadNotificationCount | unreadCount. 현재 목록 페이지 개수로 전체 수를 추측하지 않음 |
| 제목·행·상세보기 → 읽음 | `PUT /notifications/{notificationId}/read` / readNotification | body 없음, 본인 ID. 이미 읽음도 204. 다른 사용자/없는 ID는 404 |
| 모두 읽음 | `PUT /notifications/read` / readAllNotifications | body 없음. 본인의 모든 미확인 알림. 현재 페이지/필터에 한정되지 않음. 이미 모두 읽음도 204 |
| 알림 상세 | 선택한 목록 Notification 객체 | 별도 단건 GET 없음. title/message/type/createdAt/readAt/relatedType/relatedId 사용 |

실제 연결 시 readNotification 성공(204) 후 읽음 상태·목록·상단 수를 갱신한다. 204에는 readAt 응답이 없으므로 재조회한 값을 최종 기준으로 사용한다. 실패를 읽음 성공으로 표시하지 않는다. 읽지 않은 목록에서 읽음으로 이동한 행이 빠지거나 마지막 페이지가 비면 페이지를 조정한다. HTML은 서버 호출 없이 즉시 예시 상태를 갱신한다.

미확인 수 API는 주기 조회용이며 실시간 전송은 현재 계약에 없다. HTML에는 서버 polling/SSE/WebSocket을 구현하지 않았다.

### 현재 정의된 알림 분류

| 종류 | 의미·수신자 |
| --- | --- |
| LESSON_CANCELED | 예약한 수업 전체 취소, 해당 회원 |
| RESERVATION_CANCELED | 관리자가 개별 예약 취소, 해당 회원 |
| ABSENCE_REQUESTED | 강사의 부재 신청, 관리자 |
| ABSENCE_APPROVED | 부재 승인, 신청 강사 |
| ABSENCE_REJECTED | 부재 반려, 신청 강사 |

type은 고정 enum이 아니라 string이다. 알 수 없는 값은 그대로 표시하며 화면이 깨지거나 알림을 숨기지 않는다. 관리자의 예시는 부재 신청 알림 중심으로 본인 알림 10개가 초기 미확인이다. 일반 회원·강사도 각각 10개를 유지했다.

### 알림 관련 화면 이동

| relatedType | 연결할 API·화면 | 유의점 |
| --- | --- | --- |
| LESSON | getLesson → 수업 상세 | 관리자 범위, 삭제/조회 실패 안내 |
| RESERVATION | getReservation → 예약 상세 | 예약 본인/담당/관리자 권한 서버 검증 |
| ABSENCE | getAbsence 등 부재 관리 API → 부재 상세 | 관리자 처리 화면. 기존 역할별 부재 문서 참조 |
| 기타/없음 | 현재 연결 가능한 대상만 | 정보가 없으면 링크 없음, 모르는 값으로 임의 경로를 만들지 않음 |

알림은 과거 사건이며 링크한 화면은 현재 상태다. 이미 승인된 부재의 ‘접수 알림’을 열면 현재 승인 상태를 볼 수 있다. 삭제·권한 변경으로 관련 상세를 볼 수 없어도 알림 내용과 읽음 상태는 유지한다.

### 관리자 활동 이력

| 화면·버튼 | API / operationId | 연결 규칙 |
| --- | --- | --- |
| 필터 선택지 | `GET /activity-log-actions` / getActivityLogActions | categories[].code/name/actions[].code/name. 페이지 없음. 서버 카탈로그 기준 |
| 목록·조회·정렬·페이지 | `GET /activity-logs` / getActivityLogs | category, action, actorId, actorRole, subjectMemberId, targetType, targetId, from/to, order, page/size |
| 특정 대상의 이력만 | 동일 getActivityLogs | targetType+targetId. 정산은 SETTLEMENT, 이용권은 PASS, 수업은 LESSON |
| 상세보기 | 선택 ActivityLog 객체 | 별도 이력 단건 GET 없음. summary, actor, subjectMember, target, occurredAt, changes |
| 관련 관리 화면 | 대상별 기존 API | 아래 표의 관리 화면. 대상이 없어도 이력은 읽기 가능 |

- `actorId`는 행동한 사람, `subjectMemberId`는 영향을 받은 사람. ‘김강사가 홍길동 출결 기록’에서 서로 다르다.
- actor=null은 시스템 또는 행위자 미확인. 특정 actorRole/actorId 필터에는 포함되지 않고 전체 조회에는 포함된다.
- targetId는 targetType과 함께 전송한다. 숫자 ID만으로 대상 종류를 추정하지 않는다.
- from은 포함, to는 미포함 **UTC date-time**. 입력은 서울 현지 시각으로 받고 UTC 변환, 표시는 다시 서울 시간으로 표시한다.
- DESC 최신순/ASC 오래된 순. 알 수 없는 category/action/role/targetType은 원문으로 표시한다.
- 변경 전후는 당시 기록이다. 현재 값을 조회해 과거 before/after를 재구성하지 않는다. 변경 값이 없는 이벤트는 ‘변경 값 없음’으로 표시한다.
- 개인정보는 서버 마스킹을 유지하며 비밀번호/토큰을 수집하거나 UI에 기록하지 않는다. HTML 예시 연락처도 마스킹된 문자열이다.

### 활동 이력의 관련 화면

| targetType | 현재 화면 연결 |
| --- | --- |
| MEMBER | 회원 상세 또는 강사 상세 |
| LESSON / RESERVATION | 수업 상세 / 예약 상세 |
| PASS / PASS_PAYMENT / PASS_PAUSE | 이용권 상세 / 결제 상세 / 해당 이용권의 홀딩 탭 |
| PASS_PRODUCT / LESSON_TYPE / ROOM | 운영 설정 상품 상세 / 유형 목록 / 룸 상세 |
| BUSINESS_HOURS / RESERVATION_POLICY | 영업시간 / 예약 정책 |
| SETTLEMENT | 정산 상세. 폐기된 정산이면 이력만 조회 |

실제 상세 진입은 대상별 서버 조회 권한·404/403/폐기 상태를 다시 검사해야 한다. PASS_PAYMENT/PASS_PAUSE는 목록에서 관련 이용권 ID를 알 수 있어야 한다. 활동 이력의 targetId만으로 부모 passId가 없는 경우 연결 정보 보완이나 조회 전략이 필요하다. 유형은 단건 GET이 없으므로 목록에서 객체를 찾는다.

## 2. API가 없어서 추가할 기능

| 요구 | 현재 빠진 것 |
| --- | --- |
| 수업 예약 신청/승인/거절 알림 | 관련 notification type·발생 시점·수신자·relatedType/id 정의 없음 |
| 대기 신청/취소/승급 확정 알림 | 대기 API와 알림 이벤트·권한·순번·만료 정책 없음 |
| 정산 계산/검토/확정/지급 알림 | 정산 전용 알림 종류·수신 권한·관련 정산 조회 API 부족 |
| 센터 공지·알림 작성/발송 | 관리자 발송·수신자 선택·공지 API 없음. 이번 화면은 받은 알림 조회만 제공 |
| 알림 삭제·다시 미확인으로 | 기존 API 없음. 버튼 추가하지 않음 |
| 실시간 알림 | 서버 push/SSE/WebSocket 계약 없음. 기존 unread-count 주기 조회와 구분 |
| 활동 이력 수정·삭제·내보내기 | 기존 API 없음. 조회용으로만 구성 |
| 삭제/폐기 대상 상세 | 원래 대상이 조회 불가할 수 있음. 이력과 대상 조회의 계약을 분리 |

알림 유형 필터·이름/본문 검색은 getMyNotifications에 없어서 임의의 type/keyword 쿼리를 추가하지 않았다. 현재 목록에는 API가 지원하는 unread 필터만 제공한다. activity keyword 검색도 기존 API에 없어 추가하지 않았다.

## 3. 회의 내용과 기존 API의 차이

브레인스토밍2에서 예약 확정·대기 전환·취소 알림을 논의했다. 브레인스토밍3 2:17 이후에는 정책 변경·운영 영향을 논의했고 부재 처리에도 알림이 포함된다. 현재 정의된 5개 유형만으로 예약 승인·대기 관련 알림이 모두 제공된 것은 아니다. 회의에서 논의된 흐름에 맞춰 **누가 어떤 사건에서 무엇을 받는지**와 연결할 대상 정보를 API에 추가해야 한다.

‘알림을 읽었다’와 ‘예약/부재를 승인했다’는 다른 동작이다. 상세 클릭은 읽음 처리만 하며, 관련 관리 화면에서 별도로 승인·취소를 처리한다. 목록의 모든 읽음으로 업무가 승인/완료되는 기능으로 만들지 않았다.

메뉴 구조에는 10.1 알림만 적혀 있었지만 상위 제목 및 관리자 역할에 활동 이력이 포함되어 있다. 이번 작업에서 10.2 활동 이력의 화면 목록을 문서에 추가했다.

## 직접 확인할 순서

1. 관리자 → 알림: 초기 미확인 10개, 5개씩 2페이지.
2. 제목/상세보기 클릭 → 읽음·읽은 시간·상단 숫자 감소. 관련 부재 상세로 이동해 실제 처리 버튼 확인.
3. 읽지 않은 것만 → 모두 읽음 → 본인 전체 0개. 강사·회원 알림 개수는 그대로 유지.
4. 관리자 → 활동 이력 → 변경한 사람/영향받은 사람을 각각 선택해 차이 확인.
5. 카테고리 선택 → 해당 행위 선택지 → 시간 범위·대상 종류/ID → 조회·페이지 이동.
6. 상세 → before/after 확인 → 관련 이용권/수업/결제 등으로 이동.
7. 상세 → 이 대상의 이력만 보기. actor=null 자동 만료 예시도 전체 조회에서 확인.
8. 메뉴 9에서 검토·확정·폐기·지급 처리 → 정산 이력 또는 활동 이력에서 새 예시 기록 확인.
9. 폐기된 정산은 관련 상세 링크가 없어도 기록 내용이 유지되는 예시 확인.

## 데이터·검증 범위

기존 notificationStore 역할별 10개 미확인을 유지했다. 일부 알림만 실제로 존재하는 예시 예약·부재 ID를 연결했고, 그 외 related 정보 없는 알림은 내용 조회만 제공한다. 회원 관련 취소 예약 9501, 강사 본인 승인 부재 805를 추가했다.

활동 이력은 대표 사건 19건과 메뉴 9의 p9Logs를 함께 표시한다. 정산 이후 새 처리 기록은 공유되지만 다른 메뉴의 모든 버튼을 자동 감사 기록하는 시스템을 구현한 것은 아니다. 정산 행위 코드 SETTLEMENT_EVENT와 다른 대표 코드도 화면 카탈로그 예시이며 실제 서버 카탈로그의 확정 코드로 간주하지 않는다.

예시 시각은 기존 화면과 같은 2026-10-11 12:00에 고정, 현재 날짜를 의미하지 않는다. 새로고침 초기화. 문법 검사만 수행했으며 실행 테스트·브라우저 검증·서버 연동·실시간 전송은 수행하지 않았다.
