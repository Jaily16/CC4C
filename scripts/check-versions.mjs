import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const CONTROLLED_FILES = Object.freeze([
  'backend/pom.xml',
  'backend/Dockerfile',
  'scripts/development/run-backend.ps1',
  'scripts/development/host-preflight.ps1',
  'scripts/development/start-backend.ps1',
  'scripts/development/start-frontend.ps1',
  'scripts/development/start-host-stack.ps1',
  'scripts/performance/start-performance-server.ps1',
  'scripts/performance/run-container-performance.ps1',
  'scripts/deployment/migrate-compose-identity.ps1',
  'backend/src/main/resources/application-example.yml',
  'backend/src/test/resources/application-test.yml',
  'backend/src/test/java/com/cc4c/support/Cc4cTestInfrastructure.java',
  'frontend/Dockerfile',
  'frontend/package.json',
  'frontend/package-lock.json',
  'frontend/README.md',
  'frontend/eslint.config.js',
  'frontend/prettier.config.mjs',
  '.editorconfig',
  'compose.yml',
  '.github/workflows/quality.yml',
  '.github/workflows/release.yml',
  'README.md'
]);

const REQUIRED_MANIFEST_PATHS = [
  'project.id',
  'project.displayName',
  'project.version',
  'project.springApplicationName',
  'toolchain.java',
  'toolchain.maven',
  'toolchain.node',
  'toolchain.npm',
  'backend.springBoot',
  'backend.mybatisPlus',
  'backend.springModulith',
  'backend.springdoc',
  'backend.gatling',
  'backend.gatlingMavenPlugin',
  'backend.mavenEnforcer',
  'backend.rabbitAmqpClientOverride',
  'backend.nettyRuntimeOverride',
  'backend.nettyGatlingProfile',
  'frontend.vue',
  'frontend.vite',
  'frontend.axios',
  'frontend.elementPlus',
  'data.mysql',
  'data.redis',
  'data.rabbitmq',
  'data.mailpit',
  'observability.prometheus',
  'observability.grafana',
  'test.testcontainers',
  'test.temurinJre',
  'test.nginx',
  'quality.spotlessMavenPlugin',
  'quality.palantirJavaFormat',
  'quality.eslint',
  'quality.eslintJs',
  'quality.eslintPluginVue',
  'quality.globals',
  'quality.prettier',
  'quality.eslintConfigPrettier',
  'compatibility.composeProject',
  'compatibility.legacyComposeProject',
  'compatibility.ciComposeProject',
  'compatibility.performanceComposeProject',
  'compatibility.volumePrefix',
  'compatibility.legacyVolumePrefix',
  'compatibility.composeMigration',
  'compatibility.flywayMigrations',
  'compatibility.messageSchema',
  'localImages.backend',
  'localImages.frontend',
  'localImages.performanceTools',
  'images.mavenBuild',
  'images.javaRuntime',
  'images.nodeBuild',
  'images.nginx',
  'images.mysql',
  'images.redis',
  'images.rabbitmq',
  'images.mailpit',
  'images.prometheus',
  'images.grafana'
];

const ACTION_NAMES = Object.freeze([
  'checkout',
  'setupJava',
  'setupNode',
  'dependencyReview',
  'setupQemu',
  'setupBuildx',
  'login',
  'buildPush',
  'attest',
  'trivyAction'
]);

const COMPOSE_VOLUME_NAMES = Object.freeze([
  'mysql_data',
  'redis_security_data',
  'redis_cache_data',
  'rabbitmq_data',
  'prometheus_data',
  'grafana_data',
  'blog_uploads',
  'avatar_uploads'
]);

const LEGACY_IDENTITY_MARKERS = Object.freeze([
  ['my', 'vue'].join(''),
  ['com', '.CC4C'].join(''),
  ['0.0.', '1-SNAPSHOT'].join(''),
  ['CC4C-', '0.0.1'].join('')
]);

const MISSING = Symbol('missing');

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function skipWhitespace(text, state) {
  while (state.index < text.length && /[\s]/.test(text[state.index])) {
    state.index += 1;
  }
}

function parseJsonStringToken(text, state) {
  const start = state.index;
  state.index += 1;
  while (state.index < text.length) {
    const character = text[state.index];
    if (character === '\\') {
      state.index += 2;
      continue;
    }
    if (character === '"') {
      state.index += 1;
      return JSON.parse(text.slice(start, state.index));
    }
    state.index += 1;
  }
  throw new Error('Unterminated JSON string.');
}

function assertNoDuplicateJsonKeys(text) {
  const state = { index: 0 };

  function expect(character) {
    skipWhitespace(text, state);
    if (text[state.index] !== character) {
      throw new Error('Invalid JSON near offset ' + state.index + '.');
    }
    state.index += 1;
  }

  function parseValue() {
    skipWhitespace(text, state);
    const character = text[state.index];
    if (character === '{') {
      parseObject();
      return;
    }
    if (character === '[') {
      parseArray();
      return;
    }
    if (character === '"') {
      parseJsonStringToken(text, state);
      return;
    }
    if (text.startsWith('true', state.index)) {
      state.index += 4;
      return;
    }
    if (text.startsWith('false', state.index)) {
      state.index += 5;
      return;
    }
    if (text.startsWith('null', state.index)) {
      state.index += 4;
      return;
    }
    const number = text.slice(state.index).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
    if (number) {
      state.index += number[0].length;
      return;
    }
    throw new Error('Invalid JSON near offset ' + state.index + '.');
  }

  function parseObject() {
    expect('{');
    skipWhitespace(text, state);
    const keys = new Set();
    if (text[state.index] === '}') {
      state.index += 1;
      return;
    }
    while (state.index < text.length) {
      skipWhitespace(text, state);
      if (text[state.index] !== '"') {
        throw new Error('JSON object key expected near offset ' + state.index + '.');
      }
      const key = parseJsonStringToken(text, state);
      if (keys.has(key)) {
        throw new Error('Duplicate JSON key: ' + key);
      }
      keys.add(key);
      expect(':');
      parseValue();
      skipWhitespace(text, state);
      if (text[state.index] === '}') {
        state.index += 1;
        return;
      }
      expect(',');
    }
    throw new Error('Unterminated JSON object.');
  }

  function parseArray() {
    expect('[');
    skipWhitespace(text, state);
    if (text[state.index] === ']') {
      state.index += 1;
      return;
    }
    while (state.index < text.length) {
      parseValue();
      skipWhitespace(text, state);
      if (text[state.index] === ']') {
        state.index += 1;
        return;
      }
      expect(',');
    }
    throw new Error('Unterminated JSON array.');
  }

  parseValue();
  skipWhitespace(text, state);
  if (state.index !== text.length) {
    throw new Error('Unexpected JSON content near offset ' + state.index + '.');
  }
}

function getPathValue(value, dottedPath) {
  let current = value;
  for (const segment of dottedPath.split('.')) {
    if (!isObject(current) || !Object.prototype.hasOwnProperty.call(current, segment)) {
      return MISSING;
    }
    current = current[segment];
  }
  return current;
}

export function parseManifest(text) {
  if (typeof text !== 'string') {
    throw new TypeError('versions.yml must be a JSON-compatible text document.');
  }
  assertNoDuplicateJsonKeys(text);
  let manifest;
  try {
    manifest = JSON.parse(text);
  } catch (error) {
    throw new Error('versions.yml is not valid JSON: ' + error.message);
  }
  if (!isObject(manifest)) {
    throw new Error('versions.yml must contain a top-level object.');
  }
  if (manifest.schemaVersion !== 1) {
    throw new Error('Unsupported versions.yml schemaVersion: ' + String(manifest.schemaVersion));
  }
  for (const requiredPath of REQUIRED_MANIFEST_PATHS) {
    const value = getPathValue(manifest, requiredPath);
    if (value === MISSING || value === null || value === '') {
      throw new Error('Missing required versions.yml field: ' + requiredPath);
    }
  }
  for (const actionName of ACTION_NAMES) {
    for (const field of ['uses', 'version']) {
      const value = getPathValue(manifest, 'actions.' + actionName + '.' + field);
      if (value === MISSING || value === null || value === '') {
        throw new Error('Missing required versions.yml field: actions.' + actionName + '.' + field);
      }
    }
  }
  const trivyCliVersion = getPathValue(manifest, 'actions.trivyAction.cliVersion');
  if (trivyCliVersion === MISSING || trivyCliVersion === null || trivyCliVersion === '') {
    throw new Error('Missing required versions.yml field: actions.trivyAction.cliVersion');
  }
  return manifest;
}

function sourceText(sources, relativePath) {
  let value;
  if (sources instanceof Map) {
    value = sources.get(relativePath);
  } else if (sources && typeof sources === 'object') {
    value = sources[relativePath];
  }
  return typeof value === 'string' ? value.replace(/\r\n/g, '\n') : value;
}

function shortActual(value) {
  if (value === undefined || value === MISSING) {
    return null;
  }
  if (typeof value === 'string' && value.length > 180) {
    return value.slice(0, 177) + '...';
  }
  return value;
}

function addError(errors, relativePath, field, expected, actual) {
  errors.push({
    path: relativePath,
    field,
    expected: shortActual(expected),
    actual: shortActual(actual)
  });
}

function expectContains(errors, sources, relativePath, field, expected) {
  const text = sourceText(sources, relativePath);
  if (typeof text !== 'string' || !text.includes(expected)) {
    addError(errors, relativePath, field, expected, 'not found');
  }
}

function expectExactValue(errors, sources, relativePath, field, expected, actual) {
  if (actual !== expected) {
    addError(errors, relativePath, field, expected, actual);
  }
}

function extractFirst(text, expression) {
  const match = text.match(expression);
  return match ? match[1] : undefined;
}

function expectXmlTag(errors, sources, relativePath, field, tag, expected, textOverride) {
  const text = textOverride === undefined ? sourceText(sources, relativePath) : textOverride;
  const actual = typeof text === 'string'
    ? extractFirst(text, new RegExp('<' + tag + '>([^<]+)</' + tag + '>'))
    : undefined;
  expectExactValue(errors, sources, relativePath, field, expected, actual);
}

function parseSourceJson(errors, sources, relativePath) {
  const text = sourceText(sources, relativePath);
  if (typeof text !== 'string') {
    addError(errors, relativePath, 'file', 'loaded', 'missing');
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    addError(errors, relativePath, 'json', 'valid JSON', 'invalid JSON');
    return undefined;
  }
}

function manifestString(manifest, dottedPath) {
  const value = getPathValue(manifest, dottedPath);
  return value === MISSING ? undefined : String(value);
}

function serviceBlock(compose, serviceName) {
  const startExpression = new RegExp('(?:^|\n)  ' + serviceName + ':\r?\n');
  const match = compose.match(startExpression);
  if (!match || match.index === undefined) {
    return undefined;
  }
  const contentStart = match.index + match[0].length;
  const remainder = compose.slice(contentStart);
  const nextService = remainder.search(/\r?\n  [A-Za-z0-9][A-Za-z0-9_-]*:\r?\n/);
  return nextService < 0 ? remainder : remainder.slice(0, nextService);
}

function imageRepository(imageReference) {
  const tagAndDigest = imageReference.split('@', 1)[0];
  return tagAndDigest.slice(0, tagAndDigest.lastIndexOf(':'));
}

function expectComposeImageLines(errors, sources, relativePath, field, expected) {
  const compose = sourceText(sources, relativePath);
  const repository = imageRepository(expected);
  const imageLines = typeof compose === 'string'
    ? compose.split(/\r?\n/).filter((line) => {
      const trimmed = line.trim();
      return trimmed.startsWith('image: ' + repository + ':');
    })
    : [];
  if (imageLines.length === 0) {
    addError(errors, relativePath, field, 'image: ' + expected, 'not found');
    return;
  }
  for (const line of imageLines) {
    if (line.trim() !== 'image: ' + expected) {
      addError(errors, relativePath, field, 'image: ' + expected, line.trim());
    }
  }
}

export function validateSnapshot(manifest, sources) {
  const errors = [];
  if (!isObject(manifest)) {
    addError(errors, 'versions.yml', 'document', 'object', typeof manifest);
    return errors;
  }
  if (manifest.schemaVersion !== 1) {
    addError(errors, 'versions.yml', 'schemaVersion', 1, manifest.schemaVersion);
    return errors;
  }

  const pomPath = 'backend/pom.xml';
  const pom = sourceText(sources, pomPath);
  const pomAfterParent = typeof pom === 'string'
    ? (pom.match(/<\/parent>([\s\S]*?)<properties>/) || [])[1]
    : undefined;
  const pomParent = typeof pom === 'string'
    ? (pom.match(/<parent>([\s\S]*?)<\/parent>/) || [])[1]
    : undefined;
  expectXmlTag(errors, sources, pomPath, 'project.groupId', 'groupId', manifestString(manifest, 'project.id') ? 'com.' + manifestString(manifest, 'project.id') : undefined, pomAfterParent);
  expectXmlTag(errors, sources, pomPath, 'project.artifactId', 'artifactId', manifestString(manifest, 'project.id'), pomAfterParent);
  expectXmlTag(errors, sources, pomPath, 'project.version', 'version', manifestString(manifest, 'project.version'), pomAfterParent);
  expectXmlTag(errors, sources, pomPath, 'project.name', 'name', manifestString(manifest, 'project.displayName'), pomAfterParent);
  expectXmlTag(errors, sources, pomPath, 'project.description', 'description', manifestString(manifest, 'project.displayName'), pomAfterParent);
  expectXmlTag(errors, sources, pomPath, 'parent.version', 'version', manifestString(manifest, 'backend.springBoot'), pomParent);

  const pomProperties = [
    ['java.version', 'toolchain.java'],
    ['spring-modulith.version', 'backend.springModulith'],
    ['springdoc.version', 'backend.springdoc'],
    ['mybatis-plus.version', 'backend.mybatisPlus'],
    ['gatling.version', 'backend.gatling'],
    ['gatling-maven-plugin.version', 'backend.gatlingMavenPlugin'],
    ['rabbit-amqp-client.version', 'backend.rabbitAmqpClientOverride'],
    ['netty.version', 'backend.nettyRuntimeOverride'],
    ['maven-enforcer-plugin.version', 'backend.mavenEnforcer'],
    ['spotless-maven-plugin.version', 'quality.spotlessMavenPlugin'],
    ['palantir-java-format.version', 'quality.palantirJavaFormat']
  ];
  for (const [property, manifestPath] of pomProperties) {
    const actual = typeof pom === 'string'
      ? extractFirst(pom, new RegExp('<' + property.replace(/[.]/g, '\\.') + '>([^<]+)</' + property.replace(/[.]/g, '\\.') + '>'))
      : undefined;
    expectExactValue(errors, sources, pomPath, 'property.' + property, manifestString(manifest, manifestPath), actual);
  }
  expectContains(errors, sources, pomPath, 'profile.nettyGatlingProfile', '<version>' + manifestString(manifest, 'backend.nettyGatlingProfile') + '</version>');
  expectContains(errors, sources, pomPath, 'maven-enforcer-plugin', '<artifactId>maven-enforcer-plugin</artifactId>');
  expectContains(errors, sources, pomPath, 'enforcer.validate.phase', '<phase>validate</phase>');
  expectContains(errors, sources, pomPath, 'enforcer.requireJavaVersion', '<requireJavaVersion>');
  expectContains(errors, sources, pomPath, 'enforcer.requireJavaVersion.range', '<version>[' + manifestString(manifest, 'toolchain.java') + ',22)</version>');
  expectContains(errors, sources, pomPath, 'enforcer.requireMavenVersion', '<requireMavenVersion>');
  expectContains(errors, sources, pomPath, 'enforcer.requireMavenVersion.range', '<version>[' + manifestString(manifest, 'toolchain.maven') + ',4.0.0)</version>');
  expectContains(errors, sources, pomPath, 'enforcer.banDuplicatePomDependencyVersions', '<banDuplicatePomDependencyVersions/>');
  expectContains(errors, sources, pomPath, 'enforcer.dependencyConvergence', '<dependencyConvergence/>');
  expectContains(errors, sources, pomPath, 'spotless-maven-plugin', '<artifactId>spotless-maven-plugin</artifactId>');
  expectContains(errors, sources, pomPath, 'spotless.validate.phase', '<phase>validate</phase>');
  expectContains(errors, sources, pomPath, 'spotless.palantir', '<version>${palantir-java-format.version}</version>');

  const packagePath = 'frontend/package.json';
  const packageJson = parseSourceJson(errors, sources, packagePath);
  if (packageJson) {
    expectExactValue(errors, sources, packagePath, 'name', manifestString(manifest, 'project.id'), packageJson.name);
    expectExactValue(errors, sources, packagePath, 'version', manifestString(manifest, 'project.version'), packageJson.version);
    expectExactValue(errors, sources, packagePath, 'packageManager', 'npm@' + manifestString(manifest, 'toolchain.npm'), packageJson.packageManager);
    expectExactValue(errors, sources, packagePath, 'engines.node', manifestString(manifest, 'toolchain.node'), packageJson.engines && packageJson.engines.node);
    expectExactValue(errors, sources, packagePath, 'engines.npm', manifestString(manifest, 'toolchain.npm'), packageJson.engines && packageJson.engines.npm);
    expectExactValue(errors, sources, packagePath, 'private', true, packageJson.private);
    expectExactValue(errors, sources, packagePath, 'type', 'module', packageJson.type);
    for (const [dependency, manifestPath] of [
      ['vue', 'frontend.vue'],
      ['vite', 'frontend.vite'],
      ['axios', 'frontend.axios'],
      ['element-plus', 'frontend.elementPlus']
    ]) {
      expectExactValue(
        errors,
        sources,
        packagePath,
        'dependencies.' + dependency,
        manifestString(manifest, manifestPath),
        (packageJson.dependencies && packageJson.dependencies[dependency])
          || (packageJson.devDependencies && packageJson.devDependencies[dependency])
      );
    }
    for (const [dependency, manifestPath] of [
      ['@eslint/js', 'quality.eslintJs'],
      ['eslint', 'quality.eslint'],
      ['eslint-config-prettier', 'quality.eslintConfigPrettier'],
      ['eslint-plugin-vue', 'quality.eslintPluginVue'],
      ['globals', 'quality.globals'],
      ['prettier', 'quality.prettier']
    ]) {
      expectExactValue(
        errors,
        sources,
        packagePath,
        'devDependencies.' + dependency,
        manifestString(manifest, manifestPath),
        packageJson.devDependencies && packageJson.devDependencies[dependency]
      );
    }
  }

  const lockPath = 'frontend/package-lock.json';
  const lockJson = parseSourceJson(errors, sources, lockPath);
  if (lockJson) {
    expectExactValue(errors, sources, lockPath, 'lockfileVersion', 2, lockJson.lockfileVersion);
    expectExactValue(errors, sources, lockPath, 'name', manifestString(manifest, 'project.id'), lockJson.name);
    expectExactValue(errors, sources, lockPath, 'version', manifestString(manifest, 'project.version'), lockJson.version);
    const lockRoot = lockJson.packages && lockJson.packages[''];
    expectExactValue(errors, sources, lockPath, 'packages[""].name', manifestString(manifest, 'project.id'), lockRoot && lockRoot.name);
    expectExactValue(errors, sources, lockPath, 'packages[""].version', manifestString(manifest, 'project.version'), lockRoot && lockRoot.version);
  }

  const backendDockerPath = 'backend/Dockerfile';
  expectContains(errors, sources, backendDockerPath, 'from.mavenBuild', 'FROM ' + manifestString(manifest, 'images.mavenBuild') + ' AS build');
  expectContains(errors, sources, backendDockerPath, 'from.javaRuntime', 'FROM ' + manifestString(manifest, 'images.javaRuntime'));
  expectContains(errors, sources, backendDockerPath, 'jar.application', '/workspace/target/' + manifestString(manifest, 'project.id') + '-' + manifestString(manifest, 'project.version') + '.jar');
  expectContains(errors, sources, backendDockerPath, 'jar.adminBootstrap', '/workspace/target/' + manifestString(manifest, 'project.id') + '-' + manifestString(manifest, 'project.version') + '-admin-bootstrap.jar');

  const frontendDockerPath = 'frontend/Dockerfile';
  expectContains(errors, sources, frontendDockerPath, 'from.nodeBuild', 'FROM ' + manifestString(manifest, 'images.nodeBuild') + ' AS build');
  expectContains(errors, sources, frontendDockerPath, 'from.nginx', 'FROM ' + manifestString(manifest, 'images.nginx'));

  const composePath = 'compose.yml';
  const compose = sourceText(sources, composePath);
  expectContains(errors, sources, composePath, 'name', 'name: ' + manifestString(manifest, 'compatibility.composeProject'));
  const composeImages = [
    ['mysql', 'images.mysql'],
    ['redis', 'images.redis'],
    ['rabbitmq', 'images.rabbitmq'],
    ['mailpit', 'images.mailpit'],
    ['prometheus', 'images.prometheus'],
    ['grafana', 'images.grafana']
  ];
  for (const [name, manifestPath] of composeImages) {
    expectComposeImageLines(errors, sources, composePath, 'image.' + name, manifestString(manifest, manifestPath));
  }
  const localImageEntries = [
    ['backend', 'localImages.backend'],
    ['frontend', 'localImages.frontend'],
    ['performanceTools', 'localImages.performanceTools']
  ];
  for (const [name, manifestPath] of localImageEntries) {
    expectComposeImageLines(errors, sources, composePath, 'localImage.' + name, manifestString(manifest, manifestPath));
  }
  if (typeof compose === 'string') {
    const activeImageLines = compose.split(/\r?\n/).filter((line) => /^\s*image:\s*/.test(line));
    for (const line of activeImageLines) {
      if (line.includes(':aspect7')) {
        addError(errors, composePath, 'active.imageTag', 'no :aspect7', ':aspect7');
      }
    }
  }
  for (const volumeName of COMPOSE_VOLUME_NAMES) {
    expectContains(errors, sources, composePath, 'volume.' + volumeName, '\n  ' + volumeName + ':');
  }
  for (const serviceName of ['backend', 'backend-perf', 'admin-bootstrap']) {
    const block = typeof compose === 'string' ? serviceBlock(compose, serviceName) : undefined;
    if (typeof block !== 'string' || !block.includes('SPRING_APPLICATION_NAME: ' + manifestString(manifest, 'project.springApplicationName'))) {
      addError(errors, composePath, 'service.' + serviceName + '.SPRING_APPLICATION_NAME', manifestString(manifest, 'project.springApplicationName'), 'not found');
    }
  }

  const testInfrastructurePath = 'backend/src/test/java/com/cc4c/support/Cc4cTestInfrastructure.java';
  expectContains(errors, sources, testInfrastructurePath, 'testcontainers.mysql', 'mysql:' + manifestString(manifest, 'data.mysql'));
  expectContains(errors, sources, testInfrastructurePath, 'testcontainers.redis', 'redis:' + manifestString(manifest, 'data.redis'));
  expectContains(errors, sources, testInfrastructurePath, 'testcontainers.rabbitmq', 'rabbitmq:' + manifestString(manifest, 'data.rabbitmq'));

  const qualityPath = '.github/workflows/quality.yml';
  const releasePath = '.github/workflows/release.yml';
  const actionSources = [
    sourceText(sources, qualityPath) || '',
    sourceText(sources, releasePath) || ''
  ];
  for (const actionName of ACTION_NAMES) {
    const action = manifest.actions[actionName];
    const expectedReference = action.uses + ' # ' + action.version;
    const actionId = action.uses.slice(0, action.uses.indexOf('@'));
    const activeLines = actionSources
      .flatMap((text) => text.split(/\r?\n/))
      .filter((line) => line.includes(actionId + '@'));
    if (activeLines.length === 0 || activeLines.some((line) => !line.includes(expectedReference))) {
      addError(errors, actionName === 'trivyAction' ? qualityPath : releasePath, 'action.' + actionName, expectedReference, 'not found');
    }
  }
  expectContains(errors, sources, qualityPath, 'toolchain.node', 'node-version: ' + manifestString(manifest, 'toolchain.node'));
  expectContains(errors, sources, qualityPath, 'toolchain.java', "java-version: '" + manifestString(manifest, 'toolchain.java') + "'");
  expectContains(errors, sources, qualityPath, 'trivy.cliVersion', 'version: ' + manifestString(manifest, 'actions.trivyAction.cliVersion'));
  expectContains(errors, sources, qualityPath, 'compose-smoke.needs', 'compose-smoke:\n    needs: [backend, frontend, delivery-config, source-scan, versions, structure, code-quality, deployment-modes, documentation]');
  for (const imagePath of ['localImages.backend', 'localImages.frontend']) {
    expectContains(errors, sources, qualityPath, 'compose-smoke.' + imagePath, manifestString(manifest, imagePath));
  }

  const applicationExamplePath = 'backend/src/main/resources/application-example.yml';
  expectContains(errors, sources, applicationExamplePath, 'spring.application.name', 'name: ' + manifestString(manifest, 'project.springApplicationName'));
  const testConfigPath = 'backend/src/test/resources/application-test.yml';
  expectContains(errors, sources, testConfigPath, 'spring.application.name', 'spring:\n  application:\n    name: ' + manifestString(manifest, 'project.springApplicationName'));
  const runLocalPath = 'scripts/development/run-backend.ps1';
  expectContains(errors, sources, runLocalPath, 'jar.application', 'backend\\target\\cc4c-' + manifestString(manifest, 'project.version') + '.jar');
  expectContains(errors, sources, runLocalPath, 'SPRING_APPLICATION_NAME', "SetEnvironmentVariable('SPRING_APPLICATION_NAME', '" + manifestString(manifest, 'project.springApplicationName') + "', 'Process')");
  const performancePath = 'scripts/performance/start-performance-server.ps1';
  expectContains(errors, sources, performancePath, 'jar.application', 'target\\cc4c-' + manifestString(manifest, 'project.version') + '.jar');
  expectContains(errors, sources, performancePath, 'SPRING_APPLICATION_NAME', "SPRING_APPLICATION_NAME = '" + manifestString(manifest, 'project.springApplicationName') + "'");
  expectContains(errors, sources, 'scripts/performance/run-container-performance.ps1', 'compose.project', "$project = '" + manifestString(manifest, 'compatibility.performanceComposeProject') + "'");
  expectContains(errors, sources, 'scripts/deployment/migrate-compose-identity.ps1', 'legacy.compose.project', manifestString(manifest, 'compatibility.legacyComposeProject'));

  expectContains(errors, sources, 'README.md', 'versions.yml.link', '[版本基线](versions.yml)');
  for (const relativePath of CONTROLLED_FILES) {
    const text = sourceText(sources, relativePath);
    for (const forbidden of LEGACY_IDENTITY_MARKERS) {
      if (typeof text === 'string' && text.includes(forbidden)) {
        addError(errors, relativePath, 'forbidden.legacyIdentity', 'absent: ' + forbidden, forbidden);
      }
    }
  }

  return errors;
}

export async function loadSnapshot(repositoryRoot) {
  const root = path.resolve(repositoryRoot);
  const entries = await Promise.all(CONTROLLED_FILES.map(async (relativePath) => {
    const absolutePath = path.join(root, ...relativePath.split('/'));
    return [relativePath, await readFile(absolutePath, 'utf8')];
  }));
  return Object.fromEntries(entries);
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  try {
    const manifestText = await readFile(path.join(repositoryRoot, 'versions.yml'), 'utf8');
    const manifest = parseManifest(manifestText);
    const sources = await loadSnapshot(repositoryRoot);
    const errors = validateSnapshot(manifest, sources);
    if (errors.length > 0) {
      for (const error of errors) {
        process.stderr.write(JSON.stringify(error) + '\n');
      }
      process.exitCode = 1;
      return;
    }
    process.stdout.write('Version snapshot is consistent.\n');
  } catch (error) {
    process.stderr.write(String(error && error.message ? error.message : error) + '\n');
    process.exitCode = 1;
  }
}

const currentFile = path.resolve(fileURLToPath(import.meta.url));
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) {
  await main();
}
