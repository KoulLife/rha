# 메뉴 11 역할별 메뉴 요약 — 강사

## 확인 화면

강사 로그인 → **내 메뉴 안내** 또는 홈 → **내 메뉴 전체 보기**.
역할 메뉴 카드와 현재 수업 유형별 개설/수정 가능 범위를 표시한다.

| 카드 | 진입 화면 | 주요 기존 API |
| --- | --- | --- |
| 홈 | 담당 수업 대시보드 | getMyProfile, getLessons, getLessonReservations, getMyUnreadNotificationCount |
| 수업 | 시간표·목록에서 수업 선택, 담당 예약자·출결, 권한별 개설/수정 | getLessons/getLesson/getLessonReservations/confirmAttendance, createLesson/updateLesson/changeLessonStatus/cancelLesson |
| 승인 신청 대기 | 기존 신청·확정·대기 흐름 예시 | 현행 예약 승인·대기 API 없음. 별도 회의 데이터 |
| 부재 신청 | 본인 신청·조회·철회 | getAbsences/getAbsence/createAbsence/cancelAbsence |
| 내 프로필 | 본인 프로필·보유 권한 | getMyProfile/updateMyProfile |
| 알림 | 본인 알림·읽음·관련 부재 | getMyNotifications/readNotification/readAllNotifications/getMyUnreadNotificationCount |

## 권한 표시

getMyProfile.permissions와 getLessonTypes의 현재 유형을 사용한다. LESSON_CREATE:{code}와 LESSON_UPDATE:{code}는 별도다.

- 개설: 활성 유형+해당 개설 권한.
- 수정: 해당 수정 권한, 기존 수업의 운영 가능 상태를 추가 확인.
- 담당 예약자·출결: 본인 담당 수업. 개설/수정 권한을 받았다고 다른 강사의 예약자·출결 처리 범위까지 넓어지지 않는다.

예시 김강사는 초기 개설/수정 권한 없음으로 표시한다. 관리자 → 강사 관리 → 김강사 권한 부여 → 강사로 다시 로그인 → 내 메뉴 안내에서 가능 유형을 확인한다. 비활성 유형의 기존 수정 권한은 유지될 수 있으나 신규 개설은 제외한다.

## 현재 없는 기능·계약 차이

승인·대기 및 출결 시 차감은 회의 흐름에 맞춰 API/ERD를 수정할 작업이다. 출결 정정 요청→승인 과정은 기존 직접 confirmAttendance와 다르다. 강사 본인 수당/정산 조회·검토 요청도 현재 관리자 전용 계약과 권한 차이가 있으며 메뉴 9 MD에 정리했다.

회원 관리·전체 이용권/결제·센터 설정 저장·관리자 활동 이력·정산 관리 카드를 강사에게 추가하지 않았다. 안내 화면에서 역할/권한을 변경하는 버튼을 제공하지 않는다.

API·ERD 변경 없음. 문법 검사만 수행했으며 실행 테스트·브라우저 검증·서버 연결은 수행하지 않았다.
