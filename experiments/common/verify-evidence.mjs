import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import process from 'node:process';

const resultPath = resolve(process.argv[2] || '');
if (!process.argv[2] || !existsSync(resultPath)) throw new Error('Usage: node verify-evidence.mjs <result.json>');
const root = dirname(resultPath);
// Windows PowerShell 5.1 writes a BOM for `-Encoding utf8`. New evidence is
// emitted without one, but accepting the prefix keeps older local runs verifiable.
const result = JSON.parse(readFileSync(resultPath, 'utf8').replace(/^\uFEFF/, ''));

assert.equal(result.schemaVersion, 1);
assert.match(result.runId, /^.+-\d{8}T\d{6}Z-[0-9a-f]{8}$/);
assert.ok(['smoke', 'hpa', 'fault', 'performance'].includes(result.experiment));
assert.ok(['PASS', 'FAIL'].includes(result.status));
assert.match(result.gitCommit, /^[0-9a-f]{40}$/);
assert.ok(!Number.isNaN(Date.parse(result.startedAt)));
assert.ok(!Number.isNaN(Date.parse(result.finishedAt)));
assert.ok(Array.isArray(result.evidence) && result.evidence.length > 0);
if (result.status === 'PASS') assert.equal(result.failure, null);
if (result.status === 'FAIL') assert.equal(typeof result.failure, 'string');

const seen = new Set();
for (const entry of result.evidence) {
  assert.equal(typeof entry.path, 'string');
  assert.ok(!entry.path.includes('..'), `unsafe evidence path: ${entry.path}`);
  assert.ok(!seen.has(entry.path), `duplicate evidence path: ${entry.path}`);
  seen.add(entry.path);
  const path = resolve(root, entry.path);
  assert.ok(path.startsWith(`${root}\\`) || path.startsWith(`${root}/`), `evidence escaped run directory: ${entry.path}`);
  assert.ok(existsSync(path), `missing evidence: ${entry.path}`);
  const content = readFileSync(path);
  assert.equal(createHash('sha256').update(content).digest('hex'), entry.sha256, `hash mismatch: ${entry.path}`);
  assert.equal(statSync(path).size, entry.bytes, `size mismatch: ${entry.path}`);
}

process.stdout.write(`Evidence verified: ${result.runId} (${result.status}, ${result.evidence.length} files)\n`);
