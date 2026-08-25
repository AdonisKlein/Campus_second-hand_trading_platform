const { setTimeout: sleep } = require('node:timers/promises');

const endpoints = [
  process.env.E2E_BASE_URL || 'http://127.0.0.1:18080/api/actuator/health/liveness',
  process.env.MAILPIT_API_URL || 'http://127.0.0.1:18025/api/v1/messages?limit=1'
];
const timeoutMs = Number(process.env.E2E_STARTUP_TIMEOUT_MS || 120_000);
const deadline = Date.now() + timeoutMs;

async function waitFor(url) {
  let lastError = 'not attempted';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = error.message;
    }
    await sleep(1000);
  }
  throw new Error(`Timed out waiting for ${url}: ${lastError}`);
}

(async () => {
  for (const endpoint of endpoints) await waitFor(endpoint);
})().catch(error => {
  console.error(error.message);
  process.exit(1);
});
