import api from './client.js';

export function listMessages(params, http = api) {
  return http.get('/admin/messaging/messages', { params });
}

export function retryMessage(eventId, http = api) {
  return http.post(`/admin/messaging/messages/${eventId}/retry`);
}

export function ignoreMessage(eventId, http = api) {
  return http.post(`/admin/messaging/messages/${eventId}/ignore`);
}
