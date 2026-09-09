import api from './client.js';

/** 读取业务 Session 状态；Cookie 由浏览器自动携带，调用方负责处理未认证结果。 */
export function getSession(http = api) {
  return http.get('/auth/session');
}

/** 提交用户登录凭据并建立业务 Session；CSRF 由统一客户端处理，不在前端持久化密码。 */
export function loginUser(payload, http = api) {
  return http.post('/users/login', payload);
}

/** 请求创建并投递邮箱验证码邮件；页面负责防重复提交、倒计时和错误提示。 */
export function requestVerificationCode(payload, http = api) {
  return http.post('/users/email', payload);
}

/** 提交注册数据并创建用户账户，成功后的 Session 与跳转由调用页面处理。 */
export function registerUser(payload, http = api) {
  return http.post('/users', payload);
}

/** 使用邮箱验证码提交密码重置，前端不保存密码或验证码。 */
export function resetPassword(payload, http = api) {
  return http.put('/users/password/forget', payload);
}

/** 注销普通用户 Session，并在成功后由调用方清除本地会话视图。 */
export function logoutUser(http = api) {
  return http.post('/users/logout');
}

/** 提交管理员登录凭据并建立管理员 Session，不复用普通用户授权状态。 */
export function loginAdmin(payload, http = api) {
  return http.post('/admin/login', payload);
}

/** 注销管理员 Session，并在成功后由调用方清除管理员会话视图。 */
export function logoutAdmin(http = api) {
  return http.post('/admin/logout');
}

/** 提交管理员密码修改并使既有安全状态按后端规则更新。 */
export function updateAdminPassword(payload, http = api) {
  return http.put('/admin/password', payload);
}
