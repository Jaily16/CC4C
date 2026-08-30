import api from './client.js';

export function listHomeBlogs(params, http = api) {
  return http.get('/blogs/home', { params });
}

export function listAllBlogs(params, http = api) {
  return http.get('/blogs/all', { params });
}

export function listPublicBlogs(params, http = api) {
  return http.get('/blogs/list/1', { params });
}

export function getBlog(id, http = api) {
  return http.get(`/blogs/${encodeURIComponent(id)}`);
}

export function incrementBlogClick(id, http = api) {
  return http.put(`/blogs/click/${id}`);
}

export function listMyBlogs(params, http = api) {
  return http.get('/blogs/myBlogs', { params });
}

export function getDraft(http = api) {
  return http.get('/blogs/draft');
}

export function submitBlog(payload, http = api) {
  return http.post('/blogs/submit', payload);
}

export function saveDraft(payload, http = api) {
  return http.put('/blogs/draft', payload);
}

export function removeDraft(http = api) {
  return http.delete('/blogs/draft');
}

export function uploadBlogImage(formData, http = api) {
  return http.post('/blogs/uploadImg', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function listPendingBlogs(params, http = api) {
  return http.get('/blogs/examine', { params });
}

export function reviewBlog(action, id, http = api) {
  return http.put(`/blogs/${action}/${id}`);
}
