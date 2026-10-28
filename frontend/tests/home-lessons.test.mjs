import { test } from "node:test";
import assert from "node:assert/strict";
import { HOME_LESSON_LIMIT, homeLessonStatus, upcomingLessons } from "../src/features/dashboard/homeLessons.ts";

const now = Date.parse("2026-10-28T03:00:00Z");
const lesson = (id, startAt, reservedCount = 0, capacity = 6) => ({ id, startAt, reservedCount, capacity });

test("이미 시작한 수업은 자리가 남아도 예약 마감이다", () => {
  assert.equal(homeLessonStatus(lesson(1, "2026-10-27T22:00:00Z", 1), now), "CLOSED");
  assert.equal(homeLessonStatus(lesson(2, "2026-10-28T03:00:00Z", 1), now), "CLOSED");
});

test("시작 전 수업은 정원이 차면 마감, 아니면 예약 가능이다", () => {
  assert.equal(homeLessonStatus(lesson(1, "2026-10-28T04:00:00Z", 6), now), "CLOSED");
  assert.equal(homeLessonStatus(lesson(2, "2026-10-28T04:00:00Z", 5), now), "AVAILABLE");
});

test("홈 목록은 시작 전 수업만 가까운 순서로 최대 6개다", () => {
  const lessons = [
    lesson(1, "2026-10-27T22:00:00Z"),
    ...Array.from({ length: 8 }, (_, i) => lesson(10 + i, `2026-10-28T${String(10 - i).padStart(2, "0")}:00:00Z`)),
  ];
  const result = upcomingLessons(lessons, now);
  assert.equal(HOME_LESSON_LIMIT, 6);
  assert.equal(result.length, 6);
  assert.ok(result.every((item) => Date.parse(item.startAt) > now));
  assert.deepEqual(
    result.map((item) => item.startAt),
    [...result.map((item) => item.startAt)].sort(),
  );
});
