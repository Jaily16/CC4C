import api from './client.js';

export function getSession(http = api) {
  return http.get('/auth/session');
}

export function loginUser(payload, http = api) {
  return http.post('/users/login', payload);
}

export function requestVerificationCode(payload, http = api) {
  return http.post('/users/email', payload);
}

export function registerUser(payload, http = api) {
  return http.post('/users', payload);
}

export function resetPassword(payload, http = api) {
  return http.put('/users/password/forget', payload);
}

export function logoutUser(http = api) {
  return http.post('/users/logout');
}

export function loginAdmin(payload, http = api) {
  return http.post('/admin/login', payload);
}

export function logoutAdmin(http = api) {
  return http.post('/admin/logout');
}

export function updateAdminPassword(payload, http = api) {
  return http.put('/admin/password', payload);
}
