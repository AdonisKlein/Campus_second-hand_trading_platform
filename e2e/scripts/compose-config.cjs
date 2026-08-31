const { resolve } = require('node:path');

const e2eRoot = resolve(__dirname, '..');
const projectName = process.env.E2E_COMPOSE_PROJECT || 'campus-secondhand-e2e';
const composeFiles = [
  '../deploy/docker-compose.yml',
  '../deploy/docker-compose.mailpit.yml',
  'docker-compose.e2e.yml'
];
const composeArgs = composeFiles.flatMap(file => ['-f', file]);
const environment = {
  ...process.env,
  MYSQL_ROOT_PASSWORD: 'e2e-root-password',
  ACCOUNT_DB_PASSWORD: 'e2e-account-password',
  MARKETPLACE_DB_PASSWORD: 'e2e-marketplace-password',
  TRADING_DB_PASSWORD: 'e2e-trading-password',
  GOVERNANCE_DB_PASSWORD: 'e2e-governance-password',
  REDIS_PASSWORD: 'e2e-redis-password',
  RABBITMQ_USERNAME: 'e2e-rabbit',
  RABBITMQ_PASSWORD: 'e2e-rabbit-password',
  INTERNAL_SERVICE_TOKEN: 'e2e-internal-service-token-32-chars',
  INTERNAL_JWT_SECRET: 'e2e-internal-jwt-secret-at-least-32-characters',
  VERIFICATION_PEPPER: 'e2e-only-verification-pepper-at-least-32-characters',
  CORS_ORIGINS: 'http://127.0.0.1:18080',
  SESSION_COOKIE_SECURE: 'false',
  WEB_PORT: '127.0.0.1:18080',
  MAILPIT_WEB_PORT: '18025'
};

module.exports = { composeArgs, e2eRoot, environment, projectName };
