import type { DashboardData } from "../dashboard/model";
import type { InstructorState } from "./model";

export function withInstructorPreview(data: DashboardData): DashboardData {
  return {
    ...data,
    lessons: [...data.lessons,
      { id: 109, title: "모닝 리포머", instructorId: 2, instructor: "김강사", room: "룸 B", startAt: "2026-10-12T01:00:00Z", endAt: "2026-10-12T02:00:00Z", capacity: 5, status: "PUBLISHED" },
      { id: 110, title: "리포머 밸런스", instructorId: 2, instructor: "김강사", room: "룸 A", startAt: "2026-10-09T05:00:00Z", endAt: "2026-10-09T06:00:00Z", capacity: 5, status: "PUBLISHED" },
    ],
    reservations: [...data.reservations,
      { id: 1091, lessonId: 109, memberId: 10, member: "이수진", status: "ATTENDED" },
      { id: 1092, lessonId: 109, memberId: 11, member: "박민호", status: "NO_SHOW" },
      { id: 1093, lessonId: 109, memberId: 12, member: "최다은", status: "CANCELED" },
      { id: 1101, lessonId: 110, memberId: 10, member: "이수진", status: "ATTENDED" },
    ],
    absences: [...data.absences,
      { id: 203, instructorId: 2, instructor: "김강사", from: "2026-10-22", to: "2026-10-22", reason: "정기 검진", status: "APPROVED" },
      { id: 204, instructorId: 2, instructor: "김강사", from: "2026-10-15", to: "2026-10-15", reason: "외부 교육 참석", status: "REJECTED" },
      { id: 205, instructorId: 2, instructor: "김강사", from: "2026-10-19", to: "2026-10-19", reason: "개인 일정 변경", status: "CANCELED" },
    ],
  };
}

export function initialInstructorState(): InstructorState {
  return { changes: [], absenceDetails: {
    201: { startAt: "2026-10-16T00:00:00Z", endAt: "2026-10-16T09:00:00Z", createdAt: "2026-10-12T07:00:00Z" },
    203: { startAt: "2026-10-22T00:00:00Z", endAt: "2026-10-22T03:00:00Z", createdAt: "2026-10-10T01:00:00Z", processedAt: "2026-10-11T01:00:00Z" },
    204: { startAt: "2026-10-15T04:00:00Z", endAt: "2026-10-15T09:00:00Z", createdAt: "2026-10-09T01:00:00Z", processedAt: "2026-10-10T01:00:00Z", rejectionReason: "담당 수업의 대체 강사 배정이 어려워 반려되었습니다." },
    205: { startAt: "2026-10-19T00:00:00Z", endAt: "2026-10-19T03:00:00Z", createdAt: "2026-10-08T01:00:00Z", processedAt: "2026-10-09T01:00:00Z" },
  } };
}

export const reservationPassNames: Record<number, string> = { 501: "필라테스 10회권", 1010: "필라테스 10회권", 1011: "리포머 20회권", 1012: "필라테스 10회권", 1070: "리포머 20회권", 1071: "필라테스 10회권", 1072: "필라테스 10회권", 1091: "필라테스 10회권", 1092: "리포머 20회권", 1093: "필라테스 10회권", 1101: "필라테스 10회권" };
