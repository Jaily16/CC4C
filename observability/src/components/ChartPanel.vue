<script setup>
/** 观测图表面板，将受控时序数据渲染为 ECharts，并提供文本替代状态。 */
import { LineChart } from 'echarts/charts';
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { init, use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import StatusBadge from './StatusBadge.vue';

use([LineChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps({ panel: { type: Object, required: true } });
const chartRoot = ref(null);
let chart = null;
let observer = null;

/** 取每条曲线的最后一个采样值供无障碍摘要使用；没有采样点时保留 null。 */
const latestValues = computed(() =>
  props.panel.series.map((series) => ({
    name: series.name,
    value: series.points.length ? series.points.at(-1).value : null,
  })),
);

/** 面板不可用时清空图表，否则按当前序列重建时间轴折线配置，连接空值且关闭动画。 */
function renderChart() {
  if (!chart || props.panel.status !== 'OK') {
    chart?.clear();
    return;
  }
  chart.setOption(
    {
      aria: { enabled: true, decal: { show: true }, description: `${props.panel.title}时序图` },
      animation: false,
      color: ['#2563eb', '#16a34a', '#ea580c', '#7c3aed', '#0891b2', '#be123c'],
      grid: { left: 58, right: 24, top: 54, bottom: 42 },
      legend: { type: 'scroll', top: 8, textStyle: { color: '#475569' } },
      tooltip: { trigger: 'axis', confine: true },
      xAxis: { type: 'time', axisLabel: { color: '#64748b' }, splitLine: { show: false } },
      yAxis: {
        type: 'value',
        name: props.panel.unit,
        axisLabel: { color: '#64748b' },
        splitLine: { lineStyle: { color: '#e2e8f0' } },
      },
      series: props.panel.series.map((series) => ({
        name: series.name,
        type: 'line',
        showSymbol: false,
        connectNulls: false,
        data: series.points.map((point) => [point.timestamp, point.value]),
      })),
    },
    { notMerge: true },
  );
}

/** 挂载时创建 Canvas 图表和尺寸观察器，首次绘图并随容器尺寸调整图表。 */
onMounted(() => {
  chart = init(chartRoot.value, null, { renderer: 'canvas' });
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(chartRoot.value);
  renderChart();
});

/** 深度监听面板数据，重新绘制曲线或清空不可用图表。 */
watch(() => props.panel, renderChart, { deep: true });

/** 卸载时断开尺寸观察器并销毁 ECharts 实例，释放绘图资源。 */
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <article class="chart-panel surface-card">
    <header>
      <div>
        <h2>{{ panel.title }}</h2>
        <span>{{ panel.unit }}</span>
      </div>
      <StatusBadge :status="panel.status" />
    </header>
    <div v-show="panel.status === 'OK'" ref="chartRoot" class="chart" role="img" :aria-label="`${panel.title}时序图`" />
    <div v-if="panel.status !== 'OK'" class="panel-state">{{ panel.message || '当前范围暂无数据' }}</div>
    <p v-else-if="panel.message" class="panel-note">{{ panel.message }}</p>
    <ul class="sr-only">
      <li v-for="series in latestValues" :key="series.name">
        {{ series.name }}最新值：{{ series.value ?? '无数据' }} {{ panel.unit }}
      </li>
    </ul>
  </article>
</template>

<style scoped>
.chart-panel {
  min-width: 0;
  padding: 18px;
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

h2 {
  margin: 0 0 5px;
  font-size: 17px;
}

header span {
  color: var(--cc4c-muted);
  font-size: 12px;
}

.chart {
  width: 100%;
  height: 320px;
  margin-top: 8px;
}

.panel-state {
  display: grid;
  min-height: 260px;
  place-items: center;
  color: var(--cc4c-muted);
}

.panel-note {
  margin: 8px 0 0;
  color: var(--cc4c-warning);
  font-size: 13px;
}
</style>
