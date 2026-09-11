import { randomBytes, createHash } from 'node:crypto';
import { execFile as execFileCallback } from 'node:child_process';
import { chmod, lstat, mkdir, open, rename } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { childEnvironment, configurationMode, endpoint, ordinaryPath, readText, repositoryRoot, samePath } from './app-environment.mjs';

const execFile = promisify(execFileCallback);
const owner = 'cc4c-local-runtime-v1';
const generatedFiles = ['management-password.txt', 'rabbitmq-password.txt', 'prometheus.yml'];

/** 固定私有目录；它属于被 Git 忽略的 temp，不使用外部路径参数接管其他实例。 */
export function privateDirectory(root = repositoryRoot) {
  return path.join(root, 'temp', 'local-runtime');
}

/** 只计算当前生成资料的校验和，用来识别缺失、手改和未完成的更新，不回显正文。 */
function digest(text) {
  return createHash('sha256').update(text).digest('hex');
}

/** 查询当前 Windows SID，不读取配置或进程环境；系统工具失败只报告固定提示。 */
async function windowsIdentity() {
  const executable = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'whoami.exe');
  const { stdout } = await execFile(executable, ['/user', '/fo', 'csv', '/nh'], { windowsHide: true });
  const sid = stdout.match(/S-1-\d+(?:-\d+)+/)?.[0];
  if (!sid) throw new Error('Unable to identify the private directory owner.');
  return sid;
}

/** 新目录在写入秘密前移除继承权限，只保留当前用户和 SYSTEM；不修改已有用户目录 ACL。 */
async function restrictDirectory(folder) {
  if (process.platform !== 'win32') { await chmod(folder, 0o700); return; }
  const sid = await windowsIdentity();
  const executable = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'icacls.exe');
  try {
    await execFile(executable, [folder, '/inheritance:r', '/grant:r', `*${sid}:(OI)(CI)F`, '*S-1-5-18:(OI)(CI)F'], { windowsHide: true });
  } catch { throw new Error('Unable to restrict the new private directory.'); }
}

/** 校验私有文件的所有者和访问范围；Windows 只返回 ACL 判断结果，不输出账号或配置。 */
async function assertPrivate(filename, directory = false) {
  const metadata = await ordinaryPath(filename, { directory });
  if (process.platform !== 'win32') {
    if (metadata.uid !== process.getuid() || (metadata.mode & 0o077) !== 0) throw new Error('Private material has unsafe permissions.');
    return;
  }
  const script = "$ErrorActionPreference='Stop'; $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value; $acl=Get-Acl -LiteralPath $env:CC4C_PRIVATE_CHECK; if($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid){exit 1}; $rules=@($acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])); if($rules.Count -eq 0){exit 1}; foreach($rule in $rules){if($rule.IdentityReference.Value -notin @($sid,'S-1-5-18') -or $rule.AccessControlType -ne 'Allow'){exit 1}}; exit 0";
  const executable = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
  const env = childEnvironment('tool', {});
  env.CC4C_PRIVATE_CHECK = filename;
  try {
    await execFile(executable, ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], { env, windowsHide: true });
  } catch { throw new Error('Private material has unsafe ownership or permissions.'); }
}

/** 只创建不存在的文件；失败保留现场，禁止覆盖未知文件。 */
async function createFile(filename, text) {
  await ordinaryPath(path.dirname(filename), { directory: true });
  const handle = await open(filename, 'wx', 0o600);
  try { await handle.writeFile(text, 'utf8'); await handle.sync(); } finally { await handle.close(); }
  await assertPrivate(filename);
}

/** 仅替换通过清单核对的程序派生文件；临时文件也使用独占创建，失败时不删除现场。 */
async function replaceOwned(filename, text, expected) {
  await assertPrivate(filename);
  const current = await readText(filename);
  if (digest(current) !== expected) throw new Error('Generated configuration was modified; preserve it and resolve the difference.');
  if (current === text) return;
  const temporary = `${filename}.next`;
  await createFile(temporary, text);
  // 再次验证目标身份和内容，避免更新准备期间覆盖其他资料。
  await assertPrivate(filename);
  if (digest(await readText(filename)) !== expected) throw new Error('Generated configuration changed while updating.');
  await rename(temporary, filename);
}

/** 校验密钥文件、完成标记和全部派生文件；普通启动不会补齐或重建任何缺失材料。 */
export async function loadMaterial(root = repositoryRoot) {
  const folder = privateDirectory(root);
  await assertPrivate(folder, true);
  const filenames = ['material.json', 'manifest.json', ...generatedFiles];
  for (const name of filenames) await assertPrivate(path.join(folder, name));
  let material;
  let manifest;
  try {
    material = JSON.parse(await readText(path.join(folder, 'material.json')));
    manifest = JSON.parse(await readText(path.join(folder, 'manifest.json')));
  } catch { throw new Error('Local initialization is incomplete or corrupt; restore its original private material.'); }
  if (material.owner !== owner || manifest.owner !== owner || !samePath(material.workspace || '', path.resolve(root))
    || material.activeKeyId !== 'local-v1' || !/^[A-Za-z0-9+/]{43}=$/.test(material.payloadKey || '')
    || !/^[A-Za-z0-9_-]{43}$/.test(material.pepper || '') || !/^[A-Za-z0-9_-]{43}$/.test(material.managementPassword || '')
    || JSON.stringify(Object.keys(manifest.files || {}).sort()) !== JSON.stringify(['material.json', ...generatedFiles].sort())) {
    throw new Error('Local private material has an unsupported structure.');
  }
  for (const name of ['material.json', ...generatedFiles]) {
    if (digest(await readText(path.join(folder, name))) !== manifest.files[name]) throw new Error('Local private material does not match its completion record.');
  }
  return { material, manifest };
}

/** 按精确默认标记生成 YAML；值使用 JSON 字符串转义，密码只放入 password_file。 */
export function renderPrometheus(template, values, root = repositoryRoot) {
  const folder = privateDirectory(root);
  const quote = (value) => JSON.stringify(value.replaceAll('\\', '/'));
  const yamlValue = (value) => JSON.stringify(value);
  const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const rabbit = endpoint(values.CC4C_RABBITMQ_URL, ['amqp:', 'amqps:']);
  let vhost;
  try { vhost = decodeURIComponent(rabbit.pathname.slice(1)); } catch { throw new Error('RabbitMQ vhost encoding is invalid.'); }
  if (!vhost) throw new Error('Name one explicit RabbitMQ vhost.');
  const replacements = [
    ['rules/cc4c-alerts.yml', quote(path.join(root, 'infrastructure/prometheus/rules/cc4c-alerts.yml')), 1],
    ["username: 'cc4c_observer'", `username: ${yamlValue(values.CC4C_MANAGEMENT_USERNAME)}`, 1],
    ["password: '__CC4C_MANAGEMENT_PASSWORD__'", `password_file: ${quote(path.join(folder, 'management-password.txt'))}`, 1],
    ["username: 'cc4c_monitor'", `username: ${yamlValue(values.CC4C_RABBITMQ_MONITOR_USERNAME)}`, 1],
    ["password: '__CC4C_RABBITMQ_MONITOR_PASSWORD__'", `password_file: ${quote(path.join(folder, 'rabbitmq-password.txt'))}`, 1],
    ["environment: 'local'", `environment: ${yamlValue(values.CC4C_OBSERVABILITY_ENVIRONMENT)}`, 2],
    ["targets: ['127.0.0.1:15692']", `targets: [${yamlValue(`${rabbit.hostname}:15692`)}]`, 1],
    ["regex: 'cc4c'", `regex: ${yamlValue(escapeRegex(vhost))}`, 1],
    ["regex: 'cc4c\\.v3\\.messaging\\.local.*'", `regex: ${yamlValue(`${escapeRegex(values.CC4C_RABBITMQ_NAMESPACE)}.*`)}`, 1],
  ];
  for (const [source, target, count] of replacements) {
    if (template.split(source).length - 1 !== count) throw new Error('Prometheus template markers changed; review the renderer.');
    template = template.replaceAll(source, target);
  }
  if (template.includes('__CC4C_')) throw new Error('Prometheus configuration contains unresolved placeholders.');
  return template;
}

/** 初始化仅使用新配置；重复执行只更新自己拥有的抓取配置，随机材料和已有环境文件保持不变。 */
export async function setupLocal(values, root = repositoryRoot) {
  if (configurationMode(values) !== 'automatic') throw new Error('Existing complete configurations do not require setup; keep their original secrets and Prometheus configuration.');
  if (!/^[A-Za-z0-9._-]{3,64}$/.test(values.CC4C_RABBITMQ_MONITOR_USERNAME || '')
    || !values.CC4C_RABBITMQ_MONITOR_PASSWORD?.trim() || /[\r\n\0]/.test(values.CC4C_RABBITMQ_MONITOR_PASSWORD)) {
    throw new Error('Fill the dedicated RabbitMQ monitoring account and password.');
  }
  const folder = privateDirectory(root);
  await ordinaryPath(folder, { directory: true, allowMissing: true });
  let existing = false;
  try { await lstat(folder); existing = true; } catch (error) { if (error.code !== 'ENOENT') throw error; }
  let material;
  let previous;
  if (existing) {
    ({ material, manifest: previous } = await loadMaterial(root));
  } else {
    const temp = path.dirname(folder);
    await ordinaryPath(temp, { directory: true, allowMissing: true });
    try { await mkdir(temp); } catch (error) { if (error.code !== 'EEXIST') throw error; }
    await ordinaryPath(temp, { directory: true });
    await mkdir(folder, { mode: 0o700 });
    await restrictDirectory(folder);
    await assertPrivate(folder, true);
    material = { owner, workspace: path.resolve(root), activeKeyId: 'local-v1',
      pepper: randomBytes(32).toString('base64url'), payloadKey: randomBytes(32).toString('base64'),
      managementPassword: randomBytes(32).toString('base64url') };
    await createFile(path.join(folder, 'material.json'), `${JSON.stringify(material, null, 2)}\n`);
  }
  const template = await readText(path.join(root, 'infrastructure/prometheus/prometheus.yml.template'));
  const contents = {
    'management-password.txt': material.managementPassword,
    'rabbitmq-password.txt': values.CC4C_RABBITMQ_MONITOR_PASSWORD,
    'prometheus.yml': renderPrometheus(template, values, root),
  };
  for (const [name, content] of Object.entries(contents)) {
    const filename = path.join(folder, name);
    if (existing) await replaceOwned(filename, content, previous.files[name]);
    else await createFile(filename, content);
  }
  const files = { 'material.json': digest(await readText(path.join(folder, 'material.json'))) };
  for (const [name, content] of Object.entries(contents)) files[name] = digest(content);
  const manifest = `${JSON.stringify({ owner, files }, null, 2)}\n`;
  const manifestPath = path.join(folder, 'manifest.json');
  if (existing) await replaceOwned(manifestPath, manifest, digest(`${JSON.stringify(previous, null, 2)}\n`));
  else await createFile(manifestPath, manifest);
  await loadMaterial(root);
  return { existing, directory: folder };
}
