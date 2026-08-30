<template>
  <section class="review-page" aria-labelledby="review-title">
    <header class="review-heading">
      <div>
        <p>Content review</p>
        <h1 id="review-title">博客内容审核</h1>
        <span>选择待审核文章，阅读完整内容后给出审核决定。</span>
      </div>
      <div class="review-heading__count">
        <strong>{{ total }}</strong
        ><span>篇待审核</span>
      </div>
    </header>

    <PageFeedback v-if="loading || errorMessage" :loading="loading" :error="errorMessage" @retry="loadPendingBlogs" />

    <div v-else-if="blogList.length" class="review-workspace">
      <aside class="review-queue" aria-labelledby="review-queue-title">
        <header>
          <div>
            <h2 id="review-queue-title">审核队列</h2>
            <p>按提交时间排列</p>
          </div>
          <el-tag type="warning" effect="light">待审核</el-tag>
        </header>
        <div class="review-queue__list">
          <button
            v-for="blog in blogList"
            :key="blog.blogId"
            type="button"
            class="review-item"
            :class="{ 'review-item--active': selectedBlog?.blogId === blog.blogId }"
            :disabled="Boolean(operationAction)"
            @click="selectBlog(blog)"
          >
            <span class="review-item__status"><i></i>等待决定</span>
            <strong>{{ blog.title }}</strong>
            <span class="review-item__meta">作者 ID {{ blog.writerId || '未知' }}</span>
            <time :datetime="blog.publishTime">{{ formatDateTime(blog.publishTime) }}</time>
          </button>
        </div>
        <el-pagination
          v-if="total > pageSize"
          class="review-pagination"
          background
          small
          layout="prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          @current-change="changePage"
        />
      </aside>

      <article class="review-preview" aria-labelledby="review-preview-title">
        <template v-if="selectedBlog">
          <header class="review-preview__heading">
            <div>
              <span>当前审核内容</span>
              <h2 id="review-preview-title">{{ selectedBlog.title }}</h2>
              <p>
                作者 ID {{ selectedBlog.writerId || '未知' }} · 提交于 {{ formatDateTime(selectedBlog.publishTime) }}
              </p>
            </div>
            <el-tag type="warning">待审核</el-tag>
          </header>

          <div v-if="detailLoading" class="preview-loading">
            <el-icon class="is-loading"><Loading /></el-icon>正在读取博客正文…
          </div>
          <el-alert v-else-if="detailError" type="error" :closable="false" show-icon :title="detailError">
            <el-button type="primary" plain @click="selectBlog(selectedBlog)">重新加载正文</el-button>
          </el-alert>
          <div v-else class="review-preview__content">
            <md-editor
              v-model="text"
              :editor-id="editorId"
              :sanitize="sanitizeMarkdownHtml"
              :marked-heading-id="markdownHeadingId"
              :preview-only="true"
            />
          </div>

          <footer class="review-actions">
            <p>审核操作提交后会立即更新文章状态，请确认正文符合社区规范。</p>
            <div>
              <el-button
                type="danger"
                plain
                :loading="operationAction === 'deny'"
                :disabled="detailLoading || Boolean(detailError) || operationAction === 'approve'"
                @click="confirmDecision('deny')"
                >驳回文章</el-button
              >
              <el-button
                type="primary"
                :loading="operationAction === 'approve'"
                :disabled="detailLoading || Boolean(detailError) || operationAction === 'deny'"
                @click="confirmDecision('approve')"
                >通过审核</el-button
              >
            </div>
          </footer>
        </template>

        <div v-else class="review-placeholder">
          <span aria-hidden="true">✓</span>
          <h2 id="review-preview-title">请选择一篇博客</h2>
          <p>左侧会显示标题、作者 ID 和提交时间；选择后可在此阅读完整正文。</p>
        </div>
      </article>
    </div>

    <el-empty v-else description="当前没有待审核博客">
      <el-button @click="loadPendingBlogs">刷新审核队列</el-button>
    </el-empty>
  </section>
</template>

<script setup>
import { reportClientError } from '@/utils/reportClientError.js';
import { ref } from 'vue';
import { getBlog, listPendingBlogs, reviewBlog } from '@/api/community';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Loading } from '@element-plus/icons-vue';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';
import { markdownHeadingId, sanitizeMarkdownHtml } from '@/utils/markdownSanitizer';

const blogList = ref([]);
const selectedBlog = ref(null);
const text = ref('');
const loading = ref(false);
const errorMessage = ref('');
const detailLoading = ref(false);
const detailError = ref('');
const operationAction = ref('');
const editorId = 'admin-blog-review-preview';
const currentPage = ref(1);
const pageSize = 10;
const total = ref(0);

function formatDateTime(value) {
  if (!value) return '时间未知';
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

async function loadPendingBlogs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const response = await listPendingBlogs({ page: currentPage.value, size: pageSize });
    blogList.value = response.data.data?.items || [];
    total.value = response.data.data?.total || 0;
    if (selectedBlog.value && !blogList.value.some((blog) => blog.blogId === selectedBlog.value.blogId))
      resetSelection();
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '待审核博客加载失败，请稍后重试。');
    reportClientError(error, 'frontend/src/views/admin/CheckBlogView.vue');
  } finally {
    loading.value = false;
  }
}

async function selectBlog(blog) {
  if (operationAction.value) return;
  selectedBlog.value = blog;
  detailLoading.value = true;
  detailError.value = '';
  text.value = '';
  try {
    const response = await getBlog(blog.blogId);
    if (!response.data.data?.blogId) {
      detailError.value = response.data.msg || '博客正文读取失败。';
      return;
    }
    selectedBlog.value = { ...blog, ...response.data.data };
    text.value = response.data.data.content || '';
  } catch (error) {
    detailError.value = apiErrorMessage(error, '博客正文读取失败，请稍后重试。');
    reportClientError(error, 'frontend/src/views/admin/CheckBlogView.vue');
  } finally {
    detailLoading.value = false;
  }
}

function resetSelection() {
  selectedBlog.value = null;
  text.value = '';
  detailError.value = '';
}

async function confirmDecision(action) {
  if (!selectedBlog.value || operationAction.value) return;
  const approve = action === 'approve';
  try {
    await ElMessageBox.confirm(
      approve
        ? `确认通过《${selectedBlog.value.title}》？通过后文章将对用户公开。`
        : `确认驳回《${selectedBlog.value.title}》？作者将在个人文章中看到未通过状态。`,
      approve ? '确认通过审核' : '确认驳回文章',
      {
        confirmButtonText: approve ? '确认通过' : '确认驳回',
        cancelButtonText: '取消',
        type: approve ? 'success' : 'warning',
      },
    );
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') reportClientError(error, 'frontend/src/views/admin/CheckBlogView.vue');
    return;
  }
  await submitDecision(action);
}

async function submitDecision(action) {
  operationAction.value = action;
  try {
    const endpoint = action === 'approve' ? 'approve' : 'deny';
    const response = await reviewBlog(endpoint, selectedBlog.value.blogId);
    if (!response.data.data) {
      ElMessage.error(response.data.msg || '审核操作失败');
      return;
    }
    ElMessage.success(action === 'approve' ? '文章已通过审核' : '文章已驳回');
    if (blogList.value.length === 1 && currentPage.value > 1) currentPage.value -= 1;
    resetSelection();
    await loadPendingBlogs();
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '审核操作失败，请稍后重试'));
    reportClientError(error, 'frontend/src/views/admin/CheckBlogView.vue');
  } finally {
    operationAction.value = '';
  }
}

function changePage(page) {
  currentPage.value = page;
  resetSelection();
  return loadPendingBlogs();
}

loadPendingBlogs();
</script>

<style scoped>
.review-page {
  display: grid;
  gap: 24px;
  width: min(100%, 1380px);
  margin: 0 auto;
}
.review-heading {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
}
.review-heading p {
  margin: 0 0 5px;
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.review-heading h1 {
  margin: 0;
  font-size: clamp(1.65rem, 3vw, 2.25rem);
}
.review-heading > div:first-child > span {
  display: block;
  margin-top: 8px;
  color: var(--cc4c-muted);
}
.review-heading__count {
  display: grid;
  min-width: 116px;
  padding: 14px 18px;
  border: 1px solid #cfe0fb;
  border-radius: 14px;
  background: #f3f7ff;
  text-align: center;
}
.review-heading__count strong {
  color: var(--cc4c-primary);
  font-size: 1.6rem;
}
.review-heading__count span {
  color: var(--cc4c-muted);
  font-size: 0.75rem;
}
.review-workspace {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.review-queue,
.review-preview {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--cc4c-border);
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.055);
}
.review-queue > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px;
  border-bottom: 1px solid var(--cc4c-border);
}
.review-queue h2 {
  margin: 0;
  font-size: 1rem;
}
.review-queue header p {
  margin: 3px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.72rem;
}
.review-queue__list {
  display: grid;
  max-height: calc(100vh - 260px);
  min-height: 420px;
  gap: 9px;
  padding: 12px;
  overflow-y: auto;
}
.review-pagination {
  justify-content: center;
  padding: 12px;
  border-top: 1px solid var(--cc4c-border);
}
.review-item {
  display: grid;
  gap: 7px;
  width: 100%;
  padding: 15px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: #f8fafc;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--cc4c-transition),
    background var(--cc4c-transition),
    box-shadow var(--cc4c-transition);
}
.review-item:hover {
  border-color: #c7d7ef;
  background: #f2f7ff;
}
.review-item--active {
  border-color: #9ab8ed;
  background: #edf4ff;
  box-shadow: inset 3px 0 var(--cc4c-primary);
}
.review-item__status {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: #b45309;
  font-size: 0.68rem;
  font-weight: 800;
}
.review-item__status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
}
.review-item strong {
  color: var(--cc4c-text);
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.review-item__meta,
.review-item time {
  color: var(--cc4c-muted);
  font-size: 0.72rem;
}
.review-preview__heading {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22px 24px;
  border-bottom: 1px solid var(--cc4c-border);
}
.review-preview__heading span {
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 800;
}
.review-preview__heading h2 {
  margin: 6px 0;
  font-size: 1.25rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.review-preview__heading p {
  margin: 0;
  color: var(--cc4c-muted);
  font-size: 0.78rem;
}
.review-preview__content {
  min-width: 0;
  padding: 8px 18px;
}
.review-preview__content :deep(.md-editor) {
  height: min(620px, 62vh);
  min-height: 440px;
}
.preview-loading {
  display: flex;
  gap: 9px;
  align-items: center;
  justify-content: center;
  min-height: 440px;
  color: var(--cc4c-muted);
}
.review-preview > :deep(.el-alert) {
  margin: 22px;
  width: auto;
}
.review-actions {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-top: 1px solid var(--cc4c-border);
  background: #fafcff;
}
.review-actions p {
  max-width: 520px;
  margin: 0;
  color: var(--cc4c-muted);
  font-size: 0.76rem;
  line-height: 1.55;
}
.review-actions > div {
  display: flex;
  gap: 9px;
}
.review-placeholder {
  display: grid;
  min-height: 620px;
  place-items: center;
  align-content: center;
  padding: 30px;
  text-align: center;
}
.review-placeholder > span {
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  border-radius: 18px;
  background: #eaf1ff;
  color: var(--cc4c-primary);
  font-size: 1.4rem;
}
.review-placeholder h2 {
  margin: 18px 0 6px;
}
.review-placeholder p {
  max-width: 420px;
  margin: 0;
  color: var(--cc4c-muted);
  line-height: 1.65;
}

@media (max-width: 900px) {
  .review-workspace {
    grid-template-columns: 1fr;
  }
  .review-queue__list {
    max-height: 330px;
    min-height: 0;
  }
  .review-placeholder {
    min-height: 360px;
  }
}
@media (max-width: 620px) {
  .review-heading,
  .review-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .review-heading__count {
    width: 100%;
    box-sizing: border-box;
  }
  .review-actions > div {
    display: grid;
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .review-actions .el-button {
    width: 100%;
    margin: 0;
  }
  .review-preview__heading {
    flex-direction: column;
  }
}
</style>
