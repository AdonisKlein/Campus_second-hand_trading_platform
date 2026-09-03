import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const here = dirname(fileURLToPath(import.meta.url));
const common = resolve(here, '..');
const generator = resolve(common, 'dataset', 'generate-canonical-data.mjs');

function hash(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

test('canonical generator is byte-for-byte deterministic', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'campus-experiment-foundation-'));
  try {
    const first = resolve(root, 'first');
    const second = resolve(root, 'second');
    const args = ['--seed', '42', '--users', '8', '--items', '20', '--messages', '30'];
    execFileSync(process.execPath, [generator, ...args, '--output', first]);
    execFileSync(process.execPath, [generator, ...args, '--output', second]);
    for (const name of ['users.csv', 'items.csv', 'messages.csv', 'manifest.json']) {
      assert.equal(hash(resolve(first, name)), hash(resolve(second, name)), `${name} must be deterministic`);
    }
    const manifest = JSON.parse(readFileSync(resolve(first, 'manifest.json'), 'utf8'));
    assert.deepEqual(manifest.counts, { users: 8, items: 20, messages: 30 });
    assert.equal(manifest.schemaVersion, 1);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('pinned Metrics Server manifest keeps the published checksum', () => {
  const normalized = readFileSync(resolve(common, 'third-party', 'metrics-server-v0.9.0.yaml'), 'utf8')
    .replaceAll('\r\n', '\n');
  assert.equal(
    createHash('sha256').update(normalized).digest('hex'),
    '1cec29a5267809306a2c6ec74a3e449abbb705b4a8beed0c8a1963910f72c79b',
  );
});

test('result and dataset schemas remain valid JSON', () => {
  assert.equal(JSON.parse(readFileSync(resolve(common, 'result.schema.json'), 'utf8')).type, 'object');
  assert.equal(JSON.parse(readFileSync(resolve(common, 'dataset', 'manifest.schema.json'), 'utf8')).type, 'object');
});

test('Metrics Server installer uses a patch file that survives Windows PowerShell native argument handling', () => {
  const installer = readFileSync(resolve(common, 'install-metrics-server.ps1'), 'utf8');
  assert.match(installer, /--patch-file\s+\$patchFile/);
  assert.doesNotMatch(installer, /\s-p\s+\$patch/);
  assert.match(installer, /already installed; preserving its ready Pod/);
});

test('k6 adapter pins the verified GHCR image digest and preserves native stderr', () => {
  const adapter = readFileSync(resolve(common, 'invoke-k6.ps1'), 'utf8');
  assert.match(adapter, /ghcr\.io\/grafana\/k6@sha256:2072ea9eafa596532d9aee0cc0e0a50cfb0e7fb703981a46179af5f4f22c5ea4/);
  assert.match(adapter, /Tee-Object -FilePath \$consolePath/);
});

test('smoke resource sampler reuses the current PowerShell host instead of requiring pwsh', () => {
  const smoke = readFileSync(resolve(common, 'run-smoke.ps1'), 'utf8');
  assert.match(smoke, /Get-Process\s+-Id\s+\$PID/);
  assert.doesNotMatch(smoke, /Get-Command\s+pwsh/);
});

test('PowerShell JSON writers use UTF-8 without BOM and verifier accepts legacy BOM evidence', () => {
  for (const path of [
    resolve(common, '..', 'run.ps1'),
    resolve(common, 'collect-environment.ps1'),
    resolve(common, 'invoke-k6.ps1'),
  ]) {
    const script = readFileSync(path, 'utf8');
    assert.match(script, /UTF8Encoding\]::new\(\$false\)/);
  }

  const root = mkdtempSync(resolve(tmpdir(), 'campus-evidence-bom-'));
  try {
    mkdirSync(resolve(root, 'logs'));
    writeFileSync(resolve(root, 'logs', 'probe.txt'), 'ok');
    const evidenceHash = hash(resolve(root, 'logs', 'probe.txt'));
    const result = {
      schemaVersion: 1,
      runId: 'smoke-20260902T000000Z-01234567',
      experiment: 'smoke',
      status: 'PASS',
      startedAt: '2026-09-02T00:00:00.000Z',
      finishedAt: '2026-09-02T00:00:01.000Z',
      gitCommit: '0123456789abcdef0123456789abcdef01234567',
      context: 'kind-campus-ci',
      namespace: 'campus-market',
      failure: null,
      evidence: [{ path: 'logs/probe.txt', sha256: evidenceHash, bytes: 2 }],
    };
    const resultPath = resolve(root, 'result.json');
    writeFileSync(resultPath, `\uFEFF${JSON.stringify(result)}`, 'utf8');
    const output = execFileSync(process.execPath, [resolve(common, 'verify-evidence.mjs'), resultPath], { encoding: 'utf8' });
    assert.match(output, /Evidence verified/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('all delegated experiment adapters publish the same small runner interface', () => {
  for (const name of ['hpa', 'fault', 'performance']) {
    const contract = readFileSync(resolve(common, '..', name, 'README.md'), 'utf8');
    for (const parameter of ['$RunDirectory', '$Context', '$Namespace', '$BaseUrl']) assert.match(contract, new RegExp(`\\${parameter}`));
  }
});
