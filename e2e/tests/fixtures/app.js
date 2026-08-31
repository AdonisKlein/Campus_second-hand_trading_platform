const { expect } = require('@playwright/test');

const ACCOUNTS = Object.freeze({
  admin: { email: 'e2e-admin@example.test', password: 'abc123', nickname: 'E2E 管理员' },
  buyer: { email: 'e2e-buyer@example.test', password: 'abc123', nickname: 'E2E 买家' },
  seller: { email: 'e2e-seller@example.test', password: 'abc123', nickname: 'E2E 卖家' }
});

async function login(page, role) {
  const account = ACCOUNTS[role];
  if (!account) throw new Error(`Unknown E2E account role: ${role}`);
  await loginWithCredentials(page, account.email, account.password);
  return account;
}

async function loginWithCredentials(page, email, password) {
  await page.goto('/profile.html');
  await page.locator('#loginForm input[name="email"]').fill(email);
  await page.locator('#loginForm input[name="password"]').fill(password);
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const responsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/auth/login') && response.request().method() === 'POST');
    await page.locator('#loginForm button[type="submit"]').click();
    const response = await responsePromise;
    if (response.status() === 200) {
      await expect(page.locator('#profileSection')).toBeVisible();
      return;
    }
    if (response.status() !== 503 || attempt === 3) {
      expect(response.status(), `登录接口失败: ${email}`).toBe(200);
    }
    await page.waitForTimeout(1_000 * attempt);
  }
}

async function sendVerificationCode(page, messageSelector = '#registerMessage') {
  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/api/auth/verification/')
      && response.request().method() === 'POST',
  { timeout: 30_000 });
  await page.getByRole('button', { name: '发送验证码' }).click();
  const response = await responsePromise;
  expect(response.status(), '验证码发送接口应返回 202').toBe(202);
  await expect(page.locator(messageSelector)).toHaveText('验证码已发送，请查收邮箱', { timeout: 30_000 });
}

async function api(page, path, { method = 'GET', body, headers = {} } = {}) {
  const upperMethod = method.toUpperCase();
  const requestHeaders = { ...headers };
  if (!['GET', 'HEAD', 'OPTIONS'].includes(upperMethod)) {
    const csrfResponse = await page.request.get('/api/auth/csrf');
    expect(csrfResponse.ok(), 'CSRF token endpoint should succeed').toBeTruthy();
    const csrfPayload = await csrfResponse.json();
    requestHeaders['X-XSRF-TOKEN'] = csrfPayload.data;
  }
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  const response = await page.request.fetch(`/api${path}`, {
    method: upperMethod,
    headers: requestHeaders,
    data: body
  });
  const payload = await response.json();
  expect(response.ok(), `${upperMethod} ${path}: ${payload.message || response.status()}`).toBeTruthy();
  expect(payload.success, `${upperMethod} ${path}: ${payload.message || 'business failure'}`).toBeTruthy();
  return payload.data;
}

async function publishItem(page, title) {
  return api(page, '/items', {
    method: 'POST',
    body: {
      title,
      category: '生活用品',
      price: 25,
      description: 'E2E 测试商品，支持校内公共地点当面验货。',
      imageUrl: '',
      region: '学院路校区',
      tags: ['支持验货']
    }
  });
}

module.exports = { ACCOUNTS, api, login, loginWithCredentials, publishItem, sendVerificationCode };
