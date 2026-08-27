export function apiErrorMessage(error, fallback = '请求失败，请稍后重试。') {
  return error?.response?.data?.msg || error?.message || fallback;
}
