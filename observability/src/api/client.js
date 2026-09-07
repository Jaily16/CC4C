import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

/** 解包统一 API 响应，并把业务失败转换为前端可处理的受控错误。 */
export function unwrap(response) {
  if (!response?.data || response.data.code !== 200) {
    throw new Error('INVALID_API_RESPONSE');
  }
  return response.data.data;
}

/** 从受控错误中选择可展示中文消息，不暴露响应正文、凭据或内部异常。 */
export function safeMessage(error, fallback = '请求暂时无法完成，请稍后重试') {
  const status = error?.response?.status;
  if (status === 401) return '观测会话已失效，请重新登录';
  if (status === 403) return '观测请求安全校验失败';
  if (status === 429) return '登录尝试过于频繁，请稍后再试';
  if (status === 503) return '观测身份服务暂时不可用';
  return fallback;
}

export default client;
