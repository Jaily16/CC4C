import { execFile as execFileCallback } from 'node:child_process'
import { lstat, readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'

const execFile = promisify(execFileCallback)

export const CANONICAL_PATHS = Object.freeze([
  'backend',
  'frontend',
  'infrastructure/database',
  'infrastructure/rabbitmq',
  'infrastructure/observability',
  'infrastructure/secrets',
  'scripts/development',
  'scripts/testing',
  'scripts/performance',
  'scripts/deployment',
  'docs/architecture',
  'docs/development',
  'docs/operations',
  'docs/reference',
  'docs/history',
  'backend/pom.xml',
  'backend/Dockerfile',
  'frontend/package.json',
  'frontend/package-lock.json',
  'frontend/Dockerfile',
  'frontend/src/api/client.js',
  'frontend/src/api/auth.js',
  'frontend/src/api/profile.js',
  'frontend/src/api/catalog.js',
  'frontend/src/api/community.js',
  'frontend/src/api/interactions.js',
  'frontend/src/api/messaging.js',
  'frontend/src/composables/useCurrentUser.js',
  'scripts/check-versions.mjs',
  'scripts/check-versions.test.mjs',
  'scripts/check-structure.mjs',
  'scripts/check-structure.test.mjs',
  'scripts/check-deployment-modes.mjs',
  'scripts/check-deployment-modes.test.mjs',
  'scripts/development/host-environment.ps1',
  'scripts/development/host-preflight.ps1',
  'scripts/development/start-backend.ps1',
  'scripts/development/stop-backend.ps1',
  'scripts/development/start-frontend.ps1',
  'scripts/development/stop-frontend.ps1',
  'scripts/development/start-host-stack.ps1',
  'scripts/development/stop-host-stack.ps1',
  'scripts/development/health-host-stack.ps1',
  'scripts/deployment/migrate-compose-identity.ps1',
  'docs/reference/openapi.json',
  'docs/development/v4-iteration-plan.md',
])

export const LEGACY_EXCEPTIONS = Object.freeze([
  'database/legacy/cc4c.sql',
  'deploy/secrets/local',
  'back-end/CC4C/src/main/resources/application.yml',
  'back-end/CC4C/.env.*.local',
  'back-end/CC4C/target',
  'back-end/CC4C/logs',
  'front-end/CC4C/node_modules',
  'front-end/CC4C/dist',
  'front-end/CC4C/.env.*.local',
  'front-end/CC4C/public/avatar',
  'front-end/CC4C/public/blogImg',
  'observability/.env.observability.local',
  'deploy/rabbitmq/rabbitmq.conf',
  'deploy/scripts/rabbit-entrypoint.sh',
  'deploy/scripts/rabbit-init.sh',
  'deploy/scripts/rabbit-password-hash.escript',
  'observability/grafana',
  'observability/prometheus',
  'temp/cc4c-v3-aspect7-gatling',
  'docs/history/**',
  'docs/development/v4-iteration-plan.md',
  'docs/operations/compose-identity-migration.md',
  'docs/operations/host-runbook.md',
  'scripts/deployment/migrate-compose-identity.ps1',
  'compose.yml:cc4c-v3',
  'versions.yml:compatibility',
])

const TEXT_EXTENSIONS = new Set([
  '.css', '.html', '.java', '.js', '.json', '.md', '.mjs', '.ps1', '.sh', '.svg',
  '.vue', '.xml', '.yml', '.yaml', '.example',
])

const LEGACY_MARKER_PATTERN = /(?:com\.CC4C|myvue|back[-\\/]end[\\/]CC4C|front[-\\/]end[\\/]CC4C|aspect4-benchmark|aspect6-gatling|run-aspect4-benchmark|run-aspect6-gatling|Aspect2ContractFunctionalTest|V2CompatibilityFunctionalTest|Aspect4BenchmarkApplication|Aspect4PerformanceDataSeeder|CC4C-Aspect[46]|cc4c-v3-aspect4)/
const CASE_INSENSITIVE_MYVUE_PATTERN = /myvue/i

const LEGACY_LIVE_BINDINGS = new Set([
  'deploy/rabbitmq/rabbitmq.conf',
  'deploy/scripts/rabbit-entrypoint.sh',
  'deploy/scripts/rabbit-init.sh',
  'deploy/scripts/rabbit-password-hash.escript',
  'observability/grafana/provisioning/dashboards/cc4c.yml',
  'observability/grafana/provisioning/datasources/prometheus.yml',
  'observability/grafana/dashboards/cc4c-api-jvm.json',
  'observability/grafana/dashboards/cc4c-db-cache-security.json',
  'observability/grafana/dashboards/cc4c-messaging.json',
  'observability/prometheus/prometheus.compose.yml',
  'observability/prometheus/prometheus.yml.template',
  'observability/prometheus/rules/cc4c-alerts.yml',
  'observability/prometheus/tests/cc4c-alerts.test.yml',
])

const PROTECTED_FILE_PATTERN = /(^|\/)\.env(?:\.[^/]+)?\.local$/i

function normalizeRelative(relativePath) {
  return relativePath.replaceAll('\\', '/').replace(/^\.\//, '')
}

function isUnder(relativePath, parent) {
  const normalizedPath = normalizeRelative(relativePath)
  const normalizedParent = normalizeRelative(parent).replace(/\/$/, '')
  return normalizedPath === normalizedParent || normalizedPath.startsWith(normalizedParent + '/')
}

function matchesException(relativePath, exception) {
  if (exception.endsWith('/**')) return isUnder(relativePath, exception.slice(0, -3))
  if (exception.includes(':')) return false
  return isUnder(relativePath, exception)
}

function isHistoryOrTemp(relativePath) {
  return isUnder(relativePath, 'temp') || isUnder(relativePath, 'docs/history')
}

function isProtectedRelative(relativePath) {
  const normalized = normalizeRelative(relativePath)
  return isHistoryOrTemp(normalized)
    || isUnder(normalized, '.git')
    || isUnder(normalized, 'backend/target')
    || isUnder(normalized, 'backend/logs')
    || isUnder(normalized, 'frontend/node_modules')
    || isUnder(normalized, 'frontend/dist')
    || isUnder(normalized, 'back-end/CC4C/target')
    || isUnder(normalized, 'back-end/CC4C/logs')
    || isUnder(normalized, 'front-end/CC4C/node_modules')
    || isUnder(normalized, 'front-end/CC4C/dist')
    || normalized === 'database/legacy/cc4c.sql'
    || isUnder(normalized, 'deploy/secrets/local')
    || isUnder(normalized, 'infrastructure/secrets/local')
    || PROTECTED_FILE_PATTERN.test(normalized)
    || normalized.endsWith('/src/main/resources/application.yml')
    || isUnder(normalized, 'frontend/public/avatar')
    || isUnder(normalized, 'frontend/public/blogImg')
    || isUnder(normalized, 'front-end/CC4C/public/avatar')
    || isUnder(normalized, 'front-end/CC4C/public/blogImg')
}

function isTextPath(relativePath) {
  const basename = path.posix.basename(normalizeRelative(relativePath))
  return basename === '.gitignore' || basename === '.dockerignore'
    || TEXT_EXTENSIONS.has(path.posix.extname(basename).toLowerCase())
}

function absolutePath(root, relativePath) {
  const absolute = path.resolve(root, ...normalizeRelative(relativePath).split('/'))
  const rootAbsolute = path.resolve(root)
  const relative = path.relative(rootAbsolute, absolute)
  if (relative === '' || relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`Path escapes repository root: ${relativePath}`)
  }
  return absolute
}

async function gitPaths(repositoryRoot, args) {
  try {
    const result = await execFile('git', ['-C', repositoryRoot, ...args], {
      encoding: 'utf8',
      maxBuffer: 16 * 1024 * 1024,
    })
    return result.stdout.split('\0').filter(Boolean).map(normalizeRelative)
  } catch {
    return []
  }
}

async function filesystemPaths(repositoryRoot) {
  const result = []
  async function visit(absoluteDirectory, relativeDirectory) {
    let entries
    try {
      entries = await readdir(absoluteDirectory, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      const relative = normalizeRelative(path.posix.join(relativeDirectory, entry.name))
      if (isProtectedRelative(relative)) continue
      const absolute = path.join(absoluteDirectory, entry.name)
      if (entry.isDirectory()) {
        await visit(absolute, relative)
      } else {
        result.push(relative)
      }
    }
  }
  await visit(repositoryRoot, '')
  return result
}

async function collectReparsePoints(repositoryRoot, relativeDirectory, output) {
  const absoluteDirectory = absolutePath(repositoryRoot, relativeDirectory)
  let entries
  try {
    entries = await readdir(absoluteDirectory, { withFileTypes: true })
  } catch {
    return
  }
  for (const entry of entries) {
    const relative = normalizeRelative(path.posix.join(relativeDirectory, entry.name))
    if (isProtectedRelative(relative)) continue
    const absolute = absolutePath(repositoryRoot, relative)
    let stats
    try {
      stats = await lstat(absolute)
    } catch {
      continue
    }
    if (stats.isSymbolicLink()) {
      output.push(relative)
      continue
    }
    if (entry.isDirectory()) await collectReparsePoints(repositoryRoot, relative, output)
  }
}

export async function loadLayoutSnapshot(repositoryRoot) {
  const root = path.resolve(repositoryRoot)
  const trackedPaths = await gitPaths(root, ['ls-files', '--cached', '-z'])
  const untrackedPaths = await gitPaths(root, ['ls-files', '--others', '--exclude-standard', '-z'])
  const candidates = trackedPaths.length || untrackedPaths.length
    ? [...new Set([...trackedPaths, ...untrackedPaths])]
    : await filesystemPaths(root)
  const tracked = new Set(trackedPaths)
  const untracked = new Set(untrackedPaths)
  const files = []
  const sources = {}
  for (const candidate of candidates) {
    const relative = normalizeRelative(candidate)
    if (isHistoryOrTemp(relative) || isProtectedRelative(relative)) continue
    const absolute = absolutePath(root, relative)
    let stats
    try {
      stats = await lstat(absolute)
    } catch {
      continue
    }
    if (!stats.isFile()) continue
    files.push(relative)
    if (isTextPath(relative)) sources[relative] = await readFile(absolute, 'utf8')
  }

  const canonical = {}
  for (const relative of CANONICAL_PATHS) {
    const absolute = absolutePath(root, relative)
    try {
      const stats = await lstat(absolute)
      canonical[relative] = { exists: true, directory: stats.isDirectory(), symlink: stats.isSymbolicLink() }
    } catch {
      canonical[relative] = { exists: false, directory: false, symlink: false }
    }
  }

  const reparsePoints = []
  for (const directory of CANONICAL_PATHS.filter((entry) => !path.posix.extname(entry))) {
    if (canonical[directory]?.exists && canonical[directory]?.directory) {
      await collectReparsePoints(root, directory, reparsePoints)
    }
  }

  return {
    repositoryRoot: root,
    files: [...new Set(files)].sort(),
    trackedPaths: [...tracked].sort(),
    untrackedPaths: [...untracked].sort(),
    sources,
    loadedFiles: Object.keys(sources).sort(),
    canonical,
    reparsePoints: [...new Set(reparsePoints)].sort(),
    protectedPathsRead: [],
  }
}

function sourceEntries(snapshot) {
  if (snapshot?.sources instanceof Map) return [...snapshot.sources.entries()]
  if (snapshot?.sources && typeof snapshot.sources === 'object') return Object.entries(snapshot.sources)
  return []
}

function addError(errors, relativePath, field, expected, actual) {
  errors.push({ path: relativePath, field, expected, actual })
}

function snapshotFiles(snapshot) {
  return new Set((snapshot?.files || snapshot?.loadedFiles || []).map(normalizeRelative))
}

function hasPath(snapshot, relativePath) {
  const normalized = normalizeRelative(relativePath)
  const canonical = snapshot?.canonical?.[normalized]
  if (canonical?.exists) return true
  const files = snapshotFiles(snapshot)
  return files.has(normalized) || [...files].some((file) => isUnder(file, normalized))
}

function allowedLegacyText(relativePath, text) {
  let filtered = text
  if (relativePath === 'README.md' || relativePath === 'docs/operations/container-runbook.md') {
    filtered = filtered.replaceAll('back-end/CC4C/src/main/resources/application.yml', '')
  }
  if (relativePath.endsWith('.ps1')) {
    filtered = filtered
      .replaceAll('back-end\\CC4C\\.env.runtime.local', '')
      .replaceAll('back-end\\CC4C\\.env.test.local', '')
      .replaceAll('back-end\\CC4C\\.env.performance.local', '')
      .replaceAll('back-end/CC4C/.env.runtime.local', '')
      .replaceAll('back-end/CC4C/.env.test.local', '')
      .replaceAll('back-end/CC4C/.env.performance.local', '')
      .replaceAll('front-end\\CC4C\\.env.local', '')
      .replaceAll('front-end/CC4C/.env.local', '')
      .replaceAll("back-end\\CC4C'", "'")
  }
  return filtered
}

function isAllowedTextPath(relativePath) {
  return relativePath === 'versions.yml'
    || relativePath === 'compose.yml'
    || relativePath === 'README.md'
    || relativePath === 'docs/operations/container-runbook.md'
    || relativePath.endsWith('run-backend.ps1')
    || relativePath.endsWith('prepare-flyway-tests.ps1')
    || relativePath.endsWith('restore-flyway-test-backup.ps1')
    || relativePath.endsWith('capture-query-plans.ps1')
    || relativePath.endsWith('run-performance-benchmark.ps1')
    || relativePath.endsWith('run-performance-gatling.ps1')
    || relativePath.endsWith('start-performance-server.ps1')
    || relativePath.startsWith('scripts/development/')
    || relativePath === 'scripts/deployment/migrate-compose-identity.ps1'
}

function isDocumentationException(relativePath) {
  return relativePath === '.gitignore'
    || relativePath === 'scripts/check-structure.mjs'
    || relativePath === 'scripts/check-structure.test.mjs'
    || relativePath === 'scripts/check-source-quality.mjs'
    || relativePath === 'scripts/check-source-quality.test.mjs'
    || relativePath === 'scripts/check-doc-links.mjs'
    || relativePath === 'scripts/check-doc-links.test.mjs'
    || relativePath === 'scripts/check-versions.test.mjs'
    || relativePath === 'versions.yml'
    || relativePath === 'docs/development/v4-iteration-plan.md'
    || relativePath === 'docs/operations/compose-identity-migration.md'
    || relativePath === 'docs/operations/host-runbook.md'
}

function legacyPathIsAllowed(relativePath) {
  const normalized = normalizeRelative(relativePath)
  if (LEGACY_LIVE_BINDINGS.has(normalized)) return true
  return LEGACY_EXCEPTIONS.some((exception) => matchesException(normalized, exception))
}

function activeLegacyPath(relativePath) {
  const normalized = normalizeRelative(relativePath)
  if (!isUnder(normalized, 'back-end/CC4C') && !isUnder(normalized, 'front-end/CC4C')) return false
  return !legacyPathIsAllowed(normalized)
}

function expectedImportPath(snapshot, importer, specifier) {
  const root = snapshot?.repositoryRoot
  if (!root) return null
  const sourceRoot = path.resolve(root, 'frontend', 'src')
  const target = specifier.startsWith('@/')
    ? path.resolve(sourceRoot, specifier.slice(2))
    : path.resolve(path.dirname(path.resolve(root, ...importer.split('/'))), specifier)
  const relative = normalizeRelative(path.relative(root, target))
  const candidates = [
    relative,
    `${relative}.js`, `${relative}.mjs`, `${relative}.vue`, `${relative}.css`, `${relative}.json`,
    `${relative}.gif`, `${relative}.ico`, `${relative}.jpeg`, `${relative}.jpg`, `${relative}.png`,
    `${relative}.svg`, `${relative}.webp`,
    `${relative}/index.js`,
  ]
  return candidates.find((candidate) => hasPath(snapshot, candidate)) || null
}

function validateFrontendImports(errors, snapshot, relativePath, text) {
  const importPattern = /(?:\bfrom\s*|\bimport\s*\(\s*)['"]([^'"]+)['"]/g
  const code = text.replace(/\/\*[\s\S]*?\*\/|^\s*\/\/.*$/gm, '')
  for (const match of code.matchAll(importPattern)) {
    const specifier = match[1]
    if (!specifier.startsWith('@/') && !specifier.startsWith('./') && !specifier.startsWith('../')) continue
    if (!expectedImportPath(snapshot, relativePath, specifier)) {
      addError(errors, relativePath, 'frontend.import', 'resolvable: ' + specifier, 'missing')
    }
  }
}

export function validateLayout(snapshot) {
  const errors = []
  if (!snapshot || typeof snapshot !== 'object') {
    addError(errors, 'layout', 'snapshot', 'object', typeof snapshot)
    return errors
  }

  for (const canonicalPath of CANONICAL_PATHS) {
    if (!hasPath(snapshot, canonicalPath)) {
      addError(errors, canonicalPath, 'canonical.path', 'exists', 'missing')
    }
  }

  for (const protectedPath of snapshot.protectedPathsRead || []) {
    addError(errors, protectedPath, 'protected.read', 'not loaded', 'loaded')
  }

  for (const reparsePoint of snapshot.reparsePoints || []) {
    if (!legacyPathIsAllowed(reparsePoint)) {
      addError(errors, reparsePoint, 'reparsePoint', 'absent from canonical tree', 'present')
    }
  }

  for (const [relativePath, rawText] of sourceEntries(snapshot)) {
    const relative = normalizeRelative(relativePath)
    if (isHistoryOrTemp(relative) || isProtectedRelative(relative)) continue
    if (activeLegacyPath(relative)) {
      addError(errors, relative, 'legacy.activeFile', 'canonical path or approved exception', 'legacy path')
      continue
    }
    if (isDocumentationException(relative)) continue
    if (relative.endsWith('.java') && isUnder(relative, 'backend/src/')) {
      const packageName = rawText.match(/^\s*package\s+([\w.]+);/m)?.[1]
      if (packageName && packageName !== 'com.cc4c' && !packageName.startsWith('com.cc4c.')
        && packageName !== 'com.cc4ctools' && !packageName.startsWith('com.cc4ctools.')) {
        addError(errors, relative, 'java.package', 'com.cc4c or com.cc4ctools', packageName)
      }
    }
    const text = isAllowedTextPath(relative) ? allowedLegacyText(relative, rawText) : rawText
    const legacyMatch = text.match(LEGACY_MARKER_PATTERN) || relative.match(LEGACY_MARKER_PATTERN)
    const myVueMatch = text.match(CASE_INSENSITIVE_MYVUE_PATTERN) || relative.match(CASE_INSENSITIVE_MYVUE_PATTERN)
    if (legacyMatch || myVueMatch) {
      addError(errors, relative, 'legacy.reference', 'no active legacy marker', legacyMatch?.[0] || myVueMatch?.[0] || 'present')
    }
    if (relative.startsWith('frontend/src/')) validateFrontendImports(errors, snapshot, relative, rawText)
    if (rawText.includes('LoginRegister')) {
      addError(errors, relative, 'dead.loginRegister', 'no LoginRegister reference', 'present')
    }
  }

  return errors
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const snapshot = await loadLayoutSnapshot(repositoryRoot)
  const errors = validateLayout(snapshot)
  if (errors.length > 0) {
    for (const error of errors) process.stderr.write(JSON.stringify(error) + '\n')
    process.exitCode = 1
    return
  }
  process.stdout.write('Repository structure is canonical.\n')
}

const currentFile = path.resolve(fileURLToPath(import.meta.url))
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) await main()
