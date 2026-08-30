import assert from 'node:assert/strict';
import test from 'node:test';

globalThis.sessionStorage = {
  getItem() {
    return null;
  },
  setItem() {},
};

const { useCurrentUser } = await import('../src/composables/useCurrentUser.js');

test('current user composable exposes reactive role projections without side effects', () => {
  const admin = useCurrentUser({ authenticated: true, role: 'ADMIN', name: 'Admin' });
  assert.equal(admin.user.value.role, 'ADMIN');
  assert.equal(admin.role.value, 'ADMIN');
  assert.equal(admin.isAuthenticated.value, true);
  assert.equal(admin.isUser.value, false);
  assert.equal(admin.isAdmin.value, true);

  const user = useCurrentUser({ authenticated: true, role: 'USER' });
  assert.equal(user.isAuthenticated.value, true);
  assert.equal(user.isUser.value, true);
  assert.equal(user.isAdmin.value, false);

  const anonymous = useCurrentUser({ authenticated: false, role: '' });
  assert.equal(anonymous.isAuthenticated.value, false);
  assert.equal(anonymous.role.value, '');
  assert.equal(anonymous.isUser.value, false);
  assert.equal(anonymous.isAdmin.value, false);
});
