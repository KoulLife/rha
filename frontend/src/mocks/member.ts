import type { Permission, UserRole } from "../config/access";

export const currentUser: { role: UserRole; permissions: ReadonlySet<Permission> } = {
  role: "MEMBER",
  permissions: new Set<Permission>([
    "lesson.read",
    "reservation.create",
    "reservation.cancel",
    "waitlist.create",
    "waitlist.cancel",
    "pass.read",
    "reservation.read",
    "profile.read",
    "profile.update",
    "password.change",
    "notification.read",
  ]),
};

export const lessons = [
  { id: 101, date: "10.12", time: "10:00–11:00", type: "기구 필라테스", teacher: "김강사", room: "룸 A", booked: "4 / 5명", status: "예약 가능" },
  { id: 102, date: "10.12", time: "14:00–15:00", type: "리포머 그룹", teacher: "박강사", room: "룸 B", booked: "5 / 5명", status: "대기 신청 가능" },
  { id: 103, date: "10.14", time: "19:00–20:00", type: "개인 레슨", teacher: "김강사", room: "룸 A", booked: "1 / 1명", status: "예약 마감" },
];

export const passes = [
  { id: 1001, name: "필라테스 10회권", remaining: 7, total: 10, start: "2026.10.01", end: "2026.12.29", teacher: "김강사", state: "사용 중" },
  { id: 1002, name: "개인 레슨 5회권", remaining: 2, total: 5, start: "2026.09.15", end: "2026.11.15", teacher: "박강사", state: "사용 중" },
];

export const reservations = [
  { id: 501, lesson: "기구 필라테스", date: "2026.10.12 10:00", teacher: "김강사", state: "출석 완료", pass: "필라테스 10회권" },
  { id: 502, lesson: "리포머 그룹", date: "2026.10.12 14:00", teacher: "박강사", state: "예약 완료", pass: "필라테스 10회권" },
];

export const notifications = [
  { id: 1, title: "오늘 14:00 수업 예약이 확정되었습니다.", category: "예약 확정", time: "2026.10.12 09:15", read: false, message: "리포머 그룹 수업 예약이 확정되었습니다." },
  { id: 2, title: "이용권 잔여 횟수는 7회입니다.", category: "이용권", time: "2026.10.11 18:02", read: false, message: "필라테스 10회권의 잔여 횟수를 확인해 주세요." },
  { id: 3, title: "수업 출석이 기록되었습니다.", category: "출결", time: "2026.10.10 20:10", read: true, message: "기구 필라테스 수업의 출석이 기록되었습니다." },
];
