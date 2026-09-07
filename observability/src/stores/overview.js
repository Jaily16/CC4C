import { defineStore } from 'pinia';

import { safeMessage } from '@/api/client.js';
import { fetchOverview } from '@/api/observability.js';

export const useOverviewStore = defineStore('observability-overview', {
  state: () => ({ data: null, loading: false, error: null }),
  actions: {
    async load(signal) {
      this.loading = true;
      this.error = null;
      try {
        this.data = await fetchOverview(signal);
      } catch (error) {
        if (error?.code !== 'ERR_CANCELED') this.error = safeMessage(error, '总览数据暂时不可用');
      } finally {
        this.loading = false;
      }
    },
  },
});
