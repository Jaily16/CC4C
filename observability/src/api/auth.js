import client, { unwrap } from './client.js';

let csrfToken = null;

async function csrfHeader() {
  if (!csrfToken) {
    const data = unwrap(await client.get('/observability/auth/csrf'));
    csrfToken = data.token;
  }
  return { 'X-CC4C-OBSERVABILITY-CSRF': csrfToken };
}

function forgetCsrf() {
  csrfToken = null;
}

export async function fetchSession(signal) {
  return unwrap(await client.get('/observability/auth/session', { signal }));
}

export async function login(credentials) {
  const headers = await csrfHeader();
  try {
    return unwrap(await client.post('/observability/auth/login', credentials, { headers }));
  } finally {
    forgetCsrf();
  }
}

export async function logout() {
  const headers = await csrfHeader();
  try {
    return unwrap(await client.post('/observability/auth/logout', null, { headers }));
  } finally {
    forgetCsrf();
  }
}
