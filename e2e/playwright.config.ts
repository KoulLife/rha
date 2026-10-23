import path from "node:path";
import { randomBytes } from "node:crypto";
import { defineConfig, devices } from "@playwright/test";
import dotenv from "dotenv";

dotenv.config({ path: path.resolve(__dirname, ".env"), quiet: true });

const baseURL = process.env.BASE_URL;
if (!baseURL) throw new Error("BASE_URL이 없습니다. e2e/.env에 개발서버 주소를 지정하세요.");

// 실행 1회를 식별한다. config는 워커마다 다시 로드되므로 env에 고정해 같은 값을 공유한다.
process.env.TEST_RUN_ID ??= `${new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14)}-${randomBytes(3).toString("hex")}`;

export default defineConfig({
  testDir: "./tests",
  outputDir: "./test-results/artifacts",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["json", { outputFile: "test-results/report.json" }],
  ],
  use: {
    baseURL,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    extraHTTPHeaders: { "X-Test-Run-Id": process.env.TEST_RUN_ID },
  },
  projects: [
    {
      name: "desktop-1920",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1920, height: 1080 } },
    },
    {
      name: "desktop-1366",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1366, height: 768 } },
    },
    {
      name: "mobile-pixel7",
      use: { ...devices["Pixel 7"] },
    },
  ],
});
