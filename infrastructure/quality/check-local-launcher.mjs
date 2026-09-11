import assert from 'node:assert/strict';
import { mkdir, mkdtemp, writeFile, readFile, link, symlink } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';
import { childEnvironment, configurationMode, generatedNames, parseEnvironment, readEnvironment,
  repositoryRoot, ordinaryPath, databaseName, uploadRoots, validatePassword, validateGenerated } from '../host/app-environment.mjs';
import { loadMaterial, privateDirectory, renderPrometheus, setupLocal } from '../host/local-setup.mjs';
import { assertAvailable, parseArguments, runForeground } from '../host/cc4c.mjs';

const checks = [];
let current = 'initialization';

/** 每项检查只记录名称；失败不打印可能含合成口令的断言对象。 */
async function check(name, action) {
  current = name;
  await action();
  checks.push(name);
}

/** 创建仅包含公开模板与合成配置的夹具；不读取真实环境文件、密钥、日志或运行数据。 */
async function fixture(parent, values, example, prometheus) {
  const root = await mkdtemp(path.join(parent, 'case-'));
  for (const folder of ['backend', 'frontend', 'observability', 'infrastructure', 'infrastructure/prometheus']) await mkdir(path.join(root, folder));
  await writeFile(path.join(root, 'backend/.env.example'), example, { flag: 'wx' });
  await writeFile(path.join(root, 'backend/.env.local'), Object.entries(values).map(([name, value]) => `${name}=${value}`).join('\n') + '\n', { flag: 'wx', mode: 0o600 });
  for (const app of ['frontend', 'observability']) {
    for (const name of ['.env.example', '.env.local']) await writeFile(path.join(root, app, name), 'VITE_API_BASE_URL=http://localhost:4080\n', { flag: 'wx' });
  }
  await writeFile(path.join(root, 'infrastructure/prometheus/prometheus.yml.template'), prometheus, { flag: 'wx' });
  return root;
}

/** 用临时合成目录验证初始化、隔离和生命周期；保留检查现场供人工核对，不递归删除。 */
async function main() {
  const parent = process.env.CC4C_VERIFICATION_ROOT || process.env.RUNNER_TEMP || os.tmpdir();
  await ordinaryPath(parent, { directory: true });
  const run = await mkdtemp(path.join(parent, 'cc4c-launcher-'));
  const example = await readFile(path.join(repositoryRoot, 'backend/.env.example'), 'utf8');
  const prometheus = await readFile(path.join(repositoryRoot, 'infrastructure/prometheus/prometheus.yml.template'), 'utf8');
  const values = { ...parseEnvironment(example), CC4C_DB_PASSWORD: 'synthetic-db', CC4C_MAIL_HOST: 'smtp.example.test',
    CC4C_MAIL_USERNAME: 'sender@example.test', CC4C_MAIL_PASSWORD: 'synthetic-mail',
    CC4C_RABBITMQ_URL: 'amqp://fixture:synthetic@127.0.0.1:5672/cc4c',
    CC4C_RABBITMQ_MONITOR_PASSWORD: 'synthetic-monitor', CC4C_OBSERVABILITY_PASSWORD: 'synthetic $ # = secret',
    CC4C_MODERATION_NOTIFICATION_RECIPIENTS: 'reviewer@example.test' };
  const root = await fixture(run, values, example, prometheus);
  await check('literal environment values', () => {
    const parsed = parseEnvironment(' # comment\r\nVALUE=  $x#=quoted"\r\nEMPTY=\r\n', new Set(['VALUE', 'EMPTY']));
    assert.equal(parsed.VALUE, '  $x#=quoted"'); assert.equal(parsed.EMPTY, '');
  });
  await check('unknown/duplicate/malformed keys rejected', () => {
    for (const text of ['A=1\nA=2', 'B=1', 'export A=1', 'A\n', 'A=x\0', '\uFEFFA=x', 'A=x\ry']) {
      assert.throws(() => parseEnvironment(text, new Set(['A'])));
    }
  });
  await check('automatic configuration and password policy', async () => {
    assert.equal(configurationMode(await readEnvironment('backend', root)), 'automatic');
    for (const value of ['', 'short', 'x'.repeat(65), '密'.repeat(25), 'abcdefghijkl\n', 'abcdefghijkl\0']) assert.throws(() => validatePassword(value));
    validatePassword('密'.repeat(24)); validatePassword(values.CC4C_OBSERVABILITY_PASSWORD);
  });
  const hash = '$2b$12$' + 'a'.repeat(53);
  const legacy = { ...values, CC4C_SECURITY_PEPPER: 'p'.repeat(32), CC4C_MESSAGING_ACTIVE_KEY_ID: 'old',
    CC4C_MESSAGING_PAYLOAD_KEYS: `old=${Buffer.alloc(32, 1).toString('base64')}`, CC4C_MANAGEMENT_PASSWORD_HASH: hash, CC4C_OBSERVABILITY_PASSWORD_HASH: hash };
  delete legacy.CC4C_OBSERVABILITY_PASSWORD;
  await check('legacy material preserved and mixed modes rejected', async () => {
    const oldRoot = await fixture(run, legacy, example, prometheus);
    const read = await readEnvironment('backend', oldRoot);
    assert.equal(configurationMode(read), 'legacy');
    for (const name of generatedNames) assert.equal(read[name], legacy[name]);
    assert.throws(() => configurationMode({ ...values, CC4C_SECURITY_PEPPER: '' }));
    assert.throws(() => configurationMode({ ...legacy, CC4C_OBSERVABILITY_PASSWORD: 'synthetic new password' }));
    await assert.rejects(setupLocal(legacy, oldRoot));
    assert.throws(() => validateGenerated({ ...legacy, CC4C_MESSAGING_ACTIVE_KEY_ID: 'missing' }));
    assert.throws(() => validateGenerated({ ...legacy, CC4C_MESSAGING_PAYLOAD_KEYS: 'old=invalid' }));
  });
  await check('database and fixed command selection', () => {
    assert.equal(databaseName(values), 'cc4c_runtime');
    assert.throws(() => databaseName({ CC4C_DB_URL: 'jdbc:mysql://localhost/' }));
    assert.deepEqual(parseArguments(['backend', '--database', 'cc4c_runtime']), { application: 'backend', database: 'cc4c_runtime' });
    assert.equal(parseArguments(['bootstrap-admin', '--database', 'cc4c_runtime', '--id', '1000001', '--password-file', path.join(run, 'admin.txt')]).adminId, '1000001');
    assert.throws(() => parseArguments(['bootstrap-admin', '--database', 'cc4c_runtime', '--id', '1', '--password-file', 'relative.txt']));
    for (const args of [['backend'], ['backend', '--database', 'x;exit'], ['frontend', '--port', '9'], ['setup'], ['bash']]) assert.throws(() => parseArguments(args));
  });
  await check('frontend environment isolation', async () => {
    const inherited = { PATH: 'tools', CC4C_DB_PASSWORD: 'do-not-inherit', SPRING_CONFIG_IMPORT: 'bad', VITE_SECRET: 'bad', RANDOM_PASSWORD: 'bad' };
    const roots = await uploadRoots(values, root);
    const input = { ...legacy, ...roots, VITE_API_BASE_URL: 'http://localhost:4080' };
    const business = childEnvironment('frontend', input, inherited);
    const monitoring = childEnvironment('observability', input, inherited);
    assert.deepEqual(Object.keys(business).sort(), ['PATH', 'VITE_API_BASE_URL', ...Object.keys(roots)].sort());
    assert.deepEqual(Object.keys(monitoring).sort(), ['PATH', 'VITE_API_BASE_URL'].sort());
    const backend = childEnvironment('backend', { ...legacy, ...values }, inherited);
    assert.equal(backend.SPRING_CONFIG_LOCATION, 'classpath:/application.yml');
    assert.equal(backend.CC4C_OBSERVABILITY_PASSWORD, undefined); assert.equal(backend.SPRING_CONFIG_IMPORT, undefined);
    await assert.rejects(uploadRoots({ ...values, CC4C_SAVE_IMG_PATH: values.CC4C_SAVE_AVATAR_PATH }, root));
  });
  await check('extra Vite configuration rejected', async () => {
    await writeFile(path.join(root, 'frontend/.env.production'), 'VITE_SECRET=synthetic', { flag: 'wx' });
    await assert.rejects(readEnvironment('frontend', root));
  });
  await check('first initialization and restart keep independent material', async () => {
    await assert.rejects(loadMaterial(root));
    const initialized = await setupLocal(values, root); assert.equal(initialized.existing, false);
    const first = await loadMaterial(root);
    const second = await setupLocal(values, root); assert.equal(second.existing, true);
    assert.deepEqual((await loadMaterial(root)).material, first.material);
    assert.notEqual(first.material.pepper, first.material.managementPassword);
    assert.equal(Buffer.from(first.material.payloadKey, 'base64').length, 32);
  });
  await check('password changes keep pepper, encryption and metrics credentials', async () => {
    const first = (await loadMaterial(root)).material;
    await setupLocal({ ...values, CC4C_OBSERVABILITY_PASSWORD: 'another synthetic password', CC4C_RABBITMQ_MONITOR_PASSWORD: 'changed-synthetic-monitor' }, root);
    assert.deepEqual((await loadMaterial(root)).material, first);
  });
  await check('Prometheus escaping, labels and password files', async () => {
    const yaml = renderPrometheus(prometheus, { ...values, CC4C_RABBITMQ_NAMESPACE: 'cc4c.test+special', CC4C_RABBITMQ_URL: 'amqp://user:secret@localhost/cc4c%2Btest' }, root);
    assert.equal(yaml.includes('__CC4C_'), false); assert.equal(yaml.includes(values.CC4C_RABBITMQ_MONITOR_PASSWORD), false);
    assert.equal((yaml.match(/password_file:/g) || []).length, 2); assert.equal(yaml.includes('cc4c\\\\+test'), true);
    assert.throws(() => renderPrometheus(prometheus.replace("environment: 'local'", "environment: 'changed'"), values, root));
  });
  await check('collisions, partial state and modified material rejected', async () => {
    const collision = await fixture(run, values, example, prometheus);
    await mkdir(path.join(collision, 'temp')); await mkdir(privateDirectory(collision));
    await assert.rejects(setupLocal(values, collision));
    await writeFile(path.join(privateDirectory(root), 'management-password.txt'), 'tampered-synthetic', { mode: 0o600 });
    await assert.rejects(loadMaterial(root)); await assert.rejects(setupLocal(values, root));
  });
  await check('hard links and directory redirection rejected', async () => {
    const source = path.join(run, 'ordinary.txt'); const hard = path.join(run, 'hard.txt');
    await writeFile(source, 'synthetic', { flag: 'wx' }); await link(source, hard);
    await assert.rejects(ordinaryPath(hard));
    const redirected = path.join(run, 'redirected');
    await symlink(root, redirected, process.platform === 'win32' ? 'junction' : 'dir');
    await assert.rejects(ordinaryPath(redirected, { directory: true }));
  });
  await check('occupied port rejected without stopping its owner', async () => {
    const server = net.createServer(); await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    try { await assert.rejects(assertAvailable(server.address().port, '127.0.0.1')); }
    finally { await new Promise((resolve) => server.close(resolve)); }
  });
  await check('child exit status and termination cleanup', async () => {
    const base = { executable: process.execPath, cwd: run, env: childEnvironment('tool', {}) };
    assert.equal(await runForeground({ ...base, args: ['-e', 'process.exit(7)'] }), 7);
    const before = process.listenerCount('SIGTERM');
    const running = runForeground({ ...base, args: ['-e', 'setInterval(() => {}, 1000)'] });
    await delay(150); process.emit('SIGTERM');
    assert.notEqual(await running, 0); assert.equal(process.listenerCount('SIGTERM'), before);
  });
  await writeFile(path.join(run, 'result.json'), JSON.stringify({ passed: checks, runtime: process.version, syntheticOnly: true }, null, 2) + '\n', { flag: 'wx' });
  process.stdout.write(`Local launcher: ${checks.length} synthetic check groups passed; no real environment or service was accessed.\n`);
}

main().catch((error) => {
  const reason = error.code || (error.name === 'AssertionError' ? 'assertion' : error.message);
  process.stderr.write(`Local launcher check failed: ${current} (${reason}). Preserve the synthetic fixtures.\n`);
  process.exitCode = 1;
});
