import type { DashboardAbsence, DashboardData, DashboardReservation, DashboardUser } from "../dashboard/model";

export type AttendanceStatus = "ATTENDED" | "NO_SHOW";
export type AbsenceDetail = { startAt: string; endAt: string; createdAt: string; processedAt?: string; rejectionReason?: string };
export type AttendanceChange = { id: number; reservationId: number; before: DashboardReservation["status"]; after: AttendanceStatus; reason: string; at: string };
export type InstructorState = { absenceDetails: Record<number, AbsenceDetail>; changes: AttendanceChange[] };

// 정산 여부는 현재 응답에 없는 값이므로 UI 검토용 메타데이터로만 관리한다.
export const settledLessonIds: ReadonlySet<number> = new Set([110]);

export function attendanceRestriction(data: DashboardData, user: DashboardUser, reservationId: number, now: string): string | null {
  const reservation = data.reservations.find(item => item.id === reservationId);
  const lesson = data.lessons.find(item => item.id === reservation?.lessonId);
  if (!reservation || !lesson || user.role !== "INSTRUCTOR" || lesson.instructorId !== user.id) return "본인 담당 수업만 처리할 수 있습니다.";
  if (reservation.status === "CANCELED" || lesson.status === "CANCELED") return "취소된 예약 또는 수업은 처리할 수 없습니다.";
  if (Date.parse(lesson.startAt) > Date.parse(now)) return "수업 시작 후 처리할 수 있습니다.";
  if (settledLessonIds.has(lesson.id)) return "정산에 포함된 수업입니다. 관리자에게 정산 확인을 요청해 주세요.";
  return null;
}

export function absenceRestriction(absences: DashboardAbsence[], details: Record<number, AbsenceDetail>, userId: number, startAt: string, endAt: string, now: string): string | null {
  const start = Date.parse(startAt), end = Date.parse(endAt);
  if (!Number.isFinite(start) || !Number.isFinite(end)) return "시작과 종료 일시를 입력해 주세요.";
  if (start <= Date.parse(now)) return "시작 일시는 현재 시각 이후로 선택해 주세요.";
  if (end <= start) return "종료 일시는 시작 일시 이후여야 합니다.";
  const overlap = absences.some(item => {
    const detail = details[item.id];
    return item.instructorId === userId && ["REQUESTED", "APPROVED"].includes(item.status) && detail && start < Date.parse(detail.endAt) && end > Date.parse(detail.startAt);
  });
  return overlap ? "이미 신청하거나 승인된 부재 기간과 겹칩니다." : null;
}

export function canWithdrawAbsence(absence: DashboardAbsence, user: DashboardUser) {
  return user.role === "INSTRUCTOR" && absence.instructorId === user.id && absence.status === "REQUESTED";
}
