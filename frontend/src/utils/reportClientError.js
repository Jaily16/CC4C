/* eslint-disable no-console */

function safeText(value, fallback = '') {
  if (typeof value !== 'string') return fallback;
  return value.replace(/[\r\n\t]/g, ' ').slice(0, 160);
}

/**
 * 在开发环境提供脱敏错误诊断；生产环境只让调用方继续处理用户可见状态。
 * @param {unknown} error 捕获到的异常。
 * @param {string} context 固定的功能上下文，不应包含请求数据。
 * @param {{sink?: (payload: object) => void, development?: boolean}} options 测试或开发注入项。
 */
export function reportClientError(error, context = '', options = {}) {
  const development = options.development ?? import.meta.env?.DEV === true;
  if (development) {
    const payload = {
      name: safeText(error?.name, 'Error'),
      message: safeText(error?.message, 'Unknown client error'),
      context: safeText(context),
    };
    if (typeof options.sink === 'function') {
      options.sink(payload);
      return;
    }
    console.error('[CC4C client error]', payload);
  }
}
