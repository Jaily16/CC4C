import api from './client.js';

export function getCurrentUser(http = api) {
  return http.get('/users/me');
}

export function uploadAvatar(formData, http = api) {
  return http.post('/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function updateProfile(payload, http = api) {
  return http.put('/users/me', payload);
}

export function changePassword(payload, http = api) {
  return http.put('/users/me/password', payload);
}
