const { test, expect } = require('@playwright/test');

test('guest completes an AI match from the lobby', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /CODE CRASH ARENA/ })).toBeVisible();

  await page.getByRole('button', { name: '로그인 / 게스트' }).click();
  await page.getByRole('button', { name: /게스트로 플레이/ }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();

  await page.getByRole('heading', { name: 'LAND GRAB' }).click();
  await page.getByRole('combobox', { name: '난이도 선택' }).selectOption('easy');
  await page.getByRole('button', { name: '전투 시작' }).click();

  await page.getByRole('button', { name: 'GENERATE MAP' }).click();
  const submitButton = page.getByRole('button', { name: /SUBMIT CODE/ });
  await expect(submitButton).toBeEnabled();
  await submitButton.click();

  await expect(
    page.getByRole('heading', { name: /^(VICTORY|DEFEAT|DRAW)$/ })
  ).toBeVisible({ timeout: 90_000 });
  await expect(page.getByRole('button', { name: 'BACK TO LOBBY' })).toBeVisible();
});
