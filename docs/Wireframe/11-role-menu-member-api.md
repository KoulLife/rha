# 메뉴 11 역할별 메뉴 요약 — 회원

## 확인 화면

회원 로그인 → **내 메뉴 안내** 또는 홈 → **내 메뉴 전체 보기**.
본인 역할의 카드에서 ‘화면 열기’로 기존 화면에 진입한다. 새 회원 업무 화면을 복제하지 않았다.

| 카드 | 진입 화면 | 주요 기존 API |
| --- | --- | --- |
| 홈 | 회원 대시보드 | getMyProfile, getMyReservations, getMyPasses, getMyUnreadNotificationCount |
| 수업 목록 | 공개 수업·상세·예약 | getLessons, getLesson, createReservation |
| 내 이용권 | 본인 이용권 목록·상세 | getMyPasses. 상세는 선택 목록 Pass |
| 내 예약·대기·출결 | 본인 예약·취소·출결 내역 | getMyReservations, getReservation, cancelReservation. 대기 API 없음 |
| 알림 | 본인 알림·읽음·페이지 | getMyNotifications, getMyUnreadNotificationCount, readNotification, readAllNotifications |
| 내 계정 | 기존 본인 정보 예시 | getMyProfile. 아래 현재 범위 참조 |

메뉴 안내 자체는 getMyProfile의 role/name을 기준으로 구성하며 별도 API가 없다. 카드 클릭은 화면 이동이고 API는 진입한 화면의 기존 매핑을 따른다.

## 회원 범위

회원·강사 관리, 전체 이용권·결제, 운영 설정 저장, 강사 정산, 관리자 활동 이력 메뉴는 회원 안내에 넣지 않았다. 본인 예약/이용권/알림을 선택한다. 다른 사람의 ID를 넣는 것으로 조회 권한을 얻지 않는다.

## 현재 부족한 것

- 대기 신청·취소·승급과 예약 신청 후 강사 확정은 현재 API 미지원. 메뉴 6/10 MD에 추가 작업 기록.
- 회원 내 계정은 기존 기본 정보·일반 예시 모달 수준이다. 실제 정보 저장은 PATCH /members/me(updateMyProfile), 비밀번호 변경은 PUT /members/me/password(changeMyPassword), 탈퇴는 DELETE /members/me(withdrawMyAccount)가 기준이나 그 정규 폼·서버 연동은 이번 메뉴 요약의 작업 범위가 아니다.
- 실제 비밀번호 변경은 현재 비밀번호·새 비밀번호 입력 및 성공 후 재로그인, 탈퇴는 미래 예약 확인·이력 보존·토큰 만료가 필요하다. 일반 예시 모달을 이 연동이 완료된 것으로 표시하지 않는다.

## 확인 순서

회원 → 내 메뉴 안내 → 수업 목록/내 이용권/내 예약/알림의 화면 열기 → 해당 화면에서 기존 버튼 사용. 홈으로 버튼과 왼쪽 내 메뉴 안내로 돌아올 수 있다.

API·ERD 변경 없음. 문법 검사만 수행했으며 브라우저 동작 테스트·실제 API 연결은 수행하지 않았다.
