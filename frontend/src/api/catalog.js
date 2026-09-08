import api from './client.js';

/** 只读查询 listHomeCourses 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listHomeCourses(params, http = api) {
  return http.get('/courses/home', { params });
}

/** 只读查询 listCoursesByLanguage 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listCoursesByLanguage(language, params, http = api) {
  return http.get(`/courses/language/${encodeURIComponent(language)}`, { params });
}

/** 只读查询 searchCourses 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function searchCourses(query, params, http = api) {
  return http.get(`/courses/search/${encodeURIComponent(query)}`, { params });
}

/** 只读查询 getCourse 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getCourse(name, http = api) {
  return http.get(`/courses/${encodeURIComponent(name)}`);
}

/** 只读查询 getRecommendedCourse 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function getRecommendedCourse(languageNo, major, http = api) {
  return http.get(`/courses/recommend/${languageNo}/${major}`);
}

/** 只读查询 listModules 对应的业务数据；浏览器自动携带 Session，错误交由调用页面呈现。 */
export function listModules(languageId, http = api) {
  return http.get(`/courses/module/${languageId}`);
}

/** 提交 createModule 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function createModule(payload, http = api) {
  return http.post('/courses/module', payload);
}

/** 提交 createCourse 对应的业务写入；统一客户端负责凭据与 CSRF，调用方负责刷新受影响状态。 */
export function createCourse(payload, http = api) {
  return http.post('/courses/add', payload);
}
