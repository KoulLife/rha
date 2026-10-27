import * as api from "@/api/generated/api";
import type { DashboardUser } from "@/features/dashboard/model";

const typeCodes = (permissions: readonly string[], prefix: string) =>
  permissions.filter((code) => code.startsWith(prefix)).map((code) => code.slice(prefix.length));

/**
 * 수업 개설·수정 권한. 관리자는 모든 유형을 다루고, 강사는 관리자가 부여한
 * `LESSON_CREATE:{유형코드}`(개설)·`LESSON_UPDATE:{유형코드}`(수정·공개·취소) 유형만 다룬다.
 */
export function useLessonPermissions(user: DashboardUser) {
  const instructor = user.role === "INSTRUCTOR";
  const profile = api.useGetMyProfile({ query: { enabled: instructor } });
  const admin = user.role === "ADMIN";
  const permissions = instructor ? (profile.data?.permissions ?? []) : [];
  const createCodes = typeCodes(permissions, "LESSON_CREATE:");
  const updateCodes = typeCodes(permissions, "LESSON_UPDATE:");
  return {
    /** 개설할 수 있는 유형 코드. 관리자는 제한이 없어 undefined이다. */
    createCodes: admin ? undefined : createCodes,
    canCreate: admin || createCodes.length > 0,
    canUpdate: (lesson: Pick<api.Lesson, "lessonType">) =>
      admin || updateCodes.includes(lesson.lessonType.code),
    canManage: admin || createCodes.length > 0 || updateCodes.length > 0,
  };
}
