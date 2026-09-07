import { defineStore } from 'pinia';

import { safeMessage } from '@/api/client.js';
import { fetchDashboard } from '@/api/observability.js';

export const useDashboardStore = defineStore('observability-dashboard', {
  state: () => ({ data: null, loading: false, error: null }),
  actions: {
    async load(dashboardId, range, signal) {
      this.loading = true;
      this.error = null;
      try {
        this.data = await fetchDashboard(dashboardId, range, signal);
      } catch (error) {
        if (error?.code !== 'ERR_CANCELED') this.error = safeMessage(error, '面板数据暂时不可用');
      } finally {
        this.loading = false;
      }
    },
  },
});
