const { expect } = require('@playwright/test');

const ACCOUNTS = Object.freeze({
  admin: { email: 'e2e-admin@example.test', password: 'abc123', nickname: 'E2E 管理员' },
  buyer: { email: 'e2e-buyer@example.test', password: 'abc123', nickname: 'E2E 买家' },
  seller: { email: 'e2e-seller@example.test', password: 'abc123', nickname: 'E2E 卖家' }
});

async function login(page, role) {
  const account = ACCOUNTS[role];
  if (!account) throw new Error(`Unknown E2E account role: ${role}`);
  await page.goto('/profile.html');
  await page.locator('#loginForm input[name="email"]').fill(account.email);
  await page.locator('#loginForm input[name="password"]').fill(account.password);
  await page.locator('#loginForm button[type="submit"]').click();
  await expect(page.locator('#profileSection')).toBeVisible();
  return account;
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

module.exports = { ACCOUNTS, api, login, publishItem };
