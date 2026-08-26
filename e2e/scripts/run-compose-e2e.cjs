const { spawnSync } = require('node:child_process');

const run = (command, args, options = {}) => {
  const result = spawnSync(command, args, { cwd: __dirname + '/..', stdio: 'inherit', shell: false, ...options });
  if (result.error) throw result.error;
  return result.status ?? 1;
};

const keepEnvironment = process.env.KEEP_E2E_ENV === '1';
let exitCode = 1;
try {
  exitCode = run(process.execPath, ['scripts/compose-e2e.cjs', 'up']);
  if (exitCode === 0) exitCode = run(process.execPath, ['scripts/wait-for-e2e.cjs']);
  if (exitCode === 0) exitCode = run(process.execPath, ['scripts/compose-e2e.cjs', 'seed']);
  if (exitCode === 0) {
    const playwrightCli = require.resolve('@playwright/test/cli');
    exitCode = run(process.execPath, [playwrightCli, 'test', ...process.argv.slice(2)], {
      env: { ...process.env, E2E_BASE_URL: process.env.E2E_BASE_URL || 'http://127.0.0.1:18080' }
    });
  }
} finally {
  if (!keepEnvironment) run(process.execPath, ['scripts/compose-e2e.cjs', 'down']);
}
process.exit(exitCode);
