import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 120_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  workers: 2,
  retries: 0,
  forbidOnly: true,
  failOnFlakyTests: true,
  reporter: [['list'], ['html', { outputFolder: '/reports/html', open: 'never' }], ['json', { outputFile: '/reports/results.json' }], ['junit', { outputFile: '/reports/junit.xml' }]],
  outputDir: '/reports/artifacts',
  use: { baseURL: 'http://127.0.0.1:8089', trace: 'retain-on-failure', screenshot: 'only-on-failure', video: 'retain-on-failure' },
  projects: [
    { name: 'api', testMatch: 'system.spec.ts' },
    { name: 'chromium', testMatch: 'ui.spec.ts', dependencies: ['api'], use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', testMatch: 'ui.spec.ts', dependencies: ['api'], use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', testMatch: 'ui.spec.ts', dependencies: ['api'], use: { ...devices['Desktop Safari'] } },
    { name: 'mobile', testMatch: 'ui.spec.ts', dependencies: ['api'], use: { ...devices['Pixel 7'] } },
  ],
});
