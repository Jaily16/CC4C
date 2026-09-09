import api from './client.js';

/** 分页读取首页课程；Session 由统一客户端携带，错误交由页面呈现。 */
export function listHomeCourses(params, http = api) {
  return http.get('/courses/home', { params });
}

/** 按已编码的语言名称分页读取课程。 */
export function listCoursesByLanguage(language, params, http = api) {
  return http.get(`/courses/language/${encodeURIComponent(language)}`, { params });
}

/** 按已编码的搜索词分页查询课程。 */
export function searchCourses(query, params, http = api) {
  return http.get(`/courses/search/${encodeURIComponent(query)}`, { params });
}

/** 按课程名称读取详情。 */
export function getCourse(name, http = api) {
  return http.get(`/courses/${encodeURIComponent(name)}`);
}

/** 按语言编号和专业读取推荐课程。 */
export function getRecommendedCourse(languageNo, major, http = api) {
  return http.get(`/courses/recommend/${languageNo}/${major}`);
}

/** 读取指定语言的课程模块。 */
export function listModules(languageId, http = api) {
  return http.get(`/courses/module/${languageId}`);
}

/** 提交新增课程模块；统一客户端处理 Session 和 CSRF，页面负责刷新模块列表。 */
export function createModule(payload, http = api) {
  return http.post('/courses/module', payload);
}

/** 提交课程及模块内容；统一客户端处理 Session 和 CSRF，页面负责成功后的跳转。 */
export function createCourse(payload, http = api) {
  return http.post('/courses/add', payload);
}
