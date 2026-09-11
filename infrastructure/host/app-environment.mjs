import { constants } from 'node:fs';
import { lstat, open, realpath } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const generatedNames = Object.freeze([
  'CC4C_SECURITY_PEPPER', 'CC4C_MESSAGING_ACTIVE_KEY_ID', 'CC4C_MESSAGING_PAYLOAD_KEYS',
  'CC4C_MANAGEMENT_PASSWORD_HASH', 'CC4C_OBSERVABILITY_PASSWORD_HASH',
]);
export const setupNames = Object.freeze([
  'CC4C_OBSERVABILITY_PASSWORD', 'CC4C_RABBITMQ_MONITOR_USERNAME', 'CC4C_RABBITMQ_MONITOR_PASSWORD',
]);
const legacyDefaults = Object.freeze({
  CC4C_DB_CONNECTION_TIMEOUT_MS: '3000', CC4C_DB_VALIDATION_TIMEOUT_MS: '1000',
  CC4C_MESSAGING_SAMPLE_INTERVAL: '15s', CC4C_MAX_HTTP_URI_TAGS: '100',
  CC4C_MAIL_AUTH: 'false', CC4C_MAIL_SSL_ENABLED: 'false', CC4C_MAIL_STARTTLS_ENABLED: 'false',
});
export const bcryptPattern = /^\$2[aby]\$12\$[./A-Za-z0-9]{53}$/;

/** 仅比较规范路径；Windows 忽略大小写，文件内容和配置值始终区分大小写。 */
export function samePath(left, right) {
  return process.platform === 'win32' ? left.toLowerCase() === right.toLowerCase() : left === right;
}

/** 检查自身和所有父路径，不枚举目录；拒绝链接、硬链接、网络路径和特殊文件。 */
export async function ordinaryPath(filename, { directory = false, allowMissing = false } = {}) {
  const absolute = path.resolve(filename);
  if (absolute.startsWith('\\\\')) throw new Error('Network paths are not supported.');
  const segments = path.relative(path.parse(absolute).root, absolute).split(path.sep).filter(Boolean);
  let cursor = path.parse(absolute).root;
  let metadata;
  for (let index = 0; index < segments.length; index += 1) {
    cursor = path.join(cursor, segments[index]);
    try { metadata = await lstat(cursor); } catch (error) {
      if (allowMissing && error.code === 'ENOENT') return undefined;
      throw new Error('A required ordinary path is missing.');
    }
    const file = !directory && index === segments.length - 1;
    if (metadata.isSymbolicLink() || (file ? !metadata.isFile() || metadata.nlink !== 1 : !metadata.isDirectory())
      || !samePath(await realpath(cursor), cursor)) throw new Error('A path is redirected or has an unsafe type.');
  }
  return metadata;
}

/** 用已核对的文件身份读取有限长度 UTF-8 文本；错误中不包含正文、秘密或系统异常。 */
export async function readText(filename) {
  const metadata = await ordinaryPath(filename);
  if (metadata.size > 128 * 1024) throw new Error('Configuration file is too large.');
  const handle = await open(filename, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0));
  try {
    const opened = await handle.stat();
    if (!opened.isFile() || opened.nlink !== 1 || opened.ino !== metadata.ino || opened.dev !== metadata.dev) {
      throw new Error('Configuration file changed while opening.');
    }
    const bytes = await handle.readFile();
    if (bytes.length > 128 * 1024 || bytes.subarray(0, 3).equals(Buffer.from([239, 187, 191]))) {
      throw new Error('Use bounded UTF-8 configuration without BOM.');
    }
    try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
    catch { throw new Error('Configuration must be valid UTF-8.'); }
  } finally { await handle.close(); }
}

/** 按第一个等号分隔字面值；只忽略空行和整行注释，不展开美元符号、引号或行尾注释。 */
export function parseEnvironment(text, allowed) {
  if (text.includes('\0') || text.startsWith('\uFEFF')) throw new Error('Invalid environment encoding.');
  const values = Object.create(null);
  for (const [index, line] of text.replaceAll('\r\n', '\n').split('\n').entries()) {
    if (!line.trim() || line.trimStart().startsWith('#')) continue;
    const separator = line.indexOf('=');
    const name = line.slice(0, separator).trim();
    if (separator < 1 || !/^[A-Z][A-Z0-9_]*$/.test(name) || (allowed && !allowed.has(name))
      || Object.hasOwn(values, name) || line.includes('\r')) {
      throw new Error(`Invalid, duplicate or unsupported environment key on line ${index + 1}.`);
    }
    values[name] = line.slice(separator + 1);
  }
  return values;
}

/** 从固定应用目录读取配置；新模板给出白名单，旧安全字段仍可被完整兼容。 */
export async function readEnvironment(application, root = repositoryRoot) {
  if (!['backend', 'frontend', 'observability'].includes(application)) throw new Error('Unknown application.');
  const folder = path.join(root, application);
  if (application !== 'backend') {
    for (const other of ['.env', '.env.development', '.env.development.local', '.env.production', '.env.production.local']) {
      try { await lstat(path.join(folder, other)); } catch (error) { if (error.code === 'ENOENT') continue; throw error; }
      throw new Error('Preserve and resolve the extra Vite environment file before starting.');
    }
  }
  const example = parseEnvironment(await readText(path.join(folder, '.env.example')));
  const allowed = new Set(application === 'backend' ? [...Object.keys(example), ...generatedNames] : ['VITE_API_BASE_URL']);
  const values = parseEnvironment(await readText(path.join(folder, '.env.local')), allowed);
  if (application === 'backend') {
    for (const [name, value] of Object.entries(legacyDefaults)) if (!Object.hasOwn(values, name)) values[name] = value;
    const required = Object.keys(example).filter((name) => !setupNames.includes(name));
    validateRuntime(values, required);
  } else {
    endpoint(values.VITE_API_BASE_URL, ['http:', 'https:'], { credentials: false });
    // Vite 会再次解析 .env.local；公开 URL 不能含 dotenv 展开语法。
    if (/[$\s"']/.test(values.VITE_API_BASE_URL)) throw new Error('Use one literal public API URL.');
  }
  return values;
}

/** 严格区分自动配置与完整旧配置；任何部分旧密钥都不能触发自动补齐。 */
export function configurationMode(values) {
  const legacy = generatedNames.filter((name) => Object.hasOwn(values, name));
  if (legacy.length) {
    if (legacy.length !== generatedNames.length || generatedNames.some((name) => !values[name]?.trim())
      || Object.hasOwn(values, 'CC4C_OBSERVABILITY_PASSWORD')) throw new Error('Do not mix partial legacy and automatic secrets.');
    return 'legacy';
  }
  validatePassword(values.CC4C_OBSERVABILITY_PASSWORD);
  return 'automatic';
}

/** 与既有 BCrypt 工具保持同一长度规则；禁止会改变标准输入边界的换行和 NUL。 */
export function validatePassword(password) {
  if (typeof password !== 'string' || [...password].length < 12 || [...password].length > 64
    || Buffer.byteLength(password, 'utf8') > 72 || /[\r\n\0]/.test(password)) {
    throw new Error('Observability password requires 12–64 code points and at most 72 UTF-8 bytes.');
  }
}

/** 解析连接地址而不回显地址；HTTP 查询端点禁止内嵌凭据、查询和片段。 */
export function endpoint(value, protocols, { credentials = true } = {}) {
  let uri;
  try { uri = new URL(value); } catch { throw new Error('An endpoint URL is invalid.'); }
  if (!protocols.includes(uri.protocol) || !uri.hostname || uri.hash
    || (!credentials && (uri.username || uri.password || uri.search))) throw new Error('An endpoint URL is invalid.');
  return uri;
}

/** 只解析数据库名称，不连接数据库；名称必须由后端启动参数再次精确确认。 */
export function databaseName(values) {
  const match = /^jdbc:mysql:\/\/[^/]+\/([A-Za-z0-9_]+)(?:\?.*)?$/.exec(values.CC4C_DB_URL);
  if (!match) throw new Error('Name one explicit MySQL database.');
  return match[1];
}

/** 检查共有启动约束；生成模式只推迟五个安全输出字段，其他检查与旧入口保持一致。 */
export function validateRuntime(values, required) {
  const empty = new Set(['CC4C_MAIL_USERNAME', 'CC4C_MAIL_PASSWORD', 'CC4C_PROMETHEUS_USERNAME', 'CC4C_PROMETHEUS_PASSWORD']);
  for (const name of required) {
    if (!Object.hasOwn(values, name) || (!empty.has(name) && !values[name].trim())) throw new Error(`Fill required field ${name}.`);
  }
  databaseName(values);
  endpoint(values.CC4C_REDIS_URL, ['redis:', 'rediss:']);
  endpoint(values.CC4C_RABBITMQ_URL, ['amqp:', 'amqps:']);
  const namespaces = ['CC4C_SESSION_NAMESPACE', 'CC4C_CACHE_NAMESPACE', 'CC4C_OBSERVABILITY_SESSION_NAMESPACE'].map((name) => values[name]);
  if (namespaces.some((value) => !/^[A-Za-z0-9:_-]{3,120}$/.test(value)) || new Set(namespaces).size !== 3) {
    throw new Error('Use distinct valid Session, cache and observability namespaces.');
  }
  for (const name of ['SESSION_COOKIE_SECURE', 'BUSINESS_CACHE_ENABLED', 'API_DOCS_ENABLED', 'OBSERVABILITY_ENABLED',
    'OBSERVABILITY_COOKIE_SECURE', 'OUTBOX_DISPATCHER_ENABLED', 'MESSAGE_CONSUMERS_ENABLED', 'MAIL_AUTH', 'MAIL_SSL_ENABLED', 'MAIL_STARTTLS_ENABLED']) {
    if (!['true', 'false'].includes(values[`CC4C_${name}`])) throw new Error(`CC4C_${name} must be true or false.`);
  }
  for (const origin of [...values.CC4C_ALLOWED_ORIGINS.split(',').map((value) => value.trim()), values.CC4C_OBSERVABILITY_ALLOWED_ORIGIN]) {
    const uri = endpoint(origin, ['http:', 'https:'], { credentials: false });
    if (uri.pathname !== '/' || origin.includes('*')) throw new Error('CORS requires exact origins.');
  }
  if (values.CC4C_MANAGEMENT_ADDRESS !== '127.0.0.1' || values.CC4C_MANAGEMENT_PORT !== '4081') {
    throw new Error('Management must remain on 127.0.0.1:4081.');
  }
  for (const name of ['CC4C_MANAGEMENT_USERNAME', 'CC4C_OBSERVABILITY_USERNAME']) {
    if (!/^[A-Za-z0-9._-]{3,64}$/.test(values[name])) throw new Error('Invalid observability account name.');
  }
  endpoint(values.CC4C_PROMETHEUS_URL, ['http:', 'https:'], { credentials: false });
  if (Boolean(values.CC4C_PROMETHEUS_USERNAME?.trim()) !== Boolean(values.CC4C_PROMETHEUS_PASSWORD?.trim())) {
    throw new Error('Configure Prometheus API credentials as a pair.');
  }
  const numeric = (name, minimum, maximum) => /^\d+$/.test(values[name]) && Number(values[name]) >= minimum && Number(values[name]) <= maximum;
  if (!numeric('CC4C_MAIL_PORT', 1, 65535)
    || !numeric('CC4C_DB_CONNECTION_TIMEOUT_MS', 250, 60000)
    || !numeric('CC4C_DB_VALIDATION_TIMEOUT_MS', 250, Number(values.CC4C_DB_CONNECTION_TIMEOUT_MS) - 1)
    || !numeric('CC4C_MAX_HTTP_URI_TAGS', 10, 500)) throw new Error('A port, timeout or metric limit is invalid.');
  if (values.CC4C_MAIL_AUTH === 'true' && (!values.CC4C_MAIL_USERNAME?.trim() || !values.CC4C_MAIL_PASSWORD?.trim())) {
    throw new Error('SMTP authentication requires username and password.');
  }
  if (values.CC4C_MAIL_SSL_ENABLED === 'true' && values.CC4C_MAIL_STARTTLS_ENABLED === 'true') throw new Error('Choose SSL or STARTTLS.');
  if (!/^[a-z0-9-]{2,32}$/.test(values.CC4C_OBSERVABILITY_ENVIRONMENT) || values.CC4C_LOG_FORMAT !== 'ecs'
    || !/^[1-9]\d*(ms|s|m)$/.test(values.CC4C_MESSAGING_SAMPLE_INTERVAL)
    || !/^[A-Za-z0-9._:-]{3,120}$/.test(values.CC4C_RABBITMQ_NAMESPACE)) throw new Error('Invalid observability or messaging settings.');
  if (configurationMode(values) === 'legacy') validateGenerated(values);
}

/** 校验注入 Spring 的安全字段，保留原密钥环并拒绝错误的活动 ID、密钥字节和摘要格式。 */
export function validateGenerated(values) {
  if (values.CC4C_SECURITY_PEPPER.length < 32 || !bcryptPattern.test(values.CC4C_MANAGEMENT_PASSWORD_HASH)
    || !bcryptPattern.test(values.CC4C_OBSERVABILITY_PASSWORD_HASH)) throw new Error('Invalid security material.');
  const keys = new Set();
  for (const entry of values.CC4C_MESSAGING_PAYLOAD_KEYS.split(';')) {
    const separator = entry.indexOf('=');
    const id = entry.slice(0, separator).trim();
    const encoded = entry.slice(separator + 1).trim();
    if (separator < 1 || !/^[A-Za-z0-9._-]{1,64}$/.test(id) || keys.has(id)
      || !/^[A-Za-z0-9+/]{43}=$/.test(encoded) || Buffer.from(encoded, 'base64').length !== 32) throw new Error('Invalid messaging key ring.');
    keys.add(id);
  }
  if (!keys.has(values.CC4C_MESSAGING_ACTIVE_KEY_ID)) throw new Error('The active messaging key is missing.');
}

/** 解析原有上传根，只检查路径元数据；不创建、枚举或读取上传文件。 */
export async function uploadRoots(values, root = repositoryRoot) {
  const result = {};
  for (const [source, target] of [['CC4C_SAVE_IMG_PATH', 'CC4C_HOST_BLOG_IMG_ROOT'], ['CC4C_SAVE_AVATAR_PATH', 'CC4C_HOST_AVATAR_ROOT']]) {
    if (!values[source]?.trim()) throw new Error('Both upload roots are required.');
    const absolute = path.resolve(root, 'backend', values[source]);
    if (absolute === path.parse(absolute).root) throw new Error('An upload root must not be a filesystem root.');
    await ordinaryPath(absolute, { directory: true, allowMissing: true });
    result[target] = absolute;
  }
  if (samePath(...Object.values(result))) throw new Error('Upload roots must differ.');
  return result;
}

/** 为子进程创建独立工具环境；未知继承变量、Spring 外部配置和前端秘密均不透传。 */
export function childEnvironment(application, values, inherited = process.env) {
  const environment = {};
  const systemNames = new Set(['PATH', 'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'PATHEXT', 'HOME', 'USERPROFILE',
    'HOMEDRIVE', 'HOMEPATH', 'TEMP', 'TMP', 'TMPDIR', 'JAVA_HOME', 'LANG', 'LC_ALL', 'TERM', 'NO_COLOR']);
  for (const [name, value] of Object.entries(inherited)) if (systemNames.has(name.toUpperCase())) environment[name] = value;
  for (const [name, value] of Object.entries(values)) {
    if (application === 'backend' ? name.startsWith('CC4C_') && !setupNames.includes(name)
      : name === 'VITE_API_BASE_URL' || (application === 'frontend' && ['CC4C_HOST_BLOG_IMG_ROOT', 'CC4C_HOST_AVATAR_ROOT'].includes(name))) {
      environment[name] = value;
    }
  }
  if (application === 'backend') Object.assign(environment, {
    SPRING_CONFIG_NAME: 'application', SPRING_APPLICATION_NAME: 'CC4C', SPRING_CONFIG_LOCATION: 'classpath:/application.yml',
  });
  return environment;
}
