import { test, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";
import { setupServer } from "msw/node";
import { fileURLToPath } from "node:url";

const server = await createServer({
  configFile: false,
  resolve: { alias: { "@": fileURLToPath(new URL("../src", import.meta.url)) } },
  cacheDir: "node_modules/.vite-instructor-tests",
  server: { middlewareMode: true, ws: false },
  appType: "custom",
});
const { instructorDashboardData, instructorAbsenceState } = await server.ssrLoadModule(
  "/src/features/instructor/apiData.ts",
);
const { getDashboardSnapshot } = await server.ssrLoadModule("/src/features/dashboard/model.ts");
after(() => server.close());

const ownLesson = {
  id: 110,
  title: "실제 담당 수업",
  instructor: { id: 7, name: "담당 강사" },
  room: { name: "룸 A" },
  startAt: "2026-10-21T15:00:00Z",
  endAt: "2026-10-21T16:00:00Z",
  capacity: 200,
  status: "PUBLISHED",
};
const otherLesson = { ...ownLesson, id: 111, instructor: { id: 8, name: "다른 강사" } };
const absence = {
  id: 9,
  instructor: ownLesson.instructor,
  startAt: "2026-10-25T15:00:00Z",
  endAt: "2026-10-26T09:00:00Z",
  reason: null,
  status: "REJECTED",
  rejectReason: "일정 확인 필요",
  processedAt: "2026-10-22T00:00:00Z",
  createdAt: "2026-10-21T01:00:00Z",
};

test("강사 홈은 본인 담당 수업·예약·부재만 집계하고 서울 날짜 경계를 적용한다", () => {
  const reservations = Array.from({ length: 101 }, (_, index) => ({
    id: index + 1,
    lesson: ownLesson,
    member: { id: index + 100, name: `회원 ${index}` },
    status: index === 0 ? "ATTENDED" : "RESERVED",
  }));
  reservations.push({
    id: 999,
    lesson: otherLesson,
    member: { id: 999, name: "타 강사 회원" },
    status: "RESERVED",
  });
  const data = instructorDashboardData(7, [ownLesson, otherLesson], reservations, [
    { ...absence, status: "REQUESTED" },
    { ...absence, id: 10, instructor: otherLesson.instructor },
  ]);
  const snapshot = getDashboardSnapshot(
    data,
    { id: 7, role: "INSTRUCTOR" },
    "2026-10-22T01:00:00Z",
  );
  assert.equal(snapshot.todayLessons.length, 1);
  assert.equal(snapshot.reservations.length, 101);
  assert.equal(snapshot.attended, 1);
  assert.equal(snapshot.attendancePending, 100);
  assert.equal(snapshot.absences.length, 1);
  assert.equal(data.absences[0].from, "2026-10-26");
  assert.equal(data.absences[0].reason, "");
});

test("부재 상세는 서버의 반려 사유·처리 시각을 사용하며 null을 처리한다", () => {
  const state = instructorAbsenceState([
    absence,
    { ...absence, id: 10, status: "REQUESTED", rejectReason: null, processedAt: null },
  ]);
  assert.equal(state.absenceDetails[9].rejectionReason, "일정 확인 필요");
  assert.equal(state.absenceDetails[9].processedAt, absence.processedAt);
  assert.equal(state.absenceDetails[10].rejectionReason, undefined);
  assert.equal(state.absenceDetails[10].processedAt, undefined);
  assert.deepEqual(state.changes, []);
});

test("MSW 부재도 기존 계약으로 신청·중복 거부·본인 조회·철회를 처리한다", async () => {
  const { adminHandlers } = await server.ssrLoadModule("/src/mocks/admin/handlers.ts");
  const { authPreview } = await server.ssrLoadModule("/src/features/auth/preview.ts");
  const mockServer = setupServer(...adminHandlers);
  mockServer.listen({ onUnhandledRequest: "error" });
  const request = (path, method = "GET", body) =>
    fetch(`http://localhost/v1${path}`, {
      method,
      headers: { "Content-Type": "application/json" },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  try {
    authPreview.login("instructor@rha.example", "RhaDemo123!");
    const input = {
      startAt: "2026-12-21T00:00:00Z",
      endAt: "2026-12-21T09:00:00Z",
      reason: "연동 테스트",
    };
    const response = await request("/absences", "POST", input);
    assert.equal(response.status, 201);
    const created = await response.json();
    assert.equal(created.status, "REQUESTED");
    assert.equal(created.instructor.id, 2);
    const overlap = await request("/absences", "POST", input);
    assert.equal(overlap.status, 409);
    assert.equal((await overlap.json()).code, "ABSENCE_PERIOD_OVERLAP");
    const list = await (await request("/absences?instructorId=999&size=100")).json();
    assert.ok(list.content.every((row) => row.instructor.id === 2));
    assert.ok(list.content.some((row) => row.id === created.id));
    const canceled = await (await request(`/absences/${created.id}/cancellations`, "POST")).json();
    assert.equal(canceled.status, "CANCELED");
    assert.equal(canceled.processedBy.id, 2);
    const detail = await (await request(`/absences/${created.id}`)).json();
    assert.equal(detail.status, "CANCELED");
    authPreview.login("demo@rha.example", "RhaDemo123!");
    assert.equal((await request(`/absences/${created.id}`)).status, 404);
    assert.equal((await request("/absences", "POST", input)).status, 403);
  } finally {
    authPreview.logout();
    mockServer.close();
  }
});
