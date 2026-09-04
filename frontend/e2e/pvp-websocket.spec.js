const { test, expect } = require('@playwright/test');

const loginAsGuest = async (page) => {
  await page.goto('/');
  await page.getByRole('button', { name: '로그인 / 게스트' }).click();
  await page.getByRole('button', { name: /게스트로 플레이/ }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
};

const startMatchmaking = async (page) => {
  await page.getByRole('heading', { name: 'LAND GRAB' }).click();
  await page.getByRole('button', { name: '매칭 시작' }).click();
};

test('two authenticated guests complete a PvP match over STOMP WebSocket', async ({ browser }) => {
  test.setTimeout(180_000);
  const firstContext = await browser.newContext();
  const secondContext = await browser.newContext();
  const firstPage = await firstContext.newPage();
  const secondPage = await secondContext.newPage();

  try {
    await Promise.all([loginAsGuest(firstPage), loginAsGuest(secondPage)]);
    await Promise.all([startMatchmaking(firstPage), startMatchmaking(secondPage)]);

    const firstSubmit = firstPage.getByRole('button', { name: /SUBMIT CODE/ });
    const secondSubmit = secondPage.getByRole('button', { name: /SUBMIT CODE/ });
    await Promise.all([
      expect(firstSubmit).toBeEnabled({ timeout: 30_000 }),
      expect(secondSubmit).toBeEnabled({ timeout: 30_000 }),
    ]);
    await Promise.all([firstSubmit.click(), secondSubmit.click()]);

    const outcome = /^(VICTORY|DEFEAT|DRAW)$/;
    await Promise.all([
      expect(firstPage.getByRole('heading', { name: outcome })).toBeVisible({ timeout: 90_000 }),
      expect(secondPage.getByRole('heading', { name: outcome })).toBeVisible({ timeout: 90_000 }),
    ]);
  } finally {
    await Promise.all([firstContext.close(), secondContext.close()]);
  }
});
