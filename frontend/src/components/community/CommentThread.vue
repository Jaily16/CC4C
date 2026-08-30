<template>
  <section class="comments" :aria-label="label">
    <div v-if="loggedIn" class="comment-compose">
      <el-avatar :size="38" :src="avatar || ''">{{ userInitial }}</el-avatar>
      <div class="comment-compose__input">
        <label class="sr-only" :for="commentInputId">发表评论</label>
        <el-input
          :id="commentInputId"
          :model-value="commentText"
          :rows="4"
          type="textarea"
          maxlength="1000"
          show-word-limit
          resize="none"
          :placeholder="commentPlaceholder"
          @update:model-value="emit('update:commentText', $event)"
        />
        <p v-if="commentInputError" class="form-error" role="alert">{{ commentInputError }}</p>
        <el-button type="primary" :loading="commentSubmitting" @click="emit('submit-comment')">发布评论</el-button>
      </div>
    </div>

    <el-alert v-else title="登录后即可发表评论或回复" type="info" :closable="false" show-icon>
      <template #default><el-button type="primary" plain @click="emit('login')">前往登录</el-button></template>
    </el-alert>

    <div v-if="commentsLoading" class="comments__state" role="status"><el-skeleton :rows="3" animated /></div>
    <el-alert
      v-else-if="commentsError"
      title="评论加载失败"
      :description="commentsError"
      type="error"
      :closable="false"
      show-icon
    >
      <template #default><el-button type="primary" plain @click="emit('retry')">重新加载</el-button></template>
    </el-alert>
    <div v-else-if="comments.length === 0" class="comments__state">{{ emptyText }}</div>

    <article v-for="commentItem in comments" :key="commentItem.commentId" class="comment-item">
      <el-avatar :size="36">{{ commentInitial(commentItem.userName) }}</el-avatar>
      <div class="comment-item__body">
        <div class="comment-item__meta">
          <strong>{{ commentItem.userName || '用户' }}</strong>
          <span v-if="commentItem.time">{{ formatCommentTime(commentItem.time) }}</span>
        </div>
        <p>{{ commentItem.content }}</p>
        <el-button v-if="loggedIn" link type="primary" @click="emit('toggle-reply', commentItem.commentId)">
          {{ replyingTo === commentItem.commentId ? '取消回复' : '回复' }}
        </el-button>
        <div v-if="replyingTo === commentItem.commentId" class="reply-compose">
          <label class="sr-only" :for="`${replyInputPrefix}${commentItem.commentId}`">回复评论</label>
          <el-input
            :id="`${replyInputPrefix}${commentItem.commentId}`"
            :model-value="replyText"
            :rows="3"
            type="textarea"
            maxlength="1000"
            show-word-limit
            resize="none"
            placeholder="写下回复"
            @update:model-value="emit('update:replyText', $event)"
          />
          <p v-if="replyInputError" class="form-error" role="alert">{{ replyInputError }}</p>
          <el-button
            type="primary"
            size="small"
            :loading="replySubmitting"
            @click="emit('submit-reply', commentItem.commentId)"
            >发布回复</el-button
          >
        </div>
        <div :id="`${replyFocusPrefix}${commentItem.commentId}`" class="reply-list" tabindex="-1">
          <article
            v-for="subcomment in commentItem.subCommentList || []"
            :key="subcomment.commentId"
            class="reply-item"
          >
            <div class="comment-item__meta">
              <strong>{{ subcomment.userName || '用户' }}</strong>
              <span v-if="subcomment.time">{{ formatCommentTime(subcomment.time) }}</span>
            </div>
            <p>{{ subcomment.content }}</p>
          </article>
        </div>
      </div>
    </article>

    <el-pagination
      v-if="commentTotal > commentPageSize"
      class="comments-pagination"
      background
      small
      layout="prev, pager, next"
      :current-page="commentPage"
      :page-size="commentPageSize"
      :total="commentTotal"
      @current-change="emit('change-page', $event)"
    />
  </section>
</template>

<script setup>
const props = defineProps({
  label: { type: String, default: '评论' },
  loggedIn: { type: Boolean, default: false },
  avatar: { type: String, default: '' },
  userInitial: { type: String, default: '用' },
  comments: { type: Array, default: () => [] },
  commentsLoading: { type: Boolean, default: false },
  commentsError: { type: String, default: '' },
  commentText: { type: String, default: '' },
  commentInputError: { type: String, default: '' },
  commentSubmitting: { type: Boolean, default: false },
  replyingTo: { type: [String, Number], default: null },
  replyText: { type: String, default: '' },
  replyInputError: { type: String, default: '' },
  replySubmitting: { type: Boolean, default: false },
  commentPage: { type: Number, default: 1 },
  commentPageSize: { type: Number, default: 10 },
  commentTotal: { type: Number, default: 0 },
  commentInputId: { type: String, default: 'comment-input' },
  replyInputPrefix: { type: String, default: 'reply-' },
  replyFocusPrefix: { type: String, default: 'replies-' },
  commentPlaceholder: { type: String, default: '发表你的看法' },
  emptyText: { type: String, default: '还没有评论，来说说你的想法吧。' },
  formatTime: { type: Function, default: (value) => value },
});

const emit = defineEmits([
  'update:commentText',
  'update:replyText',
  'submit-comment',
  'submit-reply',
  'toggle-reply',
  'change-page',
  'retry',
  'login',
]);

function commentInitial(name) {
  return (name || '用户').trim().slice(0, 1).toUpperCase();
}

function formatCommentTime(value) {
  return props.formatTime(value);
}
</script>

<style scoped>
.comments {
  display: grid;
  gap: 18px;
}
.comment-compose,
.comment-item {
  display: flex;
  gap: 12px;
  align-items: start;
}
.comment-compose {
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
}
.comment-compose__input,
.comment-item__body {
  min-width: 0;
  flex: 1;
}
.comment-compose__input :deep(.el-button) {
  margin-top: 8px;
}
.form-error {
  margin: 7px 0 0;
  color: var(--el-color-danger);
  font-size: 0.875rem;
}
.comments__state {
  padding: 18px;
  border: 1px dashed var(--cc4c-border);
  border-radius: 10px;
  color: var(--cc4c-muted);
  text-align: center;
}
.comment-item {
  padding: 4px 0 18px;
  border-bottom: 1px solid var(--cc4c-border);
}
.comment-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: baseline;
}
.comment-item__meta span {
  color: var(--cc4c-muted);
  font-size: 0.75rem;
}
.comment-item__body > p,
.reply-item p {
  margin: 8px 0;
  color: var(--cc4c-text);
  line-height: 1.7;
  white-space: pre-wrap;
}
.reply-compose {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}
.reply-compose .el-button {
  justify-self: start;
}
.reply-item {
  padding: 12px 14px;
  margin-top: 12px;
  border-left: 3px solid #bfdbfe;
  border-radius: 0 8px 8px 0;
  background: #f8fafc;
}
.comments-pagination {
  justify-content: center;
}

@media (max-width: 480px) {
  .comment-compose {
    padding: 12px;
  }
}
</style>
