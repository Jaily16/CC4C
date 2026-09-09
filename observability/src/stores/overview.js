import { defineStore } from 'pinia';

import { safeMessage } from '@/api/client.js';
import { fetchOverview } from '@/api/observability.js';

/** 保存八项总览指标及加载错误，供总览页面和轮询流程共享。 */
export const useOverviewStore = defineStore('observability-overview', {
  state: () => ({ data: null, loading: false, error: null }),
  actions: {
    /** 刷新总览指标；取消不显示错误，失败保留上次数据并记录受控提示。 */
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
