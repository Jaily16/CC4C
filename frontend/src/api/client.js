import axios from 'axios';

const api = axios.create({
  baseURL: (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:4080').replace(/\/+$/, ''),
  withCredentials: true,
  withXSRFToken: true,
});

let csrfPromise = null;

/** 只读查询 ensureCsrfToken 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
async function ensureCsrfToken() {
  if (!csrfPromise) {
    csrfPromise = api.get('/csrf', { cc4cSkipCsrf: true }).catch((error) => {
      csrfPromise = null;
      throw error;
    });
  }
  await csrfPromise;
}

/** 只读查询 resetCsrfToken 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function resetCsrfToken() {
  csrfPromise = null;
}

/** 统一附加业务 CSRF 或处理会话失效，避免页面直接操作安全令牌。 */
api.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase();
  if (!config.cc4cSkipCsrf && ['post', 'put', 'patch', 'delete'].includes(method)) {
    await ensureCsrfToken();
  }
  return config;
});

/** 统一附加业务 CSRF 或处理会话失效，避免页面直接操作安全令牌。 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('cc4c:unauthorized'));
    }
    return Promise.reject(error);
  },
);

export default api;
