import { test } from "node:test";
import assert from "node:assert/strict";
import { canAccessScreen, getMenuScreen, getNavigation, getScreenLabel } from "../src/config/access.ts";

const permissions = new Set(["lesson.read", "pass.read", "reservation.read", "profile.read", "notification.read"]);

test("역할별 메뉴 순서와 명칭을 유지하며 중복 메뉴가 없다", () => {
  const member = getNavigation("MEMBER", permissions);
  assert.deepEqual(member.map(item => item.screen), ["home", "classes", "passes", "reservations", "profile", "notifications"]);
  for (const role of ["INSTRUCTOR", "ADMIN"]) {
    const items = getNavigation(role, permissions);
    assert.deepEqual(items.map(item => item.screen), ["home", "today-lessons", "attendance-overview", "absence-requests", "notifications"]);
    assert.equal(items[1].label, role === "INSTRUCTOR" ? "오늘 담당 수업" : "오늘 전체 수업");
    assert.equal(items[3].label, role === "INSTRUCTOR" ? "내 부재 승인 대기" : "부재 승인 대기");
    assert.equal(getScreenLabel("today-lessons", role), items[1].label);
  }
});

test("권한이 없으면 메뉴와 상위 메뉴에 속한 상세 화면 모두 접근 불가", () => {
  assert.deepEqual(getNavigation("MEMBER", new Set()), []);
  for (const [detail, parent, permission] of [
    ["lesson-detail", "classes", "lesson.read"],
    ["pass-detail", "passes", "pass.read"],
    ["reservation-detail", "reservations", "reservation.read"],
  ]) {
    assert.equal(getMenuScreen(detail), parent);
    assert.equal(canAccessScreen(detail, "MEMBER", permissions), true);
    const restricted = new Set(permissions);
    restricted.delete(permission);
    assert.equal(getNavigation("MEMBER", restricted).some(item => item.screen === parent), false);
    assert.equal(canAccessScreen(detail, "MEMBER", restricted), false);
    assert.equal(canAccessScreen(detail, "ADMIN", permissions), false);
    assert.equal(canAccessScreen(detail, "INSTRUCTOR", permissions), false);
  }
  assert.equal(canAccessScreen("absence-requests", "MEMBER", permissions), false);
  assert.equal(getScreenLabel("classes", "MEMBER"), "수업 목록");
  assert.equal(getScreenLabel("lesson-detail", "MEMBER"), "수업 상세");
});
