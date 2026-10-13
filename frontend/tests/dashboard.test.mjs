import { test } from "node:test";
import assert from "node:assert/strict";
import { getDashboardSnapshot, seoulDate } from "../src/features/dashboard/model.ts";
import { AuthPreview, demoAccounts } from "../src/features/auth/preview.ts";

const now = "2026-10-13T03:00:00Z";
const lesson = (id, instructorId, startAt, endAt, status = "PUBLISHED") => ({ id, instructorId, title: "수업", instructor: "강사", room: "A", capacity: 5, startAt, endAt, status });
const data = {
  lessons: [
    lesson(1, 2, "2026-10-12T15:00:00Z", "2026-10-12T16:00:00Z"),
    lesson(2, 4, "2026-10-13T05:00:00Z", "2026-10-13T06:00:00Z"),
    lesson(3, 2, "2026-10-13T15:00:00Z", "2026-10-13T16:00:00Z"),
    lesson(4, 2, "2026-10-13T01:00:00Z", "2026-10-13T02:00:00Z", "CANCELED"),
  ],
  reservations: [
    { id: 1, lessonId: 1, memberId: 1, member: "본인", status: "ATTENDED" },
    { id: 2, lessonId: 1, memberId: 5, member: "다른 회원", status: "RESERVED" },
    { id: 3, lessonId: 2, memberId: 1, member: "본인", status: "RESERVED" },
    { id: 4, lessonId: 1, memberId: 1, member: "본인", status: "CANCELED" },
    { id: 5, lessonId: 3, memberId: 1, member: "본인", status: "RESERVED" },
  ],
  absences: [
    { id: 1, instructorId: 2, status: "REQUESTED" },
    { id: 2, instructorId: 4, status: "REQUESTED" },
    { id: 3, instructorId: 2, status: "APPROVED" },
  ],
  notifications: [
    { id: 1, recipientId: 1, read: false, createdAt: now },
    { id: 2, recipientId: 2, read: false, createdAt: now },
    { id: 3, recipientId: 1, read: true, createdAt: now },
  ],
};

test("서울 자정 기준으로 오늘을 구분하고 취소·다음 날 수업을 제외한다", () => {
  assert.equal(seoulDate("2026-10-12T14:59:59Z"), "2026-10-12");
  assert.equal(seoulDate("2026-10-12T15:00:00Z"), "2026-10-13");
  assert.deepEqual(getDashboardSnapshot(data, { id: 3, role: "ADMIN" }, now).todayLessons.map(item => item.id), [1, 2]);
});

test("회원은 본인의 예약·알림만 보고 다른 회원의 출결·부재를 볼 수 없다", () => {
  const result = getDashboardSnapshot(data, { id: 1, role: "MEMBER" }, now);
  assert.deepEqual(result.reservations.map(item => item.id), [1, 3]);
  assert.equal(result.attended, 1);
  assert.equal(result.unread, 1);
  assert.deepEqual(result.notifications.map(item => item.id), [1, 3]);
  assert.equal(result.absences.length, 0);
  const empty = getDashboardSnapshot(data, { id: 99, role: "MEMBER" }, now);
  assert.equal(empty.todayLessons.length, 0);
  assert.equal(empty.notifications.length, 0);
});

test("강사는 담당 수업과 자기 부재만 보고 종료된 수업만 출결 대기로 집계한다", () => {
  const result = getDashboardSnapshot(data, { id: 2, role: "INSTRUCTOR" }, now);
  assert.deepEqual(result.todayLessons.map(item => item.id), [1]);
  assert.deepEqual(result.absences.map(item => item.id), [1]);
  assert.equal(result.attendancePending, 1);
  assert.equal(result.reservations.length, 2);
  const admin = getDashboardSnapshot(data, { id: 3, role: "ADMIN" }, now);
  assert.equal(admin.reservations.length, 3);
  assert.equal(admin.attendancePending, 1);
  assert.deepEqual(admin.absences.map(item => item.id), [1, 2]);
});

test("로그인 결과의 역할을 유지하고 비밀번호 재설정 후에도 역할이 바뀌지 않는다", () => {
  const auth = new AuthPreview();
  for (const account of demoAccounts) {
    assert.equal(auth.login(account.email, account.password).role, account.role);
    const token = auth.requestReset(account.email);
    auth.resetPassword(token, "NewPassword123!");
    assert.equal(auth.login(account.email, "NewPassword123!").role, account.role);
  }
});
