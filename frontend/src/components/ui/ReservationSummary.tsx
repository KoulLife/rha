import * as api from "@/api/generated/api";
import { AttendanceCount } from "./StatusBadge";
import "./reservation-summary.css";

export function ReservationSummary({
  reserved,
  attended,
  noShow,
}: {
  reserved: number;
  attended: number;
  noShow: number;
}) {
  return (
    <div className="reservation-summary">
      <div
        className="reservation-summary-counts"
        aria-label="수업 예약·출결 인원"
      >
        <span>
          예약 <strong>{reserved}명</strong>
        </span>
        <AttendanceCount
          status="ATTENDED"
          count={attended}
          unit="명"
        />
        <AttendanceCount
          status="NO_SHOW"
          count={noShow}
          unit="명"
        />
      </div>
    </div>
  );
}

export function LessonReservationSummary({ lessonId }: { lessonId: number }) {
  const reserved = api.useGetLessonReservations(lessonId, { status: "RESERVED", size: 1 });
  const attended = api.useGetLessonReservations(lessonId, { status: "ATTENDED", size: 1 });
  const noShow = api.useGetLessonReservations(lessonId, { status: "NO_SHOW", size: 1 });
  const queries = [reserved, attended, noShow];
  const count = (query: typeof reserved) => query.data?.pageInfo.totalElements ?? 0;

  if (queries.some((query) => query.isPending))
    return (
      <span
        className="menu-muted"
        role="status"
      >
        집계 중
      </span>
    );
  if (queries.some((query) => query.isError))
    return <span className="menu-muted">집계를 불러오지 못했습니다</span>;
  return (
    <ReservationSummary
      reserved={count(reserved) + count(attended) + count(noShow)}
      attended={count(attended)}
      noShow={count(noShow)}
    />
  );
}
