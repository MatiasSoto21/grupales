import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './test/browser', timeout: 30_000, fullyParallel: false, workers: 1,
  reporter: 'list', use: { baseURL: 'http://127.0.0.1:3100', headless: true, trace: 'retain-on-failure' },
  webServer: {
    command: 'node dist/server.js', url: 'http://127.0.0.1:3100', reuseExistingServer: false,
    env: { PORT: '3100', BACKEND_URL: process.env.TEST_BACKEND_URL ?? 'http://localhost:8080' },
    stdout: 'pipe', stderr: 'pipe'
  }
});
