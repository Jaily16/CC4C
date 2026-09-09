import axios from 'axios';

const api = axios.create({
  baseURL: (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:4080').replace(/\/+$/, ''),
  withCredentials: true,
  withXSRFToken: true,
});

let csrfPromise = null;

/** 合并并发 CSRF 初始化请求，失败时清空 Promise 以允许下一次重新初始化。 */
async function ensureCsrfToken() {
  if (!csrfPromise) {
    csrfPromise = api.get('/csrf', { cc4cSkipCsrf: true }).catch((error) => {
      csrfPromise = null;
      throw error;
    });
  }
  await csrfPromise;
}

/** 清除已缓存的 CSRF 初始化 Promise，使下一次写请求重新获取令牌。 */
export function resetCsrfToken() {
  csrfPromise = null;
}

/** 写请求发送前等待 CSRF 初始化；令牌获取请求通过标记跳过自身拦截。 */
api.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase();
  if (!config.cc4cSkipCsrf && ['post', 'put', 'patch', 'delete'].includes(method)) {
    await ensureCsrfToken();
  }
  return config;
});

/** 收到 401 时发布业务会话失效事件；所有错误仍拒绝 Promise，由调用方处理。 */
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
