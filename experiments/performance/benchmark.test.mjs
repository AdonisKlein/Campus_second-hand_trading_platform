import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { chmod, copyFile, mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { embedRunConfig } from './generate-benchmark.mjs';
import { summarizeComparison, summarizeDirectory } from './summarize-results.mjs';

const directory = resolve(import.meta.dirname);
const benchmark = readFileSync(resolve(directory, 'benchmark.js'), 'utf8');
const runner = readFileSync(resolve(directory, 'run.ps1'), 'utf8');
const lifecycle = resolve(directory, 'lifecycle.ps1');
const resourceSampler = resolve(directory, 'resource-sampler.ps1');
const expectedIds = [5278, 7433, 7654, 8519, 11496, 18637, 3742, 13457, 4813, 9944];

test('fixed item IDs and endpoint tags are immutable benchmark configuration', () => {
  const idMatch = benchmark.match(/FIXED_ITEM_IDS\s*=\s*Object\.freeze\(\[([^\]]+)]\)/);
  assert.ok(idMatch);
  assert.deepEqual(idMatch[1].split(',').map((value) => Number(value.trim())), expectedIds);
  const runnerIdMatch = runner.match(/\$fixedItemIds\s*=\s*@\(([^)]+)\)/);
  assert.ok(runnerIdMatch);
  assert.deepEqual(runnerIdMatch[1].split(',').map((value) => Number(value.trim())), expectedIds);
  assert.ok(expectedIds.every((id) => Number.isInteger(id) && id >= 1 && id <= 20000));
  assert.match(benchmark, /itemsList:\s*'items_list'/);
  assert.match(benchmark, /itemDetail:\s*'item_detail'/);
  assert.match(benchmark, /itemMessages:\s*'item_messages'/);
  assert.match(runner, /@\("items_list", "item_detail", "item_messages"\)/);
});

test('iteration requests the three public endpoints in fixed order without identity state', () => {
  const list = benchmark.indexOf('`${baseUrl}/api/items`');
  const detail = benchmark.indexOf('`${baseUrl}/api/items/${itemId}`');
  const messages = benchmark.indexOf('`${baseUrl}/api/messages/item/${itemId}`');
  assert.ok(list >= 0 && list < detail && detail < messages);
  assert.doesNotMatch(benchmark, /\/api\/auth\/login|\bCookie\b|Authorization|http\.cookieJar/i);
  assert.doesNotMatch(benchmark, /Math\.random/);
});

test('benchmark parses each response once and writes machine-readable summary', () => {
  assert.equal((benchmark.match(/parseJson\(/g) || []).length, 4); // helper declaration plus three calls
  assert.match(benchmark, /K6_SUMMARY_PATH/);
  assert.match(benchmark, /JSON\.stringify\(data, null, 2\)/);
  assert.match(benchmark, /benchmark_json_errors/);
});

test('generated benchmark embeds config once and remains self-contained JavaScript', async () => {
  const generated = embedRunConfig(benchmark, { vus:50, duration:'30s', architecture:'microservices-end', runLabel:'quick-50', validationMode:'record' });
  assert.match(generated, /const EMBEDDED_RUN_CONFIG = \{"vus":50,"duration":"30s"/);
  assert.doesNotMatch(generated, /__PERFORMANCE_RUN_CONFIG__/);
  assert.doesNotMatch(generated, /import .*benchmark\.js/);
  const directory = await mkdtemp(resolve(tmpdir(), 'generated-benchmark-'));
  const path = resolve(directory, 'benchmark.generated.js');
  await writeFile(path, generated, 'utf8');
  assert.equal(spawnSync(process.execPath, ['--check', path]).status, 0);
});

test('PowerShell runner keeps the public interface and delegates Docker k6 execution', () => {
  assert.match(runner, /param\([\s\S]*\[string]\$RunDirectory,[\s\S]*\[string]\$Context[\s\S]*\[string]\$Namespace[\s\S]*\[string]\$BaseUrl/);
  assert.match(runner, /common["']?\)?[\s\S]*invoke-k6\.ps1/);
  assert.match(runner, /collect-resources\.ps1/);
  assert.doesNotMatch(runner, /ghcr\.io\/grafana\/k6|docker\s+run/i);
  assert.match(runner, /\$matrixVus\s*=\s*@\(10, 50, 100\)/);
  assert.match(runner, /\$matrixRepeats\s*=\s*3/);
  assert.match(runner, /-Vus 1[\s\S]*-Duration "10s"[\s\S]*-ValidationMode "strict"/);
  assert.match(runner, /"10s"[\s\S]*"30s"[\s\S]*10/);
  assert.match(runner, /"2m"[\s\S]*"5m"[\s\S]*120/);
});

function rejected(environment) {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', resolve(directory, 'run.ps1'), '-RunDirectory', directory], {
    encoding: 'utf8',
    env: { ...process.env, ...environment },
  });
}

test('invalid mode and missing formal confirmation fail before Docker', () => {
  assert.notEqual(rejected({ PERFORMANCE_MODE: 'invalid' }).status, 0);
  assert.notEqual(rejected({ PERFORMANCE_MODE: 'formal', ARCHITECTURE:'midterm-check', PERFORMANCE_CONFIRM:'' }).status, 0);
});

async function fakeKind(body) {
  const root = await mkdtemp(resolve(tmpdir(), 'fake-kind-'));
  const path = resolve(root, process.platform === 'win32' ? 'kind.cmd' : 'kind');
  await writeFile(path, body, 'utf8');
  if (process.platform !== 'win32') await chmod(path, 0o755);
  return path;
}

let compiledNativeFixture;
async function nativeFixtureExecutable() {
  if (!compiledNativeFixture) compiledNativeFixture = (async () => {
    const root = await mkdtemp(resolve(tmpdir(), 'native-argv-fixture-'));
    const executable = resolve(root, 'fixture.exe');
    const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', 'Add-Type -Path $env:FIXTURE_SOURCE -OutputAssembly $env:FIXTURE_EXE -OutputType ConsoleApplication'], {
      encoding: 'utf8', env: { ...process.env, FIXTURE_SOURCE: resolve(directory, 'native-argv-fixture.cs'), FIXTURE_EXE: executable },
    });
    assert.equal(result.status, 0, result.stderr);
    return executable;
  })();
  return compiledNativeFixture;
}

async function fakeNative({ stdout = '', stderr = '', exitCode = 0, echoArguments = false } = {}) {
  if (process.platform !== 'win32') {
    if (echoArguments) return fakeKind('#!/bin/sh\nprintf "ARGC=%s\\n" "$#"\ni=0; for value in "$@"; do printf "ARGV[%s]=%s\\n" "$i" "$(printf %s "$value" | base64)"; i=$((i+1)); done\n');
    return fakeKind(`#!/bin/sh\nprintf %s '${stdout}'\nprintf %s '${stderr}' >&2\nexit ${exitCode}\n`);
  }
  const root = await mkdtemp(resolve(tmpdir(), 'fake-native-'));
  const executable = resolve(root, 'fixture.exe');
  await copyFile(await nativeFixtureExecutable(), executable);
  await writeFile(`${executable}.fixture`, [String(exitCode), Buffer.from(stdout).toString('base64'), Buffer.from(stderr).toString('base64'), String(echoArguments)].join('\n'), 'utf8');
  return executable;
}

function invokeKindHelper(fakeKindPath) {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:LIFECYCLE_PATH; $clusters = @(Get-KindClustersSafe -KindCommand $env:FAKE_KIND); "COUNT=$($clusters.Count)"; $clusters';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, LIFECYCLE_PATH: lifecycle, FAKE_KIND: fakeKindPath },
  });
}

test('Kind discovery treats the empty-cluster stderr message as a successful empty array', async () => {
  const body = process.platform === 'win32'
    ? '@echo off\r\necho No kind clusters found. 1>&2\r\nexit /b 0\r\n'
    : '#!/bin/sh\necho "No kind clusters found." >&2\nexit 0\n';
  const result = invokeKindHelper(await fakeKind(body));
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), 'COUNT=0');
});

test('Kind discovery returns only cluster names and rejects a real command failure', async () => {
  const successBody = process.platform === 'win32'
    ? '@echo off\r\necho campus-dev\r\necho campus-performance\r\nexit /b 0\r\n'
    : '#!/bin/sh\nprintf "campus-dev\\ncampus-performance\\n"\nexit 0\n';
  const success = invokeKindHelper(await fakeKind(successBody));
  assert.equal(success.status, 0, success.stderr);
  assert.deepEqual(success.stdout.trim().split(/\r?\n/), ['COUNT=2', 'campus-dev', 'campus-performance']);

  const failureBody = process.platform === 'win32'
    ? '@echo off\r\necho daemon unavailable 1>&2\r\nexit /b 7\r\n'
    : '#!/bin/sh\necho "daemon unavailable" >&2\nexit 7\n';
  const failure = invokeKindHelper(await fakeKind(failureBody));
  assert.notEqual(failure.status, 0);
  assert.match(failure.stderr, /exit code 7/);
});

async function fakeKindWorkspace(scriptBody) {
  const root = await mkdtemp(resolve(tmpdir(), 'fake-kind-workspace-'));
  const scripts = resolve(root, 'scripts', 'ci');
  await mkdir(scripts, { recursive: true });
  await writeFile(resolve(scripts, 'kind-local.ps1'), scriptBody, 'utf8');
  return root;
}

function invokeKindLifecycle(workspace, evidence, kindCommand, action = 'up', dockerCommand = 'docker', kubectlCommand = 'kubectl') {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:LIFECYCLE_PATH; $beforePath=$env:PATH; $global:LASTEXITCODE=4; Invoke-KindLifecycle $env:KIND_WORKSPACE $env:KIND_ACTION "test-cluster" $env:KIND_EVIDENCE -KindCommand $env:FAKE_KIND_STATE -DockerCommand $env:DOCKER_COMMAND -KubectlCommand $env:KUBECTL_COMMAND; "PARENT_LASTEXITCODE=$global:LASTEXITCODE"; "PARENT_PATH_UNCHANGED=$($env:PATH -ceq $beforePath)"';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, LIFECYCLE_PATH: lifecycle, KIND_WORKSPACE: workspace, KIND_EVIDENCE: evidence, FAKE_KIND_STATE: kindCommand, KIND_ACTION: action, DOCKER_COMMAND: dockerCommand, KUBECTL_COMMAND: kubectlCommand },
  });
}

async function fakeKindState(clusters = [], exitCode = 0) {
  if (process.platform === 'win32') return fakeNative({
    stdout: clusters.length ? `${clusters.join('\r\n')}\r\n` : '',
    stderr: clusters.length ? '' : 'No kind clusters found.\r\n',
    exitCode,
  });
  const output = clusters.join(process.platform === 'win32' ? '\r\necho ' : '\\n');
  const body = process.platform === 'win32'
    ? `@echo off\r\n${clusters.length ? `echo ${output}\r\n` : 'echo No kind clusters found. 1>&2\r\n'}exit /b ${exitCode}\r\n`
    : `#!/bin/sh\n${clusters.length ? `printf '${output}\\n'\n` : 'echo "No kind clusters found." >&2\n'}exit ${exitCode}\n`;
  return fakeKind(body);
}

test('Kind lifecycle runspace ignores stale LASTEXITCODE and isolates the parent session', async () => {
  const workspace = await fakeKindWorkspace('$global:LASTEXITCODE = 9\nWrite-Output "child completed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const result = invokeKindLifecycle(workspace, evidence, await fakeKindState(['test-cluster']));
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /child completed/);
  assert.match(result.stdout, /PARENT_LASTEXITCODE=4/);
  assert.match(result.stdout, /PARENT_PATH_UNCHANGED=True/);
  assert.match(await readFile(resolve(evidence, 'kind-up.stdout.log'), 'utf8'), /child completed/);
  assert.equal(await readFile(resolve(evidence, 'kind-up.stderr.log'), 'utf8'), '');
});

test('Kind lifecycle reports a failed runspace and preserves its evidence', async () => {
  const workspace = await fakeKindWorkspace('throw "child failed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const result = invokeKindLifecycle(workspace, evidence, await fakeKindState(['test-cluster']));
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /state Failed/);
  assert.match(await readFile(resolve(evidence, 'kind-up.stderr.log'), 'utf8'), /child failed/);
});

test('Kind lifecycle keeps benign Error stream records and relies on cluster postconditions', async () => {
  const workspace = await fakeKindWorkspace('Write-Error "benign-native-like-error"\nWrite-Output "completed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const up = invokeKindLifecycle(workspace, evidence, await fakeKindState(['test-cluster']));
  assert.equal(up.status, 0, up.stderr);
  assert.match(await readFile(resolve(evidence, 'kind-up.stderr.log'), 'utf8'), /benign-native-like-error/);

  const downEvidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const down = invokeKindLifecycle(workspace, downEvidence, await fakeKindState([]), 'down');
  assert.equal(down.status, 0, down.stderr);
});

test('Kind lifecycle rejects unmet up and down cluster postconditions', async () => {
  const workspace = await fakeKindWorkspace('Write-Output "completed"\n');
  const upEvidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const missing = invokeKindLifecycle(workspace, upEvidence, await fakeKindState([]));
  assert.notEqual(missing.status, 0);
  assert.match(missing.stderr, /cluster 'test-cluster' does not exist/);

  const downEvidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const remaining = invokeKindLifecycle(workspace, downEvidence, await fakeKindState(['test-cluster']), 'down');
  assert.notEqual(remaining.status, 0);
  assert.match(remaining.stderr, /cluster 'test-cluster' still exists/);
});

test('Kind lifecycle shim accepts kind stderr with exit 0 but propagates a checked exit 1', async () => {
  const successKind = await fakeNative({ stderr: 'No kind clusters found.\n' });
  const successWorkspace = await fakeKindWorkspace('$ErrorActionPreference = "Stop"\nkind get clusters\nif ($LASTEXITCODE -ne 0) { throw "native exit $LASTEXITCODE" }\nWrite-Output "native completed"\n');
  const successEvidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const success = invokeKindLifecycle(successWorkspace, successEvidence, successKind, 'down');
  assert.equal(success.status, 0, success.stderr);
  assert.match(await readFile(resolve(successEvidence, 'kind-down.stderr.log'), 'utf8'), /No kind clusters found/);

  const failureKind = await fakeNative({ stderr: 'native failed\n', exitCode: 1 });
  const failureWorkspace = await fakeKindWorkspace('$ErrorActionPreference = "Stop"\nkind get clusters\nif ($LASTEXITCODE -ne 0) { throw "native exit $LASTEXITCODE" }\n');
  const failureEvidence = await mkdtemp(resolve(tmpdir(), 'kind-lifecycle-evidence-'));
  const failure = invokeKindLifecycle(failureWorkspace, failureEvidence, failureKind, 'down');
  assert.notEqual(failure.status, 0);
  assert.match(failure.stderr, /state Failed/);
  assert.match(await readFile(resolve(failureEvidence, 'kind-down.stderr.log'), 'utf8'), /native failed|native exit 1/);
});

test('Docker BuildKit-style stderr uses the shared production helper and preserves real failures', async () => {
  const successDocker = await fakeNative({ stderr: '#0 building with "desktop-linux" instance using docker driver\n' });
  const workspace = await fakeKindWorkspace('$ErrorActionPreference="Stop"\ndocker build -t fixture:dev "D:\\path with spaces\\中文"\nif ($LASTEXITCODE -ne 0) { throw "docker exit $LASTEXITCODE" }\nWrite-Output "docker completed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'docker-lifecycle-evidence-'));
  const success = invokeKindLifecycle(workspace, evidence, await fakeKindState([]), 'down', successDocker);
  assert.equal(success.status, 0, success.stderr);
  const stderrEvidence = await readFile(resolve(evidence, 'kind-down.stderr.log'), 'utf8');
  assert.match(stderrEvidence, /#0 building with "desktop-linux"/);
  assert.doesNotMatch(stderrEvidence, /NativeCommandError/);

  const failedDocker = await fakeNative({ stderr: 'build failed\n', exitCode: 1 });
  const failedEvidence = await mkdtemp(resolve(tmpdir(), 'docker-lifecycle-evidence-'));
  const failure = invokeKindLifecycle(workspace, failedEvidence, await fakeKindState([]), 'down', failedDocker);
  assert.notEqual(failure.status, 0);
  assert.match(await readFile(resolve(failedEvidence, 'kind-down.stderr.log'), 'utf8'), /build failed|docker exit 1/);
});

test('real kind.exe empty-cluster stderr completes through the lifecycle runspace shim', { skip: process.platform !== 'win32' }, async () => {
  const direct = spawnSync('kind', ['get', 'clusters'], { encoding: 'utf8' });
  assert.equal(direct.status, 0, direct.stderr);
  assert.match(`${direct.stdout}${direct.stderr}`, /No kind clusters found\./);

  const workspace = await fakeKindWorkspace('$ErrorActionPreference = "Stop"\nkind get clusters\nif ($LASTEXITCODE -ne 0) { throw "kind discovery exit $LASTEXITCODE" }\nWrite-Output "real-kind-completed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'real-kind-lifecycle-evidence-'));
  const result = invokeKindLifecycle(workspace, evidence, 'kind', 'down');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /real-kind-completed/);
  const stderrEvidence = await readFile(resolve(evidence, 'kind-down.stderr.log'), 'utf8');
  assert.match(stderrEvidence, /No kind clusters found\./);
  assert.doesNotMatch(stderrEvidence, /NativeCommandError|kind\.exe\s*:/);
});

test('relative evidence directory produces an absolute PATH wrapper for real kind and Docker', { skip: process.platform !== 'win32' }, async () => {
  const artifactRoot = resolve('artifacts', 'cloud-native');
  await mkdir(artifactRoot, { recursive: true });
  const absoluteEvidence = await mkdtemp(resolve(artifactRoot, 'relative-wrapper-fixture-'));
  const relativeEvidence = relative(process.cwd(), absoluteEvidence);
  const workspace = await fakeKindWorkspace('$ErrorActionPreference="Stop"\n$firstPath=($env:PATH -split [IO.Path]::PathSeparator)[0]\nWrite-Output "PATH_FIRST=$firstPath"\nWrite-Output "PATH_ROOTED=$([IO.Path]::IsPathRooted($firstPath))"\nkind get clusters\nif ($LASTEXITCODE -ne 0) { throw "kind discovery exit $LASTEXITCODE" }\ndocker version\nif ($LASTEXITCODE -ne 0) { throw "docker version exit $LASTEXITCODE" }\nWrite-Output "relative-wrapper-completed"\n');
  try {
    const result = invokeKindLifecycle(workspace, relativeEvidence, 'kind', 'down');
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /PATH_ROOTED=True/);
    const firstPath = result.stdout.match(/PATH_FIRST=(.+)/)?.[1].trim() ?? '';
    assert.ok(firstPath && resolve(firstPath) === firstPath);
    assert.match(result.stdout, /relative-wrapper-completed/);
    const evidence = await readFile(resolve(absoluteEvidence, 'kind-down.stderr.log'), 'utf8');
    assert.match(evidence, /No kind clusters found\./);
    assert.doesNotMatch(`${result.stdout}${result.stderr}${evidence}`, /cannot run executable found relative to current directory|NativeCommandError/i);
  } finally {
    await rm(absoluteEvidence, { recursive: true, force: true });
  }
});

test('real Docker and kubectl read-only commands use the lifecycle PATH wrappers under EAP Stop', { skip: process.platform !== 'win32' }, async () => {
  const workspace = await fakeKindWorkspace('$ErrorActionPreference="Stop"\ndocker version\nif ($LASTEXITCODE -ne 0) { throw "docker version exit $LASTEXITCODE" }\nkubectl version --client -o json\nif ($LASTEXITCODE -ne 0) { throw "kubectl client exit $LASTEXITCODE" }\nWrite-Output "native-clients-completed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'native-wrapper-evidence-'));
  const result = invokeKindLifecycle(workspace, evidence, await fakeKindState([]), 'down');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /native-clients-completed/);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /NativeCommandError/);
});

test('production launcher preserves real Docker machine-readable template output', { skip: process.platform !== 'win32' }, async () => {
  const direct = spawnSync('docker', ['info', '--format', '{{json .}}'], { encoding: 'utf8' });
  assert.equal(direct.status, 0, direct.stderr);
  const directJson = JSON.parse(direct.stdout.trim());
  const workspace = await fakeKindWorkspace('$ErrorActionPreference="Stop"\ndocker info --format "{{json .}}"\nif ($LASTEXITCODE -ne 0) { throw "docker info exit $LASTEXITCODE" }\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'docker-template-evidence-'));
  const wrapped = invokeKindLifecycle(workspace, evidence, await fakeKindState([]), 'down');
  assert.equal(wrapped.status, 0, wrapped.stderr);
  const wrappedText = (await readFile(resolve(evidence, 'kind-down.stdout.log'), 'utf8')).trim();
  const wrappedJson = JSON.parse(wrappedText);
  delete directJson.SystemTime;
  delete wrappedJson.SystemTime;
  assert.deepEqual(wrappedJson, directJson);
  assert.ok(wrappedText.length > 0);
  assert.doesNotMatch(wrapped.stderr, /NativeCommandError/);
});

test('real kubectl --context client-only command passes through the lifecycle production shim', { skip: process.platform !== 'win32' }, async () => {
  const workspace = await fakeKindWorkspace('$ErrorActionPreference="Stop"\nkubectl --context definitely-unused-context version --client -o json\nif ($LASTEXITCODE -ne 0) { throw "kubectl client exit $LASTEXITCODE" }\nWrite-Output "kubectl-client-completed"\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'kubectl-shim-evidence-'));
  const result = invokeKindLifecycle(workspace, evidence, await fakeKindState([]), 'down');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /kubectl-client-completed/);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /找不到接受实际参数|positional parameter.*--context|NativeCommandError/i);
});

test('lifecycle PATH wrappers preserve comma and dash argument boundaries', async () => {
  const argvExecutable = await fakeNative({ echoArguments: true });
  const workspace = await fakeKindWorkspace('kind get clusters\nkind create cluster --name example\nkind load docker-image image1 image2 --name campus-performance\ndocker build -t example:test .\ndocker info --format "{{json .}}"\nkubectl get pods,svc,pvc\nkubectl --context abc get pods,svc,pvc -n test\n');
  const evidence = await mkdtemp(resolve(tmpdir(), 'cli-argv-evidence-'));
  const result = invokeKindLifecycle(workspace, evidence, argvExecutable, 'down', argvExecutable, argvExecutable);
  assert.equal(result.status, 0, result.stderr);
  const log = await readFile(resolve(evidence, 'kind-down.stdout.log'), 'utf8');
  const invocations = log.split(/(?=^ARGC=)/m).filter((entry) => entry.startsWith('ARGC=')).map((entry) => {
    const count = Number(entry.match(/^ARGC=(\d+)/)?.[1]);
    const args = [...entry.matchAll(/^ARGV\[\d+\]=(.+)$/gm)].map((match) => Buffer.from(match[1].trim(), 'base64').toString());
    assert.equal(args.length, count);
    return args;
  });
  assert.ok(invocations.some((args) => JSON.stringify(args) === JSON.stringify(['create', 'cluster', '--name', 'example'])));
  assert.ok(invocations.some((args) => JSON.stringify(args) === JSON.stringify(['load', 'docker-image', 'image1', 'image2', '--name', 'campus-performance'])));
  assert.ok(invocations.some((args) => JSON.stringify(args) === JSON.stringify(['build', '-t', 'example:test', '.'])));
  assert.ok(invocations.some((args) => JSON.stringify(args) === JSON.stringify(['info', '--format', '{{json .}}'])));
  assert.ok(invocations.some((args) => JSON.stringify(args) === JSON.stringify(['get', 'pods,svc,pvc'])));
  assert.ok(invocations.some((args) => JSON.stringify(args) === JSON.stringify(['--context', 'abc', 'get', 'pods,svc,pvc', '-n', 'test'])));
  const realExecutables = [...log.matchAll(/^EXECUTABLE=(.+)$/gm)].map((match) => match[1].trim());
  assert.ok(realExecutables.length >= 6);
  assert.ok(realExecutables.every((path) => !/[\\/]native-shims[\\/]/i.test(path)));
});

test('launcher refuses a real executable that resolves to its own shim directory', { skip: process.platform !== 'win32' }, async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'launcher-self-guard-'));
  const command = '. $env:LIFECYCLE_PATH; $commands=@{kind=(Get-Command kind.exe).Source;docker=(Get-Command docker.exe).Source;kubectl=(Get-Command kubectl.exe).Source}; $stderr=Join-Path $env:GUARD_ROOT "native.stderr.log"; $shim=New-PerformanceNativeWrappers $env:GUARD_ROOT $commands $stderr; $env:PERFORMANCE_KIND_EXECUTABLE=Join-Path $shim "kind.exe"; $env:PERFORMANCE_DOCKER_EXECUTABLE=$commands.docker; $env:PERFORMANCE_KUBECTL_EXECUTABLE=$commands.kubectl; $env:PERFORMANCE_NATIVE_STDERR=$stderr; $started=[DateTime]::UtcNow; & (Join-Path $shim "kind.exe") get clusters; $code=$LASTEXITCODE; "EXIT=$code"; "ELAPSED_MS=$([int]([DateTime]::UtcNow-$started).TotalMilliseconds)"; Get-Content -Raw $stderr';
  try {
    const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
      encoding: 'utf8', env: { ...process.env, LIFECYCLE_PATH: lifecycle, GUARD_ROOT: root }, timeout: 15000,
    });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /EXIT=126/);
    assert.match(result.stdout, /refused recursive self invocation.*inside native-shims/i);
    const elapsed = Number(result.stdout.match(/ELAPSED_MS=(\d+)/)?.[1]);
    assert.ok(elapsed < 5000, `self guard took ${elapsed}ms`);
    assert.equal((result.stdout.match(/refused recursive self invocation/gi) ?? []).length, 1);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test('Kind lifecycle production path is asynchronous and has no synchronous PowerShell Invoke call', () => {
  const source = readFileSync(lifecycle, 'utf8');
  const helperBody = source.slice(source.indexOf('function New-PerformanceNativeWrappers'), source.indexOf('function Get-KubectlResourcesSafe'));
  const lifecycleBody = source.slice(source.indexOf('function Invoke-KindLifecycle'), source.indexOf('function Set-FairResources'));
  assert.match(lifecycleBody, /\.BeginInvoke\(\)/);
  assert.match(lifecycleBody, /\.EndInvoke\(/);
  assert.doesNotMatch(lifecycleBody, /\.Invoke\(\)/);
  assert.match(helperBody, /kind["'],\s*["']docker["'],\s*["']kubectl/);
  assert.match(helperBody, /Add-Type[\s\S]*ConsoleApplication/);
  assert.match(helperBody, /ProcessStartInfo[\s\S]*RedirectStandardError\s*=\s*true/);
  assert.match(helperBody, /return child\.ExitCode/);
  assert.match(helperBody, /"\$name\.exe"/);
  assert.doesNotMatch(helperBody, /WriteAllText[^\n]+\.cmd|Copy-Item[^\n]+\.cmd|%\*|cmd(?:\.exe)?\s+\/c|Invoke-Expression/i);
  assert.match(helperBody, /\$absoluteDirectory\s*=\s*\[IO\.Path\]::GetFullPath/);
  assert.match(helperBody, /return \$absoluteDirectory/);
  assert.match(lifecycleBody, /\$env:PATH\s*=\s*\$args\[3\]/);
  assert.ok(lifecycleBody.indexOf('Resolve-PerformanceNativeExecutable') < lifecycleBody.indexOf('New-PerformanceNativeWrappers'));
  assert.ok(lifecycleBody.indexOf('New-PerformanceNativeWrappers') < lifecycleBody.indexOf('$env:PATH ='));
  assert.match(lifecycleBody, /finally \{[\s\S]*\$env:PATH = \$previousEnvironment\.PATH/);
  for (const command of ['kind', 'docker', 'kubectl']) {
    assert.doesNotMatch(lifecycleBody, new RegExp(`function global:${command}`));
    assert.match(helperBody, new RegExp(`\\$name in @\\("kind", "docker", "kubectl"\\)`));
  }
  assert.doesNotMatch(source, /&\s+(?:\$global:Performance\w+Executable|\$Performance\w+Executable|(?:kind|docker|kubectl)\.exe|\$(?:kind|docker|kubectl)Exe)/i);
  assert.doesNotMatch(helperBody, /Invoke-Expression|cmd(?:\.exe)?\s+\/c/i);
});

test('Web workload mapping follows each fixed tag while port-forward keeps service/web', () => {
  const source = readFileSync(lifecycle, 'utf8');
  assert.match(source, /midterm-check["']\) \{ return ["']campus-web["']/);
  assert.match(source, /microservices-end["']\) \{ return ["']web["']/);
  assert.doesNotMatch(source, /set resources deployment\/web/);
  assert.doesNotMatch(source, /rollout status deployment\/web/);
  assert.match(source, /port-forward["'],["']service\/web["']/);
  assert.doesNotMatch(source, /& \$script[\s\S]{0,200}\$LASTEXITCODE/);
});

function invokeFairnessLimits(fakeKubectlPath) {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:LIFECYCLE_PATH; Get-ResourceFairnessLimits "test-context" "test-namespace" "deployment/test" -KubectlCommand $env:FAKE_KUBECTL | ConvertTo-Json -Compress';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, LIFECYCLE_PATH: lifecycle, FAKE_KUBECTL: fakeKubectlPath },
  });
}

test('fairness reads CPU and memory limits from kubectl JSON without escaped JSONPath separators', async () => {
  const source = readFileSync(lifecycle, 'utf8');
  assert.doesNotMatch(source, /\{["']\\t["']\}/);
  assert.match(source, /Get-ResourceFairnessLimits[\s\S]*["']-o["'], ["']json["']/);
  assert.match(source, /Get-ResourceFairnessLimits[\s\S]*ConvertFrom-Json/);
  const json = '{"spec":{"template":{"spec":{"containers":[{"resources":{"limits":{"cpu":"2","memory":"2Gi"}}}]}}}}';
  const body = process.platform === 'win32'
    ? `@echo off\r\necho ${json}\r\nexit /b 0\r\n`
    : `#!/bin/sh\nprintf '%s\\n' '${json}'\nexit 0\n`;
  const result = invokeFairnessLimits(await fakeKind(body));
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout.trim()), { Cpu: '2', Memory: '2Gi' });
});

test('fairness reports explicit errors for kubectl failure and empty output without Null.Trim', async () => {
  const failureBody = process.platform === 'win32'
    ? '@echo off\r\necho API unavailable 1>&2\r\nexit /b 6\r\n'
    : '#!/bin/sh\necho "API unavailable" >&2\nexit 6\n';
  const failure = invokeFairnessLimits(await fakeKind(failureBody));
  assert.notEqual(failure.status, 0);
  assert.match(failure.stderr, /Cannot read resource fairness data for deployment\/test/);
  assert.match(failure.stderr, /exit code 6/);
  assert.doesNotMatch(failure.stderr, /Null.*Trim|null-valued expression/i);

  const emptyBody = process.platform === 'win32'
    ? '@echo off\r\nexit /b 0\r\n'
    : '#!/bin/sh\nexit 0\n';
  const empty = invokeFairnessLimits(await fakeKind(emptyBody));
  assert.notEqual(empty.status, 0);
  assert.match(empty.stderr, /kubectl returned empty output/);
  assert.doesNotMatch(empty.stderr, /Null.*Trim|null-valued expression/i);
});

function invokeMySqlPodResolver(fakeKubectlPath, architecture = 'midterm-check') {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:LIFECYCLE_PATH; Get-MySqlPodName $env:ARCHITECTURE "test-context" "test-namespace" -KubectlCommand $env:FAKE_KUBECTL';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, LIFECYCLE_PATH: lifecycle, FAKE_KUBECTL: fakeKubectlPath, ARCHITECTURE: architecture },
  });
}

async function fakeKubectlForPodList(selectorJson, podListJson) {
  const body = process.platform === 'win32'
    ? `@echo off\r\necho %* | findstr /C:"statefulset/campus-mysql" >nul\r\nif not errorlevel 1 (\r\n  echo ${selectorJson}\r\n  exit /b 0\r\n)\r\necho ${podListJson}\r\nexit /b 0\r\n`
    : `#!/bin/sh\ncase "$*" in\n  *statefulset/campus-mysql*) printf '%s\\n' '${selectorJson}' ;;\n  *) printf '%s\\n' '${podListJson}' ;;\nesac\n`;
  return fakeKind(body);
}

test('MySQL pod discovery derives each fixed tag selector and resolves the StatefulSet-owned pod', async () => {
  const midtermManifest = spawnSync('git', ['show', 'midterm-check:k8s/base/mysql-statefulset.yaml'], { encoding: 'utf8' });
  const microservicesManifest = spawnSync('git', ['show', 'microservices-end:k8s/base/platform.yaml'], { encoding: 'utf8' });
  assert.equal(midtermManifest.status, 0);
  assert.equal(microservicesManifest.status, 0);
  assert.match(midtermManifest.stdout, /matchLabels:\s*\r?\n\s+app\.kubernetes\.io\/name: campus-mysql/);
  assert.match(microservicesManifest.stdout, /selector: \{matchLabels: \{app: campus-mysql\}\}/);

  const selector = '{"spec":{"selector":{"matchLabels":{"app.kubernetes.io/name":"campus-mysql"}}}}';
  const pods = '{"items":[{"metadata":{"name":"campus-mysql-0","ownerReferences":[{"kind":"StatefulSet","name":"campus-mysql"}]},"status":{"conditions":[{"type":"Ready","status":"True"}]}}]}';
  const result = invokeMySqlPodResolver(await fakeKubectlForPodList(selector, pods));
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), 'campus-mysql-0');

  const microservicesSelector = '{"spec":{"selector":{"matchLabels":{"app":"campus-mysql"}}}}';
  const microservices = invokeMySqlPodResolver(await fakeKubectlForPodList(microservicesSelector, pods), 'microservices-end');
  assert.equal(microservices.status, 0, microservices.stderr);
  assert.equal(microservices.stdout.trim(), 'campus-mysql-0');
});

test('MySQL pod discovery reports an empty list with its derived selector and never uses items[0]', async () => {
  const source = readFileSync(lifecycle, 'utf8');
  assert.doesNotMatch(source, /jsonpath=\{\.items\[0\]/);
  const selector = '{"spec":{"selector":{"matchLabels":{"app":"campus-mysql"}}}}';
  const result = invokeMySqlPodResolver(await fakeKubectlForPodList(selector, '{"items":[]}'), 'microservices-end');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /No MySQL pods found/);
  assert.match(result.stderr, /app=campus-mysql/);
  assert.doesNotMatch(result.stderr, /array index out of bounds|Null.*Trim/i);
});

test('MySQL pod discovery reports missing selectors and owner references explicitly', async () => {
  const missingSelector = invokeMySqlPodResolver(await fakeKubectlForPodList('{"spec":{"selector":{}}}', '{"items":[]}'));
  assert.notEqual(missingSelector.status, 0);
  assert.match(missingSelector.stderr, /no selector\.matchLabels/i);
  assert.doesNotMatch(missingSelector.stderr, /Null-valued expression|Null.*method/i);

  const selector = '{"spec":{"selector":{"matchLabels":{"app":"campus-mysql"}}}}';
  const unownedPods = '{"items":[{"metadata":{"name":"campus-mysql-0","ownerReferences":[]},"status":{"conditions":[{"type":"Ready","status":"True"}]}}]}';
  const missingOwner = invokeMySqlPodResolver(await fakeKubectlForPodList(selector, unownedPods), 'microservices-end');
  assert.notEqual(missingOwner.status, 0);
  assert.match(missingOwner.stderr, /No pods owned by StatefulSet\/campus-mysql/);
});

test('post-fairness boundary prepares data-import evidence and error details preserve source context', async () => {
  const source = readFileSync(resolve(directory, 'orchestrate.ps1'), 'utf8');
  const fairness = source.indexOf('Write-Utf8Json (Join-Path $architectureDirectory "fairness.json")');
  const prepare = source.indexOf('Initialize-PerformanceDataImportEvidence $architectureDirectory');
  const environment = source.indexOf('collect-environment.ps1');
  const data = source.indexOf('Import-AndVerifyData $architecture');
  assert.ok(fairness >= 0 && fairness < prepare && prepare < environment && environment < data);

  const root = await mkdtemp(resolve(tmpdir(), 'post-fairness-boundary-'));
  const command = '. $env:LIFECYCLE_PATH; Initialize-PerformanceDataImportEvidence $env:BOUNDARY_ROOT | Out-Null; try { throw "boundary failure" } catch { Write-PerformanceErrorDetails (Join-Path $env:BOUNDARY_ROOT "error-details.json") "midterm-check" "collect-environment" $_ }';
  const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8', env: { ...process.env, LIFECYCLE_PATH: lifecycle, BOUNDARY_ROOT: root },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.ok((await stat(resolve(root, 'data-import'))).isDirectory());
  const details = JSON.parse(await readFile(resolve(root, 'error-details.json'), 'utf8'));
  assert.equal(details.architecture, 'midterm-check');
  assert.equal(details.stage, 'collect-environment');
  assert.equal(details.message, 'boundary failure');
  assert.match(details.exceptionType, /RuntimeException/);
  assert.ok(details.scriptStackTrace);
  assert.ok(Number.isInteger(details.scriptLineNumber));
});

test('performance startup removes stale native-shims PATH entries', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'stale-path-'));
  const shim = resolve(root, 'native-shims');
  await mkdir(shim);
  await writeFile(resolve(shim, 'performance-native-launcher.exe'), 'fixture');
  const command = '. $env:LIFECYCLE_PATH; $original=$env:PATH; try { $env:PATH=$env:STALE_SHIM+[IO.Path]::PathSeparator+$env:PATH; Remove-StalePerformanceShimPathEntries; "STALE_REMAINS=$(@($env:PATH -split [IO.Path]::PathSeparator | Where-Object { $_ -eq $env:STALE_SHIM }).Count)" } finally { $env:PATH=$original }';
  const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8', env: { ...process.env, LIFECYCLE_PATH: lifecycle, STALE_SHIM: shim },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /STALE_REMAINS=0/);
});

function invokeEnvironmentCollector(architecture, outputRoot, gitCommand = 'git') {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:LIFECYCLE_PATH; Invoke-PerformanceEnvironmentCollection $env:ARCHITECTURE $env:COLLECTOR_PATH (Join-Path $env:OUTPUT_ROOT "environment.json") "performance" "kind-campus-performance" "campus-market" $env:OUTPUT_ROOT -GitCommand $env:GIT_COMMAND | Out-Null; $json=[IO.File]::ReadAllText((Join-Path $env:OUTPUT_ROOT "environment.json"),[Text.Encoding]::UTF8) | ConvertFrom-Json; "COMMIT=$($json.git.commit)"; "PATH_RESTORED=$($env:PATH -ceq $env:ORIGINAL_PATH)"';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, LIFECYCLE_PATH: lifecycle, COLLECTOR_PATH: resolve(directory, '..', 'common', 'collect-environment.ps1'), OUTPUT_ROOT: outputRoot, ARCHITECTURE: architecture, GIT_COMMAND: gitCommand, ORIGINAL_PATH: process.env.PATH },
    timeout: 120000,
  });
}

test('production environment collector creates valid JSON for both architecture paths and restores PATH', { skip: process.platform !== 'win32' }, async () => {
  for (const architecture of ['midterm-check', 'microservices-end']) {
    const root = await mkdtemp(resolve(tmpdir(), `environment-${architecture}-`));
    const result = invokeEnvironmentCollector(architecture, root);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /COMMIT=[0-9a-f]{40}/);
    assert.match(result.stdout, /PATH_RESTORED=True/i);
    const environment = JSON.parse(await readFile(resolve(root, 'environment.json'), 'utf8'));
    assert.match(environment.git.commit, /^[0-9a-f]{40}$/);
    assert.ok((await stat(resolve(root, 'environment-collection.stdout.log'))).isFile());
    assert.ok((await stat(resolve(root, 'environment-collection.stderr.log'))).isFile());
  }
});

test('environment collector reports Git failure and empty stdout before common can call Null.Trim', { skip: process.platform !== 'win32' }, async () => {
  const failureRoot = await mkdtemp(resolve(tmpdir(), 'environment-git-failure-'));
  const failure = invokeEnvironmentCollector('midterm-check', failureRoot, await fakeNative({ stderr: 'git fixture failure\r\n', exitCode: 7 }));
  assert.notEqual(failure.status, 0);
  assert.match(failure.stderr, /Environment collection Git commit query for midterm-check failed with exit code 7/);
  assert.doesNotMatch(failure.stderr, /Null-valued expression|Null.*method/i);

  const emptyRoot = await mkdtemp(resolve(tmpdir(), 'environment-git-empty-'));
  const empty = invokeEnvironmentCollector('microservices-end', emptyRoot, await fakeNative({ exitCode: 0 }));
  assert.notEqual(empty.status, 0);
  assert.match(empty.stderr, /Git commit query for microservices-end returned no stdout/);
  assert.doesNotMatch(empty.stderr, /Null-valued expression|Null.*method/i);
});

test('environment collection production path uses the shared launcher and rejects Git from native-shims', { skip: process.platform !== 'win32' }, async () => {
  const source = readFileSync(lifecycle, 'utf8');
  const body = source.slice(source.indexOf('function Invoke-PerformanceEnvironmentCollection'), source.indexOf('function Get-KubectlResourcesSafe'));
  const orchestration = readFileSync(resolve(directory, 'orchestrate.ps1'), 'utf8');
  assert.match(body, /Resolve-PerformanceNativeExecutable \$GitCommand/);
  assert.match(body, /New-PerformanceNativeWrappers/);
  assert.match(body, /environment\.json is invalid JSON/);
  assert.doesNotMatch(orchestration, /&\s+\(Join-Path \$repoRoot ["']experiments\\common\\collect-environment\.ps1/);
  assert.match(orchestration, /Invoke-PerformanceEnvironmentCollection/);

  const root = await mkdtemp(resolve(tmpdir(), 'environment-stale-shim-'));
  const shim = resolve(root, 'native-shims');
  await mkdir(shim);
  const fakeGit = resolve(shim, 'git.exe');
  await copyFile(await nativeFixtureExecutable(), fakeGit);
  await writeFile(`${fakeGit}.fixture`, ['0', Buffer.from('a'.repeat(40) + '\r\n').toString('base64'), '', 'false'].join('\n'), 'utf8');
  const result = invokeEnvironmentCollector('midterm-check', root, fakeGit);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /git real executable resolves inside native-shims/i);
});

function invokeDataCommand(fakeKubectlPath, evidence) {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:LIFECYCLE_PATH; Invoke-KubectlDataCommand @("exec","campus-mysql-0","--","sh","/tmp/performance-mysql-client.sh","import","campus_account","/tmp/account.sql") "Import account SQL" (Join-Path $env:DATA_EVIDENCE "account.stdout.log") (Join-Path $env:DATA_EVIDENCE "account.stderr.log") -KubectlCommand $env:FAKE_KUBECTL | Out-Null';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, LIFECYCLE_PATH: lifecycle, FAKE_KUBECTL: fakeKubectlPath, DATA_EVIDENCE: evidence },
  });
}

test('Windows-safe MySQL client arguments succeed without sh -c or host password expansion', async () => {
  const source = readFileSync(lifecycle, 'utf8');
  const helper = readFileSync(resolve(directory, 'mysql-client.sh'), 'utf8');
  assert.doesNotMatch(source, /"sh",\s*"-c"/);
  assert.match(source, /performance-mysql-client\.sh["'],["']import/);
  assert.match(source, /performance-mysql-client\.sh["'],["']validate/);
  assert.doesNotMatch(source, /MYSQL_ROOT_PASSWORD/);
  assert.match(helper, /MYSQL_PWD="\$MYSQL_ROOT_PASSWORD" exec mysql/);
  assert.doesNotMatch(helper, /replace-with|password=/i);

  const body = process.platform === 'win32'
    ? '@echo off\r\necho %*\r\nexit /b 0\r\n'
    : '#!/bin/sh\nprintf "%s\\n" "$*"\nexit 0\n';
  const evidence = await mkdtemp(resolve(tmpdir(), 'data-command-evidence-'));
  const result = invokeDataCommand(await fakeKind(body), evidence);
  assert.equal(result.status, 0, result.stderr);
  assert.match(await readFile(resolve(evidence, 'account.stdout.log'), 'utf8'), /exec campus-mysql-0 -- sh \/tmp\/performance-mysql-client\.sh import campus_account \/tmp\/account\.sql/);
  assert.equal(await readFile(resolve(evidence, 'account.stderr.log'), 'utf8'), '');
});

test('MySQL client exit 2 produces an explicit failure and preserves stderr evidence', async () => {
  const body = process.platform === 'win32'
    ? '@echo off\r\necho invalid option name 1>&2\r\nexit /b 2\r\n'
    : '#!/bin/sh\necho "invalid option name" >&2\nexit 2\n';
  const evidence = await mkdtemp(resolve(tmpdir(), 'data-command-evidence-'));
  const result = invokeDataCommand(await fakeKind(body), evidence);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Import account SQL failed with exit code 2/);
  assert.match(result.stderr, /account\.stderr\.log/);
  assert.match(await readFile(resolve(evidence, 'account.stderr.log'), 'utf8'), /invalid option name/);
});

function invokeCopyHelper(fakeKubectlPath, evidence, shouldRethrow = false, localFile = resolve(directory, 'mysql-client.sh')) {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '$before=(Get-Location).Path; . $env:LIFECYCLE_PATH; $failure=$null; try { Copy-LocalFileToPod "test-context" "test-namespace" "pod" $env:LOCAL_FILE "/tmp/file" (Join-Path $env:COPY_EVIDENCE "copy.stdout.log") (Join-Path $env:COPY_EVIDENCE "copy.stderr.log") "Copy fixture" -KubectlCommand $env:FAKE_KUBECTL | Out-Null } catch { $failure=$_.Exception.Message }; $after=(Get-Location).Path; Write-Output "BEFORE=$before"; Write-Output "AFTER=$after"; if ($failure -and $env:RETHROW -eq "1") { throw $failure }';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: {
      ...process.env,
      LIFECYCLE_PATH: lifecycle,
      LOCAL_FILE: localFile,
      COPY_EVIDENCE: evidence,
      FAKE_KUBECTL: fakeKubectlPath,
      RETHROW: shouldRethrow ? '1' : '0',
    },
  });
}

test('kubectl cp receives a relative basename and keeps the pod remote specification', async () => {
  const body = process.platform === 'win32'
    ? '@echo off\r\necho CWD=%CD%\r\necho ARGS=%*\r\nexit /b 0\r\n'
    : '#!/bin/sh\nprintf "CWD=%s\\nARGS=%s\\n" "$PWD" "$*"\nexit 0\n';
  const evidence = await mkdtemp(resolve(tmpdir(), 'copy-evidence-'));
  const localDirectory = await mkdtemp(resolve(tmpdir(), 'some folder-'));
  const localFile = resolve(localDirectory, 'mysql-client.sh');
  await writeFile(localFile, '# fixture\n', 'utf8');
  const result = invokeCopyHelper(await fakeKind(body), evidence, false, localFile);
  assert.equal(result.status, 0, result.stderr);
  const log = await readFile(resolve(evidence, 'copy.stdout.log'), 'utf8');
  assert.match(log, /ARGS=.*\bcp \.[\\/]mysql-client\.sh pod:\/tmp\/file/);
  assert.doesNotMatch(log, /\bcp ["']?[A-Za-z]:/);
  const before = result.stdout.match(/^BEFORE=(.+)$/m)?.[1].trim();
  const after = result.stdout.match(/^AFTER=(.+)$/m)?.[1].trim();
  assert.equal(after, before);
});

test('kubectl cp failure restores the working directory, saves stderr and identifies both paths', async () => {
  const body = process.platform === 'win32'
    ? '@echo off\r\necho invalid local specification 1>&2\r\nexit /b 1\r\n'
    : '#!/bin/sh\necho "invalid local specification" >&2\nexit 1\n';
  const evidence = await mkdtemp(resolve(tmpdir(), 'copy-evidence-'));
  const result = invokeCopyHelper(await fakeKind(body), evidence, true);
  assert.notEqual(result.status, 0);
  const before = result.stdout.match(/^BEFORE=(.+)$/m)?.[1].trim();
  const after = result.stdout.match(/^AFTER=(.+)$/m)?.[1].trim();
  assert.equal(after, before);
  assert.match(result.stderr, /Copy fixture/);
  assert.match(result.stderr, /mysql-client\.sh/);
  assert.match(result.stderr, /pod:\/tmp\/file/);
  assert.match(result.stderr, /exit code 1/);
  assert.match(result.stderr, /copy\.stderr\.log/);
  assert.match(await readFile(resolve(evidence, 'copy.stderr.log'), 'utf8'), /invalid local specification/);
});

test('all performance local-to-pod copies use Copy-LocalFileToPod', () => {
  const source = readFileSync(lifecycle, 'utf8');
  assert.equal((source.match(/["']cp["']/g) || []).length, 1);
  assert.equal((source.match(/Copy-LocalFileToPod/g) || []).length, 4); // declaration plus helper, dataset and validation copies
  assert.match(source, /Copy-LocalFileToPod[^\n]+mysqlHelper/);
  assert.match(source, /Copy-LocalFileToPod[^\n]+\$source/);
  assert.match(source, /Copy-LocalFileToPod[^\n]+validationPath/);
});

function invokeSamplerFixture(collector, directoryPath, expectFailure = false) {
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const command = '. $env:SAMPLER_HELPER; $output=Join-Path $env:SAMPLER_DIR "resource-samples.csv"; $stop=Join-Path $env:SAMPLER_DIR "stop.signal"; $stdout=Join-Path $env:SAMPLER_DIR "resource-sampler.stdout.log"; $stderr=Join-Path $env:SAMPLER_DIR "resource-sampler.stderr.log"; if ($env:EXPECT_FAILURE -eq "1") { Start-ResourceSampler $env:COLLECTOR $output $stop "ctx" "ns" 30 $stdout $stderr -StartupTimeoutSeconds 5 | Out-Null } else { $sampler=Start-ResourceSampler $env:COLLECTOR $output $stop "ctx" "ns" 30 $stdout $stderr -StartupTimeoutSeconds 5; Write-Output "MAIN_CONTINUED"; Stop-ResourceSampler $sampler $stop; Stop-ResourceSampler $sampler $stop; Stop-ResourceSampler $null $stop; Assert-ResourceSamples $output; Write-Output "SAMPLER_OK" }';
  return spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], {
    encoding: 'utf8',
    env: { ...process.env, SAMPLER_HELPER: resourceSampler, SAMPLER_DIR: directoryPath, COLLECTOR: collector, EXPECT_FAILURE: expectFailure ? '1' : '0' },
  });
}

test('resource sampler runs asynchronously in-process and cleans up idempotently', async () => {
  const fixtureDirectory = await mkdtemp(resolve(tmpdir(), 'sampler path with spaces-'));
  const collector = resolve(fixtureDirectory, 'collector fixture.ps1');
  await writeFile(collector, `param([string]$OutputPath,[string]$StopFile,[string]$Context,[string]$Namespace,[int]$MaxDurationSeconds)\n[IO.File]::WriteAllText($OutputPath,"timestamp,cpu" + [Environment]::NewLine + "now,1" + [Environment]::NewLine)\nwhile (-not (Test-Path -LiteralPath $StopFile)) { Start-Sleep -Milliseconds 100 }\n`, 'utf8');
  const result = invokeSamplerFixture(collector, fixtureDirectory);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /MAIN_CONTINUED/);
  assert.match(result.stdout, /SAMPLER_OK/);
  assert.ok((await readFile(resolve(fixtureDirectory, 'resource-samples.csv'))).length > 0);
  assert.equal(await readFile(resolve(fixtureDirectory, 'resource-sampler.stderr.log'), 'utf8'), '');
});

test('resource sampler preserves the Error stream and reports a failed runspace', async () => {
  const fixtureDirectory = await mkdtemp(resolve(tmpdir(), 'sampler failure path-'));
  const collector = resolve(fixtureDirectory, 'collector failure.ps1');
  await writeFile(collector, `param([string]$OutputPath,[string]$StopFile,[string]$Context,[string]$Namespace,[int]$MaxDurationSeconds)\nthrow "collector failed"\n`, 'utf8');
  const result = invokeSamplerFixture(collector, fixtureDirectory, true);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Resource sampler terminated early/);
  assert.match(result.stderr, /state Failed/);
  assert.match(result.stderr, /resource-sampler\.stderr\.log/);
  assert.match(await readFile(resolve(fixtureDirectory, 'resource-sampler.stderr.log'), 'utf8'), /collector failed/);
});

test('smoke samples resources and requires a non-empty CSV before success', () => {
  const helper = readFileSync(resourceSampler, 'utf8');
  assert.doesNotMatch(helper, /Start-Process|powershell\.exe|pwsh(?:\.exe)?/i);
  assert.match(helper, /\[PowerShell\]::Create\(\)/);
  assert.match(helper, /\.BeginInvoke\(\)/);
  assert.match(helper, /\.Streams\.Error/);
  assert.match(helper, /\.Dispose\(\)/);
  assert.match(runner, /Phase "smoke"[\s\S]*SampleResources \$true/);
  assert.match(runner, /Assert-ResourceSamplerHealthy \$sampler \$resourceSamplesPath/);
  assert.match(helper, /Get-Item[^\n]+\.Length -le 0/);
});

test('result summarizer extracts overall and endpoint metrics and marks absent runs missing', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'performance-summary-'));
  await writeFile(resolve(root, 'benchmark-metadata.json'), JSON.stringify({ architecture:'midterm-check' }));
  const metrics = {
    http_reqs:{ values:{ count:300, rate:10 } }, http_req_duration:{ values:{ avg:12, 'p(95)':20 } },
    http_req_failed:{ values:{ rate:0.01 } }, checks:{ values:{ rate:0.99 } },
  };
  for (const endpoint of ['items_list','item_detail','item_messages']) {
    metrics[`http_reqs{endpoint:${endpoint}}`] = { values:{ count:100, rate:3.33 } };
    metrics[`http_req_duration{endpoint:${endpoint}}`] = { values:{ avg:11, 'p(95)':19 } };
    metrics[`http_req_failed{endpoint:${endpoint}}`] = { values:{ rate:0.02 } };
  }
  for (let repeat = 1; repeat <= 3; repeat += 1) {
    const measurement = resolve(root, 'vus-10', `run-${repeat}`, 'measurement');
    await mkdir(measurement, { recursive:true });
    await writeFile(resolve(measurement, 'run-metadata.json'), JSON.stringify({ architecture:'midterm-check', status:'passed', failure:null }));
    await writeFile(resolve(measurement, 'k6-summary.json'), JSON.stringify({ metrics }));
  }
  const result = await summarizeDirectory(root);
  assert.equal(result.runs[0].overall.avgLatencyMs, 12);
  assert.equal(result.runs[0].overall.p95LatencyMs, 20);
  assert.equal(result.runs[0].endpoints.items_list.throughput, 3.33);
  assert.equal(result.runs[3].status, 'missing');
  assert.equal(result.aggregates[0].statistics.errorRate.median, 0.01);
  assert.equal(result.aggregates[0].statistics.errorRate.min, 0.01);
  assert.equal(result.aggregates[0].statistics.errorRate.max, 0.01);
  assert.equal(result.aggregates[0].passedRuns, 3);
  assert.match(await readFile(resolve(root, 'summary.csv'), 'utf8'), /items_list_p95LatencyMs/);
});

test('dry-run plans both tags, cleanup, 18 measurements and does not change branch', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'performance-dry-run-'));
  const branchBefore = spawnSync('git', ['branch','--show-current'], { encoding:'utf8' }).stdout.trim();
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const result = spawnSync(shell, ['-NoProfile','-ExecutionPolicy','Bypass','-File',resolve(directory,'run.ps1'),'-RunDirectory',root], { encoding:'utf8', env:{ ...process.env, PERFORMANCE_MODE:'dry-run', PERFORMANCE_CONFIRM:'' } });
  assert.equal(result.status, 0, result.stderr);
  const plan = JSON.parse(await readFile(resolve(root, 'orchestration-plan.json'), 'utf8'));
  assert.deepEqual(plan.architectures, ['midterm-check','microservices-end']);
  assert.equal(plan.formalMatrix.measurements, 18);
  assert.ok(plan.steps.includes('cleanup-midterm'));
  assert.ok(plan.steps.includes('cleanup-microservices'));
  assert.equal(spawnSync('git', ['branch','--show-current'], { encoding:'utf8' }).stdout.trim(), branchBefore);
});

test('comparison summary uses median and retains min/max for both architectures', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'performance-comparison-'));
  for (const architecture of ['midterm-check','microservices-end']) {
    const architectureRoot = resolve(root, architecture);
    await mkdir(architectureRoot, { recursive:true });
    await writeFile(resolve(architectureRoot, 'benchmark-metadata.json'), JSON.stringify({ architecture }));
    for (const vus of [10,50,100]) for (let repeat=1; repeat<=3; repeat+=1) {
      const measurement = resolve(architectureRoot, `vus-${vus}`, `run-${repeat}`, 'measurement');
      await mkdir(measurement, { recursive:true });
      await writeFile(resolve(measurement, 'run-metadata.json'), JSON.stringify({ architecture, status:'passed' }));
      const value = repeat * 10;
      const metrics = { http_reqs:{values:{count:value,rate:value}}, http_req_duration:{values:{avg:value,'p(95)':value}}, http_req_failed:{values:{rate:0}}, checks:{values:{rate:1}} };
      for (const endpoint of ['items_list','item_detail','item_messages']) { metrics[`http_reqs{endpoint:${endpoint}}`]={values:{rate:value}}; metrics[`http_req_duration{endpoint:${endpoint}}`]={values:{avg:value,'p(95)':value}}; metrics[`http_req_failed{endpoint:${endpoint}}`]={values:{rate:0}}; }
      await writeFile(resolve(measurement, 'k6-summary.json'), JSON.stringify({ metrics }));
    }
  }
  const comparison = await summarizeComparison(root);
  const stats = comparison.architectures[0].aggregates[0].statistics.throughput;
  assert.deepEqual(stats, { median:20, min:10, max:30, mean:20 });
  assert.match(await readFile(resolve(root, 'comparison-summary.csv'), 'utf8'), /median,min,max,mean/);
});

test('smoke and demo summaries honor their actual matrix instead of reporting formal runs missing', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'performance-profiles-'));
  const emptyMetrics = { http_reqs:{values:{count:3,rate:1}}, http_req_duration:{values:{avg:1,'p(95)':1}}, http_req_failed:{values:{rate:0}}, checks:{values:{rate:1}} };
  await writeFile(resolve(root, 'benchmark-metadata.json'), JSON.stringify({ architecture:'midterm-check', mode:'smoke', matrix:{vus:[10,50,100],repeats:3} }));
  await writeFile(resolve(root, 'run-metadata.json'), JSON.stringify({ architecture:'midterm-check', status:'passed' }));
  await writeFile(resolve(root, 'k6-summary.json'), JSON.stringify({ metrics:emptyMetrics }));
  const smoke = await summarizeDirectory(root);
  assert.equal(smoke.runs.length, 1);
  assert.equal(smoke.runs[0].vus, 1);
  assert.equal(smoke.aggregates[0].expectedRuns, 1);

  const demoRoot = await mkdtemp(resolve(tmpdir(), 'performance-demo-'));
  await writeFile(resolve(demoRoot, 'benchmark-metadata.json'), JSON.stringify({ architecture:'microservices-end', mode:'formal', matrix:{vus:[10],repeats:1} }));
  const measurement = resolve(demoRoot, 'vus-10', 'run-1', 'measurement');
  await mkdir(measurement, { recursive:true });
  await writeFile(resolve(measurement, 'run-metadata.json'), JSON.stringify({ architecture:'microservices-end', status:'passed' }));
  await writeFile(resolve(measurement, 'k6-summary.json'), JSON.stringify({ metrics:emptyMetrics }));
  const demo = await summarizeDirectory(demoRoot);
  assert.equal(demo.runs.length, 1);
  assert.equal(demo.aggregates[0].passedRuns, 1);
  assert.equal(demo.aggregates[0].statistics.throughput.median, 1);
});
