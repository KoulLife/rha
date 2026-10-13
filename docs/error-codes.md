# 에러 코드

에러 응답은 RFC 7807(`application/problem+json`) 형식이며, 서비스 내부 코드는 `code` 필드에 담긴다. 스펙(`api-spec/openapi.yaml`)의 각 API 설명에 나온 코드를 한곳에 모은 목록이다.

- 아래에 없는 오류(404 리소스 없음, 500 서버 오류 등)는 별도 코드 없이 HTTP 상태로 구분한다.
- 스펙에 새 에러 코드를 추가하면 이 문서도 함께 갱신한다.

## 400 잘못된 요청

| 코드 | 의미 | 발생하는 API |
|---|---|---|
| `INVALID_ABSENCE_PERIOD` | 부재 기간 오류 (종료가 시작보다 늦지 않거나 시작이 과거) | `createAbsence` |
| `INVALID_AVAILABILITY` | 수업 가능시간 설정 오류 (시작이 종료보다 늦거나 같음, 같은 요일 시간대 겹침) | `updateInstructorAvailability` |
| `INVALID_BUSINESS_HOURS` | 영업시간 설정 오류 (요일 누락/중복, 오픈 시각이 마감 시각보다 늦음) | `updateBusinessHours` |
| `INVALID_CURRENT_PASSWORD` | 현재 비밀번호가 틀림 | `changeMyPassword` |
| `INVALID_END_DATE` | 이용권 만료일이 시작일보다 빠름 | `updatePass` |
| `INVALID_INPUT` | 요청 필드의 형식이 올바르지 않다 (`invalidParams`에 상세 내용) | 요청 본문/파라미터가 있는 모든 API |
| `INVALID_INSTRUCTOR` | 지정한 회원이 강사가 아니거나, 수업/이용권의 담당 강사로 쓸 수 없는 상태(활성이 아님) | `createLesson`, `createPass`, `createSettlement`, `reassignLessons`, `registerResignation`, `updateLesson`, `updatePass`, `updatePayRates` |
| `INVALID_LESSON_TIME` | 수업 시간 오류 (종료가 시작보다 늦지 않거나 시작이 과거) | `createLesson`, `updateLesson` |
| `INVALID_MEMBER` | 이용권 발급 대상이 활성 상태의 일반 회원이 아님 | `createPass` |
| `INVALID_PAUSE_PERIOD` | 일시정지 기간 오류 (시작일이 과거, 종료일이 시작일보다 빠름, 이용권 기간 밖) | `createPassPause` |
| `INVALID_PAYMENT_DATE` | 결제일이나 환불일이 오늘보다 미래이거나, 환불일이 결제일보다 빠름 | `recordPassPayment`, `setPassPaymentRefund`, `updatePassPayment` |
| `INVALID_PAY_RATE` | 수당 단가 목록 오류 (수업 유형 중복, 존재하지 않는 수업 유형) | `updatePayRates` |
| `INVALID_PERIOD` | 정산 기간 오류 (시작일이 종료일보다 늦거나 종료일이 오늘 이후) | `createSettlement` |
| `INVALID_PERMISSION` | 권한 코드가 목록에 없거나 중복됨 | `updateMemberPermissions` |
| `INVALID_PUBLISH_AT` | 공개 예약 시각 오류 (누락, 과거, 수업 시작 이후) | `changeLessonStatus`, `updateLesson` |
| `INVALID_REMAINING_COUNT` | 잔여 횟수가 0 미만이거나 총 횟수를 초과 | `updatePass` |
| `INVALID_RESET_TOKEN` | 비밀번호 재설정 토큰이 유효하지 않거나 만료됨 | `resetPassword` |
| `INVALID_RESIGNATION_DATE` | 퇴사일이 오늘보다 과거 | `registerResignation` |
| `INVALID_START_DATE` | 이용권 시작일 오류 (만료일이 이미 지남) | `createPass` |
| `INVALID_VERIFICATION_CODE` | 이메일 인증 코드가 일치하지 않거나 만료됨 | `confirmEmailVerification` |
| `INVALID_VERIFICATION_TOKEN` | 이메일 인증 토큰이 유효하지 않거나 요청 이메일과 다름 | `signUp` |
| `LESSON_TYPE_INACTIVE` | 비활성 수업 유형으로 수업을 개설함 | `createLesson` |
| `PERMISSION_TARGET_INVALID` | 권한 부여 대상이 강사가 아님 | `updateMemberPermissions` |
| `SAME_AS_CURRENT_PASSWORD` | 새 비밀번호가 현재 비밀번호와 같음 | `changeMyPassword` |

## 401 인증 실패

| 코드 | 의미 | 발생하는 API |
|---|---|---|
| `ACCOUNT_DISABLED` | 탈퇴, 비활성화 또는 퇴사한 계정 | `login` |
| `INVALID_CREDENTIALS` | 이메일 또는 비밀번호가 일치하지 않음 | `login` |
| `INVALID_REFRESH_TOKEN` | Refresh Token이 없거나 만료/폐기됨 | `refreshToken` |
| `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않거나 만료됨 | 인증이 필요한 모든 API (`security: []`가 아닌 API) |

## 403 권한 없음

| 코드 | 의미 | 발생하는 API |
|---|---|---|
| `CANNOT_CHANGE_OWN_ROLE` | 본인의 역할은 변경할 수 없음 | `updateMember` |
| `CANNOT_CHANGE_OWN_STATUS` | 본인 계정의 상태는 변경할 수 없음 | `changeMemberStatus` |
| `FORBIDDEN_ROLE` | 강사/관리자는 직접 탈퇴할 수 없음 | `withdrawMyAccount` |
| `PERMISSION_DENIED` | 해당 작업에 필요한 권한이 없음 | `cancelAbsence`, `cancelLesson`, `changeLessonStatus`, `createAbsence`, `createLesson`, `getMember`, `getMembers`, `getReservations`, `updateLesson` |

## 409 상태 충돌

| 코드 | 의미 | 발생하는 API |
|---|---|---|
| `ABSENCE_HAS_RESERVATIONS` | 부재 기간에 예약이 있는 수업이 있어 승인할 수 없음 | `approveAbsence` |
| `ABSENCE_PERIOD_OVERLAP` | 같은 강사의 승인 대기 또는 승인된 부재와 기간이 겹침 | `createAbsence` |
| `ALREADY_RESERVED` | 이미 같은 수업을 예약 중 | `createReservation` |
| `CANCELLATION_DEADLINE_PASSED` | 예약 취소 가능 시간이 지남 | `cancelReservation` |
| `EMAIL_ALREADY_EXISTS` | 이미 가입된 이메일 (탈퇴한 계정의 이메일 포함) | `createMember`, `requestEmailVerification`, `signUp` |
| `INSTRUCTOR_ABSENT` | 강사의 승인된 부재 기간과 겹침 | `createLesson`, `updateLesson` |
| `INSTRUCTOR_HAS_UPCOMING_LESSONS` | 시작 전 담당 수업이 있어 강사를 비활성화하거나 역할을 바꾸거나 퇴사 처리할 수 없음 | `changeMemberStatus`, `completeResignation`, `updateMember` |
| `INSTRUCTOR_RESIGNING` | 퇴사일이 등록된 강사에게 퇴사일 이후의 수업이나 이용권 담당을 배정함 | `createLesson`, `createPass`, `updateLesson`, `updatePass` |
| `INSTRUCTOR_TIME_CONFLICT` | 담당 강사의 다른 수업과 시간이 겹침 | `createLesson`, `updateLesson` |
| `INSTRUCTOR_UNAVAILABLE` | 강사의 수업 가능시간 밖 (가능시간이 설정되지 않은 강사 포함) | `createLesson`, `updateLesson` |
| `INVALID_ABSENCE_STATUS` | 현재 부재 상태에서는 할 수 없는 작업 | `approveAbsence`, `cancelAbsence`, `rejectAbsence` |
| `INVALID_PAUSE_STATUS` | 이미 끝났거나 취소된 일시정지 | `terminatePassPause` |
| `INVALID_RESERVATION_STATUS` | 현재 예약 상태에서는 할 수 없는 작업 | `cancelReservation`, `confirmAttendance` |
| `INVALID_SETTLEMENT_STATUS` | 현재 정산 상태에서는 할 수 없는 작업 | `confirmSettlement`, `discardSettlement`, `recordSettlementPayment`, `requestSettlementReview` |
| `INVALID_STATUS_TRANSITION` | 허용되지 않는 수업 공개 상태 전이 | `changeLessonStatus` |
| `LESSON_ALREADY_STARTED` | 이미 시작한 수업이라 할 수 없는 작업 | `cancelLesson`, `cancelReservation`, `changeLessonStatus`, `updateLesson` |
| `LESSON_CANCELED` | 이미 취소된 수업 | `cancelLesson`, `changeLessonStatus`, `updateLesson` |
| `LESSON_FULL` | 수업 정원이 가득 참 | `createReservation` |
| `LESSON_HAS_RESERVATIONS` | 예약이 있는 수업은 시작/종료 시각을 바꿀 수 없음 | `updateLesson` |
| `LESSON_NOT_RESERVABLE` | 예약할 수 없는 수업 (비공개, 취소, 이미 시작) | `createReservation` |
| `LESSON_NOT_STARTED` | 수업이 시작되기 전에는 출결을 기록할 수 없음 | `confirmAttendance` |
| `LESSON_SETTLED` | 강사 정산에 포함된 수업은 출결을 정정할 수 없음 | `confirmAttendance` |
| `LESSON_TYPE_CODE_DUPLICATED` | 이미 있는 수업 유형 코드 | `createLessonType` |
| `MEMBER_HAS_UPCOMING_RESERVATIONS` | 시작 전 예약이 있어 탈퇴하거나 비활성화할 수 없음 | `changeMemberStatus`, `withdrawMyAccount` |
| `MEMBER_RESIGNED` | 이미 퇴사한 강사 (퇴사일 등록/취소/처리 불가, 상태는 ACTIVE로만 되돌릴 수 있음) | `cancelResignation`, `changeMemberStatus`, `completeResignation`, `registerResignation` |
| `MEMBER_WITHDRAWN` | 탈퇴한 회원은 ACTIVE로만 되돌릴 수 있음 (INACTIVE로 변경 불가) | `changeMemberStatus` |
| `NO_AVAILABLE_PASS` | 예약에 사용할 수 있는 이용권이 없음 | `createReservation` |
| `OUTSIDE_BUSINESS_HOURS` | 영업시간 밖이거나 휴무일 | `createLesson`, `updateLesson` |
| `PASS_EXPIRED` | 이미 만료된 이용권 | `createPassPause` |
| `PASS_PRODUCT_IN_USE` | 발급된 이용권이 있는 상품은 삭제할 수 없음 | `deletePassProduct` |
| `PASS_UNPAID` | 미수 예약이 허용되지 않아 완납한 이용권이 필요함 | `createReservation` |
| `PAUSE_HAS_RESERVATIONS` | 정지 기간에 속한 예약이 있어 먼저 취소해야 함 | `createPassPause` |
| `PAUSE_PERIOD_OVERLAP` | 다른 일시정지 기간과 겹침 | `createPassPause` |
| `PAYMENT_EXCEEDS_PRICE` | 결제 합계가 이용권 금액을 초과 | `recordPassPayment`, `updatePassPayment` |
| `PAYMENT_REFUNDED` | 환불로 체크된 결제 기록은 정정하거나 삭제할 수 없음 (먼저 환불 체크를 해제) | `deletePassPayment`, `updatePassPayment` |
| `PAY_RATE_NOT_SET` | 정산 대상 수업 유형에 수당 단가가 설정되지 않음 | `createSettlement` |
| `REASSIGNMENT_FAILED` | 수업 인계 실패 (새 강사에게 배정할 수 없는 수업이 있음, `invalidParams`에 상세) | `reassignLessons` |
| `RESIGNATION_DATE_NOT_REACHED` | 퇴사일이 아직 오지 않아 퇴사 처리할 수 없음 | `completeResignation` |
| `RESIGNATION_NOT_REGISTERED` | 등록된 퇴사일이 없음 | `cancelResignation`, `completeResignation` |
| `ROOM_IN_USE` | 시작 전 수업이 있는 룸은 삭제할 수 없음 | `deleteRoom` |
| `ROOM_NAME_DUPLICATED` | 이미 있는 룸 이름 | `createRoom`, `updateRoom` |
| `ROOM_TIME_CONFLICT` | 같은 룸의 다른 수업과 시간이 겹침 | `createLesson`, `updateLesson` |
| `SETTLEMENT_PERIOD_OVERLAP` | 같은 강사의 다른 정산 기간과 겹침 | `createSettlement` |
| `UNRECORDED_ATTENDANCE_EXISTS` | 출결이 기록되지 않은 예약이 있어 정산할 수 없음 | `createSettlement` |
