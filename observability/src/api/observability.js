import client, { unwrap } from './client.js';

export async function fetchOverview(signal) {
  return unwrap(await client.get('/observability/api/overview', { signal }));
}

export async function fetchDashboard(dashboardId, range, signal) {
  return unwrap(
    await client.get(`/observability/api/dashboard/${encodeURIComponent(dashboardId)}`, {
      params: { range },
      signal,
    }),
  );
}

export async function fetchAlerts(signal) {
  return unwrap(await client.get('/observability/api/alerts', { signal }));
}

export async function fetchDependencies(signal) {
  return unwrap(await client.get('/observability/api/dependencies', { signal }));
}
