import assert from 'node:assert/strict'
import test from 'node:test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { loadLayoutSnapshot, validateLayout } from './check-structure.mjs'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const baseline = await loadLayoutSnapshot(repositoryRoot)

function cloneSnapshot() {
  return {
    ...baseline,
    files: [...baseline.files],
    sources: { ...baseline.sources },
    reparsePoints: [...baseline.reparsePoints],
    protectedPathsRead: [...baseline.protectedPathsRead],
  }
}

test('current workspace has the canonical directories and entry points', () => {
  assert.deepEqual(validateLayout(baseline), [])
})

test('active old path references are rejected', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['frontend/src/App.vue'] += '\n<!-- back-end/CC4C is not an active path -->\n'
  const errors = validateLayout(snapshot)
  assert.ok(errors.some((error) => error.field === 'legacy.reference'))
})

test('old iteration profile and class names are rejected', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['backend/pom.xml'] += '\n<!-- aspect6-gatling -->\n'
  const errors = validateLayout(snapshot)
  assert.ok(errors.some((error) => error.field === 'legacy.reference'))
})

test('cc4c-v3 Compose compatibility remains valid', () => {
  assert.deepEqual(validateLayout(baseline), [])
  assert.match(baseline.sources['compose.yml'], /name:\s*cc4c/)
  assert.match(baseline.sources['scripts/deployment/migrate-compose-identity.ps1'], /cc4c-v3/)
})

test('history and the V4 migration plan may retain old evidence', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['docs/history/example.md'] = 'back-end/CC4C aspect7 V3 evidence'
  snapshot.sources['docs/development/v4-iteration-plan.md'] += '\nback-end/CC4C -> backend\n'
  assert.deepEqual(validateLayout(snapshot), [])
})

test('protected paths are not loaded', () => {
  assert.equal(baseline.protectedPathsRead.length, 0)
  assert.equal(baseline.loadedFiles.some((file) => file.endsWith('.env.runtime.local')), false)
  assert.equal(baseline.loadedFiles.some((file) => file.endsWith('src/main/resources/application.yml')), false)
  assert.equal(baseline.loadedFiles.some((file) => file.startsWith('temp/')), false)
  assert.equal(baseline.loadedFiles.some((file) => file === 'database/legacy/cc4c.sql'), false)
  assert.equal(baseline.loadedFiles.some((file) => /\.(gif|ico|jpe?g|png|webp)$/i.test(file)), false)
})

test('LoginRegister is deleted and no active route or dynamic import remains', () => {
  assert.equal(baseline.files.some((file) => file.endsWith('/LoginRegister.vue')), false)
  assert.equal(Object.entries(baseline.sources)
    .filter(([file]) => file.startsWith('frontend/src/'))
    .some(([, source]) => source.includes('LoginRegister')), false)
})

test('frontend API imports resolve against the canonical source tree', () => {
  assert.deepEqual(validateLayout(baseline), [])
})

test('unapproved canonical reparse points are rejected', () => {
  const snapshot = cloneSnapshot()
  snapshot.reparsePoints.push('frontend/src/unapproved-link.js')
  const errors = validateLayout(snapshot)
  assert.ok(errors.some((error) => error.field === 'reparsePoint'))
})
