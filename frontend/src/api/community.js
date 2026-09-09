import api from './client.js';

/** 分页读取首页已审核博客。 */
export function listHomeBlogs(params, http = api) {
  return http.get('/blogs/home', { params });
}

/** 分页读取全部已审核公开博客，错误交由调用页面处理。 */
export function listAllBlogs(params, http = api) {
  return http.get('/blogs/all', { params });
}

/** 分页读取公开博客列表。 */
export function listPublicBlogs(params, http = api) {
  return http.get('/blogs/list/1', { params });
}

/** 按已编码的博客 ID 读取详情，可见性由后端判定。 */
export function getBlog(id, http = api) {
  return http.get(`/blogs/${encodeURIComponent(id)}`);
}

/** 记录一次博客阅读点击；调用方不据此推断事务或最终计数。 */
export function incrementBlogClick(id, http = api) {
  return http.put(`/blogs/click/${id}`);
}

/** 分页读取当前作者的博客列表。 */
export function listMyBlogs(params, http = api) {
  return http.get('/blogs/myBlogs', { params });
}

/** 请求删除指定博客；后端校验作者身份，页面负责刷新列表。 */
export function deleteBlog(id, http = api) {
  return http.delete('/blogs/delete', { params: { blogId: id } });
}

/** 读取当前用户草稿；没有草稿时由页面处理空结果。 */
export function getDraft(http = api) {
  return http.get('/blogs/draft');
}

/** 提交博客进入审核流程；成功提交会删除当前用户草稿，并登记异步审核通知。 */
export function submitBlog(payload, http = api) {
  return http.post('/blogs/submit', payload);
}

/** 保存或更新当前用户草稿。 */
export function saveDraft(payload, http = api) {
  return http.put('/blogs/draft', payload);
}

/** 请求删除当前用户草稿，页面负责清空本地编辑状态。 */
export function removeDraft(http = api) {
  return http.delete('/blogs/draft');
}

/** 上传博客编辑器图片并返回公开地址；调用方负责把地址写入当前正文。 */
export function uploadBlogImage(formData, http = api) {
  return http.post('/blogs/uploadImg', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 分页读取管理员待审核博客。 */
export function listPendingBlogs(params, http = api) {
  return http.get('/blogs/examine', { params });
}

/** 提交指定博客的审核动作；后端更新审核状态并登记作者通知。 */
export function reviewBlog(action, id, http = api) {
  return http.put(`/blogs/${action}/${id}`);
}
