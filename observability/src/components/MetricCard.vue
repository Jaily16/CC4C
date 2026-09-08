<script setup>
/** 观测指标卡片，格式化单项总览值并呈现数据可用状态。 */
import { computed } from 'vue';

import StatusBadge from './StatusBadge.vue';

const props = defineProps({ metric: { type: Object, required: true } });

/** 从现有响应式状态派生 formattedValue，不发起请求或写入外部数据。 */
const formattedValue = computed(() => {
  const value = props.metric.value;
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  if (props.metric.unit === 'ratio') return `${(value * 100).toFixed(2)}%`;
  if (props.metric.unit === 'bytes') {
    const units = ['B', 'KiB', 'MiB', 'GiB'];
    let current = value;
    let index = 0;
    while (current >= 1024 && index < units.length - 1) {
      current /= 1024;
      index += 1;
    }
    return `${current.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
  }
  if (props.metric.unit === '状态') return value === 1 ? '在线' : '离线';
  return `${value.toLocaleString('zh-CN', { maximumFractionDigits: 3 })}${props.metric.unit ? ` ${props.metric.unit}` : ''}`;
});
</script>

<template>
  <article class="metric-card surface-card">
    <div class="metric-header">
      <h2>{{ metric.title }}</h2>
      <StatusBadge :status="metric.status" />
    </div>
    <strong>{{ formattedValue }}</strong>
    <p v-if="metric.message">{{ metric.message }}</p>
  </article>
</template>

<style scoped>
.metric-card {
  min-height: 150px;
  padding: 20px;
}

.metric-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--cc4c-muted);
}

strong {
  display: block;
  margin-top: 26px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

p {
  margin: 10px 0 0;
  color: var(--cc4c-muted);
  font-size: 13px;
}
</style>
