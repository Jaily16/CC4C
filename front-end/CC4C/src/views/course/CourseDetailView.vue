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
              <md-editor v-model="text" :editor-id="editorState.id" preview-only />
            </div>
          </div>
        </article>
      </PageFeedback>
    </section>

    <div v-if="courseData" class="course-catalog-float">
      <el-popover v-model:visible="catalogOpen" trigger="click" placement="top-end" :width="272" popper-class="course-catalog-popover">
        <template #reference>
          <el-button class="course-catalog-float__button" type="primary" round :aria-expanded="catalogOpen" aria-label="打开课程目录">
            <el-icon><Reading /></el-icon>
            <span>课程目录</span>
          </el-button>
        </template>
        <nav class="floating-catalog" aria-label="课程章节导航" @click="closeCatalogAfterNavigation">
          <div class="floating-catalog__heading">
            <span>课程目录</span>
            <small>点击章节跳转</small>
          </div>
          <md-catalog :editor-id="editorState.id" :scroll-element="scrollElement" />
        </nav>
      </el-popover>
    </div>

    <el-drawer v-model="isCommentOpen" size="min(92vw, 560px)" direction="rtl" destroy-on-close>
      <template #header>
        <div class="comments-drawer__title">
          <span>课程评论</span>
          <small>{{ commentList.length }} 条讨论</small>
        </div>
      </template>

      <section class="comments" aria-label="课程评论">
        <div v-if="loggedIn" class="comment-compose">
          <el-avatar :size="38" :src="store.state.user.avatar || ''">{{ userInitial }}</el-avatar>
          <div class="comment-compose__input">
            <label class="sr-only" for="course-comment">发表评论</label>
            <el-input id="course-comment" v-model="commentText" :rows="4" type="textarea" maxlength="1000" show-word-limit resize="none" placeholder="发表你的看法" />
            <p v-if="commentInputError" class="comment-compose__error" role="alert">{{ commentInputError }}</p>
            <el-button type="primary" :loading="commentSubmitting" @click="comment">发布评论</el-button>
          </div>
        </div>

        <el-alert v-else title="登录后即可发表评论或回复" type="info" :closable="false" show-icon>
          <template #default><el-button type="primary" plain @click="goToLogin">前往登录</el-button></template>
        </el-alert>

        <div v-if="commentsLoading" class="comments__loading" role="status"><el-skeleton :rows="3" animated /></div>
        <el-alert v-else-if="commentsError" title="评论加载失败" :description="commentsError" type="error" :closable="false" show-icon>
          <template #default><el-button type="primary" plain @click="loadComments">重新加载</el-button></template>
        </el-alert>
        <div v-else-if="commentList.length === 0" class="comments__empty">还没有评论，来说说你的想法吧。</div>

        <article v-for="commentItem in commentList" :key="commentItem.commentId" class="comment-item">
          <el-avatar :size="36">{{ commentInitial(commentItem.userName) }}</el-avatar>
          <div class="comment-item__body">
            <div class="comment-item__meta">
              <strong>{{ commentItem.userName || '用户' }}</strong>
              <span v-if="commentItem.publishTime">{{ commentItem.publishTime }}</span>
            </div>
            <p>{{ commentItem.content }}</p>
            <el-button v-if="loggedIn" link type="primary" @click="toggleReply(commentItem.commentId)">
              {{ replyingTo === commentItem.commentId ? '取消回复' : '回复' }}
            </el-button>
            <div v-if="replyingTo === commentItem.commentId" class="reply-compose">
              <label class="sr-only" :for="`reply-${commentItem.commentId}`">回复评论</label>
              <el-input :id="`reply-${commentItem.commentId}`" v-model="replyText" :rows="3" type="textarea" maxlength="1000" show-word-limit resize="none" placeholder="写下回复" />
              <p v-if="replyInputError" class="comment-compose__error" role="alert">{{ replyInputError }}</p>
              <el-button type="primary" size="small" :loading="replySubmitting" @click="reply(commentItem.commentId)">发布回复</el-button>
            </div>
            <div :id="`replies-${commentItem.commentId}`" class="reply-list" tabindex="-1">
              <article v-for="subcomment in commentItem.subCommentList || []" :key="subcomment.commentId" class="reply-item">
                <div class="reply-item__meta">
                  <strong>{{ subcomment.userName || '用户' }}</strong>
                  <span v-if="subcomment.publishTime">{{ subcomment.publishTime }}</span>
                </div>
                <p>{{ subcomment.content }}</p>
              </article>
            </div>
          </div>
        </article>
      </section>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { ArrowLeft, Reading } from '@element-plus/icons-vue';
import axios from '@/plugins/axiosInstance';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import store from '@/store';
import PageFeedback from '@/components/common/PageFeedback.vue';
import ContentActionBar from '@/components/common/ContentActionBar.vue';


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
const commentList = ref([]);
const commentsLoading = ref(false);
const commentsError = ref('');
const commentText = ref('');
const commentInputError = ref('');
const commentSubmitting = ref(false);
const replyingTo = ref(null);
const replyText = ref('');
const replyInputError = ref('');
const replySubmitting = ref(false);

const loggedIn = computed(() => Boolean(store.state.user.id));
const userInitial = computed(() => commentInitial(store.state.user.name));

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

async function loadComments() {
  if (!courseData.value?.courseId) {
    commentList.value = [];
    return;
  }
  commentsLoading.value = true;
  commentsError.value = '';
  try {
    const resp = await axios.get(`/comments/course/${courseData.value.courseId}`);
    commentList.value = Array.isArray(resp.data.data) ? resp.data.data : [];
  } catch (error) {
    commentList.value = [];
    commentsError.value = '评论加载失败，请检查网络后重试。';
    console.error(error);
  } finally {
    commentsLoading.value = false;
  }
}

async function loadCourse() {
  const courseName = String(route.query.courseName || '');
  courseData.value = null;
  text.value = '';
  isFavor.value = false;
  commentList.value = [];
  courseError.value = '';

  if (!courseName) {
    courseError.value = '缺少课程名称，请从课程列表中选择课程。';
    return;
  }

  courseLoading.value = true;
  try {
    const resp = await axios.get(`/courses/${encodeURIComponent(courseName)}`);
    if (!resp.data.data?.courseId) {
      courseError.value = resp.data.msg || '未找到该课程内容。';
      return;
    }
    courseData.value = resp.data.data;
    text.value = normalizeCourseMarkdown(courseData.value.description);

    const requests = [loadComments()];
    if (loggedIn.value) {
      requests.push(
        axios.get(`/courses/ifFavor/${store.state.user.id}/${courseData.value.courseId}`)
          .then((favorResp) => { isFavor.value = favorResp.data.data === true; })
          .catch((error) => console.error(error))
      );
    }
    await Promise.all(requests);
  } catch (error) {
    courseError.value = '课程加载失败，请检查网络后重试。';
    console.error(error);
  } finally {
    courseLoading.value = false;
  }
}

async function toggleCollect() {
  if (!courseData.value?.courseId || !loggedIn.value) return;
  try {
    const resp = isFavor.value
      ? await axios.delete(`/courses/deleteFavor/${store.state.user.id}/${courseData.value.courseId}`)
      : await axios.get(`/courses/star/${store.state.user.id}/${courseData.value.courseId}`);
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '收藏操作失败');
      return;
    }
    isFavor.value = !isFavor.value;
    ElMessage.success(isFavor.value ? '收藏成功' : '取消收藏成功');
  } catch (error) {
    ElMessage.error('收藏操作失败，请稍后重试');
    console.error(error);
  }
}

async function comment() {
  commentInputError.value = '';
  if (!commentText.value.trim()) {
    commentInputError.value = '评论内容不能为空。';
    return;
  }
  commentSubmitting.value = true;
  try {
    const resp = await axios.post('/comments/course', {
      userId: store.state.user.id,
      content: commentText.value.trim(),
      courseId: courseData.value.courseId,
    });
    if (resp.data.data !== true) {
      commentInputError.value = resp.data.msg || '评论发布失败。';
      return;
    }
    commentText.value = '';
    await loadComments();
    ElMessage.success('评论成功');
  } catch (error) {
    commentInputError.value = '评论发布失败，请稍后重试。';
    console.error(error);
  } finally {
    commentSubmitting.value = false;
  }
}

function toggleReply(commentId) {
  replyingTo.value = replyingTo.value === commentId ? null : commentId;
  replyText.value = '';
  replyInputError.value = '';
}

async function reply(fatherId) {
  replyInputError.value = '';
  if (!replyText.value.trim()) {
    replyInputError.value = '回复内容不能为空。';
    return;
  }
  replySubmitting.value = true;
  try {
    const resp = await axios.post('/comments/indirect', {
      userId: store.state.user.id,
      content: replyText.value.trim(),
      fatherId,
    });
    if (resp.data.data !== true) {
      replyInputError.value = resp.data.msg || '回复发布失败。';
      return;
    }
    replyText.value = '';
    replyingTo.value = null;
    await loadComments();
    await nextTick();
    document.getElementById(`replies-${fatherId}`)?.focus();
    ElMessage.success('回复成功');
  } catch (error) {
    replyInputError.value = '回复发布失败，请稍后重试。';
    console.error(error);
  } finally {
    replySubmitting.value = false;
  }
}

watch(() => route.query.courseName, loadCourse, { immediate: true });
</script>

<style scoped>
.course-detail { min-width: 0; padding: clamp(16px, 3vw, 36px); }
.course-detail__content { width: min(100%, 1240px); margin: 0 auto; }
.course-detail__back { min-height: 36px; padding-inline: 0; margin-bottom: 12px; font-weight: 700; }
.course-reading { overflow: hidden; border: 1px solid var(--cc4c-border); border-radius: calc(var(--cc4c-radius) + 4px); background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.course-reading__header { display: flex; flex-wrap: wrap; gap: 20px; align-items: start; justify-content: space-between; padding: clamp(22px, 4vw, 38px); border-bottom: 1px solid var(--cc4c-border); background: linear-gradient(135deg, #f8fbff, #fff); }
.course-reading__eyebrow { margin: 0 0 8px; color: var(--cc4c-primary); font-size: .8125rem; font-weight: 800; letter-spacing: .07em; text-transform: uppercase; }
.course-reading__header h1 { max-width: 760px; margin: 0; color: var(--cc4c-text); font-size: clamp(1.8rem, 4vw, 3rem); line-height: 1.2; }
.course-reading__hint { margin: 12px 0 0; color: var(--cc4c-muted); line-height: 1.7; }
.course-reading__layout { min-width: 0; }
.course-reading__article { min-width: 0; padding: clamp(22px, 3vw, 42px); }
.course-reading__article :deep(.md-editor) { background: transparent; }
.course-reading__article :deep(.md-editor-preview-wrapper) { padding: 0; }
.course-reading__article :deep(.md-editor-preview) { color: var(--cc4c-text); font-size: 1rem; line-height: 1.75; }
.course-reading__article :deep(.md-editor-preview h1), .course-reading__article :deep(.md-editor-preview h2), .course-reading__article :deep(.md-editor-preview h3) { margin-top: 1.8em; margin-bottom: .65em; line-height: 1.3; }
.course-reading__article :deep(.md-editor-preview h1) { font-size: clamp(1.65rem, 3vw, 2.35rem); }
.course-catalog-float { position: fixed; right: clamp(16px, 2.5vw, 36px); bottom: clamp(20px, 4vh, 42px); z-index: 30; }
.course-catalog-float__button { min-height: 46px; padding-inline: 18px; box-shadow: 0 12px 28px rgba(37, 99, 235, .28); }
.course-catalog-float__button:hover, .course-catalog-float__button:focus-visible { box-shadow: 0 16px 34px rgba(37, 99, 235, .38); transform: translateY(-2px); }
:global(.course-catalog-popover) { max-width: calc(100vw - 32px); padding: 0 !important; overflow: hidden; border: 1px solid var(--cc4c-border) !important; border-radius: 14px !important; box-shadow: 0 18px 40px rgba(15, 23, 42, .18) !important; }
:global(.course-catalog-popover .floating-catalog) { padding: 18px; background: var(--cc4c-surface); }
:global(.course-catalog-popover .floating-catalog__heading) { display: grid; gap: 3px; padding-bottom: 12px; border-bottom: 1px solid var(--cc4c-border); }
:global(.course-catalog-popover .floating-catalog__heading span) { color: var(--cc4c-text); font-size: 1rem; font-weight: 800; }
:global(.course-catalog-popover .floating-catalog__heading small) { color: var(--cc4c-muted); }
:global(.course-catalog-popover .md-editor-catalog), :global(.course-catalog-popover .md-editor-catalog-editor) { position: static !important; width: auto !important; height: auto !important; max-height: min(48vh, 360px); padding: 0; margin-top: 12px; overflow-y: auto; border: 0; background: transparent; }
:global(.course-catalog-popover .md-editor-catalog-link span) { color: var(--cc4c-text); }
:global(.course-catalog-popover .md-editor-catalog-link span:hover), :global(.course-catalog-popover .md-editor-catalog-active > span) { color: var(--cc4c-primary); }
.comments-drawer__title { display: grid; gap: 3px; }
.comments-drawer__title span { color: var(--cc4c-text); font-size: 1.25rem; font-weight: 800; }
.comments-drawer__title small { color: var(--cc4c-muted); }
.comments { display: grid; gap: 18px; }
.comment-compose, .comment-item { display: flex; gap: 12px; align-items: start; }
.comment-compose { padding: 16px; border: 1px solid #dbeafe; border-radius: 12px; background: #f8fbff; }
.comment-compose__input, .comment-item__body { min-width: 0; flex: 1; }
.comment-compose__input :deep(.el-button) { margin-top: 8px; }
.comment-compose__error { margin: 7px 0 0; color: var(--el-color-danger); font-size: .875rem; }
.comments__loading, .comments__empty { padding: 18px; border: 1px dashed var(--cc4c-border); border-radius: 10px; color: var(--cc4c-muted); text-align: center; }
.comment-item { padding: 4px 0 18px; border-bottom: 1px solid var(--cc4c-border); }
.comment-item__meta, .reply-item__meta { display: flex; flex-wrap: wrap; gap: 6px 10px; align-items: baseline; }
.comment-item__meta span, .reply-item__meta span { color: var(--cc4c-muted); font-size: .75rem; }
.comment-item__body > p, .reply-item p { margin: 8px 0; color: var(--cc4c-text); line-height: 1.7; white-space: pre-wrap; }
.reply-compose { display: grid; gap: 8px; margin-top: 10px; padding: 12px; border-radius: 10px; background: #f8fafc; }
.reply-compose :deep(.el-button) { justify-self: start; }
.reply-list:focus { outline: 2px solid #93c5fd; outline-offset: 3px; }
.reply-item { margin-top: 12px; padding: 12px 14px; border-left: 3px solid #bfdbfe; border-radius: 0 8px 8px 0; background: #f8fafc; }
@media (max-width: 480px) { .course-detail { padding: 12px; } .course-reading__header { padding: 22px 18px; } .course-reading__article { padding: 22px 18px; } .course-catalog-float { right: 14px; bottom: 18px; } .course-catalog-float__button { min-height: 42px; padding-inline: 14px; } .comment-compose { padding: 12px; } }
</style>
