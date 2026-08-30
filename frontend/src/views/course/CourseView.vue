<template>
  <main class="course-browser">
    <section class="course-browser__content" aria-labelledby="course-browser-title">
      <header class="page-heading">
        <p class="page-heading__eyebrow">学习资源</p>
        <h1 id="course-browser-title">浏览课程</h1>
        <p>选择语言后，从课程目录中继续你的学习。</p>
      </header>

      <section class="language-bar" aria-label="课程语言选择">
        <span class="language-bar__label">选择语言</span>
        <el-radio-group v-model="mainLang" class="language-picker" @change="selectLang">
          <el-radio-button v-for="lang in langs" :key="lang.name" :label="lang.name">
            <span class="language-option">
              <el-image :src="lang.icon" :alt="lang.name" fit="contain" />
              <span>{{ lang.name }}</span>
            </span>
          </el-radio-button>
        </el-radio-group>
      </section>

      <section class="course-workspace">
        <aside class="course-directory" aria-label="课程目录">
          <div class="course-directory__heading">
            <h2>课程目录</h2>
            <span>{{ mainLang }}</span>
          </div>
          <PageFeedback
            :loading="modulesLoading"
            :empty="!modulesLoading && !modulesError && courseModules.length === 0"
            :error="modulesError"
            empty-title="当前语言暂无课程"
            empty-description="请切换语言，或稍后再试。"
            @retry="loadCourseModules"
          >
            <el-collapse accordion>
              <el-collapse-item
                v-for="module in courseModules"
                :key="module.moduleId || module.moduleName"
                :name="module.moduleId || module.moduleName"
                :title="module.moduleName"
              >
                <div class="course-directory__items">
                  <button
                    v-for="courseName in module.courseList || []"
                    :key="courseName"
                    type="button"
                    class="course-directory__item"
                    :class="{ 'course-directory__item--active': courseData && courseData.courseName === courseName }"
                    @click="openCourse(courseName)"
                  >
                    {{ courseName }}
                  </button>
                </div>
              </el-collapse-item>
            </el-collapse>
          </PageFeedback>
        </aside>

        <section class="course-reader" aria-live="polite">
          <PageFeedback
            :loading="courseLoading"
            :empty="!courseLoading && !courseError && !courseData"
            :error="courseError"
            empty-title="选择一门课程开始学习"
            empty-description="从左侧课程目录中选择想要阅读的内容。"
            @retry="retryCourse"
          >
            <header class="course-reader__heading">
              <div>
                <p class="page-heading__eyebrow">{{ courseData.languageName || mainLang }}</p>
                <h2>{{ courseData.courseName }}</h2>
              </div>
              <div class="course-actions" aria-label="课程操作">
                <el-button :type="isFavor ? 'warning' : 'default'" @click="starCourse">
                  {{ isFavor ? '已收藏' : '收藏课程' }}
                </el-button>
                <el-button type="primary" plain @click="isCommentOpen = true">查看评论</el-button>
              </div>
            </header>
            <div class="course-reader__article">
              <md-editor
                v-model="text"
                :editor-id="editorState.id"
                :sanitize="sanitizeMarkdownHtml"
                :marked-heading-id="markdownHeadingId"
                preview-only
              />
            </div>
            <details class="course-reader__catalog">
              <summary>文章目录</summary>
              <md-catalog
                :editor-id="editorState.id"
                :scroll-element="scrollElement"
                :marked-heading-id="markdownHeadingId"
              />
            </details>
          </PageFeedback>
        </section>
      </section>
    </section>

    <el-drawer v-model="isCommentOpen" size="min(92vw, 520px)" direction="rtl" title="课程评论">
      <CommentThread
        v-model:comment-text="commentText"
        v-model:reply-text="replyText"
        label="课程评论"
        :logged-in="loggedIn"
        :avatar="currentUser.avatar || assets.defaultAvatar"
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
        @submit-comment="submitComment"
        @submit-reply="submitReply"
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
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCourse, getRecommendedCourse } from '@/api/catalog';
import { getSession } from '@/api/auth';
import {
  addCourseFavorite,
  createCourseComment,
  createReply,
  getCourseComments,
  getCourseFavoriteState,
  removeCourseFavorite,
} from '@/api/interactions';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ElMessage } from 'element-plus';
import store from '@/store';
import { assets } from '@/assets';
import { markdownHeadingId, sanitizeMarkdownHtml } from '@/utils/markdownSanitizer';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';
import CommentThread from '@/components/community/CommentThread.vue';
import { useCurrentUser } from '@/composables/useCurrentUser';
import { useCommentThread } from '@/composables/useCommentThread.js';

const router = useRouter();
const MdCatalog = MdEditor.MdCatalog;
const scrollElement = document.documentElement;
const editorState = reactive({ id: 'course-browser-editor' });
const mainLang = ref('java');
const courseModules = ref([]);
const courseData = ref(null);
const text = ref('');
const modulesLoading = ref(false);
const courseLoading = ref(false);
const modulesError = ref('');
const courseError = ref('');
const isFavor = ref(false);
const isCommentOpen = ref(false);
const langs = [
  { no: '1', name: 'java', icon: assets.languageIcons.java },
  { no: '2', name: 'c++', icon: assets.languageIcons['c++'] },
  { no: '3', name: 'python', icon: assets.languageIcons.python },
  { no: '4', name: 'c', icon: assets.languageIcons.c },
];
const { user: currentUser, isUser: loggedIn } = useCurrentUser();
const userInitial = computed(() => (currentUser.value.name || '用户').trim().slice(0, 1).toUpperCase());
const commentThread = useCommentThread({
  subjectId: () => courseData.value?.courseId,
  fetchPage: getCourseComments,
  createComment: (content) => createCourseComment({ content, courseId: courseData.value?.courseId }),
  createReply: (fatherId, content) => createReply({ content, fatherId }),
  focusIdPrefix: 'replies-',
  loadErrorMessage: '评论加载失败，请稍后重试。',
  commentErrorMessage: '评论失败，请稍后重试。',
  replyErrorMessage: '回复失败，请稍后重试。',
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
  comment: submitCommentState,
  toggleReply,
  reply: submitReplyState,
  changeCommentPage,
  resetComments,
} = commentThread;

function selectedLanguage() {
  return langs.find((lang) => lang.name === mainLang.value) || langs[0];
}

async function verifyUser() {
  try {
    const resp = await getSession();
    if (resp.data.data?.role !== 'USER') {
      ElMessage.warning(resp.data.msg || '请先登录');
      await router.push('/login');
      return false;
    }
    return true;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '登录状态验证失败，请重新登录'));
    await router.push('/login');
    return false;
  }
}

async function loadCourseModules() {
  const language = selectedLanguage();
  modulesLoading.value = true;
  modulesError.value = '';
  try {
    const resp = await getRecommendedCourse(language.no, store.state.user.major);
    courseModules.value = Array.isArray(resp.data.data) ? resp.data.data : [];
  } catch (error) {
    courseModules.value = [];
    modulesError.value = apiErrorMessage(error, '课程目录加载失败，请检查网络后重试。');
    reportClientError(error, 'frontend/src/views/course/CourseView.vue');
  } finally {
    modulesLoading.value = false;
  }
}

function selectLang() {
  courseData.value = null;
  text.value = '';
  isFavor.value = false;
  resetComments();
  return loadCourseModules();
}

async function openCourse(courseName) {
  courseLoading.value = true;
  courseError.value = '';
  try {
    const courseResp = await getCourse(courseName);
    if (!courseResp.data.data?.courseId) {
      courseData.value = null;
      courseError.value = courseResp.data.msg || '课程加载失败';
      return;
    }
    courseData.value = courseResp.data.data;
    text.value = courseData.value.description || '';
    commentPage.value = 1;
    commentTotal.value = 0;
    const [favorResult] = await Promise.all([
      getCourseFavoriteState(courseData.value.courseId).catch(() => null),
      loadComments(),
    ]);
    isFavor.value = favorResult?.data?.data === true;
  } catch (error) {
    courseData.value = null;
    courseError.value = apiErrorMessage(error, '课程加载失败，请稍后重试。');
    reportClientError(error, 'frontend/src/views/course/CourseView.vue');
  } finally {
    courseLoading.value = false;
  }
}

function retryCourse() {
  if (courseData.value?.courseName) return openCourse(courseData.value.courseName);
}

async function starCourse() {
  if (!courseData.value?.courseId) return;
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
    reportClientError(error, 'frontend/src/views/course/CourseView.vue');
  }
}

async function submitComment() {
  if (await submitCommentState()) ElMessage.success('评论成功');
}

async function submitReply(fatherId) {
  if (await submitReplyState(fatherId)) ElMessage.success('回复成功');
}

verifyUser().then((verified) => {
  if (verified) loadCourseModules();
});
</script>

<style scoped>
.course-browser {
  min-width: 0;
  padding: clamp(16px, 3vw, 32px);
}
.comments-pagination {
  justify-content: center;
}
.course-browser__content {
  width: min(100%, var(--cc4c-content-max-width));
  margin: 0 auto;
}
.page-heading {
  margin-bottom: 24px;
}
.page-heading__eyebrow {
  margin: 0 0 6px;
  color: var(--cc4c-primary);
  font-size: 0.8125rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.page-heading h1 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(2rem, 4vw, 3rem);
}
.page-heading p:not(.page-heading__eyebrow) {
  margin: 8px 0 0;
  color: var(--cc4c-muted);
}
.language-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  align-items: center;
  padding: 16px 18px;
  margin-bottom: 20px;
  border: 1px solid var(--cc4c-border);
  border-radius: var(--cc4c-radius);
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}
.language-bar__label {
  color: var(--cc4c-text);
  font-weight: 700;
}
.language-picker {
  display: flex;
  flex-wrap: wrap;
}
.language-option {
  display: inline-flex;
  min-height: 32px;
  gap: 5px;
  align-items: center;
}
.language-option :deep(.el-image) {
  width: 22px;
  height: 22px;
}
.course-workspace {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(210px, 280px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.course-directory,
.course-reader {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--cc4c-border);
  border-radius: var(--cc4c-radius);
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}
.course-directory__heading {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.course-directory__heading h2,
.course-reader__heading h2 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: 1.2rem;
}
.course-directory__heading span {
  color: var(--cc4c-primary);
  font-size: 0.8125rem;
  font-weight: 700;
}
.course-directory__items {
  display: grid;
  gap: 4px;
  padding: 0 4px 8px;
}
.course-directory__item {
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--cc4c-text);
  cursor: pointer;
  text-align: left;
}
.course-directory__item:hover,
.course-directory__item--active {
  background: #eff6ff;
  color: var(--cc4c-primary);
  font-weight: 700;
}
.course-reader__heading {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: start;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--cc4c-border);
}
.course-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.course-reader__article {
  min-width: 0;
  padding-top: 16px;
}
.course-reader__article :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
.course-reader__catalog {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--cc4c-border);
}
.course-reader__catalog summary {
  color: var(--cc4c-primary);
  font-weight: 700;
  cursor: pointer;
}
.comments {
  display: grid;
  gap: 16px;
}
.comment-compose,
.comment-item {
  display: flex;
  gap: 10px;
  align-items: start;
}
.comment-compose__input,
.comment-item__body {
  min-width: 0;
  flex: 1;
}
.comment-compose__input .el-button {
  margin-top: 8px;
}
.comments__error {
  margin: 0;
  color: var(--el-color-danger);
}
.comments__empty {
  padding: 18px;
  border: 1px dashed var(--cc4c-border);
  border-radius: 8px;
  color: var(--cc4c-muted);
  text-align: center;
}
.comment-item {
  padding: 14px 0;
  border-top: 1px solid var(--cc4c-border);
}
.comment-item__body > p,
.reply-item p {
  margin: 6px 0;
  color: var(--cc4c-text);
  line-height: 1.6;
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
  padding: 10px 12px;
  margin-top: 10px;
  border-left: 3px solid #bfdbfe;
  background: #f8fafc;
}

@media (max-width: 900px) {
  .course-workspace {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .course-browser {
    padding: 12px;
  }
  .language-bar {
    padding: 14px;
  }
  .language-picker {
    width: 100%;
  }
  .language-picker :deep(.el-radio-button__inner) {
    padding-inline: 8px;
  }
  .course-directory,
  .course-reader {
    padding: 16px;
  }
  .course-reader__heading {
    flex-direction: column;
  }
  .course-actions {
    width: 100%;
  }
  .course-actions .el-button {
    flex: 1;
    margin: 0;
  }
}
</style>
