import api from './client.js';

/** 只读查询 getCourseComments 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getCourseComments(id, params, http = api) {
  return http.get(`/comments/course/${id}`, { params });
}

/** 只读查询 getBlogComments 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getBlogComments(id, params, http = api) {
  return http.get(`/comments/blog/${id}`, { params });
}

/** 只读查询 getCourseFavoriteState 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getCourseFavoriteState(id, http = api) {
  return http.get(`/courses/star/${id}`);
}

/** 提交 addCourseFavorite 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function addCourseFavorite(id, http = api) {
  return http.post(`/courses/star/${id}`);
}

/** 提交 removeCourseFavorite 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function removeCourseFavorite(id, http = api) {
  return http.delete(`/courses/star/${id}`);
}

/** 只读查询 getBlogFavoriteState 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getBlogFavoriteState(id, http = api) {
  return http.get(`/blogs/collect/${id}`);
}

/** 提交 addBlogFavorite 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function addBlogFavorite(id, http = api) {
  return http.post(`/blogs/collect/${id}`);
}

/** 提交 removeBlogFavorite 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function removeBlogFavorite(id, http = api) {
  return http.delete(`/blogs/collect/${id}`);
}

/** 只读查询 listCourseFavorites 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listCourseFavorites(params, http = api) {
  return http.get('/courses/star', { params });
}

/** 只读查询 listBlogFavorites 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listBlogFavorites(params, http = api) {
  return http.get('/blogs/collect', { params });
}

/** 提交 createCourseComment 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function createCourseComment(payload, http = api) {
  return http.post('/comments/course', payload);
}

/** 提交 createBlogComment 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function createBlogComment(payload, http = api) {
  return http.post('/comments/blog', payload);
}

/** 提交 createReply 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function createReply(payload, http = api) {
  return http.post('/comments/indirect', payload);
}

/** 复用现有评论删除接口；只传评论 ID，作者身份由后端会话校验，不由客户端指定。 */
export function deleteComment(id, http = api) {
  return http.delete(`/comments/${encodeURIComponent(id)}`);
}
