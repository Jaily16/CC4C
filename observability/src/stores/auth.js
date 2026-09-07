import { defineStore } from 'pinia';

import * as authApi from '@/api/auth.js';
import { safeMessage } from '@/api/client.js';

/** useAuthStore 集中维护本领域的加载、成功、失败和会话状态，供页面共享。 */
export const useAuthStore = defineStore('observability-auth', {
  state: () => ({
    hydrated: false,
    loading: false,
    authenticated: false,
    username: null,
    idleExpiresAt: null,
    absoluteExpiresAt: null,
    error: null,
  }),
  actions: {
    apply(session) {
      this.authenticated = Boolean(session?.authenticated);
      this.username = session?.username ?? null;
      this.idleExpiresAt = session?.idleExpiresAt ?? null;
      this.absoluteExpiresAt = session?.absoluteExpiresAt ?? null;
    },
    async hydrate(force = false) {
      if (this.hydrated && !force) return;
      this.loading = true;
      try {
        this.apply(await authApi.fetchSession());
      } catch {
        this.apply(null);
      } finally {
        this.hydrated = true;
        this.loading = false;
      }
    },
    async login(credentials) {
      this.loading = true;
      this.error = null;
      try {
        await authApi.login(credentials);
        this.hydrated = false;
        await this.hydrate(true);
      } catch (error) {
        this.apply(null);
        this.error = error?.response?.status === 401 ? '账号或密码错误' : safeMessage(error, '账号或密码错误');
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async logout() {
      this.loading = true;
      this.error = null;
      try {
        await authApi.logout();
      } catch (error) {
        this.error = safeMessage(error, '退出失败，请重试');
        throw error;
      } finally {
        this.apply(null);
        this.hydrated = true;
        this.loading = false;
      }
    },
  },
});
