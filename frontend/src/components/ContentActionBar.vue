<template>
  <div class="content-action-bar" aria-label="内容操作">
    <el-button :type="collected ? 'warning' : 'default'" @click="handleCollect">
      <el-icon><StarFilled v-if="collected" /><Star v-else /></el-icon>
      {{ collected ? '已收藏' : `收藏${contentType}` }}
    </el-button>
    <el-button type="primary" plain @click="$emit('toggle-comment')">
      <el-icon><ChatDotRound /></el-icon>
      {{ commentOpen ? '收起评论' : '查看评论' }}
    </el-button>
  </div>
</template>

<script setup>
/** 内容操作栏组件，呈现收藏与评论入口并转发用户操作。 */
import { ChatDotRound, Star, StarFilled } from '@element-plus/icons-vue';

const props = defineProps({
  collected: { type: Boolean, default: false },
  loggedIn: { type: Boolean, default: false },
  commentOpen: { type: Boolean, default: false },
  contentType: { type: String, default: '课程' },
});

const emit = defineEmits(['toggle-collect', 'toggle-comment', 'require-login']);

/** 未登录时通知父页面请求登录，否则转发收藏切换事件；组件不直接写入收藏。 */
function handleCollect() {
  if (!props.loggedIn) {
    emit('require-login');
    return;
  }
  emit('toggle-collect');
}
</script>

<style scoped>
.content-action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.content-action-bar :deep(.el-button) {
  min-height: 38px;
}
@media (max-width: 480px) {
  .content-action-bar {
    width: 100%;
  }
  .content-action-bar :deep(.el-button) {
    flex: 1;
    margin: 0;
  }
}
</style>
