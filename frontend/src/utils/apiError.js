/** apiErrorMessage 封装当前组件的一项语义操作，并保持既有状态与错误处理边界。 */
export function apiErrorMessage(error, fallback = '请求失败，请稍后重试。') {
  return error?.response?.data?.msg || error?.message || fallback;
}
