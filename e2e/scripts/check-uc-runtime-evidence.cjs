const { readFileSync, statSync } = require('node:fs');
const { resolve } = require('node:path');

const root = resolve(__dirname, '..', '..');
const mapping = JSON.parse(readFileSync(resolve(root, 'contracts/testing/uc-traceability.json'), 'utf8'));
const junitPath = resolve(root, 'e2e/test-results/junit.xml');
const maximumAgeMs = Number(process.env.UC_EVIDENCE_MAX_AGE_MS || 30 * 60 * 1000);
const evidenceAgeMs = Date.now() - statSync(junitPath).mtimeMs;
if (!Number.isFinite(maximumAgeMs) || maximumAgeMs <= 0 || evidenceAgeMs > maximumAgeMs) {
  console.error(`Runtime UC evidence check failed; JUnit report is stale (${Math.round(evidenceAgeMs / 1000)}s old). Run the full E2E suite first.`);
  process.exit(1);
}
const junit = readFileSync(junitPath, 'utf8');
const executed = [...junit.matchAll(/<testcase\b([^>]*)>/gi)]
  .map(match => attribute(match[1], 'name'))
  .filter(Boolean);

const missing = [];
for (const [useCase, entry] of Object.entries(mapping.useCases)) {
  for (const evidence of entry.e2e) {
    if (!executed.some(name => name.includes(evidence.test))) {
      missing.push(`${useCase}: ${evidence.test}`);
    }
  }
}

if (missing.length) {
  console.error('Runtime UC evidence check failed; mapped tests were not executed:');
  missing.forEach(value => console.error(`- ${value}`));
  process.exit(1);
}
console.log(`Runtime UC evidence check passed: ${Object.keys(mapping.useCases).length} use cases, ${executed.length} executed E2E tests.`);

function attribute(source, name) {
  const match = source.match(new RegExp(`${name}\\s*=\\s*(["'])(.*?)\\1`, 'i'));
  return match ? decode(match[2]) : '';
}

function decode(value) {
  return value.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'").replaceAll('&amp;', '&');
}
