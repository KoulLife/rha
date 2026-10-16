import type { ReactNode } from "react";
import type { Lesson } from "@/api/generated/api";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { getStatusLabel } from "@/components/ui/StatusBadge";
import { dateInSeoul, timeInSeoul } from "./model";

export function LessonSelectionCard({
  lesson,
  emptyMessage = "수업을 선택해 주세요.",
  children,
}: {
  lesson?: Lesson;
  emptyMessage?: string;
  children?: ReactNode;
}) {
  return (
    <MenuPanel title="선택한 수업">
      {lesson ? (
        <div className="menu-stack">
          <h3>{lesson.title}</h3>
          <dl className="menu-facts">
            {[
              ["상태", getStatusLabel({ domain: "lesson", status: lesson.status })],
              ["날짜", dateInSeoul(lesson.startAt)],
              [
                "시간",
                `${timeInSeoul(lesson.startAt)} – ${dateInSeoul(lesson.endAt) !== dateInSeoul(lesson.startAt) ? `${dateInSeoul(lesson.endAt)} ` : ""}${timeInSeoul(lesson.endAt)}`,
              ],
              ["수업 유형", lesson.lessonType.name],
              ["담당 강사", lesson.instructor.name],
              ["룸", lesson.room.name],
              ["예약 / 정원", `${lesson.reservedCount} / ${lesson.capacity}명`],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
          {children}
        </div>
      ) : (
        <p className="timetable-empty-preview">{emptyMessage}</p>
      )}
    </MenuPanel>
  );
}
