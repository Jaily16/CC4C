import { execFile as execFileCallback } from 'node:child_process';
import { lstat, readFile, realpath } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const execFile = promisify(execFileCallback);
const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function normalize(relative) {
  return relative.replaceAll('\\', '/').replace(/^\.\//, '');
}

async function inventory() {
  const [result, deleted] = await Promise.all([
    execFile('git', ['-C', repositoryRoot, 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
      encoding: 'utf8',
      maxBuffer: 16 * 1024 * 1024,
    }),
    execFile('git', ['-C', repositoryRoot, 'ls-files', '--deleted', '-z'], {
      encoding: 'utf8',
      maxBuffer: 16 * 1024 * 1024,
    }),
  ]);
  const removed = new Set(deleted.stdout.split('\0').filter(Boolean).map(normalize));
  return new Set(result.stdout.split('\0').filter(Boolean).map(normalize).filter((relative) => !removed.has(relative)));
}

async function safeRead(relative, files) {
  const normalized = normalize(relative);
  if (!files.has(normalized) || /(^|\/)\.env(?:\.[^/]+)?\.local$/i.test(normalized)) {
    throw new Error(`Contract source is absent or protected: ${normalized}`);
  }
  const absolute = path.resolve(repositoryRoot, ...normalized.split('/'));
  let cursor = absolute;
  while (true) {
    const metadata = await lstat(cursor);
    if (metadata.isSymbolicLink() || (cursor === absolute ? !metadata.isFile() : !metadata.isDirectory())) {
      throw new Error(`Contract source is redirected: ${normalized}`);
    }
    if ((await realpath(cursor)).toLowerCase() !== cursor.toLowerCase()) {
      throw new Error(`Contract source has a redirected parent: ${normalized}`);
    }
    if (cursor === repositoryRoot) break;
    cursor = path.dirname(cursor);
  }
  return readFile(absolute, 'utf8');
}

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

async function main() {
  const files = await inventory();
  const catalog = JSON.parse(await safeRead('backend/src/main/resources/observability/catalog.json', files));
  const rules = await safeRead('infrastructure/prometheus/rules/cc4c-alerts.yml', files);
  const application = await safeRead('backend/src/main/resources/application.yml', files);
  const runtimeExample = await safeRead('backend/.env.example', files);
  const environmentHelper = await safeRead('infrastructure/host/app-environment.mjs', files);
  const launcher = await safeRead('infrastructure/host/cc4c.mjs', files);
  const packageDocument = JSON.parse(await safeRead('observability/package.json', files));

  const panels = catalog.dashboards.flatMap((dashboard) => dashboard.panels);
  const queries = panels.flatMap((panel) => panel.queries);
  invariant(catalog.overview.length === 8, 'Observability overview must contain exactly 8 metrics.');
  invariant(catalog.dashboards.length === 3, 'Observability must contain exactly 3 dashboards.');
  invariant(panels.length === 20, 'Observability must contain exactly 20 panels.');
  invariant(queries.length === 39, 'Observability must contain exactly 39 dashboard queries.');
  invariant(panels.every((panel) => panel.queries.length > 0 && panel.queries.length <= 4), 'A panel query limit is invalid.');
  invariant(
    catalog.dashboards.every(
      (dashboard) => dashboard.panels.flatMap((panel) => panel.queries).length <= 16,
    ),
    'A dashboard query limit is invalid.',
  );

  const alertNames = rules
    .split('alert: ')
    .slice(1)
    .map((entry) => entry.trim().split('\n')[0].trim());
  const catalogAlertNames = catalog.alerts.map((alert) => alert.id);
  invariant(alertNames.length === 20 && catalogAlertNames.length === 20, 'Alert count must remain exactly 20.');
  invariant(
    JSON.stringify([...alertNames].sort()) === JSON.stringify([...catalogAlertNames].sort()),
    'Catalog alerts must exactly match Prometheus rules.',
  );

  for (const name of [
    'CC4C_OBSERVABILITY_USERNAME',
    'CC4C_OBSERVABILITY_SESSION_NAMESPACE',
    'CC4C_OBSERVABILITY_ALLOWED_ORIGIN',
    'CC4C_PROMETHEUS_URL',
  ]) {
    invariant(runtimeExample.includes(name) && application.includes(name), `Missing runtime contract: ${name}`);
  }
  for (const name of ['CC4C_MANAGEMENT_PASSWORD_HASH', 'CC4C_OBSERVABILITY_PASSWORD_HASH']) {
    invariant(application.includes(name) && environmentHelper.includes(name) && launcher.includes(name), `Missing generated/legacy hash contract: ${name}`);
    invariant(!runtimeExample.includes(`${name}=`), 'New environments must not require manual BCrypt hashes.');
  }
  invariant(runtimeExample.includes('CC4C_OBSERVABILITY_PASSWORD=') && !application.includes('${CC4C_OBSERVABILITY_PASSWORD}'), 'The setup password must remain outside Spring application configuration.');
  invariant(!runtimeExample.includes('CC4C_MANAGEMENT_PASSWORD='), 'Plain management password configuration must be removed.');
  invariant(application.includes('idle-timeout: 30m') && application.includes('absolute-timeout: 8h'), 'Session limits changed.');

  const sourceFiles = [...files].filter((relative) => relative.startsWith('observability/src/'));
  const browserSource = (await Promise.all(sourceFiles.map((relative) => safeRead(relative, files)))).join('\n');
  for (const forbidden of ['v-html', 'localStorage', 'sessionStorage', 'CC4C_PROMETHEUS', '127.0.0.1:9090']) {
    invariant(!browserSource.includes(forbidden), `Forbidden browser capability found: ${forbidden}`);
  }
  for (const route of ['/login', '/overview', '/api-jvm', '/data-cache-security', '/messaging', '/operations']) {
    invariant(browserSource.includes(route), `Missing observability route: ${route}`);
  }
  invariant(!browserSource.includes('params: { query'), 'The browser must not submit PromQL.');

  const expectedVersions = {
    '@vue/devtools-api': '8.2.1',
    axios: '1.19.0',
    echarts: '6.1.0',
    'element-plus': '2.14.5',
    pinia: '4.0.3',
    vue: '3.5.42',
    'vue-router': '4.1.6',
  };
  for (const [name, version] of Object.entries(expectedVersions)) {
    invariant(packageDocument.dependencies[name] === version, `Unexpected observability dependency: ${name}`);
  }

  const grafanaPaths = [...files].filter((relative) => relative.startsWith('infrastructure/prometheus/reference/grafana/'));
  invariant(grafanaPaths.length === 0, 'Grafana runtime reference assets must be absent.');
  process.stdout.write('Observability contract is consistent: 8 overview metrics, 3 dashboards, 20 panels, 39 queries, 20 alerts.\n');
}

try {
  await main();
} catch (error) {
  process.stderr.write(`${error?.message || 'Observability contract check failed'}\n`);
  process.exitCode = 1;
}
