import api from './client.js';

/** 只读查询 listHomeBlogs 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listHomeBlogs(params, http = api) {
  return http.get('/blogs/home', { params });
}

/** 只读查询 listAllBlogs 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listAllBlogs(params, http = api) {
  return http.get('/blogs/all', { params });
}

/** 只读查询 listPublicBlogs 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listPublicBlogs(params, http = api) {
  return http.get('/blogs/list/1', { params });
}

/** 只读查询 getBlog 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getBlog(id, http = api) {
  return http.get(`/blogs/${encodeURIComponent(id)}`);
}

/** 记录一次博客阅读点击；调用方不据此推断事务或最终计数。 */
export function incrementBlogClick(id, http = api) {
  return http.put(`/blogs/click/${id}`);
}

/** 只读查询 listMyBlogs 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listMyBlogs(params, http = api) {
  return http.get('/blogs/myBlogs', { params });
}

/** 提交 deleteBlog 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function deleteBlog(id, http = api) {
  return http.delete('/blogs/delete', { params: { blogId: id } });
}

/** 只读查询 getDraft 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getDraft(http = api) {
  return http.get('/blogs/draft');
}

/** 提交 submitBlog 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function submitBlog(payload, http = api) {
  return http.post('/blogs/submit', payload);
}

/** 提交 saveDraft 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function saveDraft(payload, http = api) {
  return http.put('/blogs/draft', payload);
}

/** 提交 removeDraft 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function removeDraft(http = api) {
  return http.delete('/blogs/draft');
}

/** 上传博客编辑器图片并返回公开地址；调用方负责把地址写入当前正文。 */
export function uploadBlogImage(formData, http = api) {
  return http.post('/blogs/uploadImg', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 只读查询 listPendingBlogs 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listPendingBlogs(params, http = api) {
  return http.get('/blogs/examine', { params });
}

/** 提交 reviewBlog 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function reviewBlog(action, id, http = api) {
  return http.put(`/blogs/${action}/${id}`);
}
