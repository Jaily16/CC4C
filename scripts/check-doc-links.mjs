import { execFile as execFileCallback } from 'node:child_process'
import { lstat, readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'

const execFile = promisify(execFileCallback)

export const DOC_PATHS = Object.freeze([
  'README.md',
  'frontend/README.md',
  'docs',
  'infrastructure/database/README.md',
  'infrastructure/secrets/README.md',
])

const PROTECTED_FILE_PATTERN = /(^|\/)\.env(?:\.[^/]+)?\.local$/i
const PROTECTED_DIRECTORY_PREFIXES = [
  '.git',
  'temp',
  'backend/target',
  'backend/logs',
  'back-end/CC4C/target',
  'back-end/CC4C/logs',
  'frontend/node_modules',
  'frontend/dist',
  'front-end/CC4C/node_modules',
  'front-end/CC4C/dist',
  'deploy/secrets/local',
  'infrastructure/secrets/local',
]

function normalizeRelative(relativePath) {
  return relativePath.replaceAll('\\', '/').replace(/^\.\//, '')
}

function isUnder(relativePath, parent) {
  const normalizedPath = normalizeRelative(relativePath).replace(/\/$/, '')
  const normalizedParent = normalizeRelative(parent).replace(/\/$/, '')
  return normalizedPath === normalizedParent || normalizedPath.startsWith(normalizedParent + '/')
}

function isProtectedRelative(relativePath) {
  const normalized = normalizeRelative(relativePath)
  return PROTECTED_FILE_PATTERN.test(normalized)
    || normalized.endsWith('/src/main/resources/application.yml')
    || PROTECTED_DIRECTORY_PREFIXES.some((prefix) => isUnder(normalized, prefix))
    || normalized === 'database/legacy/cc4c.sql'
}

function isMarkdownPath(relativePath) {
  return path.posix.extname(normalizeRelative(relativePath)).toLowerCase() === '.md'
}

function isDocumentationPath(relativePath) {
  const normalized = normalizeRelative(relativePath)
  return DOC_PATHS.some((entry) => entry === normalized || isUnder(normalized, entry))
}

function absolutePath(repositoryRoot, relativePath) {
  const root = path.resolve(repositoryRoot)
  const absolute = path.resolve(root, ...normalizeRelative(relativePath).split('/'))
  const relative = path.relative(root, absolute)
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
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
  const paths = []
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
      if (entry.isDirectory()) await visit(absolute, relative)
      else paths.push(relative)
    }
  }
  await visit(repositoryRoot, '')
  return paths
}

function isExternalTarget(target) {
  return /^(?:https?:|mailto:|tel:|data:|javascript:)/i.test(target)
    || target.startsWith('//')
}

function decodeTarget(target) {
  try {
    return decodeURIComponent(target)
  } catch {
    return null
  }
}

function stripCodeBlocks(text) {
  return text.replace(/^\s*(```|~~~)[^\n]*$[\s\S]*?^\s*\1\s*$/gm, '')
}

function collectMarkdownTargets(text) {
  const targets = []
  const source = stripCodeBlocks(text)
  const inlinePattern = /!?\[[^\]]*\]\((?:<([^>]+)>|([^\s)]+))(?:\s+[^)]*)?\)/g
  for (const match of source.matchAll(inlinePattern)) {
    targets.push(match[1] || match[2])
  }
  const referencePattern = /^\s{0,3}\[[^\]]+\]:\s*(?:<([^>]+)>|(\S+))/gm
  for (const match of source.matchAll(referencePattern)) {
    targets.push(match[1] || match[2])
  }
  return targets
}

function slugifyHeading(heading) {
  const withoutMarkup = heading
    .replace(/[`*_~]/g, '')
    .replace(/<[^>]+>/g, '')
    .trim()
    .toLowerCase()
  return withoutMarkup
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/[\s_]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

function collectAnchors(text) {
  const anchors = new Set()
  const headingCounts = new Map()
  const headingPattern = /^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/gm
  for (const match of stripCodeBlocks(text).matchAll(headingPattern)) {
    const base = slugifyHeading(match[1])
    if (!base) continue
    const count = headingCounts.get(base) || 0
    headingCounts.set(base, count + 1)
    anchors.add(count === 0 ? base : `${base}-${count}`)
    anchors.add(match[1].trim())
  }
  for (const match of stripCodeBlocks(text).matchAll(/\bid=["']([^"']+)["']/gi)) anchors.add(match[1])
  return anchors
}

function splitTarget(target) {
  const hashIndex = target.indexOf('#')
  const withoutFragment = hashIndex < 0 ? target : target.slice(0, hashIndex)
  const fragment = hashIndex < 0 ? '' : target.slice(hashIndex + 1)
  const queryIndex = withoutFragment.indexOf('?')
  return {
    filePart: queryIndex < 0 ? withoutFragment : withoutFragment.slice(0, queryIndex),
    fragment,
  }
}

function resolveLocalTarget(documentPath, target) {
  const decoded = decodeTarget(target)
  if (decoded === null) return { error: 'invalid percent encoding' }
  const { filePart, fragment } = splitTarget(decoded)
  if (!filePart) return { relativePath: documentPath, fragment }
  if (/^(?:[A-Za-z]:[\\/]|[\\/])/.test(filePart)) return { error: 'absolute target is not allowed' }
  const relativePath = normalizeRelative(path.posix.normalize(path.posix.join(
    path.posix.dirname(documentPath), filePart.replaceAll('\\', '/'),
  )))
  if (relativePath === '..' || relativePath.startsWith('../')) return { error: 'target escapes repository' }
  return { relativePath, fragment }
}

export async function loadDocLinkSnapshot(repositoryRoot) {
  const root = path.resolve(repositoryRoot)
  const trackedPaths = await gitPaths(root, ['ls-files', '--cached', '-z'])
  const untrackedPaths = await gitPaths(root, ['ls-files', '--others', '--exclude-standard', '-z'])
  const repositoryPaths = trackedPaths.length || untrackedPaths.length
    ? [...new Set([...trackedPaths, ...untrackedPaths])]
    : await filesystemPaths(root)
  const files = repositoryPaths.filter((relative) => isMarkdownPath(relative) && isDocumentationPath(relative))
  const sources = {}
  const loadedFiles = []
  for (const relative of files) {
    if (isProtectedRelative(relative)) continue
    const absolute = absolutePath(root, relative)
    let stats
    try {
      stats = await lstat(absolute)
    } catch {
      continue
    }
    if (!stats.isFile() || stats.isSymbolicLink()) continue
    sources[relative] = await readFile(absolute, 'utf8')
    loadedFiles.push(relative)
  }
  return {
    repositoryRoot: root,
    files: [...new Set(files)].sort(),
    repositoryPaths: [...new Set(repositoryPaths.map(normalizeRelative))].sort(),
    sources,
    loadedFiles: loadedFiles.sort(),
    protectedPathsRead: [],
  }
}

function addError(errors, relativePath, field, expected, actual) {
  errors.push({ path: relativePath, field, expected, actual })
}

function pathExists(snapshot, relativePath) {
  const normalized = normalizeRelative(relativePath)
  const repositoryPaths = new Set(snapshot.repositoryPaths || [])
  if (repositoryPaths.has(normalized)) return true
  return [...repositoryPaths].some((candidate) => isUnder(candidate, normalized))
}

export function validateDocLinks(snapshot) {
  const errors = []
  if (!snapshot || typeof snapshot !== 'object') {
    addError(errors, 'documentation', 'snapshot', 'object', typeof snapshot)
    return errors
  }
  for (const protectedPath of snapshot.protectedPathsRead || []) {
    addError(errors, protectedPath, 'protected.read', 'not loaded', 'loaded')
  }
  for (const [documentPath, text] of Object.entries(snapshot.sources || {})) {
    for (const rawTarget of collectMarkdownTargets(text)) {
      const target = rawTarget.trim()
      if (!target || target.startsWith('#') || isExternalTarget(target)) continue
      const resolved = resolveLocalTarget(documentPath, target)
      if (resolved.error) {
        addError(errors, documentPath, 'markdown.target', 'repository-relative target', resolved.error)
        continue
      }
      if (isProtectedRelative(resolved.relativePath)) continue
      if (!pathExists(snapshot, resolved.relativePath)) {
        addError(errors, documentPath, 'markdown.target', 'existing target: ' + resolved.relativePath, 'missing')
        continue
      }
      if (!resolved.fragment) continue
      if (!isMarkdownPath(resolved.relativePath)) continue
      const targetText = snapshot.sources?.[resolved.relativePath]
      if (typeof targetText !== 'string') continue
      const anchors = collectAnchors(targetText)
      const decodedFragment = decodeTarget(resolved.fragment)
      if (decodedFragment === null || (!anchors.has(decodedFragment) && !anchors.has(slugifyHeading(decodedFragment)))) {
        addError(errors, documentPath, 'markdown.anchor', 'existing anchor: ' + resolved.fragment, 'missing')
      }
    }
  }
  return errors
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const snapshot = await loadDocLinkSnapshot(repositoryRoot)
  const errors = validateDocLinks(snapshot)
  if (errors.length > 0) {
    for (const error of errors) process.stderr.write(JSON.stringify(error) + '\n')
    process.exitCode = 1
    return
  }
  process.stdout.write(`Documentation links are valid (${snapshot.loadedFiles.length} Markdown files checked).\n`)
}

const currentFile = path.resolve(fileURLToPath(import.meta.url))
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) await main()
