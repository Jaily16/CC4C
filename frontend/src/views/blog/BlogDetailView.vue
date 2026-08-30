<template>
  <main class="blog-detail">
    <section class="blog-detail__content" aria-labelledby="blog-detail-title">
      <el-button class="blog-detail__back" text type="primary" @click="backToBlogs">
        <el-icon><ArrowLeft /></el-icon>
        返回所有博客
      </el-button>

      <PageFeedback
        :loading="loading"
        :empty="!loading && !errorMessage && !blogData"
        :error="errorMessage"
        empty-title="未找到博客内容"
        empty-description="这篇博客可能已被删除，请返回博客列表继续浏览。"
        @retry="loadBlog"
      >
        <article class="blog-reading">
          <header class="blog-reading__header">
            <div class="blog-reading__heading">
              <div class="blog-reading__labels">
                <span class="blog-reading__eyebrow">社区文章</span>
                <el-tag v-if="blogData.state !== 1" :type="statusInfo.type" effect="light">{{
                  statusInfo.label
                }}</el-tag>
              </div>
              <h1 id="blog-detail-title">{{ blogData.title }}</h1>
              <div class="blog-reading__meta" aria-label="博客信息">
                <span
                  ><el-icon><User /></el-icon>{{ authorLabel }}</span
                >
                <span v-if="blogData.publishTime"
                  ><el-icon><Calendar /></el-icon>{{ formatDate(blogData.publishTime) }}</span
                >
                <span v-if="languageLabel"
                  ><el-icon><CollectionTag /></el-icon>{{ languageLabel }}</span
                >
                <span v-if="blogData.click !== undefined"
                  ><el-icon><View /></el-icon>{{ blogData.click }} 次阅读</span
                >
              </div>
            </div>

            <ContentActionBar
              v-if="blogData.state === 1"
              content-type="博客"
              :collected="isFavor"
              :logged-in="loggedIn"
              :comment-open="isCommentOpen"
              @toggle-collect="toggleCollect"
              @toggle-comment="isCommentOpen = !isCommentOpen"
              @require-login="goToLogin"
            />
          </header>

          <div class="blog-reading__article">
            <md-editor
              v-model="text"
              :editor-id="editorState.id"
              :sanitize="sanitizeMarkdownHtml"
              :marked-heading-id="markdownHeadingId"
              preview-only
            />
          </div>
        </article>
      </PageFeedback>
    </section>

    <div v-if="blogData && text" class="blog-catalog-float">
      <el-popover
        v-model:visible="catalogOpen"
        trigger="click"
        placement="top-end"
        :width="272"
        popper-class="blog-catalog-popover"
      >
        <template #reference>
          <el-button class="blog-catalog-float__button" round aria-label="打开文章目录" :aria-expanded="catalogOpen">
            <el-icon><Reading /></el-icon>
            文章目录
          </el-button>
        </template>
        <nav class="floating-catalog" aria-label="文章章节导航" @click="closeCatalogAfterNavigation">
          <div class="floating-catalog__heading">
            <span>文章目录</span>
            <small>点击章节跳转</small>
          </div>
          <md-catalog
            :editor-id="editorState.id"
            :scroll-element="scrollElement"
            :marked-heading-id="markdownHeadingId"
          />
        </nav>
      </el-popover>
    </div>

    <el-drawer v-model="isCommentOpen" size="min(92vw, 560px)" direction="rtl" destroy-on-close>
      <template #header>
        <div class="comments-drawer__title">
          <span>博客评论</span>
          <small>{{ commentTotal }} 条讨论</small>
        </div>
      </template>

      <CommentThread
        v-model:comment-text="commentText"
        v-model:reply-text="replyText"
        label="博客评论"
        :logged-in="loggedIn"
        :avatar="store.state.user.avatar || ''"
        :user-initial="userInitial"
        :comments="commentList"
        :comments-loading="commentsLoading"
        :comments-error="commentsError"
        :comment-input-error="commentInputError"
        :comment-submitting="commentSubmitting"
        :replying-to="replyingTo"
        :reply-input-error="replyInputError"
        :reply-submitting="replySubmitting"
        :comment-page="commentPage"
        :comment-page-size="commentPageSize"
        :comment-total="commentTotal"
        comment-input-id="blog-comment"
        reply-input-prefix="blog-reply-"
        reply-focus-prefix="blog-replies-"
        comment-placeholder="分享你的想法"
        :format-time="formatDate"
        @submit-comment="comment"
        @submit-reply="reply"
        @toggle-reply="toggleReply"
        @change-page="changeCommentPage"
        @retry="loadComments"
        @login="goToLogin"
      />
    </el-drawer>
  </main>
</template>

<script setup>
import { reportClientError } from '@/utils/reportClientError.js';
import { computed, reactive, ref, watch } from 'vue';
import { ArrowLeft, Calendar, CollectionTag, Reading, User, View } from '@element-plus/icons-vue';
import { getBlog } from '@/api/community';
import {
  addBlogFavorite,
  createBlogComment,
  createReply,
  getBlogComments,
  getBlogFavoriteState,
  removeBlogFavorite,
} from '@/api/interactions';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import store from '@/store';
import { markdownHeadingId, sanitizeMarkdownHtml } from '@/utils/markdownSanitizer';
import ContentActionBar from '@/components/common/ContentActionBar.vue';
import PageFeedback from '@/components/common/PageFeedback.vue';
import CommentThread from '@/components/community/CommentThread.vue';
import { apiErrorMessage } from '@/utils/apiError';
import { useCurrentUser } from '@/composables/useCurrentUser';
import { useCommentThread } from '@/composables/useCommentThread.js';

const route = useRoute();
const router = useRouter();
const MdCatalog = MdEditor.MdCatalog;
const scrollElement = document.documentElement;
const editorState = reactive({ id: 'blog-detail-editor' });
const text = ref('');
const blogData = ref(null);
const loading = ref(false);
const errorMessage = ref('');
const isFavor = ref(false);
const isCommentOpen = ref(false);
const catalogOpen = ref(false);

const { isUser: loggedIn } = useCurrentUser();
const userInitial = computed(() => commentInitial(store.state.user.name));
const commentThread = useCommentThread({
  subjectId: () => blogData.value?.blogId,
  fetchPage: getBlogComments,
  createComment: (content) =>
    createBlogComment({
      content,
      blogId: blogData.value.blogId,
    }),
  createReply: (fatherId, content) => createReply({ content, fatherId }),
  focusIdPrefix: 'blog-replies-',
  loadErrorMessage: '评论加载失败，请检查网络后重试。',
  commentErrorMessage: '评论发布失败，请稍后重试。',
  replyErrorMessage: '回复发布失败，请稍后重试。',
});
const {
  commentList,
  commentsLoading,
  commentsError,
  commentText,
  commentInputError,
  commentSubmitting,
  replyingTo,
  replyText,
  replyInputError,
  replySubmitting,
  commentPage,
  commentPageSize,
  commentTotal,
  loadComments,
  toggleReply,
  changeCommentPage,
  resetComments,
} = commentThread;
const authorLabel = computed(() => blogData.value?.poster || blogData.value?.author || '社区作者');
const statusInfo = computed(
  () =>
    ({
      '-1': { label: '审核未通过', type: 'danger' },
      0: { label: '待审核', type: 'warning' },
      1: { label: '已发布', type: 'success' },
    })[String(blogData.value?.state)] || { label: '状态未知', type: 'info' },
);
const languageLabel = computed(() => {
  const names = { 1: 'Java', 2: 'C++', 3: 'Python', 4: 'C' };
  const values = blogData.value?.languageList;
  if (!Array.isArray(values) || values.length === 0) return '';
  return values.map((value) => names[value] || value).join(' · ');
});

function formatDate(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function commentInitial(name) {
  return (name || '用户').trim().slice(0, 1).toUpperCase();
}

function backToBlogs() {
  router.push('/allBlogs');
}

function goToLogin() {
  ElMessage.warning('登录后即可收藏博客和参与评论');
  router.push('/login');
}

function closeCatalogAfterNavigation() {
  window.setTimeout(() => {
    catalogOpen.value = false;
  }, 0);
}

async function loadBlog() {
  const blogId = String(route.query.blogId || '');
  blogData.value = null;
  text.value = '';
  isFavor.value = false;
  resetComments();
  errorMessage.value = '';
  if (!blogId) {
    errorMessage.value = '缺少博客 ID，请从博客列表中选择文章。';
    return;
  }

  loading.value = true;
  try {
    const resp = await getBlog(blogId);
    if (!resp.data.data?.blogId) {
      errorMessage.value = resp.data.msg || '未找到该博客。';
      return;
    }
    blogData.value = resp.data.data;
    text.value = blogData.value.content || '';
    const requests = [];
    if (blogData.value.state === 1) requests.push(loadComments());
    if (blogData.value.state === 1 && loggedIn.value) {
      requests.push(
        getBlogFavoriteState(blogData.value.blogId)
          .then((favorResp) => {
            isFavor.value = favorResp.data.data === true;
          })
          .catch((error) => reportClientError(error, 'frontend/src/views/blog/BlogDetailView.vue')),
      );
    }
    await Promise.all(requests);
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '博客加载失败，请检查网络后重试。');
    reportClientError(error, 'frontend/src/views/blog/BlogDetailView.vue');
  } finally {
    loading.value = false;
  }
}

async function toggleCollect() {
  if (!blogData.value?.blogId || !loggedIn.value) return;
  try {
    const resp = isFavor.value
      ? await removeBlogFavorite(blogData.value.blogId)
      : await addBlogFavorite(blogData.value.blogId);
    if (!resp.data.data) {
      ElMessage.error(resp.data.msg || '收藏操作失败');
      return;
    }
    isFavor.value = !isFavor.value;
    ElMessage.success(isFavor.value ? '收藏成功' : '取消收藏成功');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '收藏操作失败，请稍后重试'));
    reportClientError(error, 'frontend/src/views/blog/BlogDetailView.vue');
  }
}

async function reply(fatherId) {
  const succeeded = await commentThread.reply(fatherId);
  if (succeeded) ElMessage.success('回复成功');
}

async function comment() {
  const succeeded = await commentThread.comment();
  if (succeeded) ElMessage.success('评论成功');
}

watch(() => route.query.blogId, loadBlog, { immediate: true });
</script>

<style scoped>
.blog-detail {
  min-width: 0;
  padding: clamp(16px, 3vw, 36px);
}
.blog-detail__content {
  width: min(100%, 1120px);
  margin: 0 auto;
}
.blog-detail__back {
  min-height: 36px;
  padding-inline: 0;
  margin-bottom: 12px;
  font-weight: 700;
}
.blog-reading {
  overflow: hidden;
  border: 1px solid var(--cc4c-border);
  border-radius: 16px;
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}
.blog-reading__header {
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
  align-items: start;
  justify-content: space-between;
  padding: clamp(24px, 5vw, 48px);
  border-bottom: 1px solid var(--cc4c-border);
  background: linear-gradient(135deg, #f7faff, #fff);
}
.blog-reading__heading {
  min-width: 0;
  flex: 1;
}
.blog-reading__labels {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.blog-reading__eyebrow {
  color: var(--cc4c-primary);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.blog-reading h1 {
  max-width: 820px;
  margin: 12px 0 0;
  color: var(--cc4c-text);
  font-size: clamp(2rem, 5vw, 3.45rem);
  line-height: 1.15;
  overflow-wrap: anywhere;
}
.blog-reading__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 9px 18px;
  margin-top: 18px;
  color: var(--cc4c-muted);
  font-size: 0.875rem;
}
.blog-reading__meta span {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}
.blog-reading__article {
  min-width: 0;
  padding: clamp(24px, 6vw, 68px);
}
.blog-reading__article :deep(.md-editor) {
  background: transparent;
}
.blog-reading__article :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
.blog-reading__article :deep(.md-editor-preview) {
  max-width: 820px;
  margin: 0 auto;
  color: var(--cc4c-text);
  font-size: 1rem;
  line-height: 1.8;
}
.blog-reading__article :deep(.md-editor-preview h1),
.blog-reading__article :deep(.md-editor-preview h2),
.blog-reading__article :deep(.md-editor-preview h3) {
  margin-top: 1.8em;
  margin-bottom: 0.65em;
  line-height: 1.3;
}
.blog-catalog-float {
  position: fixed;
  right: clamp(16px, 2.5vw, 36px);
  bottom: clamp(20px, 4vh, 42px);
  z-index: 30;
}
.blog-catalog-float__button {
  min-height: 44px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14);
}
:global(.blog-catalog-popover) {
  max-width: calc(100vw - 32px);
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid var(--cc4c-border) !important;
  border-radius: 14px !important;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.18) !important;
}
:global(.blog-catalog-popover .floating-catalog) {
  padding: 18px;
  background: var(--cc4c-surface);
}
:global(.blog-catalog-popover .floating-catalog__heading) {
  display: grid;
  gap: 3px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--cc4c-border);
}
:global(.blog-catalog-popover .floating-catalog__heading span) {
  color: var(--cc4c-text);
  font-weight: 800;
}
:global(.blog-catalog-popover .floating-catalog__heading small) {
  color: var(--cc4c-muted);
}
:global(.blog-catalog-popover .md-editor-catalog),
:global(.blog-catalog-popover .md-editor-catalog-editor) {
  position: static !important;
  width: auto !important;
  height: auto !important;
  max-height: min(48vh, 360px);
  padding: 0;
  margin-top: 12px;
  overflow-y: auto;
  border: 0;
  background: transparent;
}
:global(.blog-catalog-popover .md-editor-catalog-link span:hover),
:global(.blog-catalog-popover .md-editor-catalog-active > span) {
  color: var(--cc4c-primary);
}
.comments-drawer__title {
  display: grid;
  gap: 3px;
}
.comments-drawer__title span {
  color: var(--cc4c-text);
  font-size: 1.25rem;
  font-weight: 800;
}
.comments-drawer__title small {
  color: var(--cc4c-muted);
}
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
  margin-top: 10px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
}
.reply-compose :deep(.el-button) {
  justify-self: start;
}
.reply-list:focus {
  outline: 2px solid #93c5fd;
  outline-offset: 3px;
}
.reply-item {
  margin-top: 12px;
  padding: 12px 14px;
  border-left: 3px solid #bfdbfe;
  border-radius: 0 8px 8px 0;
  background: #f8fafc;
}
.comments-pagination {
  justify-content: center;
}
@media (max-width: 480px) {
  .blog-detail {
    padding: 12px;
  }
  .blog-reading__header {
    padding: 24px 18px;
  }
  .blog-reading__article {
    padding: 24px 18px;
  }
  .blog-catalog-float {
    right: 14px;
    bottom: 18px;
  }
  .comment-compose {
    padding: 12px;
  }
}
</style>
