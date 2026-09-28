import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests: real browser against the real Angular app and Spring Boot API.
 * Both servers are started automatically, or reused if already running locally.
 * The backend needs JAVA_HOME pointing to JDK 21.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:4200',
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: [
    {
      command: 'mvn -q spring-boot:run',
      cwd: '../../../backend',
      url: 'http://localhost:8080/api/dashboard/summary',
      reuseExistingServer: !process.env['CI'],
      timeout: 180_000
    },
    {
      command: 'npm start',
      url: 'http://localhost:4200',
      reuseExistingServer: !process.env['CI'],
      timeout: 120_000
    }
  ]
});
