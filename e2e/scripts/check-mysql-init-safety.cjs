const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

const initScript = resolve(__dirname, '../../deploy/mysql/init/01-databases.sh');
const source = readFileSync(initScript, 'utf8');

const unsafeNounset = [
  /^\s*set\s+-[^\r\n#]*u/m,
  /^\s*set\s+-o\s+nounset\b/m
];

if (unsafeNounset.some(pattern => pattern.test(source))) {
  throw new Error(
    'MySQL init scripts are sourced by docker-entrypoint.sh and must not enable nounset; ' +
    'it leaks into the parent entrypoint and breaks optional MYSQL_* variables.'
  );
}

console.log('MySQL sourced-init safety contract passed.');
