import { execFile as execFileCallback } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const execFile = promisify(execFileCallback);

export const QUALITY_PATHS = Object.freeze([
  'backend/src/main/java',
  'backend/src/test/java',
  'backend/src/gatling/java',
  'frontend/src',
  'frontend/tests',
  'scripts',
  '.editorconfig',
  'backend/pom.xml',
  'frontend/eslint.config.js',
  'frontend/prettier.config.mjs',
  'frontend/package.json',
]);

const TEXT_EXTENSIONS = new Set([
  '.css',
  '.html',
  '.java',
  '.js',
  '.json',
  '.mjs',
  '.ps1',
  '.sh',
  '.vue',
  '.xml',
  '.yml',
  '.yaml',
]);

const EXCLUDED_ROOTS = Object.freeze([
  'docs/history',
  'temp',
  'backend/target',
  'frontend/node_modules',
  'frontend/dist',
  'back-end/CC4C/target',
  'front-end/CC4C/node_modules',
  'front-end/CC4C/dist',
  'deploy/secrets/local',
  'infrastructure/secrets/local',
]);

const PROTECTED_FILE_PATTERN = /(^|\/)\.env(?:\.[^/]+)?\.local$/i;

function normalizeRelative(relativePath) {
  return relativePath.replaceAll('\\', '/').replace(/^\.\//, '');
}

function isUnder(relativePath, parent) {
  const normalizedPath = normalizeRelative(relativePath);
  const normalizedParent = normalizeRelative(parent).replace(/\/$/, '');
  return normalizedPath === normalizedParent || normalizedPath.startsWith(`${normalizedParent}/`);
}

function isExcluded(relativePath) {
  const normalized = normalizeRelative(relativePath);
  return EXCLUDED_ROOTS.some((root) => isUnder(normalized, root))
    || PROTECTED_FILE_PATTERN.test(normalized)
    || normalized.endsWith('/src/main/resources/application.yml')
    || normalized === 'database/legacy/cc4c.sql'
    || normalized.endsWith('package-lock.json')
    || normalized.startsWith('docs/reference/openapi.json');
}

function isQualityPath(relativePath) {
  const normalized = normalizeRelative(relativePath);
  if (isExcluded(normalized)) return false;
  return QUALITY_PATHS.some((qualityPath) => isUnder(normalized, qualityPath) || normalized === qualityPath);
}

function isTextPath(relativePath) {
  const basename = path.posix.basename(normalizeRelative(relativePath));
  return basename === '.editorconfig' || TEXT_EXTENSIONS.has(path.posix.extname(basename).toLowerCase());
}

function absolutePath(repositoryRoot, relativePath) {
  const root = path.resolve(repositoryRoot);
  const absolute = path.resolve(root, ...normalizeRelative(relativePath).split('/'));
  const relative = path.relative(root, absolute);
  if (relative === '' || relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`Path escapes repository root: ${relativePath}`);
  }
  return absolute;
}

async function gitPaths(repositoryRoot, args) {
  try {
    const result = await execFile('git', ['-C', repositoryRoot, ...args], {
      encoding: 'utf8',
      maxBuffer: 16 * 1024 * 1024,
    });
    return result.stdout.split('\0').filter(Boolean).map(normalizeRelative);
  } catch {
    return [];
  }
}

function addError(errors, relativePath, rule, expected, actual) {
  errors.push({
    path: normalizeRelative(relativePath),
    rule,
    expected,
    actual,
  });
}

function sourceEntries(snapshot) {
  if (snapshot?.sources instanceof Map) return [...snapshot.sources.entries()];
  if (snapshot?.sources && typeof snapshot.sources === 'object') return Object.entries(snapshot.sources);
  return [];
}

function validateTextShape(errors, relativePath, source) {
  if (typeof source !== 'string') {
    addError(errors, relativePath, 'utf8', 'valid UTF-8 text', typeof source);
    return;
  }
  if (source.startsWith('\uFEFF')) addError(errors, relativePath, 'bom', 'absent', 'present');
  if (source.includes('\r')) addError(errors, relativePath, 'line-ending', 'LF', 'CRLF or CR');
  if (source.length > 0 && !source.endsWith('\n')) addError(errors, relativePath, 'final-newline', 'present', 'missing');
  const controlCharacter = source.match(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/);
  if (controlCharacter) {
    addError(errors, relativePath, 'control-character', 'none', `U+${controlCharacter[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`);
  }
}

function validateBrowserConsole(errors, relativePath, source) {
  if (!isUnder(relativePath, 'frontend/src')) return;
  const consoleMatch = source.match(/\bconsole\.(?:log|debug|error|warn|info|trace)\s*\(/);
  const debuggerMatch = source.match(/\bdebugger\b/);
  if (debuggerMatch) addError(errors, relativePath, 'debugger', 'absent', 'present');
  if (!consoleMatch) return;
  if (relativePath !== 'frontend/src/utils/reportClientError.js') {
    addError(errors, relativePath, 'browser-console', 'absent', consoleMatch[0].trim());
    return;
  }
  if (!/if\s*\(\s*development\s*\)[\s\S]*console\./.test(source)) {
    addError(errors, relativePath, 'browser-console-development-guard', 'development-only branch', 'unguarded');
  }
}

function validatePublicJavaDocs(errors, relativePath, source) {
  if (!isUnder(relativePath, 'backend/src/main/java') || !relativePath.endsWith('.java')) return;
  const lines = source.split('\n');
  const declaration = /^\s*public\s+(?:(?:abstract|final|static|sealed|non-sealed)\s+)*(class|interface|record|enum)\s+([A-Za-z_$][\w$]*)\b/;
  for (let index = 0; index < lines.length; index += 1) {
    const match = lines[index].match(declaration);
    if (!match) continue;
    const preceding = lines.slice(Math.max(0, index - 16), index).join('\n');
    const javadoc = preceding.match(/\/\*\*[\s\S]*?\*\//g)?.at(-1);
    if (!javadoc || !/[\u3400-\u9FFF]/u.test(javadoc)) {
      addError(errors, relativePath, 'java-public-type-javadoc', 'Chinese Javadoc before public type', match[2]);
    }
  }
}

export function validateQuality(snapshot) {
  const errors = [];
  if (!snapshot || typeof snapshot !== 'object') {
    addError(errors, 'quality', 'snapshot', 'object', typeof snapshot);
    return errors;
  }
  for (const protectedPath of snapshot.protectedPathsRead || []) {
    addError(errors, protectedPath, 'protected-read', 'not loaded', 'loaded');
  }
  for (const [relativePath, source] of sourceEntries(snapshot)) {
    const normalized = normalizeRelative(relativePath);
    if (!isQualityPath(normalized) || !isTextPath(normalized)) continue;
    validateTextShape(errors, normalized, source);
    validateBrowserConsole(errors, normalized, source);
    validatePublicJavaDocs(errors, normalized, source);
  }
  return errors;
}

export async function loadQualitySnapshot(repositoryRoot) {
  const root = path.resolve(repositoryRoot);
  const trackedPaths = await gitPaths(root, ['ls-files', '--cached', '-z']);
  const untrackedPaths = await gitPaths(root, ['ls-files', '--others', '--exclude-standard', '-z']);
  const candidates = [...new Set([...trackedPaths, ...untrackedPaths])]
    .map(normalizeRelative)
    .filter((relativePath) => isQualityPath(relativePath) && isTextPath(relativePath))
    .sort();
  const sources = {};
  const loadedFiles = [];
  for (const relativePath of candidates) {
    const absolute = absolutePath(root, relativePath);
    try {
      const buffer = await readFile(absolute);
      let source;
      try {
        source = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
      } catch {
        throw new Error('invalid UTF-8');
      }
      sources[relativePath] = source;
      loadedFiles.push(relativePath);
    } catch (error) {
      if (error?.code === 'ENOENT') continue;
      throw new Error(`Unable to load quality source ${relativePath}: ${error?.message || 'read failed'}`);
    }
  }
  return {
    repositoryRoot: root,
    sources,
    loadedFiles,
    trackedPaths: trackedPaths.filter(isQualityPath).sort(),
    untrackedPaths: untrackedPaths.filter(isQualityPath).sort(),
    protectedPathsRead: [],
  };
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  try {
    const snapshot = await loadQualitySnapshot(repositoryRoot);
    const errors = validateQuality(snapshot);
    if (errors.length > 0) {
      for (const error of errors) process.stderr.write(`${JSON.stringify(error)}\n`);
      process.exitCode = 1;
      return;
    }
    process.stdout.write('Source quality snapshot is consistent.\n');
  } catch (error) {
    process.stderr.write(`${error?.message || 'Source quality check failed'}\n`);
    process.exitCode = 1;
  }
}

const currentFile = path.resolve(fileURLToPath(import.meta.url));
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) await main();
