import client, { unwrap } from './client.js';

let csrfToken = null;

/** 取得观测专用 CSRF Token 与头名；Token 只保存在内存并用于当前写请求。 */
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

/** 读取独立观测 Session 状态，浏览器仅自动携带 HttpOnly Cookie。 */
export async function fetchSession(signal) {
  return unwrap(await client.get('/observability/auth/session', { signal }));
}

/** 携带观测专用 CSRF 提交门户凭据，并由后端创建独立 HttpOnly Session。 */
export async function login(credentials) {
  const headers = await csrfHeader();
  try {
    return unwrap(await client.post('/observability/auth/login', credentials, { headers }));
  } finally {
    forgetCsrf();
  }
}

/** 携带观测专用 CSRF 注销门户 Session，并清除内存认证状态。 */
export async function logout() {
  const headers = await csrfHeader();
  try {
    return unwrap(await client.post('/observability/auth/logout', null, { headers }));
  } finally {
    forgetCsrf();
  }
}
