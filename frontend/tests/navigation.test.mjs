import { test } from "node:test";
import assert from "node:assert/strict";
import { canAccessScreen, getMenuScreen, getNavigation, getScreenLabel } from "../src/config/access.ts";

const permissions = new Set(["lesson.read", "pass.read", "reservation.read", "profile.read", "notification.read"]);

test("역할별 메뉴 순서와 명칭을 유지하며 중복 메뉴가 없다", () => {
  const member = getNavigation("MEMBER", permissions);
  assert.deepEqual(member.map(item => item.screen), ["home", "timetable", "classes", "passes", "reservations", "profile", "notifications"]);
  for (const role of ["INSTRUCTOR", "ADMIN"]) {
    const items = getNavigation(role, permissions);
    assert.deepEqual(items.map(item => item.screen), role === "INSTRUCTOR" ? ["home", "timetable", "today-lessons", "attendees", "attendance", "notifications"] : ["home", "timetable", "admin-members", "admin-instructors", "admin-lessons", "admin-passes", "notifications", "activity-logs", "admin-settings"]);
    assert.equal(items.find(item => item.screen === "timetable").label, "시간표");
    assert.equal(getScreenLabel(role === "INSTRUCTOR" ? "absences" : "admin-absences", role), role === "INSTRUCTOR" ? "부재 신청" : "부재 관리");
    assert.equal(new Set(items.map(item => item.screen)).size, items.length);
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


test("강사 출결 그룹은 하위 화면 선택과 개별 접근 권한을 유지한다", () => {
  const group = getNavigation("INSTRUCTOR", permissions).find(item => item.screen === "attendance");
  assert.equal(group.label, "출결");
  assert.deepEqual(group.children.map(item => item.screen), ["attendance", "attendance-corrections", "absences"]);
  for (const child of group.children) {
    assert.equal(getMenuScreen(child.screen), "attendance");
    assert.equal(child.activeScreens.includes(child.screen), true);
    assert.equal(canAccessScreen(child.screen, "INSTRUCTOR", permissions), true);
    assert.equal(canAccessScreen(child.screen, "MEMBER", permissions), false);
    assert.equal(canAccessScreen(child.screen, "ADMIN", permissions), false);
  }
  assert.equal(getScreenLabel("attendance", "INSTRUCTOR"), "출결 처리");
  const profileOnly = new Set(["profile.read"]);
  assert.deepEqual(getNavigation("INSTRUCTOR", profileOnly)[0].children.map(item => item.screen), ["absences"]);
  assert.equal(canAccessScreen("attendance", "INSTRUCTOR", profileOnly), false);
  assert.equal(canAccessScreen("absences", "INSTRUCTOR", profileOnly), true);
  const reservationOnly = new Set(["reservation.read"]);
  assert.deepEqual(getNavigation("INSTRUCTOR", reservationOnly).find(item => item.screen === "attendance").children.map(item => item.screen), ["attendance", "attendance-corrections"]);
  assert.equal(canAccessScreen("absences", "INSTRUCTOR", reservationOnly), false);
  assert.deepEqual(getNavigation("INSTRUCTOR", new Set()), []);
});


test("관리자 정산은 강사 관리 아래에서 접근하고 수업 예약은 수업 상세에서 확인한다", () => {
  const admin = getNavigation("ADMIN", permissions);
  const instructors = admin.find(item => item.screen === "admin-instructors");
  assert.ok(instructors.children.some(item => item.screen === "admin-settlements"));
  assert.ok(instructors.children.some(item => item.screen === "admin-pay-rates"));
  assert.equal(getMenuScreen("admin-settlement-detail"), "admin-instructors");
  assert.equal(canAccessScreen("admin-settlement-detail", "ADMIN", permissions), true);
  assert.equal(canAccessScreen("admin-settlement-detail", "INSTRUCTOR", permissions), false);
  assert.equal(canAccessScreen("admin-settlement-detail", "ADMIN", new Set(["lesson.read"])), false);
  assert.equal(canAccessScreen("admin-lesson-detail", "ADMIN", permissions), true);
  assert.equal(canAccessScreen("admin-reservations", "ADMIN", permissions), false);
});
