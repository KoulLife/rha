import { test } from "node:test";
import assert from "node:assert/strict";
import { blockedPeriods, endSlots, startSlots } from "../src/features/admin/lessonSlots.ts";

const at = (time, date = "2026-10-29") => Date.parse(`${date}T${time}:00+09:00`);
const hours = { open: "09:00", close: "12:00" };
const reasonOf = (slots, time) => slots.find((slot) => slot.time === time)?.reason;

test("시작 시각은 영업시간 안에서 30분 간격이고 마감 시각은 시작으로 고를 수 없다", () => {
  const slots = startSlots({ date: "2026-10-29", hours, blocked: [], now: at("08:00") });
  assert.deepEqual(
    slots.map((slot) => slot.time),
    ["09:00", "09:30", "10:00", "10:30", "11:00", "11:30"],
  );
  assert.ok(slots.every((slot) => !slot.reason));
});

test("휴무일은 시작 시각이 없다", () => {
  assert.deepEqual(startSlots({ date: "2026-10-29", hours: undefined, blocked: [], now: 0 }), []);
});

test("지난 시각과 막힌 시간대는 이유와 함께 고를 수 없다", () => {
  const blocked = [{ start: at("10:00"), end: at("10:50"), reason: "룸 사용 중" }];
  const slots = startSlots({ date: "2026-10-29", hours, blocked, now: at("09:10") });
  assert.equal(reasonOf(slots, "09:00"), "지난 시간");
  assert.equal(reasonOf(slots, "09:30"), undefined);
  assert.equal(reasonOf(slots, "10:00"), "룸 사용 중");
  assert.equal(reasonOf(slots, "10:30"), "룸 사용 중");
  assert.equal(reasonOf(slots, "11:00"), undefined);
});

test("종료 시각은 시작 30분 뒤부터 마감까지이고, 막힌 시간대를 넘어서는 고를 수 없다", () => {
  const blocked = [{ start: at("10:30"), end: at("11:00"), reason: "강사 다른 수업" }];
  const slots = endSlots({ date: "2026-10-29", start: "09:30", hours, blocked });
  assert.deepEqual(
    slots.map((slot) => slot.time),
    ["10:00", "10:30", "11:00", "11:30", "12:00"],
  );
  assert.equal(reasonOf(slots, "10:00"), undefined);
  assert.equal(reasonOf(slots, "10:30"), undefined);
  assert.equal(reasonOf(slots, "11:00"), "강사 다른 수업");
  assert.equal(reasonOf(slots, "12:00"), "강사 다른 수업");
});

test("막힌 시간대: 같은 룸·같은 강사 수업, 취소·자기 자신 제외, 부재, 가능시간 밖, 퇴사일 이후", () => {
  const lessons = [
    { id: 1, roomId: 7, instructorId: 3, status: "PUBLISHED", startAt: "2026-10-29T01:00:00Z", endAt: "2026-10-29T01:50:00Z" },
    { id: 2, roomId: 8, instructorId: 4, status: "PUBLISHED", startAt: "2026-10-29T02:00:00Z", endAt: "2026-10-29T02:50:00Z" },
    { id: 3, roomId: 7, instructorId: 9, status: "CANCELED", startAt: "2026-10-29T02:00:00Z", endAt: "2026-10-29T02:50:00Z" },
    { id: 4, roomId: 7, instructorId: 4, status: "CREATED", startAt: "2026-10-29T02:00:00Z", endAt: "2026-10-29T02:50:00Z" },
    { id: 5, roomId: 9, instructorId: 5, status: "PUBLISHED", startAt: "2026-10-29T02:00:00Z", endAt: "2026-10-29T02:50:00Z" },
  ];
  const periods = blockedPeriods({
    date: "2026-10-29",
    hours,
    roomId: 7,
    instructorId: 4,
    excludeLessonId: 4,
    lessons,
    absences: [{ startAt: "2026-10-29T02:30:00Z", endAt: "2026-10-29T03:00:00Z" }],
    availability: [{ startTime: "09:00", endTime: "11:30" }],
  });
  const reasons = periods.map((p) => [new Date(p.start).toISOString(), p.reason]);
  assert.deepEqual(reasons, [
    ["2026-10-29T01:00:00.000Z", "룸 사용 중"],
    ["2026-10-29T02:00:00.000Z", "강사 다른 수업"],
    ["2026-10-29T02:30:00.000Z", "강사 부재"],
    ["2026-10-29T02:30:00.000Z", "강사 가능시간 외"],
  ]);

  const noAvailability = blockedPeriods({ date: "2026-10-29", hours, instructorId: 4, lessons: [], availability: [] });
  assert.deepEqual(noAvailability.map((p) => p.reason), ["강사 가능시간 외"]);
  const resigned = blockedPeriods({ date: "2026-11-30", hours, instructorId: 4, lessons: [], resignationDate: "2026-11-30" });
  assert.deepEqual(resigned.map((p) => p.reason), ["퇴사일 이후"]);
});

test("가능시간·부재를 모르면(강사 화면) 그 조건은 막지 않는다", () => {
  const periods = blockedPeriods({ date: "2026-10-29", hours, instructorId: 4, lessons: [] });
  assert.deepEqual(periods, []);
});
