/** 优先取后端业务提示，其次取异常消息，缺失时使用页面提供的兜底提示。 */
export function apiErrorMessage(error, fallback = '请求失败，请稍后重试。') {
  return error?.response?.data?.msg || error?.message || fallback;
}
