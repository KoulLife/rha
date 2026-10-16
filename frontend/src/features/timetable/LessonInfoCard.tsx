import type { ReactNode } from "react";
import type { Lesson } from "@/api/generated/api";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Facts, dateTime } from "@/features/admin/AdminUI";
import "./timetable.css";

export function LessonInfoCard({ lesson, action }: { lesson: Lesson; action?: ReactNode }) {
  return (
    <MenuPanel
      title="수업 정보"
      className="lesson-detail-card"
      action={action}
    >
      <Facts
        items={[
          ["수업명", lesson.title],
          ["수업 유형", lesson.lessonType.name],
          [
            "상태",
            <StatusBadge
              domain="lesson"
              status={lesson.status}
            />,
          ],
          ["담당 강사", lesson.instructor.name],
          ["룸", lesson.room.name],
          ["시작", dateTime(lesson.startAt)],
          ["종료", dateTime(lesson.endAt)],
          ["예약 / 정원", `${lesson.reservedCount} / ${lesson.capacity}명`],
        ]}
      />
      {lesson.publishAt && <p className="menu-note">공개 예정: {dateTime(lesson.publishAt)}</p>}
      {lesson.cancelReason && <p className="menu-note">취소 사유: {lesson.cancelReason}</p>}
    </MenuPanel>
  );
}
