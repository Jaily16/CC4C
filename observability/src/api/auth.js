import client, { unwrap } from './client.js';

let csrfToken = null;

/** 首次写请求前获取并缓存观测 CSRF Token，使用固定的 X-CC4C-OBSERVABILITY-CSRF 请求头。 */
async function csrfHeader() {
  if (!csrfToken) {
    const data = unwrap(await client.get('/observability/auth/csrf'));
    csrfToken = data.token;
  }
  return { 'X-CC4C-OBSERVABILITY-CSRF': csrfToken };
}

/** 清除内存中的观测 CSRF 状态，不读取或操作 Session Cookie。 */
function forgetCsrf() {
  csrfToken = null;
}

/** 读取独立观测 Session 状态；浏览器自动携带 Cookie，调用方可通过 signal 取消请求。 */
export async function fetchSession(signal) {
  return unwrap(await client.get('/observability/auth/session', { signal }));
}

/** 携带观测 CSRF 提交门户凭据；无论请求成功与否均清除缓存的 CSRF Token。 */
export async function login(credentials) {
  const headers = await csrfHeader();
  try {
    return unwrap(await client.post('/observability/auth/login', credentials, { headers }));
  } finally {
    forgetCsrf();
  }
}

/** 携带观测 CSRF 请求注销门户 Session，并清除 CSRF 缓存；身份视图由 Store 更新。 */
export async function logout() {
  const headers = await csrfHeader();
  try {
    return unwrap(await client.post('/observability/auth/logout', null, { headers }));
  } finally {
    forgetCsrf();
  }
}
