const { existsSync, readFileSync } = require('node:fs');
const { resolve } = require('node:path');

const root = resolve(__dirname, '..', '..');
const mappingPath = resolve(root, 'contracts/testing/uc-traceability.json');
const mapping = JSON.parse(readFileSync(mappingPath, 'utf8'));
const sourcePath = resolve(root, mapping.source);
const source = readFileSync(sourcePath, 'utf8');
const documented = [...source.matchAll(/^\|\s*(UC\d{2})\s*\|/gm)].map(match => match[1]);
const mapped = Object.keys(mapping.useCases).sort();
const expected = [...new Set(documented)].sort();

const failures = [];
if (expected.length === 0) failures.push(`No use cases found in ${mapping.source}`);
if (JSON.stringify(expected) !== JSON.stringify(mapped)) {
  failures.push(`Use-case IDs differ: document=[${expected.join(', ')}], mapping=[${mapped.join(', ')}]`);
}

for (const [useCase, entry] of Object.entries(mapping.useCases)) {
  if (!Array.isArray(entry.e2e) || entry.e2e.length === 0) {
    failures.push(`${useCase} has no E2E evidence`);
    continue;
  }
  for (const evidence of entry.e2e) {
    const testPath = resolve(root, evidence.file);
    if (!existsSync(testPath)) {
      failures.push(`${useCase} references missing file: ${evidence.file}`);
      continue;
    }
    const testSource = readFileSync(testPath, 'utf8');
    const singleQuoted = `test('${evidence.test}'`;
    const doubleQuoted = `test("${evidence.test}"`;
    const titleIndex = Math.max(testSource.indexOf(singleQuoted), testSource.indexOf(doubleQuoted));
    if (titleIndex < 0) {
      failures.push(`${useCase} references missing test title in ${evidence.file}: ${evidence.test}`);
      continue;
    }
    const nextTest = testSource.indexOf('\ntest(', titleIndex + 5);
    const testBlock = testSource.slice(titleIndex, nextTest < 0 ? undefined : nextTest);
    if (!/\bexpect\s*\(/.test(testBlock)) {
      failures.push(`${useCase} references a test without an assertion in ${evidence.file}: ${evidence.test}`);
    }
  }
}

if (failures.length) {
  console.error('UC traceability check failed:');
  failures.forEach(failure => console.error(`- ${failure}`));
  process.exit(1);
}
console.log(`UC traceability check passed: ${expected.length} use cases (${expected.join(', ')}).`);
