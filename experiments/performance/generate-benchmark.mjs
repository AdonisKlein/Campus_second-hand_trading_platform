#!/usr/bin/env node
import { pathToFileURL } from 'node:url';
import { readFile, writeFile } from 'node:fs/promises';
import process from 'node:process';

export const SENTINEL = '/*__PERFORMANCE_RUN_CONFIG__*/ null';

export function embedRunConfig(source, config) {
  const occurrences = source.split(SENTINEL).length - 1;
  if (occurrences !== 1) throw new Error(`Expected exactly one embedded config sentinel; found ${occurrences}.`);
  if (!Number.isInteger(config.vus) || config.vus <= 0) throw new Error('Embedded vus must be a positive integer.');
  if (!/^\d+(ms|s|m|h)$/.test(config.duration) || config.duration.startsWith('0')) throw new Error('Embedded duration is invalid.');
  if (!['strict', 'record'].includes(config.validationMode)) throw new Error('Embedded validationMode is invalid.');
  for (const key of ['architecture', 'runLabel']) if (typeof config[key] !== 'string' || config[key].length === 0) throw new Error(`Embedded ${key} is required.`);
  return source.replace(SENTINEL, JSON.stringify(config));
}

function parseArguments(argv) {
  const values = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]?.replace(/^--/, '');
    const value = argv[index + 1];
    if (!['source','output','vus','duration','architecture','run-label','validation-mode'].includes(key) || value === undefined) throw new Error(`Unknown or incomplete argument: ${argv[index]}`);
    values[key] = value;
  }
  for (const key of ['source','output','vus','duration','architecture','run-label','validation-mode']) if (!values[key]) throw new Error(`--${key} is required.`);
  return values;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const args = parseArguments(process.argv.slice(2));
    const source = await readFile(args.source, 'utf8');
    const generated = embedRunConfig(source, { vus:Number(args.vus), duration:args.duration, architecture:args.architecture, runLabel:args['run-label'], validationMode:args['validation-mode'] });
    await writeFile(args.output, generated, 'utf8');
  } catch (error) {
    process.stderr.write(`Benchmark generation failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
