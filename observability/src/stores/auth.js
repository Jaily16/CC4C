import { defineStore } from 'pinia';

import * as authApi from '@/api/auth.js';
import { safeMessage } from '@/api/client.js';

/** 维护独立观测身份、会话到期时间和登录错误；不复用业务端的用户或管理员状态。 */
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
    /** 采用服务端身份与两类到期时间；空响应清除内存身份视图。 */
    apply(session) {
      this.authenticated = Boolean(session?.authenticated);
      this.username = session?.username ?? null;
      this.idleExpiresAt = session?.idleExpiresAt ?? null;
      this.absoluteExpiresAt = session?.absoluteExpiresAt ?? null;
    },
    /** 按需恢复观测 Session，失败时清空身份并标记已恢复，避免守卫重复初始化。 */
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
    /** 提交观测登录后强制刷新 Session；请求异常清空身份、记录受控提示并交给页面处理。 */
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
    /** 请求注销观测 Session；失败记录并抛出错误，但始终清空本地身份和加载状态。 */
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
