<script setup>
import MetricCard from '@/components/MetricCard.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import TimeRangeToolbar from '@/components/TimeRangeToolbar.vue';
import { usePolling } from '@/composables/usePolling.js';
import { useOverviewStore } from '@/stores/overview.js';

const store = useOverviewStore();
const polling = usePolling((signal) => store.load(signal));
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>运行总览</h1>
        <p>八项关键指标由后端固定查询生成，浏览器不会接收 Prometheus 地址或任意 PromQL 能力。</p>
      </div>
      <StatusBadge v-if="store.data" :status="store.data.sourceStatus" />
    </div>
    <TimeRangeToolbar
      model-value="1h"
      :show-range="false"
      :paused="polling.paused.value"
      :refreshing="polling.refreshing.value"
      :last-updated-at="polling.lastUpdatedAt.value"
      @refresh="polling.refresh()"
      @toggle-pause="polling.togglePause"
    />
    <div v-if="store.error" class="feedback error surface-card" role="alert">{{ store.error }}</div>
    <div v-else-if="!store.data && store.loading" class="feedback surface-card">正在读取运行总览…</div>
    <div v-else-if="store.data" class="metric-grid">
      <MetricCard v-for="metric in store.data.metrics" :key="metric.id" :metric="metric" />
    </div>
  </section>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
