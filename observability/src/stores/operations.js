import { defineStore } from 'pinia';

import { safeMessage } from '@/api/client.js';
import { fetchAlerts, fetchDependencies } from '@/api/observability.js';

export const useOperationsStore = defineStore('observability-operations', {
  state: () => ({ alerts: null, dependencies: null, loading: false, error: null }),
  actions: {
    async load(signal) {
      this.loading = true;
      this.error = null;
      try {
        const [alerts, dependencies] = await Promise.all([fetchAlerts(signal), fetchDependencies(signal)]);
        this.alerts = alerts;
        this.dependencies = dependencies;
      } catch (error) {
        if (error?.code !== 'ERR_CANCELED') this.error = safeMessage(error, '运行状态暂时不可用');
      } finally {
        this.loading = false;
      }
    },
  },
});
