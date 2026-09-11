import { execFile as execFileCallback, spawn } from 'node:child_process';
import { realpath } from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import { promisify } from 'node:util';
import { pathToFileURL } from 'node:url';
import { bcryptPattern, childEnvironment, configurationMode, databaseName, ordinaryPath, readEnvironment,
  readText, repositoryRoot, uploadRoots, validateGenerated, validatePassword } from './app-environment.mjs';
import { loadMaterial, setupLocal } from './local-setup.mjs';

const execFile = promisify(execFileCallback);
const usage = 'Usage: node infrastructure/host/cc4c.mjs setup --new-environment | backend --database NAME | frontend | observability | bootstrap-admin --database NAME --id SEVEN_DIGITS --password-file ABSOLUTE_PATH';

/** 命令参数只选择固定入口；拒绝多余参数、任意命令和未确认的后端数据库。 */
export function parseArguments(args) {
  if (args.length === 7 && args[0] === 'bootstrap-admin' && args[1] === '--database' && /^[A-Za-z0-9_]+$/.test(args[2])
    && args[3] === '--id' && /^\d{7}$/.test(args[4]) && args[5] === '--password-file' && path.isAbsolute(args[6])
    && !/[\r\n\0]/.test(args[6])) return { application: 'bootstrap-admin', database: args[2], adminId: args[4], passwordFile: args[6] };
  if (args.length === 2 && args[0] === 'setup' && args[1] === '--new-environment') return { application: 'setup' };
  if (args.length === 3 && args[0] === 'backend' && args[1] === '--database' && /^[A-Za-z0-9_]+$/.test(args[2])) {
    return { application: 'backend', database: args[2] };
  }
  if (args.length === 1 && ['frontend', 'observability'].includes(args[0])) return { application: args[0] };
  throw new Error(usage);
}

/** 使用 JAVA_HOME 或 PATH 中已有的 Java；只允许 Java 21，不安装或修改用户工具链。 */
export async function javaExecutable(environment = process.env) {
  const filename = process.platform === 'win32' ? 'java.exe' : 'java';
  const candidates = environment.JAVA_HOME ? [path.join(environment.JAVA_HOME, 'bin', filename)]
    : (environment.PATH || environment.Path || '').split(path.delimiter).filter(Boolean).map((folder) => path.join(folder, filename));
  for (const candidate of candidates) {
    let resolved;
    try { resolved = await realpath(candidate); } catch (error) { if (error.code === 'ENOENT') continue; throw new Error('Unable to resolve Java.'); }
    await ordinaryPath(resolved);
    try {
      const result = await execFile(resolved, ['-version'], { env: childEnvironment('tool', {}, environment), windowsHide: true });
      if (!/version "21(?:\.|"|-)/.test(`${result.stdout}${result.stderr}`)) throw new Error('version');
    } catch { throw new Error('Select Java 21 with JAVA_HOME and check mvn --version.'); }
    return resolved;
  }
  throw new Error('Java is missing; set JAVA_HOME to the installed JDK 21.');
}

/** 通过有限标准输入计算 BCrypt；秘密不进入参数或子进程环境，工具输出仅供内部使用。 */
export async function hashPassword(java, jar, password) {
  validatePassword(password);
  await ordinaryPath(jar);
  return new Promise((resolve, reject) => {
    const child = spawn(java, ['-jar', jar, '--stdin'], { env: childEnvironment('tool', {}), windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    let failed = false;
    const timer = setTimeout(() => { failed = true; child.kill(); }, 30000);
    child.stdout.on('data', (chunk) => {
      output += chunk.toString('utf8');
      if (output.length > 256) { failed = true; child.kill(); }
    });
    child.stderr.on('data', () => { failed = true; });
    child.stdin.on('error', () => { failed = true; });
    child.on('error', () => { clearTimeout(timer); reject(new Error('Unable to launch the password helper.')); });
    child.on('close', (code) => {
      clearTimeout(timer);
      const hash = output.trim();
      if (failed || code !== 0 || !bcryptPattern.test(hash)) reject(new Error('Password helper failed; rebuild the backend and preserve the local material.'));
      else resolve(hash);
    });
    child.stdin.end(Buffer.from(password, 'utf8'));
  });
}

/** 从受控版本清单定位现有 JAR，不猜测旧版本产物或接受任意可执行文件名。 */
async function artifactPaths(root) {
  const version = JSON.parse(await readText(path.join(root, 'versions.yml'))).project.version;
  if (!/^\d+\.\d+\.\d+-SNAPSHOT$/.test(version)) throw new Error('Unsupported project version.');
  return { main: path.join(root, 'backend/target', `cc4c-${version}.jar`), admin: path.join(root, 'backend/target', `cc4c-${version}-admin-bootstrap.jar`),
    password: path.join(root, 'backend/target', `cc4c-${version}-observability-password.jar`) };
}

/** 准备固定应用的子进程参数；旧配置完全复用，新模式只在内存中补入安全输出。 */
export async function prepareApplication(options, root = repositoryRoot) {
  const bootstrap = options.application === 'bootstrap-admin';
  const application = bootstrap ? 'backend' : options.application;
  const values = await readEnvironment(application, root);
  const cwd = path.join(root, application);
  await ordinaryPath(cwd, { directory: true });
  if (application === 'backend') {
    if (databaseName(values) !== options.database) throw new Error('Configured database does not match --database.');
    const artifacts = await artifactPaths(root);
    const jar = bootstrap ? artifacts.admin : artifacts.main;
    await ordinaryPath(jar);
    const java = await javaExecutable();
    if (configurationMode(values) === 'automatic') {
      const { material } = await loadMaterial(root);
      values.CC4C_SECURITY_PEPPER = material.pepper;
      values.CC4C_MESSAGING_ACTIVE_KEY_ID = material.activeKeyId;
      values.CC4C_MESSAGING_PAYLOAD_KEYS = `${material.activeKeyId}=${material.payloadKey}`;
      values.CC4C_MANAGEMENT_PASSWORD_HASH = await hashPassword(java, artifacts.password, material.managementPassword);
      values.CC4C_OBSERVABILITY_PASSWORD_HASH = await hashPassword(java, artifacts.password, values.CC4C_OBSERVABILITY_PASSWORD);
      validateGenerated(values);
    }
    await uploadRoots(values, root);
    if (bootstrap) {
      await ordinaryPath(options.passwordFile);
      const relative = path.relative(root, path.resolve(options.passwordFile));
      if (!path.isAbsolute(relative) && relative !== '..' && !relative.startsWith(`..${path.sep}`)) throw new Error('Administrator password file must remain outside the repository.');
      values.CC4C_ADMIN_BOOTSTRAP_ID = options.adminId;
      values.CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE = options.database;
      values.CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE = path.resolve(options.passwordFile);
    }
    return { executable: java, args: ['-jar', jar], cwd, env: childEnvironment(application, values), ports: bootstrap ? [] : [4080, 4081] };
  }
  if (application === 'frontend') Object.assign(values, await uploadRoots(await readEnvironment('backend', root), root));
  const vite = path.join(cwd, 'node_modules/vite/bin/vite.js');
  await ordinaryPath(vite);
  const port = application === 'frontend' ? 5173 : 5174;
  return { executable: process.execPath, args: [vite, '--host', 'localhost', '--port', String(port), '--strictPort'],
    cwd, env: childEnvironment(application, values), ports: [port] };
}

/** 只尝试绑定即将使用的本机端口；不查询服务数据、不结束占用者，也不更换端口。 */
export async function assertAvailable(port, host) {
  await new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', () => reject(new Error(`Port ${port} is unavailable; identify its owner before starting.`)));
    server.listen({ port, host, exclusive: true }, () => server.close((error) => error ? reject(new Error('Port check failed.')) : resolve()));
  });
}

/** 前台运行唯一子进程；POSIX 显式转交中断，Windows 使用共享控制台的 Ctrl+C 广播。 */
export function runForeground(command) {
  return new Promise((resolve, reject) => {
    const child = spawn(command.executable, command.args, { cwd: command.cwd, env: command.env, stdio: 'inherit', shell: false });
    let interrupted = false;
    const interrupt = (signal) => {
      if (interrupted) return;
      interrupted = true;
      if (process.platform !== 'win32') child.kill(signal);
    };
    const onInterrupt = () => interrupt('SIGINT');
    const onTerminate = () => { interrupted = true; child.kill('SIGTERM'); };
    process.once('SIGINT', onInterrupt);
    process.once('SIGTERM', onTerminate);
    const cleanup = () => { process.removeListener('SIGINT', onInterrupt); process.removeListener('SIGTERM', onTerminate); };
    child.once('error', () => { cleanup(); reject(new Error('Unable to start the selected application.')); });
    child.once('exit', (code, signal) => { cleanup(); resolve(code ?? (signal || interrupted ? 130 : 1)); });
  });
}

/** CLI 只输出操作结果；启动动作由明确子命令触发，导入模块供合成检查使用时没有副作用。 */
async function main() {
  if (process.argv.length === 3 && ['--help', '-h'].includes(process.argv[2])) { process.stdout.write(`${usage}\n`); return; }
  const options = parseArguments(process.argv.slice(2));
  if (options.application === 'setup') {
    const result = await setupLocal(await readEnvironment('backend'));
    process.stdout.write(`Local material ${result.existing ? 'preserved; generated configuration refreshed' : 'initialized'}. Private files: temp/local-runtime\n`);
    return;
  }
  const command = await prepareApplication(options);
  for (const port of command.ports) await assertAvailable(port, options.application === 'backend' ? '127.0.0.1' : 'localhost');
  process.stdout.write(`Starting ${options.application}; use Ctrl+C in this terminal to stop.\n`);
  process.exitCode = await runForeground(command);
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  main().catch((error) => {
    process.stderr.write(`${error.code ? 'A required local file or tool is unavailable; preserve the current state.' : error.message}\n`);
    process.exitCode = 1;
  });
}
