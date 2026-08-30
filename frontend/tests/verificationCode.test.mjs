import assert from 'node:assert/strict';
import test from 'node:test';

import { useVerificationCode } from '../src/composables/useVerificationCode.js';

test('verification request builds payload once and starts cooldown on success', async () => {
  const originalSetInterval = globalThis.setInterval;
  const originalClearInterval = globalThis.clearInterval;
  const timers = [];
  globalThis.setInterval = (callback) => {
    timers.push(callback);
    return timers.length;
  };
  globalThis.clearInterval = () => {};
  try {
    const requests = [];
    const code = useVerificationCode({
      requestCode: async (payload) => {
        requests.push(payload);
        return { data: { data: true } };
      },
      buildRequest: () => ({ email: 'user@example.com', purpose: 'REGISTER' }),
      cooldownSeconds: 60,
    });
    await code.request();
    assert.deepEqual(requests, [{ email: 'user@example.com', purpose: 'REGISTER' }]);
    assert.equal(code.countdown.value, 60);
    assert.equal(await code.request(), null);
    timers[0]();
    assert.equal(code.countdown.value, 59);
  } finally {
    globalThis.setInterval = originalSetInterval;
    globalThis.clearInterval = originalClearInterval;
  }
});

test('failed verification response does not start cooldown', async () => {
  const code = useVerificationCode({
    requestCode: async () => ({ data: { data: false } }),
    buildRequest: () => ({ email: 'user@example.com' }),
  });
  await code.request();
  assert.equal(code.countdown.value, 0);
  assert.equal(code.sending.value, false);
});
