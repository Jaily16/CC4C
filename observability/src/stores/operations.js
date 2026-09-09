import { defineStore } from 'pinia';

import { safeMessage } from '@/api/client.js';
import { fetchAlerts, fetchDependencies } from '@/api/observability.js';

/** 共同维护告警与依赖响应，以及这组查询的加载和错误状态。 */
export const useOperationsStore = defineStore('observability-operations', {
  state: () => ({ alerts: null, dependencies: null, loading: false, error: null }),
  actions: {
    /** 并行查询告警与依赖，两者成功后共同更新；取消静默处理，失败保留原响应并记录提示。 */
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
