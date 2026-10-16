import type { Lesson } from "../../api/generated/api";

const DAY = 86400000;
export const dateInSeoul = (value: string | number = Date.now()) =>
  new Date((typeof value === "number" ? value : Date.parse(value)) + 9 * 3600000)
    .toISOString()
    .slice(0, 10);
export const shiftDate = (date: string, days: number) =>
  new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY).toISOString().slice(0, 10);
export function weekStart(date: string) {
  return shiftDate(date, -((new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7));
}
export const weekRange = (start: string) => ({
  from: new Date(`${start}T00:00:00+09:00`).toISOString(),
  to: new Date(`${shiftDate(start, 7)}T00:00:00+09:00`).toISOString(),
});
export const timeInSeoul = (iso: string) =>
  new Date(Date.parse(iso) + 9 * 3600000).toISOString().slice(11, 16);
export const minuteInDay = (iso: string) =>
  Number(timeInSeoul(iso).slice(0, 2)) * 60 + Number(timeInSeoul(iso).slice(3));

// Split cross-midnight lessons and place simultaneous classes side by side.
export function layoutDay(lessons: Lesson[], date: string) {
  const midnight = Date.parse(`${date}T00:00:00+09:00`);
  const events = lessons
    .filter((l) => Date.parse(l.startAt) < midnight + DAY && Date.parse(l.endAt) > midnight)
    .map((lesson) => ({
      lesson,
      start: Math.max(0, (Date.parse(lesson.startAt) - midnight) / 60000),
      end: Math.min(1440, (Date.parse(lesson.endAt) - midnight) / 60000),
      column: 0,
      columns: 1,
    }))
    .sort((a, b) => a.start - b.start || b.end - a.end || a.lesson.id - b.lesson.id);
  let group: typeof events = [],
    ends: number[] = [],
    groupEnd = -1;
  const finish = () => {
    group.forEach((event) => {
      event.columns = ends.length;
    });
  };
  for (const event of events) {
    if (event.start >= groupEnd) {
      finish();
      group = [];
      ends = [];
      groupEnd = -1;
    }
    let column = ends.findIndex((end) => end <= event.start);
    if (column === -1) column = ends.length;
    ends[column] = event.end;
    event.column = column;
    group.push(event);
    groupEnd = Math.max(groupEnd, event.end);
  }
  finish();
  return events;
}
