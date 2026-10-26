import { useCallback, useEffect, useState } from "react";
import type { Screen } from "@/app/routes";
import type { Lesson } from "@/api/generated/api";
import { todayInSeoul } from "@/api/runtime";
import { QueryState, dayStart, dayEnd } from "../admin/AdminUI";
import { useAllLessons, useAllAbsences, useAllLessonReservations } from "../admin/queries";
import { DashboardPage } from "../dashboard/DashboardPage";
import type { DashboardUser } from "../dashboard/model";
import { instructorDashboardData } from "./apiData";

type Roster = Pick<
  ReturnType<typeof useAllLessonReservations>,
  "data" | "isPending" | "isError" | "error" | "refetch"
>;

export function InstructorHomePage({
  user,
  onNavigate,
  empty = false,
}: {
  user: DashboardUser;
  onNavigate: (screen: Screen, id?: number) => void;
  empty?: boolean;
}) {
  const lessons = useAllLessons(
    {
      instructorId: user.id,
      from: dayStart(todayInSeoul()),
      to: dayEnd(todayInSeoul()),
      order: "ASC",
    },
    !empty,
  );
  const absences = useAllAbsences({ instructorId: user.id, status: "REQUESTED" }, !empty);
  const ownLessons = (lessons.data?.content ?? []).filter(
    (lesson) => lesson.instructor.id === user.id && lesson.status !== "CANCELED",
  );
  const [rosters, setRosters] = useState<Record<number, Roster>>({});
  const report = useCallback((id: number, result: Roster) => {
    setRosters((previous) => ({ ...previous, [id]: result }));
  }, []);
  const queries = [
    lessons,
    absences,
    ...ownLessons.flatMap((lesson) => (rosters[lesson.id] ? [rosters[lesson.id]] : [])),
  ];
  const error = queries.find((query) => query.isError);
  const data = instructorDashboardData(
    user.id,
    ownLessons,
    ownLessons.flatMap((lesson) => rosters[lesson.id]?.data?.content ?? []),
    absences.data?.content ?? [],
  );
  return (
    <>
      {!empty &&
        ownLessons.map((lesson) => (
          <LoadRoster
            key={lesson.id}
            lesson={lesson}
            report={report}
          />
        ))}
      <QueryState
        query={{
          isPending:
            !empty &&
            !error &&
            (queries.some((query) => query.isPending) ||
              ownLessons.some((lesson) => !rosters[lesson.id])),
          isError: !empty && !!error,
          error: error?.error,
          refetch: () => Promise.all(queries.map((query) => query.refetch())),
        }}
      >
        <DashboardPage
          user={user}
          data={data}
          empty={empty}
          onNavigate={onNavigate}
        />
      </QueryState>
    </>
  );
}

// 담당 수업별 생성 훅을 사용하므로 MEMBER_READ 권한이 필요하지 않다.
function LoadRoster({
  lesson,
  report,
}: {
  lesson: Lesson;
  report: (id: number, result: Roster) => void;
}) {
  const { data, isPending, isError, error, refetch } = useAllLessonReservations(lesson.id);
  useEffect(() => {
    report(lesson.id, { data, isPending, isError, error, refetch });
  }, [lesson.id, data, isPending, isError, error, refetch, report]);
  return null;
}
