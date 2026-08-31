const { test, expect } = require('./fixtures/evidence');
const { waitForVerificationCode } = require('./fixtures/mailpit');
const { loginWithCredentials, sendVerificationCode } = require('./fixtures/app');

test('注册→登录→退出→重置密码', async ({ page }) => {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `e2e-${suffix}@example.com`;
  const username = `e2e${suffix}`.slice(0, 50);
  const firstPassword = 'Aa123456';
  const resetPassword = 'Bb654321';

  await page.goto('/register.html');
  await page.getByLabel('用户名').fill(username);
  await page.getByLabel('邮箱').fill(email);
  await sendVerificationCode(page);
  await page.getByLabel('密码').fill(firstPassword);
  await page.getByLabel('验证码').fill(await waitForVerificationCode({ email }));
  await page.getByRole('button', { name: '注册', exact: true }).click();

  await expect(page).toHaveURL(/\/profile\.html$/);
  await expect(page.locator('#loginMessage')).toHaveText('注册成功，请使用邮箱和密码登录');
  await loginWithCredentials(page, email, firstPassword);
  await expect(page.locator('#viewUsername')).toHaveText(username);

  page.once('dialog', dialog => dialog.accept());
  const loggedOut = page.waitForResponse(response => response.url().endsWith('/api/auth/logout')
    && response.request().method() === 'POST');
  await page.getByRole('button', { name: '退出登录' }).click();
  expect((await loggedOut).ok()).toBeTruthy();
  await expect(page.locator('#loginForm')).toBeVisible({ timeout: 15_000 });

  await page.getByRole('link', { name: '忘记密码' }).click();
  await page.locator('#fpEmail').fill(email);
  await page.getByRole('button', { name: '发送验证码' }).click();
  await expect(page.locator('#fpMessage')).toHaveText('success');
  await expect(page.locator('#fpStep2')).toBeVisible();
  await page.locator('#forgotPasswordForm input[name="code"]').fill(await waitForVerificationCode({ email }));
  await page.locator('#forgotPasswordForm input[name="newPassword"]').fill(resetPassword);
  await page.locator('#forgotPasswordForm input[name="confirmPassword"]').fill(resetPassword);
  await page.getByRole('button', { name: '重置密码' }).click();

  await expect(page).toHaveURL(/\/profile\.html$/);
  await expect(page.locator('#loginMessage')).toHaveText('密码重置成功，请使用新密码登录');
  await loginWithCredentials(page, email, resetPassword);
  await expect(page.locator('#viewUsername')).toHaveText(username);
});
