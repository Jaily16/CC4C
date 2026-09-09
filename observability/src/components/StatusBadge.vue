<script setup>
/** 观测状态徽标，把有限状态映射为统一中文标签和视觉语义。 */
import { computed } from 'vue';

const props = defineProps({
  status: { type: String, default: 'UNKNOWN' },
});

/** 将输入状态转为大写，统一后续标签与颜色匹配。 */
const normalized = computed(() => props.status.toUpperCase());
/** 将已知运行和消息状态映射为中文标签；未识别状态显示未知。 */
const label = computed(() => {
  const labels = {
    AVAILABLE: '可用',
    PARTIAL: '部分可用',
    EMPTY: '暂无数据',
    UNAVAILABLE: '不可用',
    UP: '正常',
    DOWN: '不可用',
    OUT_OF_SERVICE: '停止服务',
    DEGRADED: '降级',
    UNKNOWN: '未知',
    FIRING: '触发中',
    PENDING: '等待确认',
    INACTIVE: '未触发',
    MISSING: '未载入',
    OK: '正常',
    ERR: '异常',
  };
  return labels[normalized.value] ?? '未知';
});

/** 正常状态使用成功色，部分可用或等待状态使用警示色，其余状态使用危险色。 */
const tone = computed(() => {
  if (['AVAILABLE', 'UP', 'INACTIVE', 'OK'].includes(normalized.value)) return 'success';
  if (['PARTIAL', 'EMPTY', 'DEGRADED', 'PENDING', 'UNKNOWN', 'MISSING'].includes(normalized.value)) return 'warning';
  return 'danger';
});
</script>

<template>
  <span class="status-badge" :class="tone">{{ label }}</span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.success {
  color: var(--cc4c-success);
  background: #dcfce7;
}

.warning {
  color: var(--cc4c-warning);
  background: #fef3c7;
}

.danger {
  color: var(--cc4c-danger);
  background: #fee2e2;
}
</style>
