import { expect, test as base, type Page } from "@playwright/test";

export type Role = "MEMBER" | "INSTRUCTOR" | "ADMIN";

// storageState 재사용을 하지 않는다.
// - Refresh Token은 재발급 시 이전 토큰이 폐기(rotation)되어, 저장된 쿠키를 여러 테스트가 공유하면 서로의 세션을 끊는다.
// - 쿠키가 Secure라 http 개발서버에서는 브라우저가 저장하지 않는다.
// 그래서 테스트마다 로그인 화면으로 직접 로그인한다.
export function credentials(role: Role) {
  const email = process.env[`E2E_${role}_EMAIL`];
  const password = process.env[`E2E_${role}_PASSWORD`];
  return email && password ? { email, password } : null;
}

export async function loginAs(page: Page, role: Role) {
  const account = credentials(role);
  if (!account) throw new Error(`E2E_${role}_EMAIL / E2E_${role}_PASSWORD가 e2e/.env에 없습니다.`);

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "로그인" })).toBeVisible();
  await page.getByLabel("이메일").fill(account.email);
  await page.getByLabel("비밀번호").fill(account.password);

  const response = page.waitForResponse(
    (res) => res.url().endsWith("/auth/login") && res.request().method() === "POST",
  );
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  expect((await response).status(), "로그인 API 상태코드").toBe(200);
  await expect(page.getByRole("heading", { name: "로그인" })).toBeHidden();
}

export const test = base.extend<{ loginAs: (role: Role) => Promise<void> }>({
  loginAs: async ({ page }, use) => {
    await use((role) => loginAs(page, role));
  },
});

export { expect };
