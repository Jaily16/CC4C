import axios from 'axios';

const api = axios.create({
  baseURL: (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:4080').replace(/\/+$/, ''),
  withCredentials: true,
  withXSRFToken: true,
});

let csrfPromise = null;

async function ensureCsrfToken() {
  if (!csrfPromise) {
    csrfPromise = api.get('/csrf', { cc4cSkipCsrf: true }).catch((error) => {
      csrfPromise = null;
      throw error;
    });
  }
  await csrfPromise;
}

export function resetCsrfToken() {
  csrfPromise = null;
}

api.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase();
  if (!config.cc4cSkipCsrf && ['post', 'put', 'patch', 'delete'].includes(method)) {
    await ensureCsrfToken();
  }
  return config;
});

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
