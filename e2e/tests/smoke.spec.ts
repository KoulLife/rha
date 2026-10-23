import { credentials, expect, test, type Role } from "./fixtures/auth";

test("메인 페이지 접속 시 로그인 화면이 보인다", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.status(), "메인 페이지 상태코드").toBe(200);
  await expect(page).toHaveTitle("R.HA");
  await expect(page.getByRole("heading", { name: "로그인" })).toBeVisible();
});

for (const role of ["MEMBER", "INSTRUCTOR", "ADMIN"] as Role[]) {
  test(`${role} 계정으로 로그인된다`, async ({ loginAs }) => {
    test.skip(!credentials(role), `E2E_${role}_* 계정이 .env에 없음`);
    await loginAs(role);
  });
}
