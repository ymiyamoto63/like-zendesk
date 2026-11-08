import { defineConfig, devices } from '@playwright/test'

// docker compose -f compose.yaml -f compose.e2e.yaml up --build --wait で起動した環境に対して実行する
export default defineConfig({
  testDir: './tests',
  // シナリオは時計を進めるので、並列に走らせない
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    actionTimeout: 15_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
