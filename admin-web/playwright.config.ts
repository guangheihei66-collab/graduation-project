import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 120000,
  expect: { timeout: 10000 },
  reporter: "list",
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://localhost:5173",
    headless: true,
    actionTimeout: 10000,
    trace: "off",
    screenshot: "off",
    video: "off",
    launchOptions: { executablePath: process.env.CHROMIUM_PATH },
  },
});
