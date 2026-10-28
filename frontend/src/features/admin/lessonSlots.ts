// 수업 개설·수정에서 고를 수 있는 시작·종료 시각을 계산한다.
// 서버의 createLesson/updateLesson 검증(영업시간, 강사 가능시간·부재·퇴사일, 강사·룸 시간 충돌)을 화면에서 미리 반영한다.
// 최종 판단은 서버가 하며, 강사 화면처럼 일부 정보를 조회할 수 없으면 그 조건은 막지 않는다.

export const SLOT_MINUTES = 30;

export type BlockedPeriod = { start: number; end: number; reason: string };
export type Slot = { time: string; reason?: string };
export type OpenHours = { open: string; close: string } | undefined;

type LessonLike = {
  id: number;
  roomId: number;
  instructorId: number;
  status: string;
  startAt: string;
  endAt: string;
};

// 센터 현지(Asia/Seoul) 날짜·시각을 epoch ms로 바꾼다.
export const seoulTime = (date: string, time: string) => Date.parse(`${date}T${time}:00+09:00`);

const toTime = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
const toMinutes = (time: string) => {
  const [hour, minute] = time.split(":").map(Number);
  return hour * 60 + minute;
};
const overlaps = (start: number, end: number, period: BlockedPeriod) =>
  start < period.end && period.start < end;

export function blockedPeriods({
  date,
  hours,
  roomId,
  instructorId,
  excludeLessonId,
  lessons,
  absences,
  availability,
  resignationDate,
}: {
  date: string;
  hours: OpenHours;
  roomId?: number;
  instructorId?: number;
  excludeLessonId?: number;
  lessons: LessonLike[];
  /** 승인된 부재. 모르면 생략한다. */
  absences?: { startAt: string; endAt: string }[];
  /** 그 요일의 강사 가능시간. 모르면 생략하고, 설정되지 않았으면 빈 목록이다. */
  availability?: { startTime: string; endTime: string }[];
  resignationDate?: string | null;
}): BlockedPeriod[] {
  if (!hours) return [];
  const dayStart = seoulTime(date, hours.open);
  const dayEnd = seoulTime(date, hours.close);
  const periods: BlockedPeriod[] = [];
  for (const lesson of lessons) {
    if (lesson.status === "CANCELED" || lesson.id === excludeLessonId) continue;
    const reason =
      lesson.roomId === roomId
        ? "룸 사용 중"
        : lesson.instructorId === instructorId
          ? "강사 다른 수업"
          : undefined;
    if (reason)
      periods.push({ start: Date.parse(lesson.startAt), end: Date.parse(lesson.endAt), reason });
  }
  if (instructorId === undefined) return periods;
  for (const absence of absences ?? [])
    periods.push({
      start: Date.parse(absence.startAt),
      end: Date.parse(absence.endAt),
      reason: "강사 부재",
    });
  if (availability) {
    // 영업시간 중 가능시간이 아닌 구간을 막는다.
    let cursor = dayStart;
    for (const slot of [...availability].sort((a, b) => a.startTime.localeCompare(b.startTime))) {
      const start = seoulTime(date, slot.startTime);
      if (start > cursor) periods.push({ start: cursor, end: start, reason: "강사 가능시간 외" });
      cursor = Math.max(cursor, seoulTime(date, slot.endTime));
    }
    if (cursor < dayEnd) periods.push({ start: cursor, end: dayEnd, reason: "강사 가능시간 외" });
  }
  if (resignationDate && date >= resignationDate)
    periods.push({ start: dayStart, end: dayEnd, reason: "퇴사일 이후" });
  return periods;
}

export function startSlots({
  date,
  hours,
  blocked,
  now,
}: {
  date: string;
  hours: OpenHours;
  blocked: BlockedPeriod[];
  now: number;
}): Slot[] {
  if (!hours) return [];
  const slots: Slot[] = [];
  for (let m = toMinutes(hours.open); m + SLOT_MINUTES <= toMinutes(hours.close); m += SLOT_MINUTES) {
    const time = toTime(m);
    const start = seoulTime(date, time);
    const end = start + SLOT_MINUTES * 60000;
    slots.push({
      time,
      reason:
        start <= now ? "지난 시간" : blocked.find((period) => overlaps(start, end, period))?.reason,
    });
  }
  return slots;
}

export function endSlots({
  date,
  start,
  hours,
  blocked,
}: {
  date: string;
  start: string;
  hours: OpenHours;
  blocked: BlockedPeriod[];
}): Slot[] {
  if (!hours) return [];
  const from = seoulTime(date, start);
  const slots: Slot[] = [];
  for (let m = toMinutes(start) + SLOT_MINUTES; m <= toMinutes(hours.close); m += SLOT_MINUTES) {
    const time = toTime(m);
    const end = seoulTime(date, time);
    slots.push({ time, reason: blocked.find((period) => overlaps(from, end, period))?.reason });
  }
  return slots;
}
