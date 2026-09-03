#!/usr/bin/env node
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import process from 'node:process';

export const ENDPOINTS = ['items_list', 'item_detail', 'item_messages'];

function metric(summary, name) {
  return summary?.metrics?.[name]?.values ?? null;
}

function endpointMetrics(summary, endpoint) {
  const requests = metric(summary, `http_reqs{endpoint:${endpoint}}`);
  const duration = metric(summary, `http_req_duration{endpoint:${endpoint}}`);
  const failed = metric(summary, `http_req_failed{endpoint:${endpoint}}`);
  return {
    throughput: requests?.rate ?? null,
    avgLatencyMs: duration?.avg ?? null,
    p95LatencyMs: duration?.['p(95)'] ?? null,
    errorRate: failed?.rate ?? null,
  };
}

export function extractRun(summary, metadata, vus, repeat) {
  const overallRequests = metric(summary, 'http_reqs');
  const overallDuration = metric(summary, 'http_req_duration');
  const overallFailed = metric(summary, 'http_req_failed');
  const checks = metric(summary, 'checks');
  return {
    architecture: metadata?.architecture ?? 'unknown', vus, repeat,
    status: metadata?.status === 'passed' ? 'passed' : (metadata?.status ?? 'unknown'),
    failure: metadata?.failure ?? null,
    overall: {
      requestCount: overallRequests?.count ?? null,
      throughput: overallRequests?.rate ?? null,
      avgLatencyMs: overallDuration?.avg ?? null,
      p95LatencyMs: overallDuration?.['p(95)'] ?? null,
      errorRate: overallFailed?.rate ?? null,
      checksRate: checks?.rate ?? null,
    },
    endpoints: Object.fromEntries(ENDPOINTS.map((endpoint) => [endpoint, endpointMetrics(summary, endpoint)])),
  };
}

function average(values) {
  const numeric = values.filter((value) => typeof value === 'number' && Number.isFinite(value));
  return numeric.length === values.length && numeric.length > 0 ? numeric.reduce((sum, value) => sum + value, 0) / numeric.length : null;
}

function statistics(values, complete) {
  if (!complete) return { median:null, min:null, max:null, mean:null };
  const sorted = values.filter((value) => typeof value === 'number' && Number.isFinite(value)).sort((a,b) => a-b);
  if (sorted.length !== values.length || sorted.length === 0) return { median:null, min:null, max:null, mean:null };
  return { median:sorted[Math.floor(sorted.length / 2)], min:sorted[0], max:sorted.at(-1), mean:average(sorted) };
}

function aggregate(runs, vus, expectedRuns) {
  const group = runs.filter((run) => run.vus === vus);
  const passed = group.filter((run) => run.status === 'passed');
  const fields = ['requestCount','throughput','avgLatencyMs','p95LatencyMs','errorRate','checksRate'];
  const complete = passed.length === expectedRuns;
  return {
    vus, expectedRuns, passedRuns:passed.length,
    missingRuns:group.filter((run) => run.status === 'missing').length,
    failedRuns:group.filter((run) => !['passed','missing'].includes(run.status)).length,
    statistics: Object.fromEntries(fields.map((field) => [field, statistics(passed.map((run) => run.overall[field]), complete)])),
    endpoints: Object.fromEntries(ENDPOINTS.map((endpoint) => [endpoint, Object.fromEntries(
      ['throughput','avgLatencyMs','p95LatencyMs','errorRate'].map((field) => [field, statistics(passed.map((run) => run.endpoints[endpoint][field]), complete)])
    )])),
  };
}

function csv(value) {
  if (value === null || value === undefined) return '';
  const text = String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function csvOutput(runs) {
  const columns = ['architecture','vus','repeat','status','failure','requestCount','throughput','avgLatencyMs','p95LatencyMs','errorRate','checksRate'];
  for (const endpoint of ENDPOINTS) for (const field of ['throughput','avgLatencyMs','p95LatencyMs','errorRate']) columns.push(`${endpoint}_${field}`);
  const rows = runs.map((run) => {
    const values = [run.architecture,run.vus,run.repeat,run.status,run.failure,...['requestCount','throughput','avgLatencyMs','p95LatencyMs','errorRate','checksRate'].map((field) => run.overall[field])];
    for (const endpoint of ENDPOINTS) for (const field of ['throughput','avgLatencyMs','p95LatencyMs','errorRate']) values.push(run.endpoints[endpoint]?.[field]);
    return values.map(csv).join(',');
  });
  return `${columns.join(',')}\n${rows.join('\n')}\n`;
}

export async function summarizeDirectory(inputDirectory, outputDirectory = inputDirectory) {
  const input = resolve(inputDirectory);
  const runs = [];
  let architecture = 'unknown';
  let benchmarkMetadata = null;
  try { benchmarkMetadata = JSON.parse(await readFile(resolve(input, 'benchmark-metadata.json'), 'utf8')); architecture = benchmarkMetadata.architecture ?? architecture; } catch { }
  const matrixVus = benchmarkMetadata?.mode === 'smoke' ? [1] : (benchmarkMetadata?.matrix?.vus ?? [10,50,100]);
  const repeats = benchmarkMetadata?.mode === 'smoke' ? 1 : (benchmarkMetadata?.matrix?.repeats ?? 3);
  for (const vus of matrixVus) for (let repeat = 1; repeat <= repeats; repeat += 1) {
    const directory = benchmarkMetadata?.mode === 'smoke' ? input : resolve(input, `vus-${vus}`, `run-${repeat}`, 'measurement');
    let metadata = null;
    let summary = null;
    try { metadata = JSON.parse(await readFile(resolve(directory, 'run-metadata.json'), 'utf8')); } catch { }
    try { summary = JSON.parse(await readFile(resolve(directory, 'k6-summary.json'), 'utf8')); } catch { }
    if (!summary) {
      runs.push({ architecture:metadata?.architecture ?? architecture, vus, repeat, status:'missing', failure:metadata?.failure ?? 'measurement/k6-summary.json is missing or invalid', overall:{ requestCount:null,throughput:null,avgLatencyMs:null,p95LatencyMs:null,errorRate:null,checksRate:null }, endpoints:Object.fromEntries(ENDPOINTS.map((endpoint) => [endpoint,{ throughput:null,avgLatencyMs:null,p95LatencyMs:null,errorRate:null }])) });
    } else {
      runs.push(extractRun(summary, metadata ?? { architecture, status:'unknown' }, vus, repeat));
    }
  }
  const result = { schemaVersion:1, architecture, mode:benchmarkMetadata?.mode ?? 'formal', runs, aggregates:matrixVus.map((vus) => aggregate(runs, vus, repeats)) };
  const output = resolve(outputDirectory);
  await mkdir(output, { recursive:true });
  await writeFile(resolve(output, 'summary.json'), `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  await writeFile(resolve(output, 'summary.csv'), csvOutput(runs), 'utf8');
  return result;
}

function comparisonCsv(architectures) {
  const header = 'architecture,vus,scope,metric,median,min,max,mean,passedRuns,missingRuns,failedRuns';
  const rows = [];
  for (const architecture of architectures) for (const aggregate of architecture.aggregates) {
    for (const [metricName, values] of Object.entries(aggregate.statistics)) rows.push([architecture.architecture,aggregate.vus,'overall',metricName,values.median,values.min,values.max,values.mean,aggregate.passedRuns,aggregate.missingRuns,aggregate.failedRuns]);
    for (const [endpoint, metrics] of Object.entries(aggregate.endpoints)) for (const [metricName, values] of Object.entries(metrics)) rows.push([architecture.architecture,aggregate.vus,endpoint,metricName,values.median,values.min,values.max,values.mean,aggregate.passedRuns,aggregate.missingRuns,aggregate.failedRuns]);
  }
  return `${header}\n${rows.map((row) => row.map(csv).join(',')).join('\n')}\n`;
}

export async function summarizeComparison(inputDirectory) {
  const input = resolve(inputDirectory);
  const architectures = [];
  for (const name of ['midterm-check','microservices-end']) architectures.push(await summarizeDirectory(resolve(input, name)));
  const comparison = { schemaVersion:1, generatedAt:new Date().toISOString(), primaryStatistic:'median', architectures };
  await writeFile(resolve(input, 'comparison-summary.json'), `${JSON.stringify(comparison, null, 2)}\n`, 'utf8');
  await writeFile(resolve(input, 'comparison-summary.csv'), comparisonCsv(architectures), 'utf8');
  return comparison;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const inputIndex = process.argv.indexOf('--input');
    const outputIndex = process.argv.indexOf('--output');
    if (inputIndex < 0 || !process.argv[inputIndex + 1]) throw new Error('--input is required.');
    const input = process.argv[inputIndex + 1];
    const output = outputIndex >= 0 ? process.argv[outputIndex + 1] : input;
    if (!output) throw new Error('--output is incomplete.');
    if (process.argv.includes('--comparison')) {
      const result = await summarizeComparison(input);
      process.stdout.write(`Summarized ${result.architectures.length} architectures.\n`);
    } else {
      const result = await summarizeDirectory(input, output);
      process.stdout.write(`Summarized ${result.runs.length} measurement runs for ${result.architecture}.\n`);
    }
  } catch (error) {
    process.stderr.write(`Result summarization failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
