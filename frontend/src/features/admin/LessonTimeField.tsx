import { useState } from "react";
import * as api from "@/api/generated/api";
import { Button } from "@/components/ui/button";
import { DatePicker } from "@/components/ui/date-picker";
import { Dialog } from "@/components/ui/Dialog";
import { dateInSeoul } from "@/features/timetable/model";
import { useAllAbsences, useAllLessons } from "./queries";
import type { CustomFieldProps, FormField, Values } from "./adminUtils";
import {
  blockedPeriods,
  endSlots,
  seoulTime,
  startSlots,
  type OpenHours,
  type Slot,
} from "./lessonSlots";
import "./lesson-time-field.css";

const weekdays: api.DayOfWeek[] = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];
const dayOfWeek = (date: string) => weekdays[new Date(`${date}T12:00:00+09:00`).getUTCDay()];
const split = (value: string) => {
  const [date = "", time = ""] = value.split("T");
  return { date, time };
};

/**
 * 수업의 시작·종료 시각("YYYY-MM-DDTHH:mm", 서울)을 함께 고르는 칸. 시작 시각은 폼의 `startAt`, 종료 시각은 `endAt`에 둔다.
 * 영업시간과 룸·강사 일정을 반영해 고를 수 없는 시각을 이유와 함께 흐리게 보여준다.
 */
export function lessonTimeField({
  excludeLessonId,
  canSeeInstructorSchedule,
  disabled,
}: {
  excludeLessonId?: number;
  /** 강사 가능시간·부재·퇴사일 조회 권한 (관리자). 없으면 그 조건은 서버가 확인한다. */
  canSeeInstructorSchedule: boolean;
  disabled?: boolean;
}): FormField {
  return {
    name: "startAt",
    label: "수업 일시 (서울)",
    required: true,
    disabled,
    render: (field) => (
      <LessonTimeField
        {...field}
        excludeLessonId={excludeLessonId}
        canSeeInstructorSchedule={canSeeInstructorSchedule}
      />
    ),
  };
}

/** 저장 전에 시작·종료 시각을 모두 골랐는지 확인한다. 날짜만 고른 상태는 "YYYY-MM-DDT"이다. */
export function assertLessonTime(values: Values) {
  const complete = (value: unknown) => /T\d\d:\d\d$/.test(String(value ?? ""));
  if (!complete(values.startAt)) throw new Error("시작 시각을 선택해 주세요.");
  if (!complete(values.endAt)) throw new Error("종료 시각을 선택해 주세요.");
}

function LessonTimeField({
  id,
  value,
  onChange,
  onBlur,
  values,
  setValue,
  disabled,
  invalid,
  describedBy,
  excludeLessonId,
  canSeeInstructorSchedule,
}: CustomFieldProps & { excludeLessonId?: number; canSeeInstructorSchedule: boolean }) {
  const [picking, setPicking] = useState<"start" | "end" | null>(null);
  const start = split(value);
  const end = split(String(values.endAt ?? ""));
  const date = start.date || end.date;
  const roomId = Number(values.roomId) || undefined;
  const instructorId = Number(values.instructorId) || undefined;
  const instructorQuery = canSeeInstructorSchedule && !!instructorId && !!date;

  const businessHours = api.useGetBusinessHours();
  const hoursOf = (day: string): OpenHours => {
    const row = businessHours.data?.businessHours.find((h) => h.dayOfWeek === dayOfWeek(day));
    return row && !row.closed && row.openTime && row.closeTime
      ? { open: row.openTime, close: row.closeTime }
      : undefined;
  };
  const hours = date ? hoursOf(date) : undefined;
  const dayRange = date
    ? {
        from: new Date(seoulTime(date, "00:00")).toISOString(),
        to: new Date(seoulTime(date, "00:00") + 86400000).toISOString(),
      }
    : {};
  const lessons = useAllLessons(dayRange, !!date);
  const availability = api.useGetInstructorAvailability(instructorId ?? 0, {
    query: { enabled: instructorQuery },
  });
  const absences = useAllAbsences(
    { ...dayRange, instructorId, status: "APPROVED" },
    instructorQuery,
  );
  const instructor = api.useGetMember(instructorId ?? 0, { query: { enabled: instructorQuery } });

  const blocked = date
    ? blockedPeriods({
        date,
        hours,
        roomId,
        instructorId,
        excludeLessonId,
        lessons: (lessons.data?.content ?? []).map((lesson) => ({
          id: lesson.id,
          roomId: lesson.room.id,
          instructorId: lesson.instructor.id,
          status: lesson.status,
          startAt: lesson.startAt,
          endAt: lesson.endAt,
        })),
        ...(instructorQuery
          ? {
              absences: absences.data?.content ?? [],
              availability: availability.data?.slots.filter((s) => s.dayOfWeek === dayOfWeek(date)),
              resignationDate: instructor.data?.resignationDate,
            }
          : {}),
      })
    : [];
  const starts = date ? startSlots({ date, hours, blocked, now: Date.now() }) : [];
  const ends =
    date && start.time ? endSlots({ date, start: start.time, hours, blocked }) : [];
  const loading =
    businessHours.isPending ||
    lessons.isPending ||
    (instructorQuery && (availability.isPending || absences.isPending || instructor.isPending));

  const setEnd = (time: string) => setValue("endAt", time ? `${date}T${time}` : "");
  const pickDate = (next: string) => {
    // 날짜가 바뀌면 그날의 일정으로 다시 고른다.
    onChange(next ? `${next}T` : "");
    setValue("endAt", "");
    onBlur();
  };
  const pickStart = (time: string) => {
    const minutes =
      start.time && end.time
        ? (seoulTime(date, end.time) - seoulTime(date, start.time)) / 60000
        : 0;
    onChange(`${date}T${time}`);
    // 앞서 고른 수업 길이가 새 시작 시각에서도 가능하면 유지하고, 아니면 종료 시각을 다시 고르게 한다.
    const keep = endSlots({ date, start: time, hours, blocked }).find(
      (slot) => !slot.reason && seoulTime(date, slot.time) - seoulTime(date, time) === minutes * 60000,
    );
    setEnd(keep?.time ?? "");
    setPicking(keep ? null : "end");
  };
  const minutes =
    date && start.time && end.time
      ? (seoulTime(date, end.time) - seoulTime(date, start.time)) / 60000
      : undefined;
  const hint = !roomId || !instructorId
    ? "룸과 담당 강사를 먼저 선택하면 사용 중인 시간이 표시됩니다."
    : canSeeInstructorSchedule
      ? undefined
      : "다른 강사의 가능시간·부재는 저장할 때 확인합니다.";

  return (
    <div
      className="lesson-time-field"
      data-invalid={invalid}
    >
      <DatePicker
        id={id}
        value={date}
        onChange={pickDate}
        min={dateInSeoul()}
        isDisabled={(day) => !!businessHours.data && !hoursOf(day)}
        disabled={disabled}
        aria-invalid={invalid}
        aria-describedby={describedBy}
      />
      <div className="lesson-time-range">
        <Button
          type="button"
          variant="outline"
          disabled={disabled || !date}
          data-empty={!start.time}
          onClick={() => setPicking("start")}
          aria-label={`시작 시각 ${start.time || "선택"}`}
        >
          {start.time || "시작"}
        </Button>
        <span aria-hidden>~</span>
        <Button
          type="button"
          variant="outline"
          disabled={disabled || !start.time}
          data-empty={!end.time}
          onClick={() => setPicking("end")}
          aria-label={`종료 시각 ${end.time || "선택"}`}
        >
          {end.time || "종료"}
        </Button>
        {minutes !== undefined && minutes > 0 && (
          <span className="lesson-time-duration">{minutes}분</span>
        )}
      </div>
      {hint && <p className="lesson-time-hint">{hint}</p>}
      {picking && (
        <Dialog
          dialog={{
            title: picking === "start" ? "시작 시각" : "종료 시각",
            body:
              picking === "start"
                ? `${date} 수업을 시작할 시각을 고르세요.`
                : `${date} ${start.time}에 시작하는 수업이 끝날 시각을 고르세요.`,
          }}
          onClose={() => setPicking(null)}
        >
          <SlotList
            slots={picking === "start" ? starts : ends}
            selected={picking === "start" ? start.time : end.time}
            loading={loading}
            empty={hours ? "고를 수 있는 시각이 없습니다." : "휴무일입니다."}
            onPick={(time) => {
              if (picking === "start") pickStart(time);
              else {
                setEnd(time);
                setPicking(null);
              }
            }}
          />
        </Dialog>
      )}
    </div>
  );
}

function SlotList({
  slots,
  selected,
  loading,
  empty,
  onPick,
}: {
  slots: Slot[];
  selected: string;
  loading: boolean;
  empty: string;
  onPick: (time: string) => void;
}) {
  if (loading) return <p className="lesson-time-hint">일정을 불러오는 중입니다.</p>;
  if (!slots.length) return <p className="lesson-time-hint">{empty}</p>;
  return (
    <ul
      className="lesson-time-list"
      aria-label="시각 목록"
    >
      {slots.map((slot) => (
        <li key={slot.time}>
          <button
            type="button"
            disabled={!!slot.reason}
            aria-pressed={slot.time === selected}
            onClick={() => onPick(slot.time)}
          >
            <span>{slot.time}</span>
            {slot.reason && <span className="lesson-time-reason">{slot.reason}</span>}
          </button>
        </li>
      ))}
    </ul>
  );
}
