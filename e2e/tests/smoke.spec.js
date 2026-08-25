const { test, expect } = require('./fixtures/evidence');

test('Playwright runner smoke placeholder', async ({ page }) => {
  await page.goto('data:text/html,<title>Campus E2E Smoke</title><main>ready</main>');
  await expect(page).toHaveTitle('Campus E2E Smoke');
  await expect(page.locator('main')).toHaveText('ready');
});
