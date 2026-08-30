import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  QUALITY_PATHS,
  loadQualitySnapshot,
  validateQuality,
} from './check-source-quality.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function snapshotWith(relativePath, source) {
  return {
    sources: { [relativePath]: source },
    loadedFiles: [relativePath],
    protectedPathsRead: [],
  };
}

test('current workspace source quality passes', async () => {
  const snapshot = await loadQualitySnapshot(repositoryRoot);
  assert.deepEqual(validateQuality(snapshot), []);
});

test('BOM, CRLF and missing final newline are rejected', () => {
  const errors = validateQuality(snapshotWith('frontend/src/example.js', '\uFEFFconst value = 1;\r'));
  assert.ok(errors.some((error) => error.rule === 'bom'));
  assert.ok(errors.some((error) => error.rule === 'line-ending'));
  assert.ok(errors.some((error) => error.rule === 'final-newline'));
});

test('debug output is rejected outside the development reporter', () => {
  const errors = validateQuality(snapshotWith('frontend/src/example.js', 'console.log(\'debug\');\n'));
  assert.ok(errors.some((error) => error.rule === 'browser-console'));
});

test('public production Java types require Chinese Javadoc', () => {
  const errors = validateQuality(snapshotWith('backend/src/main/java/com/cc4c/Example.java', 'public class Example {}\n'));
  assert.ok(errors.some((error) => error.rule === 'java-public-type-javadoc'));
});

test('development error reporter is the only allowed console sink', () => {
  const source = [
    'export function reportClientError(error, context = \'\', options = {}) {',
    '  const development = options.development ?? import.meta.env?.DEV === true;',
    '  if (development) { console.error(error?.name, error?.message, context); }',
    '}\n',
  ].join('\n');
  assert.deepEqual(validateQuality(snapshotWith('frontend/src/utils/reportClientError.js', source)), []);
});

test('protected, historical and generated paths are not loaded', () => {
  assert.equal(QUALITY_PATHS.some((entry) => entry.includes('application.yml')), false);
  assert.equal(QUALITY_PATHS.some((entry) => entry.startsWith('temp')), false);
  assert.equal(QUALITY_PATHS.some((entry) => entry.startsWith('docs/history')), false);
  const snapshot = {
    sources: {
      'backend/src/main/resources/application.yml': 'password: should-not-load\n',
      'frontend/package-lock.json': '{"name":"ignored"}\n',
      'docs/history/old.md': 'console.log(\'history\')\n',
      'temp/evidence.txt': 'console.log(\'temp\')\n',
    },
    protectedPathsRead: [],
  };
  assert.deepEqual(validateQuality(snapshot), []);
});

test('errors include only path, rule, expected and actual fields', () => {
  const [error] = validateQuality(snapshotWith('frontend/src/example.js', 'debugger;\n'));
  assert.deepEqual(Object.keys(error).sort(), ['actual', 'expected', 'path', 'rule']);
});

test('UTF-8 source with a Chinese public type Javadoc passes', () => {
  const source = '/** 示例公开类型。 */\npublic final class Example {}\n';
  assert.deepEqual(validateQuality(snapshotWith('backend/src/main/java/com/cc4c/Example.java', source)), []);
});
