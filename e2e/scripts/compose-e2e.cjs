const { spawnSync } = require('node:child_process');
const { readFileSync } = require('node:fs');

const composeFile = 'docker-compose.e2e.yml';
const projectName = process.env.E2E_COMPOSE_PROJECT || 'campus-secondhand-e2e';
const compose = (args, options = {}) => {
  const result = spawnSync('docker', ['compose', '-p', projectName, '-f', composeFile, ...args], {
    cwd: __dirname + '/..',
    stdio: 'inherit',
    shell: false,
    ...options
  });
  if (result.error) throw result.error;
  return result.status ?? 1;
};

const action = process.argv[2] || 'up';
if (action === 'up') process.exit(compose(['up', '-d', '--build']));
if (action === 'down') process.exit(compose(['down', '--volumes', '--remove-orphans']));
if (action === 'seed') {
  const sql = readFileSync(__dirname + '/../fixtures/seed.sql');
  process.exit(compose(['exec', '-T', 'mysql', 'mysql', '-ucampus', '-pcampus-e2e-password',
    'campus_secondhand_e2e'], { input: sql, stdio: ['pipe', 'inherit', 'inherit'] }));
}
if (action === 'logs') process.exit(compose(['logs', '--tail=200']));
console.error(`Unknown action: ${action}. Use up, seed, down, or logs.`);
process.exit(2);
