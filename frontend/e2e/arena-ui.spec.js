const { test, expect } = require('@playwright/test');

const corsHeaders = {
  'access-control-allow-origin': 'http://localhost:3000',
  'access-control-allow-credentials': 'true',
  'access-control-allow-headers': 'content-type',
  'access-control-allow-methods': 'GET,POST,OPTIONS',
};

test('production arena keeps the AI editor and result composition', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders });
      return;
    }

    const path = new URL(request.url()).pathname;
    const responses = {
      '/api/auth/me': { userId: 7, nickname: 'BrowserTester', role: 'USER' },
      '/api/match/land-grab/start': { matchId: 'browser-ai-1', walls: [], coins: [] },
      '/api/match/land-grab/compile': { status: 'ok' },
      '/api/match/land-grab/run': { winner: 'p1', logs: [] },
    };

    await route.fulfill({
      status: responses[path] ? 200 : 404,
      contentType: 'application/json',
      headers: corsHeaders,
      body: JSON.stringify(responses[path] || { error: 'not found' }),
    });
  });

  await page.goto('/');
  await expect(page.getByText('BrowserTester')).toBeVisible();
  await page.getByRole('heading', { name: 'LAND GRAB' }).click();
  await page.getByRole('button', { name: '전투 시작' }).click();

  await expect(page.getByRole('heading', { name: /ARENA 01/ })).toBeVisible();
  await expect(page.getByText('VS Code Style Editor')).toBeVisible();
  await page.getByRole('button', { name: 'GENERATE MAP' }).click();
  await page.getByRole('button', { name: /SUBMIT CODE/ }).click();

  await expect(page.getByRole('heading', { name: 'VICTORY' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'BACK TO LOBBY' })).toBeVisible();
});
