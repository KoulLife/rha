import { test } from "node:test";
import assert from "node:assert/strict";
import { nextPass } from "../src/features/member/nextPass.ts";

// getMyPasses 응답 순서(만료일 오름차순, 같으면 id 오름차순)를 그대로 받는다.
const pass = (id, status, remainingCount, instructorId) => ({
  id,
  status,
  remainingCount,
  instructor: { id: instructorId, name: `강사${instructorId}` },
});

test("예약 시 서버가 쓸 이용권(사용 중이고 잔여가 있는 첫 이용권)을 고른다", () => {
  const passes = [
    pass(1, "ACTIVE", 0, 11),
    pass(2, "PAUSED", 5, 12),
    pass(3, "ACTIVE", 4, 13),
    pass(4, "ACTIVE", 9, 14),
  ];
  assert.equal(nextPass(passes)?.id, 3);
});

test("사용할 이용권이 없으면 시작 전 이용권 중 첫 번째를 고른다", () => {
  const passes = [pass(1, "ACTIVE", 0, 11), pass(2, "EXPIRED", 3, 12), pass(3, "UPCOMING", 10, 13)];
  assert.equal(nextPass(passes)?.id, 3);
});

test("사용 중·시작 전 이용권이 모두 없으면 고르지 않는다", () => {
  assert.equal(nextPass([pass(1, "ACTIVE", 0, 11), pass(2, "PAUSED", 4, 12)]), undefined);
  assert.equal(nextPass([]), undefined);
});
