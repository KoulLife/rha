import { test } from "node:test";
import assert from "node:assert/strict";
import { AuthPreview, demoAccount } from "../src/features/auth/preview.ts";

test("재발송 후 이전 코드 거절, 이메일과 인증 토큰 일치, 가입 후 토큰 재사용 차단", () => {
  const auth = new AuthPreview();
  const email = "new@rha.example";
  const oldCode = auth.requestVerification(email);
  const code = auth.requestVerification(email);
  assert.throws(() => auth.confirmVerification(email, oldCode));
  const verified = auth.confirmVerification(email, code);
  const input = { email, password: "NewPassword123!", name: "테스트 회원", phoneNumber: "01012345678", birthDate: "1990-01-31", emailVerificationToken: verified.token };
  assert.throws(() => auth.signUp({ ...input, email: "other@rha.example" }));
  auth.signUp(input);
  assert.throws(() => auth.signUp(input));
  assert.equal(auth.login(email, input.password).role, "MEMBER");
  assert.throws(() => auth.requestVerification(email));
});

test("인증 코드와 가입 인증 토큰은 만료되면 사용할 수 없다", () => {
  let now = 0;
  const auth = new AuthPreview(() => now);
  const email = "new@rha.example";
  const code = auth.requestVerification(email);
  now = 5 * 60_000;
  assert.throws(() => auth.confirmVerification(email, code));
  const freshCode = auth.requestVerification(email);
  const verified = auth.confirmVerification(email, freshCode);
  now += 30 * 60_000;
  assert.throws(() => auth.signUp({ email, name: "회원", password: "password123", phoneNumber: "01012345678", birthDate: "1990-01-31", emailVerificationToken: verified.token }));
});

test("재설정은 기존 비밀번호·로그인 상태를 무효화하고 링크를 한 번만 허용한다", () => {
  const auth = new AuthPreview();
  auth.login(demoAccount.email, demoAccount.password);
  const oldToken = auth.requestReset(demoAccount.email);
  const token = auth.requestReset(demoAccount.email);
  assert.throws(() => auth.resetPassword(oldToken, "NewPassword123!"));
  auth.resetPassword(token, "NewPassword123!");
  assert.equal(auth.session, null);
  assert.throws(() => auth.login(demoAccount.email, demoAccount.password));
  assert.throws(() => auth.resetPassword(token, "AnotherPassword123!"));
  auth.login(demoAccount.email, "NewPassword123!");
  auth.logout();
  assert.equal(auth.session, null);
});

test("미가입 이메일도 재설정 요청은 동일하게 접수하고, 만료 링크는 거절한다", () => {
  let now = 0;
  const auth = new AuthPreview(() => now);
  const unknown = auth.requestReset("unknown@rha.example");
  assert.equal(typeof unknown, "string");
  assert.throws(() => auth.resetPassword(unknown, "NewPassword123!"));
  const token = auth.requestReset(demoAccount.email);
  now = 30 * 60_000;
  assert.throws(() => auth.resetPassword(token, "NewPassword123!"));
});
