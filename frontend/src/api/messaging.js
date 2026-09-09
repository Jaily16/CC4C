import api from './client.js';

/** 按筛选条件分页读取管理员异步消息摘要，不获取加密载荷正文。 */
export function listMessages(params, http = api) {
  return http.get('/admin/messaging/messages', { params });
}

/** 请求管理员重试选中的事件；后端校验状态并递增代次，页面负责刷新。 */
export function retryMessage(eventId, http = api) {
  return http.post(`/admin/messaging/messages/${eventId}/retry`);
}

/** 请求管理员忽略选中的事件；后端校验当前状态，页面负责刷新。 */
export function ignoreMessage(eventId, http = api) {
  return http.post(`/admin/messaging/messages/${eventId}/ignore`);
}
