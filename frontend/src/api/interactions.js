import api from './client.js';

/** 分页读取指定课程的评论树。 */
export function getCourseComments(id, params, http = api) {
  return http.get(`/comments/course/${id}`, { params });
}

/** 分页读取指定博客的评论树。 */
export function getBlogComments(id, params, http = api) {
  return http.get(`/comments/blog/${id}`, { params });
}

/** 读取当前用户是否收藏该课程。 */
export function getCourseFavoriteState(id, http = api) {
  return http.get(`/courses/star/${id}`);
}

/** 为当前用户收藏指定课程，页面在成功后更新收藏状态。 */
export function addCourseFavorite(id, http = api) {
  return http.post(`/courses/star/${id}`);
}

/** 取消当前用户对该课程的收藏。 */
export function removeCourseFavorite(id, http = api) {
  return http.delete(`/courses/star/${id}`);
}

/** 读取当前用户是否收藏该博客。 */
export function getBlogFavoriteState(id, http = api) {
  return http.get(`/blogs/collect/${id}`);
}

/** 为当前用户收藏指定博客。 */
export function addBlogFavorite(id, http = api) {
  return http.post(`/blogs/collect/${id}`);
}

/** 取消当前用户对该博客的收藏。 */
export function removeBlogFavorite(id, http = api) {
  return http.delete(`/blogs/collect/${id}`);
}

/** 分页读取当前用户收藏的课程。 */
export function listCourseFavorites(params, http = api) {
  return http.get('/courses/star', { params });
}

/** 分页读取当前用户收藏的博客。 */
export function listBlogFavorites(params, http = api) {
  return http.get('/blogs/collect', { params });
}

/** 向指定课程新增顶层评论，作者身份由 Session 确定。 */
export function createCourseComment(payload, http = api) {
  return http.post('/comments/course', payload);
}

/** 向指定博客新增顶层评论，作者身份由 Session 确定。 */
export function createBlogComment(payload, http = api) {
  return http.post('/comments/blog', payload);
}

/** 向指定父评论新增回复，页面在成功后刷新评论树。 */
export function createReply(payload, http = api) {
  return http.post('/comments/indirect', payload);
}

/** 复用现有评论删除接口；只传评论 ID，作者身份由后端会话校验，不由客户端指定。 */
export function deleteComment(id, http = api) {
  return http.delete(`/comments/${encodeURIComponent(id)}`);
}
