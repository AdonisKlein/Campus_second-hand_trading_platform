#!/usr/bin/env node
import process from 'node:process';
import { displayOutput, generateAdapters } from './dataset-adapter.mjs';

function argumentsFrom(argv) {
  const result = { target: 'all' };
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]?.replace(/^--/, '');
    const value = argv[index + 1];
    if (!['input', 'output', 'target'].includes(key) || value === undefined) throw new Error(`Unknown or incomplete argument: ${argv[index]}`);
    result[key] = value;
  }
  if (!result.input) throw new Error('--input is required.');
  if (!result.output) throw new Error('--output is required.');
  return result;
}

try {
  const options = argumentsFrom(process.argv.slice(2));
  const report = await generateAdapters({ inputDirectory: options.input, outputDirectory: options.output, target: options.target });
  process.stdout.write(`${displayOutput(report, options.output)}\n`);
} catch (error) {
  process.stderr.write(`Dataset adapter failed: ${error.message}\n`);
  process.exitCode = 1;
}
