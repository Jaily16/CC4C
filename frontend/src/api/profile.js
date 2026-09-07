import api from './client.js';

/** 只读查询 getCurrentUser 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getCurrentUser(http = api) {
  return http.get('/users/me');
}

/** 上传当前用户头像文件并返回公开地址；页面负责文件前置校验与成功后资料刷新。 */
export function uploadAvatar(formData, http = api) {
  return http.post('/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 提交 updateProfile 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function updateProfile(payload, http = api) {
  return http.put('/users/me', payload);
}

/** 提交 changePassword 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function changePassword(payload, http = api) {
  return http.put('/users/me/password', payload);
}
