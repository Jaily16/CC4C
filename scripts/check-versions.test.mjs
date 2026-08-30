import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  CONTROLLED_FILES,
  loadSnapshot,
  parseManifest,
  validateSnapshot
} from './check-versions.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const manifestText = await readFile(path.join(repositoryRoot, 'versions.yml'), 'utf8');
const manifest = parseManifest(manifestText);
const baselineSources = await loadSnapshot(repositoryRoot);

function cloneSources() {
  return { ...baselineSources };
}

function replaceOnce(sources, relativePath, from, to) {
  const source = sources[relativePath];
  assert.equal(typeof source, 'string', 'controlled source must be loaded: ' + relativePath);
  assert.notEqual(source.indexOf(from), -1, 'test fixture text must exist: ' + from);
  sources[relativePath] = source.replace(from, to);
}

test('current workspace passes the version snapshot', () => {
  assert.deepEqual(validateSnapshot(manifest, baselineSources), []);
});

test('manifest rejects a missing top-level field', () => {
  const missingProject = JSON.parse(manifestText);
  delete missingProject.project;
  assert.throws(() => parseManifest(JSON.stringify(missingProject)), /project\.id/);
});

test('manifest rejects an unknown schema version', () => {
  const unknownSchema = JSON.parse(manifestText);
  unknownSchema.schemaVersion = 2;
  assert.throws(() => parseManifest(JSON.stringify(unknownSchema)), /Unsupported versions\.yml schemaVersion/);
});

test('manifest rejects duplicate keys', () => {
  assert.throws(() => parseManifest('{"schemaVersion":1,"schemaVersion":1}'), /Duplicate JSON key/);
});

test('package and lockfile name drift is reported', () => {
  const sources = cloneSources();
  replaceOnce(sources, 'frontend/package.json', '"name": "cc4c"', '"name": "wrong-name"');
  replaceOnce(sources, 'frontend/package-lock.json', '"name": "cc4c"', '"name": "wrong-name"');
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.path === 'frontend/package.json' && error.field === 'name'));
  assert.ok(errors.some((error) => error.path === 'frontend/package-lock.json' && error.field === 'name'));
});

test('POM coordinate or version drift is reported', () => {
  const sources = cloneSources();
  replaceOnce(sources, 'backend/pom.xml', '<groupId>com.cc4c</groupId>', '<groupId>com.other</groupId>');
  replaceOnce(sources, 'backend/pom.xml', '<version>4.0.0-SNAPSHOT</version>', '<version>4.1.0-SNAPSHOT</version>');
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.field === 'project.groupId'));
  assert.ok(errors.some((error) => error.field === 'project.version'));
});

test('Docker digest drift is reported', () => {
  const sources = cloneSources();
  replaceOnce(
    sources,
    'backend/Dockerfile',
    'sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf',
    'sha256:0000000000000000000000000000000000000000000000000000000000000000'
  );
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.path === 'backend/Dockerfile' && error.field === 'from.javaRuntime'));
});

test('active Compose images cannot use the aspect7 tag', () => {
  const sources = cloneSources();
  replaceOnce(sources, 'compose.yml', 'cc4c/backend:4.0.0-SNAPSHOT', 'cc4c/backend:aspect7');
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.path === 'compose.yml' && error.field === 'active.imageTag'));
});

test('every repeated Compose image reference must match the manifest', () => {
  const sources = cloneSources();
  replaceOnce(sources, 'compose.yml', 'cc4c/backend:4.0.0-SNAPSHOT', 'cc4c/backend:4.0.1-SNAPSHOT');
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.path === 'compose.yml' && error.field === 'localImage.backend'));
});

test('action SHA drift is reported even when the version comment is unchanged', () => {
  const sources = cloneSources();
  replaceOnce(
    sources,
    '.github/workflows/quality.yml',
    'actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2',
    'actions/checkout@0000000000000000000000000000000000000000 # v6.0.2'
  );
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.field === 'action.checkout'));
});

test('action version comment drift is reported even when the SHA is unchanged', () => {
  const sources = cloneSources();
  replaceOnce(
    sources,
    '.github/workflows/quality.yml',
    'actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2',
    'actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v0.0.0'
  );
  const errors = validateSnapshot(manifest, sources);
  assert.ok(errors.some((error) => error.field === 'action.checkout'));
});

test('cc4c-v3 Compose compatibility remains valid', () => {
  assert.deepEqual(validateSnapshot(manifest, baselineSources), []);
});

test('historical evidence is not loaded into the active snapshot', () => {
  assert.equal(CONTROLLED_FILES.some((file) => file.startsWith('temp/')), false);
  assert.equal(CONTROLLED_FILES.some((file) => file.includes('docs/reports')), false);
  assert.deepEqual(validateSnapshot(manifest, baselineSources), []);
});

test('protected local configuration paths are not loaded', () => {
  const protectedPath = (file) => file === 'backend/src/main/resources/application.yml'
    || file.endsWith('.env.runtime.local')
    || file.endsWith('.env.test.local')
    || file.endsWith('.env.performance.local')
    || file.startsWith('deploy/secrets/local/');
  assert.equal(CONTROLLED_FILES.some(protectedPath), false);
  assert.equal(Object.keys(baselineSources).some(protectedPath), false);
});
