import { test } from "node:test";
import assert from "node:assert/strict";
import { hourOptions, joinDateTime, minuteOptions, splitDateTime } from "../src/lib/date-time.ts";

test("datetime-local 값을 날짜·시·분으로 나누고 다시 합친다", () => {
  assert.deepEqual(splitDateTime("2026-10-29T19:50"), { date: "2026-10-29", hour: "19", minute: "50" });
  assert.deepEqual(splitDateTime(""), { date: "", hour: "", minute: "" });
  assert.deepEqual(splitDateTime(undefined), { date: "", hour: "", minute: "" });
  assert.equal(joinDateTime({ date: "2026-10-29", hour: "07", minute: "05" }), "2026-10-29T07:05");
});

test("날짜가 없으면 빈 값이고, 시·분을 아직 고르지 않았으면 00으로 채운다", () => {
  assert.equal(joinDateTime({ date: "", hour: "09", minute: "00" }), "");
  assert.equal(joinDateTime({ date: "2026-10-29", hour: "", minute: "" }), "2026-10-29T00:00");
});

test("시는 00~23 24개다", () => {
  const hours = hourOptions();
  assert.equal(hours.length, 24);
  assert.equal(hours[0], "00");
  assert.equal(hours[23], "23");
});

test("분은 5분 단위이고, 단위에 맞지 않는 기존 값은 순서대로 끼워 넣어 보존한다", () => {
  assert.deepEqual(minuteOptions(), ["00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"]);
  const withOdd = minuteOptions("07");
  assert.equal(withOdd.length, 13);
  assert.deepEqual(withOdd.slice(0, 3), ["00", "05", "07"]);
  assert.equal(minuteOptions("10").length, 12);
});
