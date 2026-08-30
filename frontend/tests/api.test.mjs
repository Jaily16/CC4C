import assert from 'node:assert/strict';
import test from 'node:test';

import {
  getSession,
  loginUser,
  requestVerificationCode,
  registerUser,
  resetPassword,
  logoutUser,
  loginAdmin,
  logoutAdmin,
  updateAdminPassword,
} from '../src/api/auth.js';
import { getCurrentUser, uploadAvatar, updateProfile, changePassword } from '../src/api/profile.js';
import {
  listHomeCourses,
  listCoursesByLanguage,
  searchCourses,
  getCourse,
  getRecommendedCourse,
  listModules,
  createModule,
  createCourse,
} from '../src/api/catalog.js';
import {
  listHomeBlogs,
  listAllBlogs,
  listPublicBlogs,
  getBlog,
  incrementBlogClick,
  listMyBlogs,
  getDraft,
  submitBlog,
  saveDraft,
  removeDraft,
  uploadBlogImage,
  listPendingBlogs,
  reviewBlog,
} from '../src/api/community.js';
import {
  getCourseComments,
  getBlogComments,
  getCourseFavoriteState,
  addCourseFavorite,
  removeCourseFavorite,
  getBlogFavoriteState,
  addBlogFavorite,
  removeBlogFavorite,
  listCourseFavorites,
  listBlogFavorites,
  createCourseComment,
  createBlogComment,
  createReply,
} from '../src/api/interactions.js';
import { listMessages, retryMessage, ignoreMessage } from '../src/api/messaging.js';

function fakeHttp() {
  const calls = [];
  const http = {};
  for (const method of ['get', 'post', 'put', 'delete']) {
    http[method] = (...args) => {
      calls.push({ method, args });
      return Promise.resolve({ data: { ok: true } });
    };
  }
  return { http, calls };
}

test('auth and profile wrappers forward payloads and paths unchanged', async () => {
  const { http, calls } = fakeHttp();
  const payload = { email: 'user@example.invalid', password: 'secret' };
  const form = { file: 'avatar' };

  await getSession(http);
  await loginUser(payload, http);
  await requestVerificationCode({ email: payload.email, purpose: 'REGISTER' }, http);
  await registerUser({ name: 'User' }, http);
  await resetPassword({ email: payload.email, verificationCode: '123456', newPassword: 'new-secret' }, http);
  await logoutUser(http);
  await loginAdmin({ adminId: 'admin', adminPassword: 'secret' }, http);
  await logoutAdmin(http);
  await updateAdminPassword({ password: 'old', newPassword: 'new' }, http);
  await getCurrentUser(http);
  await uploadAvatar(form, http);
  await updateProfile({ name: 'User', major: 1 }, http);
  await changePassword({ password: 'old', newPassword: 'new' }, http);

  assert.deepEqual(calls, [
    { method: 'get', args: ['/auth/session'] },
    { method: 'post', args: ['/users/login', payload] },
    { method: 'post', args: ['/users/email', { email: payload.email, purpose: 'REGISTER' }] },
    { method: 'post', args: ['/users', { name: 'User' }] },
    {
      method: 'put',
      args: ['/users/password/forget', { email: payload.email, verificationCode: '123456', newPassword: 'new-secret' }],
    },
    { method: 'post', args: ['/users/logout'] },
    { method: 'post', args: ['/admin/login', { adminId: 'admin', adminPassword: 'secret' }] },
    { method: 'post', args: ['/admin/logout'] },
    { method: 'put', args: ['/admin/password', { password: 'old', newPassword: 'new' }] },
    { method: 'get', args: ['/users/me'] },
    { method: 'post', args: ['/users/me/avatar', form, { headers: { 'Content-Type': 'multipart/form-data' } }] },
    { method: 'put', args: ['/users/me', { name: 'User', major: 1 }] },
    { method: 'put', args: ['/users/me/password', { password: 'old', newPassword: 'new' }] },
  ]);
});

test('catalog, community, interaction and messaging wrappers preserve request shapes', async () => {
  const { http, calls } = fakeHttp();
  const params = { page: 2, size: 10 };
  const form = { file: 'image' };

  await listHomeCourses(params, http);
  await listCoursesByLanguage('C++/入门', params, http);
  await searchCourses('Spring Boot/3', params, http);
  await getCourse('C++/入门', http);
  await getRecommendedCourse(1, -1, http);
  await listModules(1, http);
  await createModule({ moduleName: 'Basics' }, http);
  await createCourse({ courseName: 'CC4C' }, http);
  await listHomeBlogs(params, http);
  await listAllBlogs(params, http);
  await listPublicBlogs(params, http);
  await getBlog('博客/1', http);
  await incrementBlogClick(7, http);
  await listMyBlogs(params, http);
  await getDraft(http);
  await submitBlog({ title: 'Title' }, http);
  await saveDraft({ content: 'Draft' }, http);
  await removeDraft(http);
  await uploadBlogImage(form, http);
  await listPendingBlogs(params, http);
  await reviewBlog('approve', 7, http);
  await getCourseComments(1, params, http);
  await getBlogComments(2, params, http);
  await getCourseFavoriteState(1, http);
  await addCourseFavorite(1, http);
  await removeCourseFavorite(1, http);
  await getBlogFavoriteState(2, http);
  await addBlogFavorite(2, http);
  await removeBlogFavorite(2, http);
  await listCourseFavorites(params, http);
  await listBlogFavorites(params, http);
  await createCourseComment({ content: 'course' }, http);
  await createBlogComment({ content: 'blog' }, http);
  await createReply({ content: 'reply', fatherId: 1 }, http);
  await listMessages({ page: 1, size: 20, status: 'FAILED' }, http);
  await retryMessage('evt/1', http);
  await ignoreMessage('evt/2', http);

  assert.deepEqual(calls, [
    { method: 'get', args: ['/courses/home', { params }] },
    { method: 'get', args: ['/courses/language/C%2B%2B%2F%E5%85%A5%E9%97%A8', { params }] },
    { method: 'get', args: ['/courses/search/Spring%20Boot%2F3', { params }] },
    { method: 'get', args: ['/courses/C%2B%2B%2F%E5%85%A5%E9%97%A8'] },
    { method: 'get', args: ['/courses/recommend/1/-1'] },
    { method: 'get', args: ['/courses/module/1'] },
    { method: 'post', args: ['/courses/module', { moduleName: 'Basics' }] },
    { method: 'post', args: ['/courses/add', { courseName: 'CC4C' }] },
    { method: 'get', args: ['/blogs/home', { params }] },
    { method: 'get', args: ['/blogs/all', { params }] },
    { method: 'get', args: ['/blogs/list/1', { params }] },
    { method: 'get', args: ['/blogs/%E5%8D%9A%E5%AE%A2%2F1'] },
    { method: 'put', args: ['/blogs/click/7'] },
    { method: 'get', args: ['/blogs/myBlogs', { params }] },
    { method: 'get', args: ['/blogs/draft'] },
    { method: 'post', args: ['/blogs/submit', { title: 'Title' }] },
    { method: 'put', args: ['/blogs/draft', { content: 'Draft' }] },
    { method: 'delete', args: ['/blogs/draft'] },
    { method: 'post', args: ['/blogs/uploadImg', form, { headers: { 'Content-Type': 'multipart/form-data' } }] },
    { method: 'get', args: ['/blogs/examine', { params }] },
    { method: 'put', args: ['/blogs/approve/7'] },
    { method: 'get', args: ['/comments/course/1', { params }] },
    { method: 'get', args: ['/comments/blog/2', { params }] },
    { method: 'get', args: ['/courses/star/1'] },
    { method: 'post', args: ['/courses/star/1'] },
    { method: 'delete', args: ['/courses/star/1'] },
    { method: 'get', args: ['/blogs/collect/2'] },
    { method: 'post', args: ['/blogs/collect/2'] },
    { method: 'delete', args: ['/blogs/collect/2'] },
    { method: 'get', args: ['/courses/star', { params }] },
    { method: 'get', args: ['/blogs/collect', { params }] },
    { method: 'post', args: ['/comments/course', { content: 'course' }] },
    { method: 'post', args: ['/comments/blog', { content: 'blog' }] },
    { method: 'post', args: ['/comments/indirect', { content: 'reply', fatherId: 1 }] },
    { method: 'get', args: ['/admin/messaging/messages', { params: { page: 1, size: 20, status: 'FAILED' } }] },
    { method: 'post', args: ['/admin/messaging/messages/evt/1/retry'] },
    { method: 'post', args: ['/admin/messaging/messages/evt/2/ignore'] },
  ]);
});
