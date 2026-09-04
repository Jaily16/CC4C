import api from './client.js';

export function getCourseComments(id, params, http = api) {
  return http.get(`/comments/course/${id}`, { params });
}

export function getBlogComments(id, params, http = api) {
  return http.get(`/comments/blog/${id}`, { params });
}

export function getCourseFavoriteState(id, http = api) {
  return http.get(`/courses/star/${id}`);
}

export function addCourseFavorite(id, http = api) {
  return http.post(`/courses/star/${id}`);
}

export function removeCourseFavorite(id, http = api) {
  return http.delete(`/courses/star/${id}`);
}

export function getBlogFavoriteState(id, http = api) {
  return http.get(`/blogs/collect/${id}`);
}

export function addBlogFavorite(id, http = api) {
  return http.post(`/blogs/collect/${id}`);
}

export function removeBlogFavorite(id, http = api) {
  return http.delete(`/blogs/collect/${id}`);
}

export function listCourseFavorites(params, http = api) {
  return http.get('/courses/star', { params });
}

export function listBlogFavorites(params, http = api) {
  return http.get('/blogs/collect', { params });
}

export function createCourseComment(payload, http = api) {
  return http.post('/comments/course', payload);
}

export function createBlogComment(payload, http = api) {
  return http.post('/comments/blog', payload);
}

export function createReply(payload, http = api) {
  return http.post('/comments/indirect', payload);
}

/** 复用现有评论删除接口；只传评论 ID，作者身份由后端会话校验，不由客户端指定。 */
export function deleteComment(id, http = api) {
  return http.delete(`/comments/${encodeURIComponent(id)}`);
}
