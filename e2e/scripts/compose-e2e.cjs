const { spawnSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const { composeArgs, e2eRoot, environment, projectName } = require('./compose-config.cjs');

const compose = (args, options = {}) => {
  const result = spawnSync('docker', ['compose', '-p', projectName, ...composeArgs, ...args], {
    cwd: e2eRoot,
    env: environment,
    stdio: 'inherit',
    shell: false,
    ...options
  });
  if (result.error) throw result.error;
  return result.status ?? 1;
};

const action = process.argv[2] || 'up';
if (action === 'up') process.exit(compose(['up', '-d', '--build', '--wait']));
if (action === 'down') process.exit(compose(['down', '--volumes', '--remove-orphans']));
if (action === 'seed') {
  const sql = readFileSync(`${e2eRoot}/fixtures/seed.sql`);
  process.exit(compose(['exec', '-T', 'mysql', 'mysql', '--default-character-set=utf8mb4',
    '-uroot', '-pe2e-root-password'], {
    input: sql,
    stdio: ['pipe', 'inherit', 'inherit']
  }));
}
if (action === 'logs') process.exit(compose(['logs', '--tail=200']));
console.error(`Unknown action: ${action}. Use up, seed, down, or logs.`);
process.exit(2);
