import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e', testMatch: '**/*.pw.ts', workers: 1,
  outputDir: './test-results',
  use: { baseURL: 'http://127.0.0.1:4178', channel: 'chrome', viewport: {width:1440,height:900} },
  webServer: { command: 'npm run dev -- --host 127.0.0.1 --port 4178 --strictPort', url: 'http://127.0.0.1:4178', reuseExistingServer: false },
});
