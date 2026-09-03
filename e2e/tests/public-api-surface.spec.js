const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test, expect } = require('./fixtures/evidence');
const { login } = require('./fixtures/app');

const contractFile = resolve(__dirname, '../../contracts/http/public-api-v1.tsv');
const FROZEN_ENDPOINT_COUNT = 45;
const endpoints = readFileSync(contractFile, 'utf8')
  .split(/\r?\n/)
  .filter(line => line && !line.startsWith('#'))
  .map(line => {
    const [method, path, owner, authentication, csrf] = line.split('\t');
    return { method, path, owner, authentication, csrf };
  });

function concretePath(template) {
  return template
    .replaceAll('{itemId}', '999999')
    .replaceAll('{ownerId}', '999999')
    .replaceAll('{userId}', '999999')
    .replaceAll('{fileName}', '00000000-0000-0000-0000-000000000000.png')
    .replaceAll('{id}', '999999');
}

test('冻结的全部公开 API 均可路由且匿名权限边界一致', async ({ page }) => {
  expect(endpoints, '公开 API 冻结清单数量变化时必须同步审查并更新固定数量').toHaveLength(FROZEN_ENDPOINT_COUNT);
  const observations = [];
  for (const endpoint of endpoints) {
    const response = await page.request.fetch(concretePath(endpoint.path), {
      method: endpoint.method,
      data: ['POST', 'PUT'].includes(endpoint.method) ? {} : undefined,
      failOnStatusCode: false
    });
    observations.push({ endpoint, status: response.status(), body: await response.text() });
  }

  for (const { endpoint, status, body } of observations) {
    const label = `${endpoint.method} ${endpoint.path} (${endpoint.owner})`;
    const evidence = `${label}; response=${body.slice(0, 500)}`;
    expect(status, `${evidence}; must never fail as an unhandled server error`).toBeLessThan(500);
    expect(status, `${evidence}; must be registered for its frozen HTTP method`).not.toBe(405);
    if (endpoint.authentication !== 'PUBLIC') {
      expect([401, 403], `${evidence}; must reject an anonymous caller`).toContain(status);
    } else if (endpoint.method === 'GET') {
      expect([200, 404], `${evidence}; must remain anonymously reachable`).toContain(status);
    } else {
      expect(status, `${evidence}; must enforce CSRF before accepting an anonymous write`).toBe(403);
    }
  }
  expect(observations).toHaveLength(endpoints.length);

  await login(page, 'buyer');
  for (const endpoint of endpoints.filter(value => value.authentication === 'AUTHENTICATED'
    && value.path !== '/api/auth/logout')) {
    await expectAuthenticatedRoute(page, endpoint);
  }
  await expectAuthenticatedRoute(page, endpoints.find(value => value.path === '/api/auth/logout'));

  await login(page, 'admin');
  for (const endpoint of endpoints.filter(value => value.authentication === 'ADMIN')) {
    await expectAuthenticatedRoute(page, endpoint);
  }
});

async function expectAuthenticatedRoute(page, endpoint) {
  const headers = {};
  if (endpoint.csrf === 'YES') {
    const csrfResponse = await page.request.get('/api/auth/csrf');
    expect(csrfResponse.ok(), 'authenticated CSRF bootstrap should succeed').toBeTruthy();
    headers['X-XSRF-TOKEN'] = (await csrfResponse.json()).data;
  }
  const response = await page.request.fetch(concretePath(endpoint.path), {
    method: endpoint.method,
    headers,
    data: ['POST', 'PUT'].includes(endpoint.method) ? {} : undefined,
    failOnStatusCode: false
  });
  const body = await response.text();
  const evidence = `${endpoint.method} ${endpoint.path} (${endpoint.owner}); response=${body.slice(0, 500)}`;
  expect(response.status(), `${evidence}; authenticated route must pass the identity boundary`).not.toBe(401);
  if (response.status() === 403) {
    const rejection = rejectionText(body);
    expect(rejection, `${evidence}; 403 may be a domain rejection, but not an identity, role or CSRF rejection`)
      .not.toMatch(/CSRF|Forbidden|未登录|无权访问|需要管理员权限/i);
    expect(body, `${evidence}; domain rejection must use the owning service response contract`).toContain('"success"');
  }
  expect(response.status(), `${evidence}; frozen HTTP method must be registered`).not.toBe(405);
  expect(response.status(), `${evidence}; route must not fail as an unhandled server error`).toBeLessThan(500);
  if (response.status() === 404) {
    expect(body, `${evidence}; 404 must come from the owning service rather than an unmatched gateway route`).toContain('"success"');
  }
}

function rejectionText(body) {
  try {
    const payload = JSON.parse(body);
    return typeof payload.message === 'string' ? payload.message : body;
  } catch {
    return body;
  }
}
