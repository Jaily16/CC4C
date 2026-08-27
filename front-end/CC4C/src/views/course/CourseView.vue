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
              <md-editor v-model="text" :editor-id="editorState.id" preview-only />
            </div>
            <details class="course-reader__catalog">
              <summary>文章目录</summary>
              <md-catalog :editor-id="editorState.id" :scroll-element="scrollElement" />
            </details>
          </PageFeedback>
        </section>
      </section>
    </section>

    <el-drawer v-model="isCommentOpen" size="min(92vw, 520px)" direction="rtl" title="课程评论">
      <section class="comments" aria-label="课程评论">
        <div class="comment-compose">
          <el-avatar :size="36" :src="store.state.user.avatar || assets.defaultAvatar" />
          <div class="comment-compose__input">
            <label class="sr-only" for="course-comment">发表评论</label>
            <el-input id="course-comment" v-model="commentText" :rows="4" type="textarea" maxlength="1000" show-word-limit resize="none" placeholder="发表你的看法" />
            <el-button type="primary" :disabled="!courseData" @click="comment">发布评论</el-button>
          </div>
        </div>

        <p v-if="commentsError" class="comments__error">{{ commentsError }}</p>
        <div v-if="!commentsError && commentList.length === 0" class="comments__empty">还没有评论，来说说你的想法吧。</div>
        <article v-for="commentItem in commentList" :key="commentItem.commentId" class="comment-item">
          <el-avatar :size="34" :src="assets.defaultAvatar" />
          <div class="comment-item__body">
            <strong>{{ commentItem.userName || '用户' }}</strong>
            <p>{{ commentItem.content }}</p>
            <el-button link type="primary" @click="toggleReply(commentItem.commentId)">回复</el-button>
            <div v-if="replyingTo === commentItem.commentId" class="reply-compose">
              <label class="sr-only" :for="`reply-${commentItem.commentId}`">回复评论</label>
              <el-input :id="`reply-${commentItem.commentId}`" v-model="replyText" :rows="3" type="textarea" maxlength="1000" show-word-limit resize="none" placeholder="写下回复" />
              <el-button type="primary" size="small" @click="reply(commentItem.commentId)">发布回复</el-button>
            </div>
            <div v-for="subcomment in commentItem.subCommentList || []" :key="subcomment.commentId" class="reply-item">
              <strong>{{ subcomment.userName || '用户' }}</strong>
              <p>{{ subcomment.content }}</p>
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
          @current-change="changeCommentPage"
        />
      </section>
    </el-drawer>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from '@/plugins/axiosInstance';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { ElMessage } from 'element-plus';
import store from '@/store';
import { assets } from '@/assets';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';


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
const commentText = ref('');
const replyText = ref('');
const replyingTo = ref(null);
const commentList = ref([]);
const commentsError = ref('');
const commentPage = ref(1);
const commentPageSize = 10;
const commentTotal = ref(0);
const langs = [
  { no: '1', name: 'java', icon: assets.languageIcons.java },
  { no: '2', name: 'c++', icon: assets.languageIcons['c++'] },
  { no: '3', name: 'python', icon: assets.languageIcons.python },
  { no: '4', name: 'c', icon: assets.languageIcons.c },
];

function selectedLanguage() {
  return langs.find((lang) => lang.name === mainLang.value) || langs[0];
}

async function verifyUser() {
  try {
    const resp = await axios.get('/users/verify');
    if (resp.data.data === false) {
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
    const resp = await axios.get(`/courses/recommend/${language.no}/${store.state.user.major}`);
    courseModules.value = Array.isArray(resp.data.data) ? resp.data.data : [];
  } catch (error) {
    courseModules.value = [];
    modulesError.value = apiErrorMessage(error, '课程目录加载失败，请检查网络后重试。');
    console.error(error);
  } finally {
    modulesLoading.value = false;
  }
}

function selectLang() {
  courseData.value = null;
  text.value = '';
  isFavor.value = false;
  commentList.value = [];
  commentPage.value = 1;
  commentTotal.value = 0;
  return loadCourseModules();
}

async function loadComments() {
  if (!courseData.value?.courseId) {
    commentList.value = [];
    return;
  }
  commentsError.value = '';
  try {
    const resp = await axios.get(`/comments/course/${courseData.value.courseId}`, {
      params: { page: commentPage.value, size: commentPageSize },
    });
    commentList.value = resp.data.data?.items || [];
    commentTotal.value = resp.data.data?.total || 0;
  } catch (error) {
    commentList.value = [];
    commentTotal.value = 0;
    commentsError.value = apiErrorMessage(error, '评论加载失败，请稍后重试。');
    console.error(error);
  }
}

async function openCourse(courseName) {
  courseLoading.value = true;
  courseError.value = '';
  try {
    const courseResp = await axios.get(`/courses/${encodeURIComponent(courseName)}`);
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
      axios.get(`/courses/ifFavor/${store.state.user.id}/${courseData.value.courseId}`).catch(() => null),
      loadComments(),
    ]);
    isFavor.value = favorResult?.data?.data === true;
  } catch (error) {
    courseData.value = null;
    courseError.value = apiErrorMessage(error, '课程加载失败，请稍后重试。');
    console.error(error);
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
      ? await axios.delete(`/courses/deleteFavor/${store.state.user.id}/${courseData.value.courseId}`)
      : await axios.post(`/courses/star/${store.state.user.id}/${courseData.value.courseId}`);
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '收藏操作失败');
      return;
    }
    isFavor.value = !isFavor.value;
    ElMessage.success(isFavor.value ? '收藏成功' : '取消收藏成功');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '收藏操作失败，请稍后重试'));
    console.error(error);
  }
}

async function comment() {
  if (!courseData.value?.courseId) return;
  if (!commentText.value.trim()) {
    ElMessage.warning('评论内容不能为空');
    return;
  }
  try {
    const resp = await axios.post('/comments/course', {
      userId: store.state.user.id,
      content: commentText.value.trim(),
      courseId: courseData.value.courseId,
    });
    if (!resp.data.data) {
      ElMessage.error(resp.data.msg || '评论失败');
      return;
    }
    commentText.value = '';
    commentPage.value = 1;
    await loadComments();
    ElMessage.success('评论成功');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '评论失败，请稍后重试'));
    console.error(error);
  }
}

function toggleReply(commentId) {
  replyingTo.value = replyingTo.value === commentId ? null : commentId;
  replyText.value = '';
}

async function reply(fatherId) {
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空');
    return;
  }
  try {
    const resp = await axios.post('/comments/indirect', {
      userId: store.state.user.id,
      content: replyText.value.trim(),
      fatherId,
    });
    if (!resp.data.data) {
      ElMessage.error(resp.data.msg || '回复失败');
      return;
    }
    replyText.value = '';
    replyingTo.value = null;
    await loadComments();
    ElMessage.success('回复成功');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '回复失败，请稍后重试'));
    console.error(error);
  }
}

function changeCommentPage(page) {
  commentPage.value = page;
  replyingTo.value = null;
  return loadComments();
}

verifyUser().then((verified) => {
  if (verified) loadCourseModules();
});
</script>

<style scoped>
.course-browser { min-width: 0; padding: clamp(16px, 3vw, 32px); }
.comments-pagination { justify-content: center; }
.course-browser__content { width: min(100%, var(--cc4c-content-max-width)); margin: 0 auto; }
.page-heading { margin-bottom: 24px; }
.page-heading__eyebrow { margin: 0 0 6px; color: var(--cc4c-primary); font-size: .8125rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
.page-heading h1 { margin: 0; color: var(--cc4c-text); font-size: clamp(2rem, 4vw, 3rem); }
.page-heading p:not(.page-heading__eyebrow) { margin: 8px 0 0; color: var(--cc4c-muted); }
.language-bar { display: flex; flex-wrap: wrap; gap: 12px 18px; align-items: center; padding: 16px 18px; margin-bottom: 20px; border: 1px solid var(--cc4c-border); border-radius: var(--cc4c-radius); background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.language-bar__label { color: var(--cc4c-text); font-weight: 700; }
.language-picker { display: flex; flex-wrap: wrap; }
.language-option { display: inline-flex; min-height: 32px; gap: 5px; align-items: center; }
.language-option :deep(.el-image) { width: 22px; height: 22px; }
.course-workspace { display: grid; min-width: 0; grid-template-columns: minmax(210px, 280px) minmax(0, 1fr); gap: 20px; align-items: start; }
.course-directory, .course-reader { min-width: 0; padding: 20px; border: 1px solid var(--cc4c-border); border-radius: var(--cc4c-radius); background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.course-directory__heading { display: flex; gap: 10px; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.course-directory__heading h2, .course-reader__heading h2 { margin: 0; color: var(--cc4c-text); font-size: 1.2rem; }
.course-directory__heading span { color: var(--cc4c-primary); font-size: .8125rem; font-weight: 700; }
.course-directory__items { display: grid; gap: 4px; padding: 0 4px 8px; }
.course-directory__item { width: 100%; padding: 8px 10px; border: 0; border-radius: 6px; background: transparent; color: var(--cc4c-text); cursor: pointer; text-align: left; }
.course-directory__item:hover, .course-directory__item--active { background: #eff6ff; color: var(--cc4c-primary); font-weight: 700; }
.course-reader__heading { display: flex; flex-wrap: wrap; gap: 16px; align-items: start; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--cc4c-border); }
.course-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.course-reader__article { min-width: 0; padding-top: 16px; }
.course-reader__article :deep(.md-editor-preview-wrapper) { padding: 0; }
.course-reader__catalog { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--cc4c-border); }
.course-reader__catalog summary { color: var(--cc4c-primary); font-weight: 700; cursor: pointer; }
.comments { display: grid; gap: 16px; }
.comment-compose, .comment-item { display: flex; gap: 10px; align-items: start; }
.comment-compose__input, .comment-item__body { min-width: 0; flex: 1; }
.comment-compose__input .el-button { margin-top: 8px; }
.comments__error { margin: 0; color: var(--el-color-danger); }
.comments__empty { padding: 18px; border: 1px dashed var(--cc4c-border); border-radius: 8px; color: var(--cc4c-muted); text-align: center; }
.comment-item { padding: 14px 0; border-top: 1px solid var(--cc4c-border); }
.comment-item__body > p, .reply-item p { margin: 6px 0; color: var(--cc4c-text); line-height: 1.6; }
.reply-compose { display: grid; gap: 8px; margin-top: 8px; }
.reply-compose .el-button { justify-self: start; }
.reply-item { padding: 10px 12px; margin-top: 10px; border-left: 3px solid #bfdbfe; background: #f8fafc; }

@media (max-width: 900px) { .course-workspace { grid-template-columns: 1fr; } }
@media (max-width: 480px) { .course-browser { padding: 12px; } .language-bar { padding: 14px; } .language-picker { width: 100%; } .language-picker :deep(.el-radio-button__inner) { padding-inline: 8px; } .course-directory, .course-reader { padding: 16px; } .course-reader__heading { flex-direction: column; } .course-actions { width: 100%; } .course-actions .el-button { flex: 1; margin: 0; } }
</style>
