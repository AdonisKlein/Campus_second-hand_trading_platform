const { spawnSync } = require('node:child_process');
const { mkdirSync, rmSync, writeFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { composeArgs, environment } = require('./compose-config.cjs');

const e2eRoot = resolve(__dirname, '..');
const evidenceDir = resolve(e2eRoot, 'test-results');
const composeProject = process.env.E2E_COMPOSE_PROJECT || 'campus-secondhand-e2e';

const run = (command, args, options = {}) => {
  const result = spawnSync(command, args, { cwd: __dirname + '/..', stdio: 'inherit', shell: false, ...options });
  if (result.error) throw result.error;
  return result.status ?? 1;
};

const keepEnvironment = process.env.KEEP_E2E_ENV === '1';
let exitCode = 1;
let stage = 'uc-traceability';
try {
  exitCode = run(process.execPath, ['scripts/check-uc-traceability.cjs']);
  stage = 'routing-contract';
  if (exitCode === 0) exitCode = run(process.execPath, ['scripts/check-compose-routing.cjs']);
  stage = 'compose-up';
  if (exitCode === 0) exitCode = run(process.execPath, ['scripts/compose-e2e.cjs', 'up']);
  stage = 'environment-readiness';
  if (exitCode === 0) exitCode = run(process.execPath, ['scripts/wait-for-e2e.cjs']);
  stage = 'database-seed';
  if (exitCode === 0) exitCode = run(process.execPath, ['scripts/compose-e2e.cjs', 'seed']);
  if (exitCode === 0) {
    stage = 'playwright';
    mkdirSync(evidenceDir, { recursive: true });
    rmSync(resolve(evidenceDir, 'junit.xml'), { force: true });
    const playwrightCli = require.resolve('@playwright/test/cli');
    exitCode = run(process.execPath, [playwrightCli, 'test', ...process.argv.slice(2)], {
      env: { ...process.env, E2E_BASE_URL: process.env.E2E_BASE_URL || 'http://127.0.0.1:18080' }
    });
    if (exitCode === 0 && process.argv.slice(2).length === 0) {
      stage = 'runtime-uc-traceability';
      exitCode = run(process.execPath, ['scripts/check-uc-runtime-evidence.cjs']);
    }
  }
} finally {
  mkdirSync(evidenceDir, { recursive: true });
  if (exitCode !== 0) {
    const logs = spawnSync('docker', ['compose', '-p', composeProject, ...composeArgs,
      'logs', '--no-color', '--tail=200'], {
      cwd: e2eRoot, env: environment, encoding: 'utf8', shell: false
    });
    const logText = `${logs.stdout || ''}${logs.stderr || ''}`;
    writeFileSync(resolve(evidenceDir, 'compose-startup.log'), logText || 'Compose produced no logs.\n');
    if (logText) process.stderr.write(logText);
  }
  if (!keepEnvironment) run(process.execPath, ['scripts/compose-e2e.cjs', 'down']);
  writeFileSync(resolve(evidenceDir, 'compose-run-summary.txt'), `stage=${stage}\nexitCode=${exitCode}\n`);
}
process.exit(exitCode);
