import { test, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  configureSession,
  getAccessToken,
  renewSession,
  setAccessToken,
} from "../src/api/session.ts";

let cleanup = () => {};
afterEach(() => {
  cleanup();
  setAccessToken(null);
});

test("여러 요청의 토큰 갱신을 하나로 합친다", async () => {
  let calls = 0;
  cleanup = configureSession(
    async () => {
      calls++;
      return "renewed";
    },
    () => assert.fail("session expired"),
  );
  setAccessToken("old");
  assert.deepEqual(await Promise.all([renewSession(), renewSession(), renewSession()]), [
    "renewed",
    "renewed",
    "renewed",
  ]);
  assert.equal(calls, 1);
  assert.equal(getAccessToken(), "renewed");
});

test("갱신 실패 시 토큰을 비우고 로그인 복귀를 한 번 요청한다", async () => {
  let expired = 0;
  cleanup = configureSession(
    async () => {
      throw new Error("expired");
    },
    () => expired++,
  );
  setAccessToken("old");
  const results = await Promise.allSettled([renewSession(), renewSession()]);
  assert.ok(results.every((result) => result.status === "rejected"));
  assert.equal(getAccessToken(), null);
  assert.equal(expired, 1);
});

test("로그아웃 후 늦게 도착한 갱신 응답은 세션을 복원하지 않는다", async () => {
  let resolve;
  cleanup = configureSession(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
    () => assert.fail("stale request expired a new session"),
  );
  setAccessToken("old");
  const request = renewSession();
  await Promise.resolve();
  setAccessToken(null);
  resolve("late");
  await assert.rejects(request);
  assert.equal(getAccessToken(), null);
});

test("이전 세션의 갱신 실패가 새 로그인을 지우지 않는다", async () => {
  let reject;
  cleanup = configureSession(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      }),
    () => assert.fail("new session expired"),
  );
  setAccessToken("old");
  const request = renewSession();
  await Promise.resolve();
  setAccessToken("new-login");
  reject(new Error("old request"));
  await assert.rejects(request);
  assert.equal(getAccessToken(), "new-login");
});
