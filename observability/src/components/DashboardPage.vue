<script setup>
import { watch } from 'vue';

import ChartPanel from '@/components/ChartPanel.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import TimeRangeToolbar from '@/components/TimeRangeToolbar.vue';
import { usePolling } from '@/composables/usePolling.js';
import { useDashboardStore } from '@/stores/dashboard.js';

const props = defineProps({
  dashboardId: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
});

const store = useDashboardStore();
const range = defineModel('range', { type: String, default: '1h' });
const polling = usePolling((signal) => store.load(props.dashboardId, range.value, signal));

watch(range, () => void polling.refresh(true));
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <StatusBadge v-if="store.data" :status="store.data.sourceStatus" />
    </div>
    <TimeRangeToolbar
      v-model="range"
      :paused="polling.paused.value"
      :refreshing="polling.refreshing.value"
      :last-updated-at="polling.lastUpdatedAt.value"
      @refresh="polling.refresh()"
      @toggle-pause="polling.togglePause"
    />
    <div v-if="store.error" class="feedback error surface-card" role="alert">{{ store.error }}</div>
    <div v-else-if="!store.data && store.loading" class="feedback surface-card">正在读取固定观测指标…</div>
    <div v-else-if="store.data" class="chart-grid">
      <ChartPanel v-for="panel in store.data.panels" :key="panel.id" :panel="panel" />
    </div>
  </section>
</template>

<style scoped>
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 1050px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
