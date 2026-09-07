import client, { unwrap } from './client.js';

/** 只读获取八项固定总览指标，浏览器不会接收 Prometheus 地址或查询语句。 */
export async function fetchOverview(signal) {
  return unwrap(await client.get('/observability/api/overview', { signal }));
}

/** 按固定仪表板和时间范围读取白名单时序数据，不允许浏览器提交 PromQL。 */
export async function fetchDashboard(dashboardId, range, signal) {
  return unwrap(
    await client.get(`/observability/api/dashboard/${encodeURIComponent(dashboardId)}`, {
      params: { range },
      signal,
    }),
  );
}

/** 只读获取二十条固定告警的脱敏状态。 */
export async function fetchAlerts(signal) {
  return unwrap(await client.get('/observability/api/alerts', { signal }));
}

/** 只读获取应用依赖、存活、就绪和请求关联的脱敏状态。 */
export async function fetchDependencies(signal) {
  return unwrap(await client.get('/observability/api/dependencies', { signal }));
}
