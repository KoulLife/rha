# 메뉴 5 강사 관리 — 관리자 기준

기준: `docs/menu-structure.md` 5.1~5.3, `api-spec/openapi.yaml`.
화면: `docs/wireframes/wireframes.html`에 누적 추가. API·ERD는 변경하지 않았습니다.

## 화면과 진입 경로

관리자 로그인 → 왼쪽 **강사 관리**.

| 메뉴·화면 | 확인·처리할 내용 |
| --- | --- |
| 강사 목록 | 이름/이메일 검색·계정 상태 필터·페이지·상세보기·강사 등록 |
| 강사 상세 / 기본 정보 | 개인정보·계정 상태·퇴사일·정보 수정·상태 변경 |
| 강사 상세 / 권한 | 보유 권한 조회·체크박스 선택·전체 교체 저장 |
| 강사 상세 / 부재 | 선택 강사의 신청 목록·상세 |
| 상단 부재 관리 | 전체 강사 부재·강사/상태/기간 필터·페이지·상세·승인/반려/취소 |
| 부재 상세 / 겹치는 수업 | 예약이 있는 수업 확인·담당 강사 변경·수업 취소 후 승인 재시도 |
| 상단 퇴사 관리 / 상세 | 퇴사일 등록/변경/취소·시작 전 수업·미래 수업 일괄 인계·퇴사 처리 |
| 퇴사·인계 화면 / 강사 정산 보기 | 선택 강사의 단가·정산 목록·계산·상세·검토 요청·확정·외부 지급 기록·폐기 |

상태 변경은 계정의 활성/비활성 처리입니다. 퇴사일 등록과 퇴사 완료는 별도의 업무입니다. 이 구분은 현재 API 기준이며 모든 세부 규칙이 회의에서 확정됐다는 뜻은 아닙니다.

## 5.1 강사 계정·권한: 기존 API 연결

| 화면·버튼 | API · operationId | 연결 기준 |
| --- | --- | --- |
| 관리용 강사 목록·검색 | `GET /members` · `getMembers` | role=INSTRUCTOR, keyword(이름/이메일), status, page, size. /instructors는 활성 강사의 ID·이름만 주므로 관리용 목록으로 대체하지 않음 |
| 강사 상세보기 | `GET /members/{memberId}` · `getMember` | 목록의 강사 회원 ID로 조회 |
| 강사 등록 | `POST /members` · `createMember` | email, name, phoneNumber, birthDate, address?, role=INSTRUCTOR. 비밀번호 입력 없음. API는 비밀번호 설정 링크를 이메일로 발송. 화면은 성공 후 신규 강사 상세로 이동 |
| 이메일 설정 링크 | `POST /auth/password-resets` · `resetPassword` | 실제 초대 메일 링크에서 사용. 이번 관리자 화면에는 링크를 수신하는 후속 화면을 추가하지 않음 |
| 정보 수정 → 저장 | `PATCH /members/{memberId}` · `updateMember` | 변경한 name/phoneNumber/birthDate/address만 전송. 이메일은 수정 불가. 주소 삭제 null. 역할 변경 기능은 제공하지 않음 |
| 계정 상태 변경 | `PUT /members/{memberId}/status` · `changeMemberStatus` | ACTIVE/INACTIVE. 시작 전 비취소 담당 수업이 있으면 비활성 차단. RESIGNED는 ACTIVE로만 복구. 복구 시 퇴사일 제거, 권한은 자동 복구되지 않음 |
| 권한 목록 구성 | `GET /permissions` · `getPermissions` | API가 반환한 권한 코드·이름·설명으로 체크박스 구성. 유형 추가 시 목록도 변경되므로 실제 코드에서 고정 enum으로 사용하지 않음 |
| 강사 보유 권한 | `GET /members/{memberId}/permissions` · `getMemberPermissions` | 현재 보유 코드 체크 표시 |
| 권한 저장 | `PUT /members/{memberId}/permissions` · `updateMemberPermissions` | `{ permissions: [...] }` 전체 교체. 빈 배열은 전체 회수. INSTRUCTOR만 대상, 중복·미등록 코드 불가 |

권한 예시는 MEMBER_READ, LESSON_CREATE:GROUP_3, LESSON_UPDATE:GROUP_3, LESSON_CREATE:PRIVATE, LESSON_UPDATE:PRIVATE입니다. 퇴사 완료 상태에서는 이 UI의 권한 편집을 막고 복구 후 설정하도록 안내합니다.

## 5.2 부재: 기존 API 연결

| 화면·버튼 | API · operationId | 연결 기준 |
| --- | --- | --- |
| 전체/선택 강사 부재 목록·필터 | `GET /absences` · `getAbsences` | instructorId, status, from/to, page, size. 기간은 겹침 기준. from 포함/to 미포함 UTC |
| 상세보기 | `GET /absences/{absenceId}` · `getAbsence` | 사유·상태·처리자·처리 시각·반려 사유 표시 |
| 부재 승인 | `POST /absences/{absenceId}/approvals` · `approveAbsence` | REQUESTED만 가능. 겹치는 비취소 수업에 예약이 하나라도 있으면 ABSENCE_HAS_RESERVATIONS, 승인 대기 유지. 예약 없는 수업은 승인과 동시에 자동 취소 |
| 부재 반려 | `POST /absences/{absenceId}/rejections` · `rejectAbsence` | REQUESTED만 가능. reason? 입력. 강사에게 표시 |
| 부재 취소 | `POST /absences/{absenceId}/cancellations` · `cancelAbsence` | 관리자 REQUESTED/APPROVED 둘 다 가능. 승인 때 취소된 수업은 되살아나지 않음 |
| 겹치는 수업 조회 | `GET /lessons` · `getLessons` 후 기간 겹침 확인 | 강사 ID와 UTC 수업 범위로 조회. 시작 전에 시작해서 부재와 겹치는 수업도 놓치지 않도록 조회 범위 확보 필요. API에 부재별 영향 수업 전용 조회 경로는 없음 |
| 겹치는 수업의 예약 확인 | `GET /lessons/{lessonId}/reservations` · `getLessonReservations` 및 reservedCount | 예약 존재를 확인하며 목록 첫 페이지 길이만으로 전체 판단하지 않음. 승인 API가 최종 검사 |
| 새 강사 선택 | `GET /instructors` · `getInstructors` | 활성 강사 후보. 현재 담당자 제외 |
| 강사 변경 | `PATCH /lessons/{lessonId}` · `updateLesson` | `{ instructorId }`. 시간과 예약 유지. 대상 강사 활성 상태·가능시간·승인 부재·퇴사일·시간 충돌은 서버 검증 |
| 수업 취소 | `POST /lessons/{lessonId}/cancellations` · `cancelLesson` | reason? 입력. 시작 전만 허용. RESERVED 예약 함께 취소·횟수 복구 |

관리자가 강사 명의로 부재를 직접 신청하는 API는 없습니다. createAbsence는 강사 본인만 허용하므로 관리자 신청 버튼을 만들지 않았습니다. 승인·반려 시 김강사 미리보기의 알림 예시에 결과를 추가하며 읽음 API는 기존 알림 계약을 사용합니다.

## 5.3 퇴사·수업 인계: 기존 API 연결

| 화면·버튼 | API · operationId | 연결 기준 |
| --- | --- | --- |
| 퇴사일·상태 조회 | `getMembers(role=INSTRUCTOR)`, `getMember` | Member의 resignationDate/status 사용 |
| 퇴사일 등록·변경 | `PUT /members/{memberId}/resignation` · `registerResignation` | `{ resignationDate }`. 활성 강사의 오늘 또는 미래 서울 날짜. 등록 자체는 RESIGNED 변경이 아님 |
| 퇴사일 등록 취소 | `DELETE /members/{memberId}/resignation` · `cancelResignation` | 등록일 제거·새 배정 차단 해제. 이미 퇴사 완료면 불가 |
| 시작 전 담당 수업 조회 | `GET /lessons` · `getLessons` | instructorId, 현재 이후, 비취소. 퇴사일 당일/이후 수업 별도 표시 |
| 미래 수업 인계 | `POST /members/{memberId}/lesson-reassignments` · `reassignLessons` | `{ toInstructorId, from? }`. from UTC, 미입력 시 현재. 지정 강사의 이후 수업을 일괄 인계. 전체 성공/전체 실패. 실패 수업·사유는 invalidParams |
| 인계 후보 가능시간 확인 | `GET /members/{memberId}/availability` · `getInstructorAvailability` | 관리자 조회 가능. 새 강사의 가능시간·기존 수업·승인 부재·퇴사일과 배정 가능 여부 확인. 서버가 최종 검사 |
| 퇴사 처리 | `POST /members/{memberId}/resignation/completions` · `completeResignation` | 등록된 퇴사일 도래·시작 전 담당 수업 없음 필요. RESIGNED, 로그인 불가, 토큰 폐기, 권한 회수, 대기/미래 부재 취소 |
| 담당 이용권 조회·변경(연결 계획) | `GET /passes?instructorId=...` · `getPasses`; `PATCH /passes/{passId}` · `updatePass` | 퇴사·수업 인계로 담당 이용권은 자동 변경되지 않음. 이 작업은 안내만 제공, 이용권 인계 처리 UI는 메뉴 7에서 추가 필요 |

현재 일괄 인계 UI는 시작 전 수업을 대상으로 하며 기준 시각을 현재 이후로 제한합니다. 실패 시 일부 수업만 먼저 변경하지 않습니다. 새로 등록한 강사의 가능시간은 미설정이므로 예시의 인계 대상 검증에서 배정이 차단됩니다. 가능시간 설정 자체는 이번 5번 화면에 추가하지 않았으며 실제 배정을 위한 선행 설정입니다.

## 퇴사자 정산: 기존 API 연결

| 화면·버튼 | API · operationId | 연결 기준 |
| --- | --- | --- |
| 선택 강사 정산 목록 | `GET /settlements` · `getSettlements` | instructorId 유지. 실제 구현 status, periodFrom/periodTo, page/size 가능. 퇴사한 강사도 조회 |
| 단가 조회·저장 | `GET /members/{memberId}/pay-rates` · `getPayRates`; `PUT /members/{memberId}/pay-rates` · `updatePayRates` | payRates: [{lessonTypeId, unitPrice}] 전체 교체. 체크 해제 유형 삭제. 수업 한 회당 금액, 인원 수와 무관 |
| 정산 계산 | `POST /settlements` · `createSettlement` | instructorId, periodStart, periodEnd. 서울 날짜 양끝 포함, 종료일은 오늘보다 과거. 기존 비폐기 정산과 기간 중복·미처리 출결·단가 누락 차단 |
| 정산 상세 | `GET /settlements/{settlementId}` · `getSettlement` | 수업별 items·출석/노쇼 수·계산 당시 단가·금액·미정산 사유 표시 |
| 검토 요청 | `POST /settlements/{settlementId}/review-requests` · `requestSettlementReview` | CALCULATED → REVIEW_REQUESTED. 현재는 관리자 내부 단계이며 강사 검토 권한을 뜻하지 않음 |
| 정산 확정 | `POST /settlements/{settlementId}/confirmations` · `confirmSettlement` | REVIEW_REQUESTED → CONFIRMED. 이후 금액 변경 불가 |
| 외부 지급 기록 | `POST /settlements/{settlementId}/payments` · `recordSettlementPayment` | CONFIRMED → PAID. paidAt, memo?. 실제 송금 없음. 확정 금액 전액 지급 기록, 분할 지급 미지원 |
| 정산 폐기 | `DELETE /settlements/{settlementId}` · `discardSettlement` | CALCULATED/REVIEW_REQUESTED만 가능. 폐기 후 출결 정정·재계산 가능. 확정 이후 폐기 불가 |

정산 계산은 수업 종료 플래그가 아니라 현재 API의 수업 시각·예약·출결을 기준으로 합니다. 취소되지 않은 예약이 있는 수업은 전원 노쇼여도 정산하며, 예약 없는 수업은 금액 0·NO_RESERVATION으로 표시합니다. 이미 계산한 단가는 단가 설정 변경에 따라 재계산되지 않습니다.

## API가 없거나 팀 결정이 필요한 사항

| 항목 | 현재 기준과 남은 작업 |
| --- | --- |
| 관리자 대신 부재 신청 | 관리자 명의 대리 신청 API 없음. 필요 여부 결정 후 스펙 보완 |
| 부재 신청 수정·승인된 부재 취소 요청 | 별도 요청/승인·수정 API 없음. 철회 후 재신청 또는 관리자 직접 취소 방식으로 운영할지 결정 |
| 부재 영향 수업 전용 조회 | 전용 API 없음. 목록·예약 조회와 승인 실패의 invalidParams를 사용. 기간 겹침을 정확히 판단하는 조회 전략 필요 |
| 강사 변경·퇴사 안내 알림 | 현재 알림 종류에는 전용 이벤트 없음. 수업 취소 알림과 구분하여 발생 조건·대상·메시지 결정 |
| 예약 승인·거절 전용 권한 | 회의안 권한이 현행 permissions와 1:1로 대응하지 않음. 새로운 권한 코드를 임의로 저장하지 않음 |
| 퇴사 시 담당 이용권 인계 | 개별 updatePass는 존재하지만 일괄 인계는 없음. 자동 변경 여부·일괄 처리 필요 여부 결정. 이번 화면은 자동 인계하지 않음 |
| 퇴사자 정산·강사 조회 | 정산은 관리자 전용이며 퇴사자의 강사 로그인을 허용하지 않음. 본인 지급 내역 접근 경로가 필요하면 별도 결정 |
| 활성·비활성·휴면 | 현재 API의 로그인 차단·복구 규칙을 화면에 반영. 회의의 상태 관리/휴면 표현과 동일한 운영 의미인지 팀 확인 필요 |
| 부재·퇴사와 회의안 예약 상태 | 현재 API는 RESERVED를 기준으로 예약을 처리. 회의안 REQUESTED/CONFIRMED·대기/횟수 확보까지 적용하려면 승인 차단·수업 취소·인계 정책 확장 필요 |

## 확인할 예시

1. 강사 관리 → `＋ 강사 등록` → 입력·등록 → 신규 강사 상세. 예시 저장이며 메일 발송 없음.
2. 김강사 상세 → 권한 → 수업 유형별 권한 체크·저장 → 보유 권한 확인.
3. 부재 관리 → 김강사 `10/12 17:00~20:00` → 예약 있는 수업 때문에 승인 차단.
4. 겹치는 `저녁 코어 필라테스`의 강사를 박강사로 바꾸거나 수업 취소 → 부재 승인 재시도.
5. 김강사 `10/13 09:00~12:00` 부재 승인 → 예약 없는 `기초 리포머` 자동 취소. 승인된 부재를 취소해도 수업은 복구되지 않음.
6. 퇴사 관리 → 김강사 상세 → 퇴사일 등록 → 수업 인계 → 퇴사일 도래/남은 미래 수업 여부에 따라 완료 가능 여부 확인. 예시 기준일은 2026-10-11이므로 10/12 퇴사일은 아직 완료 불가.
7. 퇴사 상태의 이강사 → 정산 보기 → 9/21~9/30 계산 → 정산 3개·미정산 1개·110,000원 → 검토 요청 → 확정 → 외부 지급 기록.

## 예시 구현과 제한

- 회원은 강사 관리 메뉴 없음. 강사는 본인 프로필·부재만, 관리자는 전체 관리 화면을 제공.
- 강사·관리자는 동일한 instructorMgmtStore의 프로필·권한·부재·퇴사·정산 예시를 사용.
- 부재 영향 수업·인계·취소는 기존 API 기준 courseStore를 사용. 회원 수업·예약·이용권, 회원 관리 조회와 메뉴 6 기본 수업 운영에 반영됨. 메뉴 6 기본 수업 운영에도 같은 변경이 반영됨. 별도 `승인 신청 대기`의 workflowStore에는 같은 변경을 자동으로 적용하지 않음. 신청 승인 모델의 계약 변경이 필요한 상태이므로 혼합하지 않음.
- 정산은 예시 과거 수업과 courseStore의 끝난 수업으로 계산. 정산에 포함된 courseStore 수업의 직접 출결 정정은 폐기 전 차단. 별도 회의안 출결 데이터와 서버 정산 잠금은 연결하지 않음.
- 김·박강사의 인계 검증용 가능시간은 모든 요일 09:00~21:00 예시. 실제 영업시간·가능시간·부재·퇴사·충돌 검사와 동시성 보장은 서버가 최종 처리해야 함.
- 관리용 강사 목록·부재 목록은 5개씩 예시 페이지 이동. 소량의 퇴사·정산 목록/내역은 전체 예시를 표시하며 실제 구현은 각 pageInfo 기준으로 페이지 조회.
- 실제 API·인증·메일·송금 호출 없음. 메모리 변경이며 새로고침하면 초기화. 계정 상태의 로그인 불가·토큰 폐기는 실제 인증 서버 동작이고 역할 선택 미리보기는 실제 인증을 대체하지 않음.
- 현재 초대 링크 수신·비밀번호 설정 후 로그인, 가능시간 설정, 담당 이용권 인계 UI는 별도 작업이 필요함.
- 저장 실패 시 완료로 표시하지 않고 폼/확인 창 오류 유지. 부재 승인 차단·기간 겹침·미래 수업 잔존·퇴사일 미도래·정산 기간 중복 등의 예시 오류를 안내.
- 실행 테스트·브라우저 검증은 수행하지 않음.

## 메뉴 9 추가 후 정산 화면

강사 정산 보기는 메뉴 9의 동일 저장소·목록·상세에 연결한다. 전체 목록은 왼쪽 강사 수당·정산에서 조회하며 강사/상태/겹치는 기간 필터와 항목·이력 페이지를 제공한다. 기존 퇴사 강사 110,000원 계산 예시는 유지한다. 상세 연결·수정/재계산·현재 없는 API는 `09-instructor-settlements-admin-api.md`가 최신 기준이다.
