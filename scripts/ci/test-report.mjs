#!/usr/bin/env node

/**
 * Merge JUnit XML from Maven Surefire/Failsafe and Playwright into one report.
 *
 * No third-party package is required, so the same command works on Windows,
 * Linux and CI runners. XML files can be supplied explicitly with --input or
 * discovered from the conventional project output directories.
 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';

const root = process.cwd();
const args = parseArgs(process.argv.slice(2));

if (args.selfTest) {
  runSelfTest();
  process.stdout.write('test-report self-test passed\n');
  process.exit(0);
}

const inputs = args.input.length ? args.input : discoverInputs(root);
const suites = inputs.flatMap((file) => parseJUnit(file, classify(file)));
const report = makeReport(suites, args);
const outputDir = path.resolve(root, args.output ?? 'test-results/summary');
fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, 'test-report.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
fs.writeFileSync(path.join(outputDir, 'test-report.md'), renderMarkdown(report), 'utf8');
process.stdout.write(`Wrote ${path.join(outputDir, 'test-report.json')}\n`);
process.stdout.write(`Wrote ${path.join(outputDir, 'test-report.md')}\n`);
if (report.summary.failed > 0 || report.summary.errors > 0) process.exitCode = 1;
if (report.summary.total.total === 0 && !args.allowEmpty) process.exitCode = 1;

function parseArgs(argv) {
  const result = { input: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const value = argv[i];
    if (value === '--self-test') result.selfTest = true;
    else if (value === '--allow-empty') result.allowEmpty = true;
    else if (value === '--input') result.input.push(path.resolve(root, argv[++i]));
    else if (value === '--output') result.output = argv[++i];
    else if (value === '--commit') result.commit = argv[++i];
    else if (value === '--branch') result.branch = argv[++i];
    else if (value === '--environment') result.environment = argv[++i];
    else if (value === '--java') result.java = argv[++i];
    else if (value === '--database') result.database = argv[++i];
    else if (value === '--docker') result.docker = argv[++i];
    else if (value === '--kubernetes') result.kubernetes = argv[++i];
    else if (value === '--help') {
      process.stdout.write('Usage: node scripts/ci/test-report.mjs [--input file] [--output dir] [--commit sha] [--branch name] [--environment name]\n');
      process.exit(0);
    } else throw new Error(`Unknown option: ${value}`);
  }
  return result;
}

function discoverInputs(projectRoot) {
  const candidates = [
    ['unit', path.join(projectRoot, 'backend', 'target', 'surefire-reports')],
    ['integration', path.join(projectRoot, 'backend', 'target', 'failsafe-reports')],
    ['e2e', path.join(projectRoot, 'e2e', 'test-results')],
  ];
  return candidates.flatMap(([, dir]) => exists(dir) ? findXml(dir) : []);
}

function findXml(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return findXml(full);
    return entry.name.toLowerCase().endsWith('.xml') ? [full] : [];
  });
}

function classify(file) {
  const normalized = file.replaceAll('\\', '/').toLowerCase();
  if (normalized.includes('failsafe') || normalized.includes('/integration')) return 'integration';
  if (normalized.includes('/e2e') || normalized.includes('playwright')) return 'e2e';
  return 'unit';
}

function parseJUnit(file, kind) {
  if (!exists(file)) return [];
  const source = fs.readFileSync(file, 'utf8');
  const suiteMatches = [...source.matchAll(/<testsuite\b([^>]*)>([\s\S]*?)<\/testsuite\s*>/gi)];
  const fragments = suiteMatches.length
    ? suiteMatches
    : [[null, source.match(/<testsuites?\b([^>]*)>/i)?.[1] ?? '', source]];
  return fragments.map((match) => {
    const attributes = attrs(match[1]);
    const cases = [...match[2].matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase\s*>)/gi)].map((test) => {
      const body = test[2] ?? '';
      const attr = attrs(test[1]);
      const failure = body.match(/<(failure|error)\b([^>]*)>([\s\S]*?)<\/\1\s*>/i);
      const skipped = /<skipped\b/i.test(body);
      return {
        name: attr.name ?? '(unnamed)',
        classname: attr.classname ?? '',
        durationSeconds: number(attr.time),
        status: failure ? (failure[1].toLowerCase() === 'error' ? 'error' : 'failed') : skipped ? 'skipped' : 'passed',
        message: failure ? (attrs(failure[2]).message ?? text(failure[3])) : '',
      };
    });
    return { kind, file: path.relative(root, file).replaceAll('\\', '/'), name: attributes.name ?? path.basename(file), cases };
  });
}

function makeReport(suites, options) {
  const cases = suites.flatMap((suite) => suite.cases.map((test) => ({ ...test, kind: suite.kind, suite: suite.name, file: suite.file })));
  const summary = ['unit', 'integration', 'e2e'].reduce((all, kind) => { all[kind] = stats(cases.filter((test) => test.kind === kind)); return all; }, {});
  summary.total = stats(cases);
  summary.failed = summary.total.failed;
  summary.errors = summary.total.errors;
  return {
    schemaVersion: '1.0',
    generatedAt: new Date().toISOString(),
    commit: options.commit ?? process.env.GITHUB_SHA ?? process.env.GIT_COMMIT ?? gitValue(['rev-parse', 'HEAD']),
    branch: options.branch ?? process.env.GITHUB_REF_NAME ?? process.env.GIT_BRANCH ?? gitValue(['branch', '--show-current']),
    environment: options.environment ?? (process.env.CI ? 'ci' : 'local'),
    runtime: {
      os: `${os.type()} ${os.release()}`,
      architecture: process.arch,
      node: process.version,
      java: options.java ?? process.env.JAVA_VERSION ?? commandVersion('java', ['-version']),
      database: options.database ?? process.env.TEST_DATABASE ?? 'not-reported',
      docker: options.docker ?? process.env.DOCKER_VERSION ?? 'not-reported',
      kubernetes: options.kubernetes ?? process.env.KUBERNETES_VERSION ?? 'not-reported',
    },
    inputs: [...new Set(suites.map((suite) => suite.file))],
    summary,
    failures: cases.filter((test) => test.status === 'failed' || test.status === 'error'),
    suites,
  };
}

function stats(cases) {
  return {
    total: cases.length,
    passed: cases.filter((test) => test.status === 'passed').length,
    failed: cases.filter((test) => test.status === 'failed').length,
    errors: cases.filter((test) => test.status === 'error').length,
    skipped: cases.filter((test) => test.status === 'skipped').length,
    durationSeconds: Number(cases.reduce((sum, test) => sum + test.durationSeconds, 0).toFixed(3)),
  };
}

function renderMarkdown(report) {
  const row = (name, stat) => `| ${name} | ${stat.total} | ${stat.passed} | ${stat.failed} | ${stat.errors} | ${stat.skipped} | ${stat.durationSeconds}s |`;
  const failures = report.failures.length ? report.failures.map((failure) => `- **${failure.kind}** ${failure.classname ? `${failure.classname} :: ` : ''}${failure.name} — ${failure.message || 'no message'} (${failure.file})`).join('\n') : '无失败用例。';
  return `# Test report\n\n- 提交号：\`${report.commit}\`\n- 分支：\`${report.branch}\`\n- 环境：\`${report.environment}\`\n- 生成时间：\`${report.generatedAt}\`\n- 操作系统：\`${report.runtime.os} (${report.runtime.architecture})\`\n- Java：\`${report.runtime.java}\`\n- Node：\`${report.runtime.node}\`\n- 数据库：\`${report.runtime.database}\`\n- Docker：\`${report.runtime.docker}\`\n- Kubernetes：\`${report.runtime.kubernetes}\`\n\n## 测试统计\n\n| 类型 | 总数 | 通过 | 失败 | 错误 | 跳过 | 时长 |\n| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n${row('单元测试', report.summary.unit)}\n${row('API/集成测试', report.summary.integration)}\n${row('E2E 测试', report.summary.e2e)}\n${row('总计', report.summary.total)}\n\n## 失败摘要\n\n${failures}\n\n## 输入报告\n\n${report.inputs.length ? report.inputs.map((input) => `- ${input}`).join('\n') : '- 未发现 JUnit XML（可作为空测试占位报告）'}\n`;
}

function attrs(source) {
  const result = {};
  for (const match of source.matchAll(/([:\w.-]+)\s*=\s*(["'])(.*?)\2/g)) result[match[1]] = decode(match[3]);
  return result;
}
function text(value) { return decode((value ?? '').replace(/<[^>]*>/g, '').trim()).slice(0, 1000); }
function decode(value) { return value.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&amp;', '&'); }
function number(value) { const parsed = Number(value ?? 0); return Number.isFinite(parsed) ? parsed : 0; }
function exists(file) { return fs.existsSync(file); }
function gitValue(command) {
  try { return execFileSync('git', command, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || 'unknown'; }
  catch { return 'unknown'; }
}
function commandVersion(command, argv) {
  const result = spawnSync(command, argv, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  const detail = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim().split(/\r?\n/)[0];
  return detail || 'not-reported';
}

function runSelfTest() {
  const temp = path.join(root, '.test-report-self-test.xml');
  fs.writeFileSync(temp, '<testsuite name="smoke"><testcase classname="Smoke" name="passes" time="0.1"/><testcase classname="Smoke" name="fails"><failure message="broken">details</failure></testcase><testcase name="skip"><skipped/></testcase></testsuite>');
  try {
    const suites = parseJUnit(temp, 'unit');
    const report = makeReport(suites, { commit: 'self-test', branch: 'self-test', environment: 'test' });
    assert.equal(report.summary.total.total, 3);
    assert.equal(report.summary.total.failed, 1);
    assert.equal(report.summary.total.skipped, 1);
  } finally { fs.rmSync(temp, { force: true }); }
}
