const { spawnSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { composeArgs, e2eRoot, environment, projectName } = require('./compose-config.cjs');

const result = spawnSync('docker', ['compose', '-p', projectName, ...composeArgs, 'config', '--services'], {
  cwd: e2eRoot,
  env: environment,
  encoding: 'utf8',
  shell: false
});
if (result.error) throw result.error;
if (result.status !== 0) throw new Error(result.stderr || 'Unable to resolve E2E Compose services.');

const serviceNames = new Set(result.stdout.split(/\r?\n/).filter(Boolean));
const nginx = readFileSync(resolve(e2eRoot, '../frontend/deploy/nginx.conf'), 'utf8');
const upstreamHosts = [...nginx.matchAll(/proxy_pass\s+http:\/\/([a-z0-9-]+)/g)].map(match => match[1]);
if (upstreamHosts.length === 0) throw new Error('Production Nginx config has no proxy_pass upstream.');

const missingHosts = [...new Set(upstreamHosts.filter(host => !serviceNames.has(host)))];
if (missingHosts.length > 0) {
  throw new Error(`Nginx references services absent from the E2E topology: ${missingHosts.join(', ')}`);
}

const requiredServices = ['mysql', 'redis', 'rabbitmq', 'api-gateway', 'account-service',
  'marketplace-service', 'trading-service', 'governance-service', 'mailpit', 'web'];
const missingServices = requiredServices.filter(service => !serviceNames.has(service));
if (missingServices.length > 0) {
  throw new Error(`E2E topology is incomplete: ${missingServices.join(', ')}`);
}

console.log(`Microservice E2E routing contract passed: ${[...new Set(upstreamHosts)].join(', ')}`);
