# 메뉴 11 역할별 메뉴 요약 — 관리자

## 확인 화면

관리자 로그인 → **내 메뉴 안내** 또는 홈 → **내 메뉴 전체 보기**.
전체 관리 메뉴 카드에서 기존 화면으로 이동한다. 주요 관리 업무 버튼으로 강사 관리·정산·이용권/결제도 바로 연다.

| 카드 | 기존 업무 화면 | 주요 기존 API |
| --- | --- | --- |
| 홈 | 전체 수업·예약·출결·알림 | getLessons/getReservations/getMyUnreadNotificationCount |
| 회원 관리 | 목록·상세·정보·상태 | getMembers/getMember/updateMember/changeMemberStatus |
| 강사 관리 | 계정·권한·부재·퇴사·인계 | getMembers/createMember/getMember, 권한·부재·퇴사 관련 API(메뉴 5 MD) |
| 수업 운영 | 회차·공개·취소·예약·출결 | getLessons/getLesson/createLesson/updateLesson/changeLessonStatus/cancelLesson, getReservations/confirmAttendance |
| 승인 신청 대기 | 기존 승인형 신청·대기 예시 | 현재 해당 상태/처리 API 없음 |
| 회원 이용권 | 발급·조정·홀딩·결제 기록 | getPasses/getPass/createPass/updatePass, 홀딩·결제 API(메뉴 7 MD) |
| 운영 설정 | 상품·유형·룸·시간·정책 | getPassProducts/createPassProduct/updatePassProduct, 유형/룸/business-hours/reservation-policy API(메뉴 8 MD) |
| 강사 수당·정산 | 단가·계산·검토·확정·외부 지급 | getPayRates/updatePayRates/getSettlements/createSettlement/getSettlement/requestSettlementReview/confirmSettlement/recordSettlementPayment/discardSettlement |
| 알림 | 관리자 본인의 받은 알림 | getMyNotifications/readNotification/readAllNotifications/getMyUnreadNotificationCount |
| 활동 이력 | 전체 행위·대상·변경 전후 | getActivityLogs/getActivityLogActions |

메뉴 진입은 서버 변경 요청이 아니다. 해당 화면의 실제 요청·권한·실패 처리는 이전 메뉴별 MD가 기준이다. 역할별 메뉴 안내에는 별도 API를 추가하지 않았다.

## 대표·상위 관리자 권한 후보

메뉴 구조에 후보로 적힌 부재 승인·반려, 정산 확정, 퇴사자 정산, 전체 권한 설정, 결제·환불 기록 관리는 현재 ADMIN 업무다.

현재 MemberRole은 MEMBER/INSTRUCTOR/ADMIN이고 별도 대표·상위 관리자 역할은 없다. 기존 permission도 MEMBER_READ와 유형별 개설/수정 중심이며 위 주요 관리자 업무의 전용 permission이 정의되어 있지 않다. 새로운 대표 로그인·권한 스위치·승인자를 이번 화면에서 만들어 실제로 구분된 것처럼 표시하지 않았다.

담당자별 승인/조회/변경 범위를 나누려면 역할 또는 관리자 permission, 서버 정책·승인 이력·권한 부여 범위를 계약에 먼저 반영해야 한다. 기존 관리자 업무 버튼으로 해당 화면에 진입하도록 안내한다.

## 현재 계약 부족 사항

승인 신청 대기·반복 일정·누적 재등록·날짜별 휴무/일괄 보상·판매 중지/룸 비활성·정산 확정 후 정정·예약/대기 알림은 이전 메뉴별 MD에 정리되어 있다. 메뉴 카드가 있다고 해당 API가 구현된 것은 아니다.

## 확인 순서

관리자 → 내 메뉴 안내 → 회원/강사/수업/이용권/운영 설정/정산/알림/활동 이력의 화면 열기. 강사 관리에서 권한 변경 후 강사 메뉴 안내를 열어 표시 범위 확인. 역할별 화면 비교는 기존 로그인 미리보기에서 수행한다.

API·ERD 변경 없음. 문법 검사만 수행했으며 브라우저 테스트·실제 API 연결은 수행하지 않았다.
