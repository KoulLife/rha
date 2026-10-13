import type { UserRole } from "../../config/access";

export type DashboardUser = { id: number; name: string; role: UserRole };
export type DashboardLesson = {
  id: number; title: string; instructorId: number; instructor: string; room: string;
  startAt: string; endAt: string; capacity: number; status: "PUBLISHED" | "CANCELED";
};
export type DashboardReservation = {
  id: number; lessonId: number; memberId: number; member: string;
  status: "RESERVED" | "ATTENDED" | "NO_SHOW" | "CANCELED";
};
export type DashboardAbsence = {
  id: number; instructorId: number; instructor: string; from: string; to: string; reason: string;
  status: "REQUESTED" | "APPROVED" | "REJECTED" | "CANCELED";
};
export type DashboardNotification = { id: number; recipientId: number; title: string; message: string; category?: string; createdAt: string; read: boolean };
export type DashboardData = {
  lessons: DashboardLesson[]; reservations: DashboardReservation[];
  absences: DashboardAbsence[]; notifications: DashboardNotification[];
};

export const roleLabels: Record<UserRole, string> = { MEMBER: "회원", INSTRUCTOR: "강사", ADMIN: "관리자" };
export function seoulDate(value: string) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
}
export function seoulTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date(value));
}
export function getDashboardSnapshot(data: DashboardData, user: DashboardUser, now: string) {
  const ownReservations = data.reservations.filter(item => item.memberId === user.id && item.status !== "CANCELED");
  const todayLessons = data.lessons.filter(lesson =>
    seoulDate(lesson.startAt) === seoulDate(now) && lesson.status !== "CANCELED" &&
    (user.role === "ADMIN" || (user.role === "INSTRUCTOR" ? lesson.instructorId === user.id : ownReservations.some(item => item.lessonId === lesson.id))),
  ).sort((a, b) => Date.parse(a.startAt) - Date.parse(b.startAt));
  const lessonIds = new Set(todayLessons.map(lesson => lesson.id));
  const reservations = data.reservations.filter(item => lessonIds.has(item.lessonId) && item.status !== "CANCELED" && (user.role !== "MEMBER" || item.memberId === user.id));
  const attended = reservations.filter(item => item.status === "ATTENDED").length;
  const noShow = reservations.filter(item => item.status === "NO_SHOW").length;
  const attendancePending = reservations.filter(item => item.status === "RESERVED" && todayLessons.some(lesson => lesson.id === item.lessonId && Date.parse(lesson.endAt) <= Date.parse(now))).length;
  const absences = user.role === "MEMBER" ? [] : data.absences.filter(item => item.status === "REQUESTED" && (user.role === "ADMIN" || item.instructorId === user.id));
  const notifications = data.notifications.filter(item => item.recipientId === user.id).sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
  return { todayLessons, reservations, attended, noShow, attendancePending, absences, notifications, unread: notifications.filter(item => !item.read).length };
}
