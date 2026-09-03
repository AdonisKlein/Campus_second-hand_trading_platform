import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const matrix = JSON.parse(fs.readFileSync(path.join(root, 'contracts/testing/public-api-coverage.json'), 'utf8'));
const contract = fs.readFileSync(path.join(root, 'contracts/http/public-api-v1.tsv'), 'utf8')
  .trim().split(/\r?\n/).slice(1).filter(Boolean).map(line => line.split('\t'));
const key = (method, route) => `${method} ${route}`;
const fail = message => { throw new Error(`[public-api-coverage] ${message}`); };

if (contract.length !== 45) fail(`expected 45 contract rows, found ${contract.length}`);
if (!Array.isArray(matrix.routes) || matrix.routes.length !== contract.length) {
  fail(`matrix must contain ${contract.length} routes`);
}
const expected = new Set(contract.map(([method, route]) => key(method, route)));
const seen = new Set();

function testBody(sourceText, testName, javascript) {
  const namePosition = sourceText.indexOf(testName);
  if (namePosition < 0) return null;
  const searchFrom = javascript ? sourceText.indexOf('=>', namePosition) : namePosition;
  const bodyStart = sourceText.indexOf('{', searchFrom);
  if (bodyStart < 0) return null;
  let depth = 0;
  for (let index = bodyStart; index < sourceText.length; index += 1) {
    if (sourceText[index] === '{') depth += 1;
    if (sourceText[index] === '}' && --depth === 0) return sourceText.slice(bodyStart, index + 1);
  }
  return null;
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function verifiesHttpCall(body, method, routePrefix, javascript) {
  const escapedRoute = escapeRegExp(routePrefix);
  if (javascript) {
    const fetchCall = new RegExp(`(?:fetch|\\.${method.toLowerCase()})\\s*\\([^)]*[\"'\\\`]${escapedRoute}`);
    const observedResponse = body.includes(routePrefix)
      && new RegExp(`method\\(\\)\\s*===?\\s*[\"']${method}[\"']`).test(body);
    return fetchCall.test(body) || observedResponse;
  }
  const mvcCall = new RegExp(`\\b${method.toLowerCase()}\\s*\\(\\s*[\"']${escapedRoute}`);
  const multipartPost = method === 'POST'
    && new RegExp(`\\bmultipart\\s*\\(\\s*[\"']${escapedRoute}`).test(body);
  const webTestClientCall = new RegExp(`\\.${method.toLowerCase()}\\s*\\(\\s*\\)\\s*\\.uri\\s*\\(\\s*[\"']${escapedRoute}`);
  return mvcCall.test(body) || multipartPost || webTestClientCall.test(body);
}

function hasAssertion(body, javascript) {
  return javascript
    ? /\bexpect\s*\(/.test(body)
    : /\.andExpect\s*\(|\.expectStatus\s*\(|\bassertThat\s*\(|\bverify\s*\(/.test(body);
}

for (const [method, route, success, alternate, access] of matrix.routes) {
  const routeKey = key(method, route);
  if (!expected.has(routeKey)) fail(`unknown route ${routeKey}`);
  if (seen.has(routeKey)) fail(`duplicate route ${routeKey}`);
  seen.add(routeKey);
  for (const [category, evidenceId] of Object.entries({ success, validationOrAlternate: alternate, authOrForbidden: access })) {
    const evidence = matrix.evidence[evidenceId];
    if (!evidence?.file || !evidence?.test || !evidence?.kind) fail(`${routeKey} missing ${category} evidence`);
    const evidencePath = path.join(root, evidence.file);
    if (!fs.existsSync(evidencePath)) fail(`${routeKey} evidence file does not exist: ${evidence.file}`);
    const sourceText = fs.readFileSync(evidencePath, 'utf8');
    const body = testBody(sourceText, evidence.test, evidencePath.endsWith('.js'));
    if (!body) fail(`${routeKey} evidence test not found: ${evidence.file}#${evidence.test}`);
    if (evidence.scope === 'contract') {
      if (!sourceText.includes('public-api-v1.tsv') || !body.includes('for (const endpoint of endpoints')
          || !body.includes('method: endpoint.method') || !hasAssertion(body, true)) {
        fail(`${routeKey} contract sweep does not iterate the frozen API contract`);
      }
      continue;
    }
    const routePrefix = route.split('{', 1)[0];
    const javascript = evidencePath.endsWith('.js');
    if (!verifiesHttpCall(body, method, routePrefix, javascript)) {
      fail(`${routeKey} ${category} evidence does not execute ${method} ${routePrefix}: ${evidence.file}#${evidence.test}`);
    }
    if (!hasAssertion(body, javascript)) {
      fail(`${routeKey} ${category} evidence has no executable assertion: ${evidence.file}#${evidence.test}`);
    }
  }
}
for (const routeKey of expected) if (!seen.has(routeKey)) fail(`contract route missing from matrix: ${routeKey}`);
console.log(`public API coverage verified: ${seen.size}/45 routes x 3 evidence categories`);
