<template>
  <section class="page-feedback" :aria-busy="loading">
    <div v-if="loading" class="page-feedback__loading" role="status" aria-live="polite">
      <span class="sr-only">正在加载内容</span>
      <el-skeleton :rows="4" animated />
    </div>

    <el-alert
      v-else-if="error"
      class="page-feedback__alert"
      title="加载失败"
      :description="error"
      type="error"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button type="primary" plain @click="$emit('retry')">
          {{ retryText }}
        </el-button>
      </template>
    </el-alert>

    <el-empty v-else-if="empty" :description="emptyDescription || emptyTitle" class="page-feedback__empty">
      <template #description>
        <h3>{{ emptyTitle }}</h3>
        <p v-if="emptyDescription">{{ emptyDescription }}</p>
      </template>
    </el-empty>

    <slot v-else />
  </section>
</template>

<script setup>
defineProps({
  loading: {
    type: Boolean,
    default: false,
  },
  empty: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: '',
  },
  emptyTitle: {
    type: String,
    default: '暂无内容',
  },
  emptyDescription: {
    type: String,
    default: '',
  },
  retryText: {
    type: String,
    default: '重新加载',
  },
});

defineEmits(['retry']);
</script>

<style scoped>
.page-feedback {
  min-width: 0;
}

.page-feedback__loading,
.page-feedback__empty,
.page-feedback__alert {
  box-sizing: border-box;
  width: 100%;
  min-height: 180px;
  padding: 24px;
  border: 1px solid var(--cc4c-border);
  border-radius: var(--cc4c-radius);
  background: var(--cc4c-surface);
}

.page-feedback__empty :deep(h3) {
  margin: 0;
  color: var(--cc4c-text);
  font-size: 1.05rem;
}

.page-feedback__empty :deep(p) {
  margin: 8px 0 0;
  color: var(--cc4c-muted);
}

@media (max-width: 480px) {
  .page-feedback__loading,
  .page-feedback__empty,
  .page-feedback__alert {
    padding: 16px;
  }
}
</style>
