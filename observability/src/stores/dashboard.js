import { defineStore } from 'pinia';

import { safeMessage } from '@/api/client.js';
import { fetchDashboard } from '@/api/observability.js';

/** 保存当前仪表板响应和加载错误，向页面共享最近一次成功取得的数据。 */
export const useDashboardStore = defineStore('observability-dashboard', {
  state: () => ({ data: null, loading: false, error: null }),
  actions: {
    /** 读取指定仪表板和时间范围；取消不显示错误，其他失败保留旧数据并记录受控提示。 */
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
