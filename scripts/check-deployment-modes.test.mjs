import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  MODE_PATHS,
  loadDeploymentModeSnapshot,
  validateDeploymentModes
} from './check-deployment-modes.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const baseline = await loadDeploymentModeSnapshot(repositoryRoot);

function cloneSnapshot() {
  return {
    repositoryRoot: baseline.repositoryRoot,
    sources: { ...baseline.sources }
  };
}

function replaceOnce(snapshot, relativePath, from, to) {
  assert.equal(typeof snapshot.sources[relativePath], 'string');
  assert.notEqual(snapshot.sources[relativePath].indexOf(from), -1, 'fixture text must exist: ' + from);
  snapshot.sources[relativePath] = snapshot.sources[relativePath].replace(from, to);
}

test('current deployment modes pass', () => {
  assert.deepEqual(validateDeploymentModes(baseline), []);
});

test('Compose project drift is rejected', () => {
  const snapshot = cloneSnapshot();
  replaceOnce(snapshot, 'compose.yml', 'name: cc4c', 'name: cc4c-v3');
  assert.ok(validateDeploymentModes(snapshot).some((error) => error.rule === 'project'));
});

test('volume prefix drift is rejected', () => {
  const snapshot = cloneSnapshot();
  replaceOnce(snapshot, 'versions.yml', '"volumePrefix": "cc4c"', '"volumePrefix": "other"');
  assert.ok(validateDeploymentModes(snapshot).some((error) => error.rule === 'compatibility.volumePrefix'));
});

test('host port drift is rejected', () => {
  const snapshot = cloneSnapshot();
  snapshot.sources['scripts/development/host-preflight.ps1'] = snapshot.sources['scripts/development/host-preflight.ps1']
    .replaceAll('Assert-Cc4cFreePort $FrontendPort', 'Assert-Cc4cFreePort 5174');
  assert.ok(validateDeploymentModes(snapshot).some((error) => error.path.includes('host-preflight')));
});

test('Compose service, port, and network drift is rejected', () => {
  const snapshot = cloneSnapshot();
  replaceOnce(snapshot, 'compose.yml', '127.0.0.1:5173:8080', '127.0.0.1:5174:8080');
  assert.ok(validateDeploymentModes(snapshot).some((error) => error.rule === 'port.frontendHttp'));
});

test('shared environment and namespace contract remains valid', () => {
  assert.deepEqual(validateDeploymentModes(baseline), []);
});

test('historical paths and v3 migration identity are not loaded as active files', () => {
  assert.equal(MODE_PATHS.some((file) => file.startsWith('docs/history/')), false);
  assert.equal(MODE_PATHS.some((file) => file.startsWith('temp/')), false);
  assert.equal(MODE_PATHS.includes('scripts/deployment/migrate-compose-identity.ps1'), true);
  assert.deepEqual(validateDeploymentModes(baseline), []);
});

test('protected paths cannot enter the deployment mode allowlist', () => {
  assert.equal(MODE_PATHS.some((file) => file.endsWith('application.yml')), false);
  assert.equal(MODE_PATHS.some((file) => file.endsWith('.env.runtime.local')), false);
  assert.equal(MODE_PATHS.some((file) => file.startsWith('deploy/secrets/local/')), false);
});

test('Flyway and published message schema markers remain valid', () => {
  assert.equal(baseline.sources['versions.yml'].includes('"flywayMigrations": "V1-V7"'), true);
  for (const eventName of [
    'identity.verification-email.requested.v1',
    'community.blog.submitted.v1',
    'community.blog.reviewed.v1'
  ]) {
    assert.equal(baseline.sources['backend/src/main/java/com/cc4c/shared/AsyncEventTypes.java'].includes(eventName), true);
  }
});
