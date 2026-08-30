<template>
  <main class="course-detail">
    <section class="course-detail__content" aria-labelledby="course-detail-title">
      <el-button class="course-detail__back" text type="primary" @click="backToCourses">
        <el-icon><ArrowLeft /></el-icon>
        返回所有课程
      </el-button>

      <PageFeedback
        :loading="courseLoading"
        :empty="!courseLoading && !courseError && !courseData"
        :error="courseError"
        empty-title="未找到课程内容"
        empty-description="请从课程列表中选择要阅读的课程。"
        @retry="loadCourse"
      >
        <article class="course-reading">
          <header class="course-reading__header">
            <div>
              <p class="course-reading__eyebrow">{{ courseData.languageName || '编程课程' }}</p>
              <h1 id="course-detail-title">{{ courseData.courseName }}</h1>
              <p class="course-reading__hint">按自己的节奏阅读课程内容，需要时可打开目录或参与讨论。</p>
            </div>
            <ContentActionBar
              :collected="isFavor"
              :logged-in="loggedIn"
              :comment-open="isCommentOpen"
              @toggle-collect="toggleCollect"
              @toggle-comment="isCommentOpen = !isCommentOpen"
              @require-login="goToLogin"
            />
          </header>

          <div class="course-reading__layout">
            <div class="course-reading__article">
              <md-editor
                v-model="text"
                :editor-id="editorState.id"
                :sanitize="sanitizeMarkdownHtml"
                :marked-heading-id="markdownHeadingId"
                preview-only
              />
            </div>
          </div>
        </article>
      </PageFeedback>
    </section>

    <div v-if="courseData" class="course-catalog-float">
      <el-popover
        v-model:visible="catalogOpen"
        trigger="click"
        placement="top-end"
        :width="272"
        popper-class="course-catalog-popover"
      >
        <template #reference>
          <el-button
            class="course-catalog-float__button"
            type="primary"
            round
            :aria-expanded="catalogOpen"
            aria-label="打开课程目录"
          >
            <el-icon><Reading /></el-icon>
            <span>课程目录</span>
          </el-button>
        </template>
        <nav class="floating-catalog" aria-label="课程章节导航" @click="closeCatalogAfterNavigation">
          <div class="floating-catalog__heading">
            <span>课程目录</span>
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
          <span>课程评论</span>
          <small>{{ commentTotal }} 条讨论</small>
        </div>
      </template>

      <CommentThread
        v-model:comment-text="commentText"
        v-model:reply-text="replyText"
        label="课程评论"
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
        comment-input-id="course-comment"
        reply-input-prefix="reply-"
        reply-focus-prefix="replies-"
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
import { ArrowLeft, Reading } from '@element-plus/icons-vue';
import { getCourse } from '@/api/catalog';
import {
  getCourseFavoriteState,
  addCourseFavorite,
  removeCourseFavorite,
  createCourseComment,
  createReply,
  getCourseComments,
} from '@/api/interactions';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import store from '@/store';
import { markdownHeadingId, sanitizeMarkdownHtml } from '@/utils/markdownSanitizer';
import PageFeedback from '@/components/common/PageFeedback.vue';
import ContentActionBar from '@/components/common/ContentActionBar.vue';
import CommentThread from '@/components/community/CommentThread.vue';
import { apiErrorMessage } from '@/utils/apiError';
import { useCurrentUser } from '@/composables/useCurrentUser';
import { useCommentThread } from '@/composables/useCommentThread.js';

const route = useRoute();
const router = useRouter();
const MdCatalog = MdEditor.MdCatalog;
const scrollElement = document.documentElement;
const editorState = reactive({ id: 'course-detail-editor' });
const text = ref('');
const courseData = ref(null);
const courseLoading = ref(false);
const courseError = ref('');
const isFavor = ref(false);
const isCommentOpen = ref(false);
const catalogOpen = ref(false);

const { isUser: loggedIn } = useCurrentUser();
const userInitial = computed(() => commentInitial(store.state.user.name));
const commentThread = useCommentThread({
  subjectId: () => courseData.value?.courseId,
  fetchPage: getCourseComments,
  createComment: (content) =>
    createCourseComment({
      content,
      courseId: courseData.value.courseId,
    }),
  createReply: (fatherId, content) => createReply({ content, fatherId }),
  focusIdPrefix: 'replies-',
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

function commentInitial(name) {
  return (name || '用户').trim().slice(0, 1).toUpperCase();
}

function normalizeCourseMarkdown(content) {
  return String(content || '').replace(/:star:/gi, '⭐');
}

function backToCourses() {
  router.push('/allCourses');
}

function goToLogin() {
  ElMessage.warning('登录后即可收藏课程和参与评论');
  router.push('/login');
}

function closeCatalogAfterNavigation() {
  window.setTimeout(() => {
    catalogOpen.value = false;
  }, 0);
}

async function loadCourse() {
  const courseName = String(route.query.courseName || '');
  courseData.value = null;
  text.value = '';
  isFavor.value = false;
  resetComments();
  courseError.value = '';

  if (!courseName) {
    courseError.value = '缺少课程名称，请从课程列表中选择课程。';
    return;
  }

  courseLoading.value = true;
  try {
    const resp = await getCourse(courseName);
    if (!resp.data.data?.courseId) {
      courseError.value = resp.data.msg || '未找到该课程内容。';
      return;
    }
    courseData.value = resp.data.data;
    text.value = normalizeCourseMarkdown(courseData.value.description);

    const requests = [loadComments()];
    if (loggedIn.value) {
      requests.push(
        getCourseFavoriteState(courseData.value.courseId)
          .then((favorResp) => {
            isFavor.value = favorResp.data.data === true;
          })
          .catch((error) => reportClientError(error, 'frontend/src/views/course/CourseDetailView.vue')),
      );
    }
    await Promise.all(requests);
  } catch (error) {
    courseError.value = apiErrorMessage(error, '课程加载失败，请检查网络后重试。');
    reportClientError(error, 'frontend/src/views/course/CourseDetailView.vue');
  } finally {
    courseLoading.value = false;
  }
}

async function toggleCollect() {
  if (!courseData.value?.courseId || !loggedIn.value) return;
  try {
    const resp = isFavor.value
      ? await removeCourseFavorite(courseData.value.courseId)
      : await addCourseFavorite(courseData.value.courseId);
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '收藏操作失败');
      return;
    }
    isFavor.value = !isFavor.value;
    ElMessage.success(isFavor.value ? '收藏成功' : '取消收藏成功');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '收藏操作失败，请稍后重试'));
    reportClientError(error, 'frontend/src/views/course/CourseDetailView.vue');
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

watch(() => route.query.courseName, loadCourse, { immediate: true });
</script>

<style scoped>
.course-detail {
  min-width: 0;
  padding: clamp(16px, 3vw, 36px);
}
.course-detail__content {
  width: min(100%, 1240px);
  margin: 0 auto;
}
.course-detail__back {
  min-height: 36px;
  padding-inline: 0;
  margin-bottom: 12px;
  font-weight: 700;
}
.course-reading {
  overflow: hidden;
  border: 1px solid var(--cc4c-border);
  border-radius: calc(var(--cc4c-radius) + 4px);
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}
.course-reading__header {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: start;
  justify-content: space-between;
  padding: clamp(22px, 4vw, 38px);
  border-bottom: 1px solid var(--cc4c-border);
  background: linear-gradient(135deg, #f8fbff, #fff);
}
.course-reading__eyebrow {
  margin: 0 0 8px;
  color: var(--cc4c-primary);
  font-size: 0.8125rem;
  font-weight: 800;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}
.course-reading__header h1 {
  max-width: 760px;
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(1.8rem, 4vw, 3rem);
  line-height: 1.2;
}
.course-reading__hint {
  margin: 12px 0 0;
  color: var(--cc4c-muted);
  line-height: 1.7;
}
.course-reading__layout {
  min-width: 0;
}
.course-reading__article {
  min-width: 0;
  padding: clamp(22px, 3vw, 42px);
}
.course-reading__article :deep(.md-editor) {
  background: transparent;
}
.course-reading__article :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
.course-reading__article :deep(.md-editor-preview) {
  color: var(--cc4c-text);
  font-size: 1rem;
  line-height: 1.75;
}
.course-reading__article :deep(.md-editor-preview h1),
.course-reading__article :deep(.md-editor-preview h2),
.course-reading__article :deep(.md-editor-preview h3) {
  margin-top: 1.8em;
  margin-bottom: 0.65em;
  line-height: 1.3;
}
.course-reading__article :deep(.md-editor-preview h1) {
  font-size: clamp(1.65rem, 3vw, 2.35rem);
}
.course-catalog-float {
  position: fixed;
  right: clamp(16px, 2.5vw, 36px);
  bottom: clamp(20px, 4vh, 42px);
  z-index: 30;
}
.course-catalog-float__button {
  min-height: 46px;
  padding-inline: 18px;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.28);
}
.course-catalog-float__button:hover,
.course-catalog-float__button:focus-visible {
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.38);
  transform: translateY(-2px);
}
:global(.course-catalog-popover) {
  max-width: calc(100vw - 32px);
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid var(--cc4c-border) !important;
  border-radius: 14px !important;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.18) !important;
}
:global(.course-catalog-popover .floating-catalog) {
  padding: 18px;
  background: var(--cc4c-surface);
}
:global(.course-catalog-popover .floating-catalog__heading) {
  display: grid;
  gap: 3px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--cc4c-border);
}
:global(.course-catalog-popover .floating-catalog__heading span) {
  color: var(--cc4c-text);
  font-size: 1rem;
  font-weight: 800;
}
:global(.course-catalog-popover .floating-catalog__heading small) {
  color: var(--cc4c-muted);
}
:global(.course-catalog-popover .md-editor-catalog),
:global(.course-catalog-popover .md-editor-catalog-editor) {
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
:global(.course-catalog-popover .md-editor-catalog-link span) {
  color: var(--cc4c-text);
}
:global(.course-catalog-popover .md-editor-catalog-link span:hover),
:global(.course-catalog-popover .md-editor-catalog-active > span) {
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
.comment-compose__error {
  margin: 7px 0 0;
  color: var(--el-color-danger);
  font-size: 0.875rem;
}
.comments__loading,
.comments__empty {
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
.comment-item__meta,
.reply-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: baseline;
}
.comment-item__meta span,
.reply-item__meta span {
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
  .course-detail {
    padding: 12px;
  }
  .course-reading__header {
    padding: 22px 18px;
  }
  .course-reading__article {
    padding: 22px 18px;
  }
  .course-catalog-float {
    right: 14px;
    bottom: 18px;
  }
  .course-catalog-float__button {
    min-height: 42px;
    padding-inline: 14px;
  }
  .comment-compose {
    padding: 12px;
  }
}
</style>
