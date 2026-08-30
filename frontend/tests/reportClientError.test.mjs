import assert from 'node:assert/strict';
import test from 'node:test';

import { reportClientError } from '../src/utils/reportClientError.js';

test('production error reporting is silent and sanitized', () => {
  const payloads = [];
  reportClientError(
    { name: 'AxiosError', message: 'request failed', config: { headers: { Authorization: 'secret' } } },
    'login',
    { development: false, sink: (payload) => payloads.push(payload) },
  );
  assert.deepEqual(payloads, []);
});

test('development error reporting exposes only safe fields to the injected sink', () => {
  let payload;
  reportClientError(
    { name: 'AxiosError', message: 'request failed', response: { data: { token: 'secret' } } },
    'login\nwith-secret',
    {
      development: true,
      sink: (value) => {
        payload = value;
      },
    },
  );
  assert.deepEqual(payload, {
    name: 'AxiosError',
    message: 'request failed',
    context: 'login with-secret',
  });
});
