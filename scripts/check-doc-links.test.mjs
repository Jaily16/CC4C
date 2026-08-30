import assert from 'node:assert/strict'
import test from 'node:test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { loadDocLinkSnapshot, validateDocLinks } from './check-doc-links.mjs'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const baseline = await loadDocLinkSnapshot(repositoryRoot)

function cloneSnapshot() {
  return {
    ...baseline,
    files: [...baseline.files],
    repositoryPaths: [...baseline.repositoryPaths],
    sources: { ...baseline.sources },
    loadedFiles: [...baseline.loadedFiles],
    protectedPathsRead: [...baseline.protectedPathsRead],
  }
}

test('current Markdown links and local anchors pass', () => {
  assert.deepEqual(validateDocLinks(baseline), [])
})

test('missing local file link fails with a relative diagnostic', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['README.md'] += '\n[missing](docs/does-not-exist.md)\n'
  const errors = validateDocLinks(snapshot)
  assert.ok(errors.some((error) => error.field === 'markdown.target' && error.path === 'README.md'))
  assert.equal(errors.some((error) => JSON.stringify(error).includes('password')), false)
})

test('missing local anchor fails', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['README.md'] += '\n[bad anchor](docs/development/v4-iteration-plan.md#not-present)\n'
  const errors = validateDocLinks(snapshot)
  assert.ok(errors.some((error) => error.field === 'markdown.anchor'))
})

test('historical documents and old evidence links are checked without rewriting their facts', () => {
  assert.ok(baseline.loadedFiles.some((file) => file.startsWith('docs/history/')))
  const snapshot = cloneSnapshot()
  snapshot.sources['docs/history/v3/iteration-plan.md'] += '\n历史路径：back-end/CC4C。\n'
  assert.deepEqual(validateDocLinks(snapshot), [])
})

test('protected paths are not loaded', () => {
  assert.equal(baseline.loadedFiles.some((file) => file.endsWith('application.yml')), false)
  assert.equal(baseline.loadedFiles.some((file) => file.endsWith('.env.runtime.local')), false)
  assert.equal(baseline.loadedFiles.some((file) => file.startsWith('deploy/secrets/local/')), false)
  assert.deepEqual(baseline.protectedPathsRead, [])
})

test('external links do not require network access', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['README.md'] += '\n[external](https://example.invalid/missing)\n'
  assert.deepEqual(validateDocLinks(snapshot), [])
})

test('repository escape is rejected', () => {
  const snapshot = cloneSnapshot()
  snapshot.sources['README.md'] += '\n[escape](../outside.md)\n'
  const errors = validateDocLinks(snapshot)
  assert.ok(errors.some((error) => error.expected === 'repository-relative target'))
})
