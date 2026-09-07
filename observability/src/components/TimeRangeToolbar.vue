<script setup>
/** 观测时间工具栏，提供固定范围、刷新及暂停控制。 */
import { ElButton, ElRadioButton, ElRadioGroup } from 'element-plus';

defineProps({
  modelValue: { type: String, required: true },
  paused: { type: Boolean, default: false },
  refreshing: { type: Boolean, default: false },
  lastUpdatedAt: { type: Date, default: null },
  showRange: { type: Boolean, default: true },
});

const emit = defineEmits(['update:modelValue', 'refresh', 'toggle-pause']);
</script>

<template>
  <div class="toolbar surface-card" aria-label="观测刷新控制">
    <ElRadioGroup
      v-if="showRange"
      :model-value="modelValue"
      size="small"
      aria-label="时间范围"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <ElRadioButton value="15m">15 分钟</ElRadioButton>
      <ElRadioButton value="1h">1 小时</ElRadioButton>
      <ElRadioButton value="6h">6 小时</ElRadioButton>
      <ElRadioButton value="24h">24 小时</ElRadioButton>
    </ElRadioGroup>
    <div class="toolbar-actions">
      <span class="updated">{{
        lastUpdatedAt ? `更新于 ${lastUpdatedAt.toLocaleTimeString('zh-CN')}` : '尚未更新'
      }}</span>
      <ElButton size="small" :disabled="refreshing" @click="emit('toggle-pause')">
        {{ paused ? '继续自动刷新' : '暂停自动刷新' }}
      </ElButton>
      <ElButton type="primary" size="small" :loading="refreshing" @click="emit('refresh')">立即刷新</ElButton>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  margin-bottom: 20px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.updated {
  color: var(--cc4c-muted);
  font-size: 13px;
}

@media (max-width: 880px) {
  .toolbar,
  .toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
