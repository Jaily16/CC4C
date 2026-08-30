import api from './client.js';

export function listHomeCourses(params, http = api) {
  return http.get('/courses/home', { params });
}

export function listCoursesByLanguage(language, params, http = api) {
  return http.get(`/courses/language/${encodeURIComponent(language)}`, { params });
}

export function searchCourses(query, params, http = api) {
  return http.get(`/courses/search/${encodeURIComponent(query)}`, { params });
}

export function getCourse(name, http = api) {
  return http.get(`/courses/${encodeURIComponent(name)}`);
}

export function getRecommendedCourse(languageNo, major, http = api) {
  return http.get(`/courses/recommend/${languageNo}/${major}`);
}

export function listModules(languageId, http = api) {
  return http.get(`/courses/module/${languageId}`);
}

export function createModule(payload, http = api) {
  return http.post('/courses/module', payload);
}

export function createCourse(payload, http = api) {
  return http.post('/courses/add', payload);
}
