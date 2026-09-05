import { test, expect } from '@playwright/test';

test.beforeEach(async ({page}) => {
  await page.addInitScript(() => localStorage.setItem('benchlab.token', 'local-ui-test-only'));
  await page.route('**/api/**', route => route.fulfill({json:[]}));
});

test('first-run action stays visible and advanced settings remain accessible', async ({page}) => {
  await page.goto('/');
  const launch = page.getByRole('button', {name:'Run comparison', exact:true});
  await expect(launch).toBeInViewport();
  await expect(page.getByRole('heading', {name:'1. Set up your comparison'})).toBeVisible();
  await expect(page.getByLabel('Measured iterations')).toBeHidden();
  await page.getByText('Advanced settings · sizes & execution', {exact:true}).click();
  await expect(page.getByLabel('Measured iterations')).toHaveValue('7');
  await expect(page.getByLabel('Warmup iterations')).toHaveValue('2');
  await page.getByText('View previous experiments or change metric', {exact:true}).click();
  await expect(page.getByLabel('Primary metric')).toHaveValue('cpuTimeMs');
  await page.getByLabel('Primary metric').selectOption('executionWallTimeMs');
  await expect(page.getByText('Wall time · ms', {exact:true})).toBeVisible();
  await page.getByText('Advanced settings · sizes & execution', {exact:true}).click();
  await page.getByText('View previous experiments or change metric', {exact:true}).click();
  await page.screenshot({path:'test-results/dashboard-desktop.png', fullPage:true});
});

test('mobile layout does not overflow and can submit against an isolated fixture', async ({page}) => {
  await page.setViewportSize({width:390,height:844});
  await page.goto('/');
  await expect(page.getByRole('button', {name:'Run comparison', exact:true})).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  // An API error must be actionable without queuing real production work.
  await page.route('**/api/algorithms', route => route.request().method() === 'POST'
    ? route.fulfill({status:429, json:{detail:'Outstanding run limit reached'}})
    : route.fulfill({json:[]}));
  await page.getByRole('button', {name:'Run comparison', exact:true}).click();
  await expect(page.locator('.launch-status')).toContainText('limit');
  await expect(page.getByRole('button', {name:'Run comparison', exact:true})).toBeEnabled();
  await page.screenshot({path:'test-results/dashboard-mobile.png', fullPage:true});
});

test('rejected session returns to sign-in instead of leaving a broken dashboard', async ({page}) => {
  await page.route('**/api/**', route => route.fulfill({status:401, json:{detail:'Unauthorized'}}));
  await page.goto('/');
  await expect(page.getByRole('button', {name:'Sign in', exact:true})).toBeVisible();
  await expect(page.getByRole('status')).toContainText('Please sign in again');
  expect(await page.evaluate(() => localStorage.getItem('benchlab.token'))).toBeNull();
});
