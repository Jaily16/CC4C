import api from './client.js';

/** 读取当前 USER 会话对应的个人资料。 */
export function getCurrentUser(http = api) {
  return http.get('/users/me');
}

/** 上传当前用户头像文件并返回公开地址；页面负责文件前置校验与成功后资料刷新。 */
export function uploadAvatar(formData, http = api) {
  return http.post('/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 提交当前用户资料修改，页面在成功后刷新 Vuex 中的用户信息。 */
export function updateProfile(payload, http = api) {
  return http.put('/users/me', payload);
}

/** 提交当前用户密码修改；成功后后端撤销既有会话，前端按结果重新登录。 */
export function changePassword(payload, http = api) {
  return http.put('/users/me/password', payload);
}
