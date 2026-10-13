import type { DashboardData, DashboardReservation } from "./model";
import { notifications } from "../../mocks/member";

// 기존 회원 화면과 같은 수업/예약 ID를 사용한다. API 응답을 대체하는 화면 검토용 데이터다.
export const dashboardPreviewTime = "2026-10-13T03:00:00Z";
const roster = (lessonId: number, memberIds: number[], statuses: DashboardReservation["status"][]): DashboardReservation[] => memberIds.map((memberId, index) => ({
  id: lessonId * 10 + index, lessonId, memberId, member: `회원 ${memberId}`, status: statuses[index] ?? "RESERVED",
}));
export const dashboardPreview: DashboardData = {
  lessons: [
    { id: 101, title: "기구 필라테스", instructorId: 2, instructor: "김강사", room: "룸 A", startAt: "2026-10-13T01:00:00Z", endAt: "2026-10-13T02:00:00Z", capacity: 5, status: "PUBLISHED" },
    { id: 102, title: "리포머 그룹", instructorId: 4, instructor: "박강사", room: "룸 B", startAt: "2026-10-13T05:00:00Z", endAt: "2026-10-13T06:00:00Z", capacity: 5, status: "PUBLISHED" },
    { id: 107, title: "코어 밸런스", instructorId: 2, instructor: "김강사", room: "룸 A", startAt: "2026-10-13T09:00:00Z", endAt: "2026-10-13T10:00:00Z", capacity: 5, status: "PUBLISHED" },
    { id: 103, title: "개인 레슨", instructorId: 2, instructor: "김강사", room: "룸 A", startAt: "2026-10-14T10:00:00Z", endAt: "2026-10-14T11:00:00Z", capacity: 1, status: "PUBLISHED" },
  ],
  reservations: [
    { id: 501, lessonId: 101, memberId: 1, member: "홍길동", status: "ATTENDED" },
    { id: 502, lessonId: 102, memberId: 1, member: "홍길동", status: "RESERVED" },
    ...roster(101, [5, 6, 7], ["ATTENDED", "NO_SHOW", "RESERVED"]),
    ...roster(102, [5, 6, 8, 9], []),
    ...roster(107, [6, 7, 8], []),
  ],
  absences: [
    { id: 201, instructorId: 2, instructor: "김강사", from: "2026-10-16", to: "2026-10-16", reason: "개인 일정", status: "REQUESTED" },
    { id: 202, instructorId: 4, instructor: "박강사", from: "2026-10-20", to: "2026-10-21", reason: "교육 참석", status: "REQUESTED" },
  ],
  notifications: [
    ...notifications.map(item => ({ id: item.id, recipientId: 1, title: item.title, message: item.message, category: item.category, createdAt: new Date(`${item.time.replace(/\./g, "-").replace(" ", "T")}:00+09:00`).toISOString(), read: item.read })),
    { id: 11, recipientId: 2, title: "기구 필라테스 출결을 확인해 주세요.", message: "10:00 수업에서 아직 출결을 기록하지 않은 예약이 1건 있습니다.", createdAt: "2026-10-13T02:10:00Z", read: false },
    { id: 12, recipientId: 2, title: "부재 신청이 접수되었습니다.", message: "10월 16일 부재 신청이 관리자 승인 대기 중입니다.", createdAt: "2026-10-12T07:00:00Z", read: false },
    { id: 13, recipientId: 2, category: "예약", title: "코어 밸런스에 새 예약이 있습니다.", message: "오늘 18:00 코어 밸런스 수업에 회원 8님의 예약이 접수되었습니다.", createdAt: "2026-10-13T01:40:00Z", read: false },
    { id: 14, recipientId: 2, category: "예약 취소", title: "개인 레슨 예약이 취소되었습니다.", message: "10월 14일 19:00 개인 레슨 예약이 취소되었습니다. 수업 예약 현황을 확인해 주세요.", createdAt: "2026-10-13T00:30:00Z", read: false },
    { id: 15, recipientId: 2, category: "수업 변경", title: "코어 밸런스 수업 룸이 변경되었습니다.", message: "오늘 18:00 코어 밸런스 수업 장소가 룸 B에서 룸 A로 변경되었습니다.", createdAt: "2026-10-12T08:00:00Z", read: true },
    { id: 16, recipientId: 2, category: "정산", title: "9월 수업 수당 지급이 완료되었습니다.", message: "9월 수업 수당 지급이 완료되었습니다. 정산 내역에서 지급 내용을 확인해 주세요.", createdAt: "2026-10-12T01:00:00Z", read: true },
    { id: 21, recipientId: 3, title: "새로운 강사 부재 신청이 있습니다.", message: "김강사님의 10월 16일 부재 신청을 확인해 주세요.", createdAt: "2026-10-13T00:00:00Z", read: false },
    { id: 22, recipientId: 3, title: "오늘 수업 출결을 확인해 주세요.", message: "종료된 수업의 출결 미처리 예약이 1건 있습니다.", createdAt: "2026-10-13T02:10:00Z", read: false },
    { id: 23, recipientId: 3, category: "예약", title: "리포머 그룹의 예약 정원이 찼습니다.", message: "오늘 14:00 리포머 그룹 수업의 예약이 5명으로 정원에 도달했습니다.", createdAt: "2026-10-13T01:50:00Z", read: false },
    { id: 24, recipientId: 3, category: "예약 취소", title: "개인 레슨 예약 취소가 접수되었습니다.", message: "10월 14일 19:00 개인 레슨 예약이 취소되었습니다. 예약 내역에서 변경 사항을 확인해 주세요.", createdAt: "2026-10-13T00:30:00Z", read: false },
    { id: 25, recipientId: 3, category: "수업 변경", title: "코어 밸런스 수업 룸이 변경되었습니다.", message: "김강사님의 오늘 18:00 코어 밸런스 수업 장소가 룸 A로 변경되었습니다.", createdAt: "2026-10-12T08:00:00Z", read: true },
    { id: 26, recipientId: 3, category: "정산", title: "9월 강사 수당 지급이 완료되었습니다.", message: "9월 강사 수당 지급 처리가 완료되었습니다. 정산 내역에서 강사별 지급 내용을 확인해 주세요.", createdAt: "2026-10-12T01:00:00Z", read: true },
  ],
};
