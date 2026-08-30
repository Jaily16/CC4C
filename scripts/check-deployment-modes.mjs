import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const MODE_PATHS = Object.freeze([
  'compose.yml',
  'compose.smtp.yml',
  'versions.yml',
  'backend/src/main/resources/application-example.yml',
  'backend/.env.runtime.example',
  'frontend/.env.example',
  'infrastructure/observability/.env.observability.example',
  'infrastructure/observability/prometheus/prometheus.compose.yml',
  'infrastructure/observability/prometheus/prometheus.yml.template',
  'backend/src/main/java/com/cc4c/shared/AsyncEventTypes.java',
  'scripts/development/host-environment.ps1',
  'scripts/development/host-preflight.ps1',
  'scripts/development/start-backend.ps1',
  'scripts/development/stop-backend.ps1',
  'scripts/development/start-frontend.ps1',
  'scripts/development/stop-frontend.ps1',
  'scripts/development/start-host-stack.ps1',
  'scripts/development/stop-host-stack.ps1',
  'scripts/development/health-host-stack.ps1',
  'scripts/development/start-observability.ps1',
  'scripts/development/stop-observability.ps1',
  'scripts/development/observability-preflight.ps1',
  'scripts/development/run-backend.ps1',
  'scripts/deployment/bootstrap-admin.ps1',
  'scripts/deployment/reset-local.ps1',
  'scripts/deployment/migrate-compose-identity.ps1',
  'scripts/performance/run-container-performance.ps1'
]);

const REQUIRED_COMPOSE_VOLUMES = Object.freeze([
  'mysql_data',
  'redis_security_data',
  'redis_cache_data',
  'rabbitmq_data',
  'prometheus_data',
  'grafana_data',
  'blog_uploads',
  'avatar_uploads'
]);

const PROTECTED_PATH_PATTERN = /(?:application\.yml$|\.env\.[^.]+\.local$|^deploy\/secrets\/local(?:\/|$))/i;
const EVENT_NAMES = Object.freeze([
  'identity.verification-email.requested.v1',
  'community.blog.submitted.v1',
  'community.blog.reviewed.v1'
]);

function sourceMap(snapshot) {
  return snapshot && snapshot.sources ? snapshot.sources : snapshot;
}

function textOf(snapshot, relativePath) {
  const sources = sourceMap(snapshot);
  const value = sources instanceof Map ? sources.get(relativePath) : sources?.[relativePath];
  return typeof value === 'string' ? value.replace(/\r\n/g, '\n') : undefined;
}

function addError(errors, relativePath, rule, expected, actual) {
  errors.push({
    path: relativePath,
    rule,
    expected,
    actual
  });
}

function requireText(errors, snapshot, relativePath) {
  const text = textOf(snapshot, relativePath);
  if (typeof text !== 'string') {
    addError(errors, relativePath, 'file', 'loaded', 'missing');
  }
  return text;
}

function requireContains(errors, snapshot, relativePath, rule, expected) {
  const text = requireText(errors, snapshot, relativePath);
  if (typeof text === 'string' && !text.includes(expected)) {
    addError(errors, relativePath, rule, expected, 'not found');
  }
}

function requireNotContains(errors, snapshot, relativePath, rule, forbidden) {
  const text = textOf(snapshot, relativePath);
  if (typeof text === 'string' && text.includes(forbidden)) {
    addError(errors, relativePath, rule, 'absent: ' + forbidden, forbidden);
  }
}

function requireVersion(errors, manifest, relativePath, expected) {
  const actual = relativePath.split('.').reduce((value, key) => value?.[key], manifest);
  if (actual !== expected) {
    addError(errors, 'versions.yml', relativePath, expected, actual ?? null);
  }
}

function serviceBlock(compose, serviceName) {
  const match = compose.match(new RegExp('(?:^|\\n)  ' + serviceName + ':\\n'));
  if (!match || match.index === undefined) return undefined;
  const start = match.index + match[0].length;
  const rest = compose.slice(start);
  const next = rest.search(/\n  [A-Za-z0-9][A-Za-z0-9_-]*:\n/);
  return next < 0 ? rest : rest.slice(0, next);
}

function parseManifest(text) {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export async function loadDeploymentModeSnapshot(repositoryRoot) {
  const root = path.resolve(repositoryRoot);
  const entries = await Promise.all(MODE_PATHS.map(async (relativePath) => {
    if (PROTECTED_PATH_PATTERN.test(relativePath)) {
      throw new Error('Protected path was included in the deployment mode allowlist.');
    }
    const absolutePath = path.join(root, ...relativePath.split('/'));
    return [relativePath, await readFile(absolutePath, 'utf8')];
  }));
  return {
    repositoryRoot: root,
    sources: Object.fromEntries(entries)
  };
}

export function validateDeploymentModes(snapshot) {
  const errors = [];
  const manifest = parseManifest(textOf(snapshot, 'versions.yml') || '');
  if (!manifest || manifest.schemaVersion !== 1) {
    addError(errors, 'versions.yml', 'schemaVersion', 1, manifest?.schemaVersion ?? 'invalid');
    return errors;
  }
  const compatibility = manifest.compatibility || {};
  for (const [field, expected] of [
    ['composeProject', 'cc4c'],
    ['legacyComposeProject', 'cc4c-v3'],
    ['ciComposeProject', 'cc4c-ci'],
    ['performanceComposeProject', 'cc4c-perf'],
    ['volumePrefix', 'cc4c'],
    ['legacyVolumePrefix', 'cc4c-v3'],
    ['flywayMigrations', 'V1-V7'],
    ['messageSchema', '*.v1']
  ]) {
    if (compatibility[field] !== expected) {
      addError(errors, 'versions.yml', 'compatibility.' + field, expected, compatibility[field] ?? null);
    }
  }

  const compose = requireText(errors, snapshot, 'compose.yml') || '';
  if (!/^name: cc4c$/m.test(compose)) {
    addError(errors, 'compose.yml', 'project', 'name: cc4c', 'missing or drifted');
  }
  for (const service of [
    'mysql',
    'redis-security',
    'redis-cache',
    'rabbitmq',
    'rabbit-init',
    'mailpit',
    'storage-init',
    'backend',
    'frontend',
    'prometheus',
    'grafana',
    'perf-init',
    'backend-perf',
    'performance-tools',
    'admin-bootstrap'
  ]) {
    if (!serviceBlock(compose, service)) {
      addError(errors, 'compose.yml', 'service.' + service, 'declared', 'missing');
    }
  }
  for (const [name, port] of [
    ['rabbitmqManagement', '127.0.0.1:${CC4C_RABBITMQ_MANAGEMENT_PORT:-15672}:15672'],
    ['mailpitUi', '127.0.0.1:${CC4C_MAILPIT_UI_PORT:-8025}:8025'],
    ['backendHttp', '127.0.0.1:4080:4080'],
    ['backendManagement', '127.0.0.1:4081:4081'],
    ['frontendHttp', '127.0.0.1:5173:8080'],
    ['prometheusHttp', '127.0.0.1:9090:9090'],
    ['grafanaHttp', '127.0.0.1:3000:3000']
  ]) {
    requireContains(errors, snapshot, 'compose.yml', 'port.' + name, port);
  }
  for (const network of [
    'app',
    'observability',
    'rabbit-host',
    'mailpit-host',
    'frontend-host',
    'prometheus-host',
    'grafana-host',
    'backend-host',
    'egress'
  ]) {
    requireContains(errors, snapshot, 'compose.yml', 'network.' + network, '\n  ' + network + ':');
  }
  for (const volume of REQUIRED_COMPOSE_VOLUMES) {
    requireContains(errors, snapshot, 'compose.yml', 'volume.' + volume, '\n  ' + volume + ':');
  }
  const volumeSection = compose.match(/\nvolumes:\n([\s\S]*?)(?:\nconfigs:\n|\nsecrets:\n|$)/)?.[1] || '';
  if (/\n\s+name\s*:/.test(volumeSection)) {
    addError(errors, 'compose.yml', 'volumes.fixedName', 'no fixed volume name', 'name:');
  }
  for (const pathText of [
    './backend',
    './frontend',
    './infrastructure/observability/grafana',
    './infrastructure/observability/prometheus',
    './infrastructure/rabbitmq',
    './scripts/performance/perf-init.sh',
    './scripts/performance/performance-entrypoint.sh',
    './deploy/secrets/local',
    '${CC4C_PERF_GATLING_HOST_PATH:-./temp/cc4c-v3-aspect7-gatling}'
  ]) {
    requireContains(errors, snapshot, 'compose.yml', 'path.' + pathText, pathText);
  }
  for (const image of [
    'cc4c/backend:4.0.0-SNAPSHOT',
    'cc4c/frontend:4.0.0-SNAPSHOT',
    'cc4c/performance-tools:4.0.0-SNAPSHOT'
  ]) {
    requireContains(errors, snapshot, 'compose.yml', 'localImage.' + image, 'image: ' + image);
  }
  for (const namespace of [
    'cc4c:v3:session:compose',
    'cc4c:v3:cache:compose',
    'cc4c.v3.messaging.local',
    'cc4c:v3:session:compose-perf',
    'cc4c:v3:cache:compose-perf',
    'cc4c.v3.messaging.perf'
  ]) {
    requireContains(errors, snapshot, 'compose.yml', 'namespace.' + namespace, namespace);
  }
  for (const service of ['backend', 'backend-perf', 'admin-bootstrap']) {
    const block = serviceBlock(compose, service);
    if (!block || !block.includes('SPRING_APPLICATION_NAME: CC4C')) {
      addError(errors, 'compose.yml', 'service.' + service + '.applicationName', 'SPRING_APPLICATION_NAME: CC4C', 'not found');
    }
  }
  for (const upload of [
    '/var/lib/cc4c/uploads/blog',
    '/var/lib/cc4c/uploads/avatar',
    '/blogImg/',
    '/avatar/'
  ]) {
    requireContains(errors, snapshot, 'compose.yml', 'upload.' + upload, upload);
  }
  requireNotContains(errors, snapshot, 'compose.yml', 'forbidden.composeProject', 'name: cc4c-v3');

  const smtp = requireText(errors, snapshot, 'compose.smtp.yml') || '';
  requireContains(errors, snapshot, 'compose.smtp.yml', 'smtp.username.secret', './deploy/secrets/local/smtp_username');
  requireContains(errors, snapshot, 'compose.smtp.yml', 'smtp.password.secret', './deploy/secrets/local/smtp_password');
  if (smtp.includes('docker compose -p cc4c-v3')) {
    addError(errors, 'compose.smtp.yml', 'forbidden.legacyProject', 'no active cc4c-v3 command', 'cc4c-v3');
  }

  const application = requireText(errors, snapshot, 'backend/src/main/resources/application-example.yml') || '';
  requireContains(errors, snapshot, 'backend/src/main/resources/application-example.yml', 'spring.application.name', 'name: CC4C');
  requireContains(errors, snapshot, 'backend/src/main/resources/application-example.yml', 'flyway.location', 'locations: classpath:db/migration');
  for (const upload of [
    '../../frontend/public/blogImg/',
    '../../frontend/public/avatar/',
    'http://localhost:5173/blogImg/',
    'http://localhost:5173/avatar/'
  ]) {
    requireContains(errors, snapshot, 'backend/src/main/resources/application-example.yml', 'upload.' + upload, upload);
  }
  if (application.includes('application.yml')) {
    addError(errors, 'backend/src/main/resources/application-example.yml', 'protected.applicationConfig', 'application-example only', 'application.yml');
  }

  const runtimeExample = requireText(errors, snapshot, 'backend/.env.runtime.example') || '';
  for (const name of [
    'CC4C_MAIL_HOST',
    'CC4C_MAIL_PORT',
    'CC4C_MAIL_AUTH',
    'CC4C_MAIL_SSL_ENABLED',
    'CC4C_MAIL_STARTTLS_ENABLED',
    'CC4C_SESSION_NAMESPACE',
    'CC4C_CACHE_NAMESPACE',
    'CC4C_RABBITMQ_NAMESPACE'
  ]) {
    requireContains(errors, snapshot, 'backend/.env.runtime.example', 'env.' + name, name + '=');
  }
  const frontendExample = requireText(errors, snapshot, 'frontend/.env.example') || '';
  requireContains(errors, snapshot, 'frontend/.env.example', 'env.VITE_API_BASE_URL', 'VITE_API_BASE_URL=');

  const eventTypes = requireText(errors, snapshot, 'backend/src/main/java/com/cc4c/shared/AsyncEventTypes.java') || '';
  for (const eventName of EVENT_NAMES) {
    requireContains(errors, snapshot, 'backend/src/main/java/com/cc4c/shared/AsyncEventTypes.java', 'event.' + eventName, eventName);
  }

  const hostEnvironment = requireText(errors, snapshot, 'scripts/development/host-environment.ps1') || '';
  for (const fallback of [
    'backend\\.env.runtime.local',
    'back-end\\CC4C\\.env.runtime.local',
    'frontend\\.env.local',
    'front-end\\CC4C\\.env.local',
    'infrastructure\\observability\\.env.observability.local',
    'observability\\.env.observability.local'
  ]) {
    requireContains(errors, snapshot, 'scripts/development/host-environment.ps1', 'fallback.' + fallback, fallback);
  }
  for (const forbidden of ['application.yml', 'deploy\\secrets\\local']) {
    requireNotContains(errors, snapshot, 'scripts/development/host-environment.ps1', 'protected.' + forbidden, forbidden);
  }

  const preflight = requireText(errors, snapshot, 'scripts/development/host-preflight.ps1') || '';
  for (const marker of ['MySQL', 'Security Redis', 'Business cache Redis', 'RabbitMQ', 'Mailpit or SMTP']) {
    requireContains(errors, snapshot, 'scripts/development/host-preflight.ps1', 'preflight.' + marker, marker);
  }
  requireContains(errors, snapshot, 'scripts/development/host-preflight.ps1', 'no.service.control', '不启动或停止');
  requireContains(errors, snapshot, 'scripts/development/host-preflight.ps1', 'port.frontend', 'Assert-Cc4cFreePort $FrontendPort');
  const startHost = requireText(errors, snapshot, 'scripts/development/start-host-stack.ps1') || '';
  for (const step of ['start-backend.ps1', 'start-frontend.ps1', 'start-observability.ps1']) {
    requireContains(errors, snapshot, 'scripts/development/start-host-stack.ps1', 'order.' + step, step);
  }
  requireContains(errors, snapshot, 'scripts/development/start-host-stack.ps1', 'rollback', 'stop-frontend.ps1');
  requireContains(errors, snapshot, 'scripts/development/start-host-stack.ps1', 'rollback.backend', 'stop-backend.ps1');
  for (const [file, markers] of [
    ['scripts/development/start-backend.ps1', ['Start-Process', 'cc4c-4.0.0-SNAPSHOT.jar', 'Set-Cc4cProcessEnvironment']],
    ['scripts/development/stop-backend.ps1', ['Stop-Process -Id', 'jarFileName']],
    ['scripts/development/start-frontend.ps1', ['--host', '127.0.0.1', '5173', 'temp\\cc4c-host-frontend']],
    ['scripts/development/stop-frontend.ps1', ['Stop-Process -Id', 'state.marker']],
    ['scripts/development/health-host-stack.ps1', ['Get-NetTCPConnection', 'Assert-Cc4cRecordedProcess']]
  ]) {
    for (const marker of markers) {
      requireContains(errors, snapshot, file, 'host.' + marker, marker);
    }
  }
  if (startHost.includes('docker compose') || startHost.includes('Stop-Process -Name')) {
    addError(errors, 'scripts/development/start-host-stack.ps1', 'host.boundary', 'no external service control', 'broad service control');
  }
  for (const marker of ['prometheusProcess', 'grafanaProcess', 'commandLineSummary', 'Stop-Process -Id']) {
    requireContains(errors, snapshot, 'scripts/development/start-observability.ps1', 'observability.' + marker, marker);
  }

  const bootstrap = requireText(errors, snapshot, 'scripts/deployment/bootstrap-admin.ps1') || '';
  requireContains(errors, snapshot, 'scripts/deployment/bootstrap-admin.ps1', 'project', 'docker compose -p cc4c');
  const reset = requireText(errors, snapshot, 'scripts/deployment/reset-local.ps1') || '';
  requireContains(errors, snapshot, 'scripts/deployment/reset-local.ps1', 'project', '$expectedProject = \'cc4c\'');
  requireContains(errors, snapshot, 'scripts/deployment/reset-local.ps1', 'confirmation', 'DELETE-$expectedProject');
  const performance = requireText(errors, snapshot, 'scripts/performance/run-container-performance.ps1') || '';
  requireContains(errors, snapshot, 'scripts/performance/run-container-performance.ps1', 'project', "$project = 'cc4c-perf'");
  requireNotContains(errors, snapshot, 'scripts/performance/run-container-performance.ps1', 'legacy.active.project', 'docker compose -p cc4c-v3');
  const migration = requireText(errors, snapshot, 'scripts/deployment/migrate-compose-identity.ps1') || '';
  for (const marker of [
    'cc4c-v3_mysql_data',
    'cc4c_mysql_data',
    'DELETE-CC4C-V3-SOURCE-VOLUMES',
    'RestoreLegacy',
    '绝不执行 Compose 启停',
    "volume', 'rm'"
  ]) {
    requireContains(errors, snapshot, 'scripts/deployment/migrate-compose-identity.ps1', 'migration.' + marker, marker);
  }

  const versionsPath = 'versions.yml';
  for (const [field, expected] of [
    ['compatibility.composeProject', 'cc4c'],
    ['compatibility.legacyComposeProject', 'cc4c-v3'],
    ['compatibility.ciComposeProject', 'cc4c-ci'],
    ['compatibility.performanceComposeProject', 'cc4c-perf']
  ]) {
    requireVersion(errors, manifest, field, expected);
  }
  for (const relativePath of MODE_PATHS) {
    if (PROTECTED_PATH_PATTERN.test(relativePath)) {
      addError(errors, relativePath, 'protected.allowlist', 'not loaded', 'loaded');
    }
  }
  return errors;
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  try {
    const snapshot = await loadDeploymentModeSnapshot(repositoryRoot);
    const errors = validateDeploymentModes(snapshot);
    if (errors.length > 0) {
      for (const error of errors) process.stderr.write(JSON.stringify(error) + '\n');
      process.exitCode = 1;
      return;
    }
    process.stdout.write('Deployment mode configuration is consistent.\n');
  } catch (error) {
    process.stderr.write(String(error?.message || error) + '\n');
    process.exitCode = 1;
  }
}

const currentFile = path.resolve(fileURLToPath(import.meta.url));
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) {
  await main();
}
