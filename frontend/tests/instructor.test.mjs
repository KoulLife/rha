import { test } from "node:test";
import assert from "node:assert/strict";
import { attendanceChangeError, attendanceRestriction, absenceRestriction, canWithdrawAbsence } from "../src/features/instructor/model.ts";
import { canAccessScreen, getNavigation } from "../src/config/access.ts";

const user = { id: 2, role: "INSTRUCTOR", name: "김강사" };
const now = "2026-10-13T03:00:00Z";
const data = {
  lessons: [
    { id: 101, instructorId: 2, startAt: "2026-10-13T01:00:00Z", status: "PUBLISHED" },
    { id: 102, instructorId: 4, startAt: "2026-10-13T01:00:00Z", status: "PUBLISHED" },
    { id: 103, instructorId: 2, startAt: "2026-10-13T04:00:00Z", status: "PUBLISHED" },
    { id: 110, instructorId: 2, startAt: "2026-10-09T04:00:00Z", status: "PUBLISHED" },
  ],
  reservations: [
    { id: 1, lessonId: 101, status: "RESERVED" },
    { id: 2, lessonId: 102, status: "RESERVED" },
    { id: 3, lessonId: 103, status: "RESERVED" },
    { id: 4, lessonId: 101, status: "CANCELED" },
    { id: 5, lessonId: 110, status: "ATTENDED" },
  ],
};

test("강사는 시작한 담당 수업만 출결 처리하고 취소·정산 수업은 차단한다", () => {
  assert.equal(attendanceRestriction(data, user, 1, now), null);
  for (const id of [2, 3, 4, 5, 999]) assert.ok(attendanceRestriction(data, user, id, now));
  assert.ok(attendanceRestriction(data, { ...user, role: "MEMBER" }, 1, now));
});

const absences = [{ id: 1, instructorId: 2, status: "REQUESTED" }, { id: 2, instructorId: 4, status: "APPROVED" }];
const details = { 1: { startAt: "2026-10-16T00:00:00Z", endAt: "2026-10-16T09:00:00Z" }, 2: { startAt: "2026-10-17T00:00:00Z", endAt: "2026-10-17T09:00:00Z" } };
test("부재 신청의 과거·역순·중복 기간을 막고 인접한 기간과 다른 강사 일정은 허용한다", () => {
  assert.ok(absenceRestriction(absences, details, 2, "invalid", "invalid", now));
  assert.ok(absenceRestriction(absences, details, 2, "2026-10-12T00:00:00Z", "2026-10-12T09:00:00Z", now));
  assert.ok(absenceRestriction(absences, details, 2, details[1].endAt, details[1].startAt, now));
  assert.ok(absenceRestriction(absences, details, 2, "2026-10-16T08:00:00Z", "2026-10-16T10:00:00Z", now));
  assert.equal(absenceRestriction(absences, details, 2, "2026-10-16T09:00:00Z", "2026-10-16T10:00:00Z", now), null);
  assert.equal(absenceRestriction(absences, details, 2, details[2].startAt, details[2].endAt, now), null);
  assert.equal(absenceRestriction([{ ...absences[0], status: "CANCELED" }], details, 2, details[1].startAt, details[1].endAt, now), null);
});

test("본인의 승인 대기 부재만 철회할 수 있다", () => {
  assert.equal(canWithdrawAbsence(absences[0], user), true);
  for (const status of ["APPROVED", "REJECTED", "CANCELED"]) assert.equal(canWithdrawAbsence({ ...absences[0], status }, user), false);
  assert.equal(canWithdrawAbsence({ ...absences[0], instructorId: 4 }, user), false);
});

test("강사 업무 메뉴는 강사에게만 노출되고 관리자 수업 상세·부재 접근을 유지한다", () => {
  const permissions = new Set(["lesson.read", "reservation.read", "profile.read", "notification.read"]);
  for (const screen of ["attendees", "attendance", "attendance-corrections", "absences"]) {
    assert.equal(canAccessScreen(screen, "INSTRUCTOR", permissions), true);
    assert.equal(canAccessScreen(screen, "MEMBER", permissions), false);
    assert.equal(canAccessScreen(screen, "ADMIN", permissions), false);
  }
  assert.ok(canAccessScreen("admin-absences", "ADMIN", permissions));
  assert.ok(canAccessScreen("admin-lesson-detail", "ADMIN", permissions));
});


test("첫 출결은 사유 없이 기록하고, 수정은 다른 상태와 정정 사유가 필요하다", () => {
  assert.equal(attendanceChangeError(data, user, 1, "ATTENDED", "", now), null);
  assert.equal(attendanceChangeError(data, user, 1, "NO_SHOW", "", now), null);
  const recorded = { ...data, reservations: data.reservations.map(row => row.id === 1 ? { ...row, status: "ATTENDED" } : row) };
  assert.ok(attendanceChangeError(recorded, user, 1, "NO_SHOW", "  ", now));
  assert.ok(attendanceChangeError(recorded, user, 1, "ATTENDED", "동일 상태", now));
  assert.ok(attendanceChangeError(recorded, user, 1, "NO_SHOW", "가".repeat(501), now));
  assert.equal(attendanceChangeError(recorded, user, 1, "NO_SHOW", "출석 오입력 확인", now), null);
  const noShow = { ...recorded, reservations: recorded.reservations.map(row => row.id === 1 ? { ...row, status: "NO_SHOW" } : row) };
  assert.equal(attendanceChangeError(noShow, user, 1, "ATTENDED", "실제 출석 확인", now), null);
  for (const id of [2, 3, 4, 5, 999]) assert.ok(attendanceChangeError(recorded, user, id, "NO_SHOW", "정정 사유", now));
  assert.equal(data.reservations[0].status, "RESERVED");
});

test("강사는 하나의 수업 메뉴에서 예약자·출결에 접근한다", () => {
  const permissions = new Set(["lesson.read", "reservation.read", "profile.read", "notification.read"]);
  const screens = getNavigation("INSTRUCTOR", permissions).map(item => item.screen);
  for (const screen of ["timetable", "absences"]) assert.equal(screens.includes(screen), true);
  for (const screen of ["today-lessons", "attendees", "attendance"]) assert.equal(screens.includes(screen), false);
  assert.equal(canAccessScreen("attendees", "INSTRUCTOR", permissions), true);
});
