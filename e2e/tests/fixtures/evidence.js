const base = require('@playwright/test');

/**
 * Collect browser-side evidence that is useful when a journey fails. The
 * files are written below Playwright's per-test output directory and are
 * therefore included by the CI artifact upload without shared state.
 */
const test = base.test.extend({
  page: async ({ page }, use, testInfo) => {
    const consoleLines = [];
    const failedRequests = [];
    const pageErrors = [];

    page.on('console', (message) => {
      consoleLines.push(`[${message.type()}] ${message.text()}`);
    });
    page.on('requestfailed', (request) => {
      failedRequests.push(`${request.method()} ${request.url()} :: ${request.failure()?.errorText || 'unknown'}`);
    });
    page.on('pageerror', (error) => {
      pageErrors.push(error.stack || String(error));
    });

    await use(page);

    await testInfo.attach('console.log', {
      body: Buffer.from(consoleLines.join('\n') + (consoleLines.length ? '\n' : '')),
      contentType: 'text/plain'
    });
    await testInfo.attach('failed-requests.log', {
      body: Buffer.from(failedRequests.join('\n') + (failedRequests.length ? '\n' : '')),
      contentType: 'text/plain'
    });
    await testInfo.attach('page-errors.log', {
      body: Buffer.from(pageErrors.join('\n') + (pageErrors.length ? '\n' : '')),
      contentType: 'text/plain'
    });
  }
});

module.exports = { test, expect: base.expect };
