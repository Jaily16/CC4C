/* eslint-disable no-console */

/** 仅接收字符串，将换行和制表符压成空格，并保留前 160 个 UTF-16 代码单元。 */
function safeText(value, fallback = '') {
  if (typeof value !== 'string') return fallback;
  return value.replace(/[\r\n\t]/g, ' ').slice(0, 160);
}

/**
 * 仅在开发模式输出异常名称、截短消息和固定上下文；不采集请求对象，但消息内容仍由异常来源决定。
 * @param {unknown} error 捕获到的异常。
 * @param {string} context 固定的功能上下文，不应包含请求数据。
 * @param {{sink?: (payload: object) => void, development?: boolean}} options 开发或校验使用的输出与模式覆盖项。
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
