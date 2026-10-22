import type { Absence, Lesson, Reservation } from "@/api/generated/api";
import { seoulDate, type DashboardData } from "../dashboard/model";
import type { InstructorState } from "./model";

export function instructorDashboardData(
  instructorId: number,
  lessons: Lesson[],
  reservations: Reservation[],
  absences: Absence[],
): DashboardData {
  const ownLessons = lessons.filter((lesson) => lesson.instructor.id === instructorId);
  const ids = new Set(ownLessons.map((lesson) => lesson.id));
  return {
    lessons: ownLessons.map((lesson) => ({
      id: lesson.id,
      title: lesson.title,
      instructorId: lesson.instructor.id,
      instructor: lesson.instructor.name,
      room: lesson.room.name,
      startAt: lesson.startAt,
      endAt: lesson.endAt,
      capacity: lesson.capacity,
      status: lesson.status,
    })),
    reservations: reservations
      .filter((row) => ids.has(row.lesson.id))
      .map((row) => ({
        id: row.id,
        lessonId: row.lesson.id,
        memberId: row.member.id,
        member: row.member.name,
        status: row.status,
      })),
    absences: absences
      .filter((row) => row.instructor.id === instructorId)
      .map((row) => ({
        id: row.id,
        instructorId: row.instructor.id,
        instructor: row.instructor.name,
        from: seoulDate(row.startAt),
        to: seoulDate(row.endAt),
        reason: row.reason ?? "",
        status: row.status,
      })),
    notifications: [],
  };
}

export function instructorAbsenceState(absences: Absence[]): InstructorState {
  return {
    absenceDetails: Object.fromEntries(
      absences.map((row) => [
        row.id,
        {
          startAt: row.startAt,
          endAt: row.endAt,
          createdAt: row.createdAt,
          processedAt: row.processedAt ?? undefined,
          rejectionReason: row.rejectReason ?? undefined,
        },
      ]),
    ),
    changes: [],
  };
}
