import api from './client.js';

/** 只读查询 listMessages 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listMessages(params, http = api) {
  return http.get('/admin/messaging/messages', { params });
}

/** 请求管理员重试明确选中的异步消息，成功后刷新该消息状态。 */
export function retryMessage(eventId, http = api) {
  return http.post(`/admin/messaging/messages/${eventId}/retry`);
}

/** 请求管理员忽略明确选中的异步消息，成功后刷新该消息状态。 */
export function ignoreMessage(eventId, http = api) {
  return http.post(`/admin/messaging/messages/${eventId}/ignore`);
}
