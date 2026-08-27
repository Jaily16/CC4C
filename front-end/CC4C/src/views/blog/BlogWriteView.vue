<template>
  <main class="blog-write">
    <section class="blog-write__content" aria-labelledby="blog-write-title">
      <header class="page-heading">
        <p class="page-heading__eyebrow">创作中心</p>
        <h1 id="blog-write-title">撰写博客</h1>
        <p>记录学习过程和实践经验，提交后将进入管理员审核流程。</p>
      </header>

      <PageFeedback :loading="pageLoading" :error="pageError" @retry="initializePage">
        <form class="editor-card" @submit.prevent="publish">
          <section class="editor-section" aria-labelledby="basic-info-title">
            <div class="editor-section__heading">
              <div>
                <span>01</span>
                <h2 id="basic-info-title">文章信息</h2>
              </div>
              <small><b>*</b> 发布时必填</small>
            </div>

            <div class="basic-grid">
              <div class="form-field">
                <label for="blog-title">文章标题 <b aria-hidden="true">*</b></label>
                <el-input id="blog-title" v-model="title" maxlength="75" show-word-limit clearable placeholder="用清晰的标题概括文章主题" @input="titleError = ''" />
                <p v-if="titleError" class="field-error" role="alert">{{ titleError }}</p>
                <p v-else class="field-help">建议控制在 15–40 个字，便于读者快速理解。</p>
              </div>

              <fieldset class="form-field language-field">
                <legend>涉及语言 <b aria-hidden="true">*</b></legend>
                <el-checkbox-group v-model="langList" class="language-options" @change="languageError = ''">
                  <el-checkbox v-for="lang in languages" :key="lang.value" :label="lang.value">{{ lang.label }}</el-checkbox>
                </el-checkbox-group>
                <p v-if="languageError" class="field-error" role="alert">{{ languageError }}</p>
                <p v-else class="field-help">可多选，帮助文章被准确分类。</p>
              </fieldset>
            </div>
          </section>

          <section class="editor-section editor-section--content" aria-labelledby="content-title">
            <div class="editor-section__heading">
              <div>
                <span>02</span>
                <h2 id="content-title">文章正文 <b aria-hidden="true">*</b></h2>
              </div>
              <div class="editor-status" aria-live="polite">
                <el-icon v-if="uploading" class="is-loading"><Loading /></el-icon>
                <span>{{ editorStatus }}</span>
              </div>
            </div>

            <div class="markdown-editor" :class="{ 'markdown-editor--error': contentError }">
              <md-editor
                v-model="text"
                :toolbars-exclude="['link', 'mermaid', 'katex', 'github']"
                @on-save="codeSave"
                @on-upload-img="onUploadImg"
              />
            </div>
            <p v-if="contentError" class="field-error" role="alert">{{ contentError }}</p>
            <p v-else class="field-help">支持 Markdown；上传图片时请等待右上角状态恢复后再提交。</p>
          </section>

          <footer class="editor-actions">
            <p>草稿只保存当前正文；提交审核需要标题、语言和正文完整填写。</p>
            <div>
              <el-button v-if="hasDraft" size="large" type="danger" plain :disabled="draftSaving || publishSubmitting || uploading" @click="deleteDraft">删除草稿</el-button>
              <el-button size="large" :loading="draftSaving" :disabled="publishSubmitting || uploading" @click="draft">保存草稿</el-button>
              <el-button native-type="submit" type="primary" size="large" :loading="publishSubmitting" :disabled="draftSaving || uploading">提交审核</el-button>
            </div>
          </footer>
        </form>
      </PageFeedback>
    </section>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { Loading } from '@element-plus/icons-vue';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import axios from '@/plugins/axiosInstance';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import store from '@/store';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';


const router = useRouter();
const title = ref('');
const hasDraft = ref(false);
const langList = ref([]);
const text = ref('');
const pageLoading = ref(false);
const pageError = ref('');
const uploading = ref(false);
const draftSaving = ref(false);
const publishSubmitting = ref(false);
const titleError = ref('');
const languageError = ref('');
const contentError = ref('');
const languages = [
  { value: 1, label: 'Java' },
  { value: 2, label: 'C++' },
  { value: 3, label: 'Python' },
  { value: 4, label: 'C' },
];

const editorStatus = computed(() => {
  if (uploading.value) return '正在上传图片…';
  if (!text.value.trim()) return '正文尚未开始';
  return `已输入约 ${text.value.trim().length} 个字符`;
});

watch(text, (value) => {
  if (value.trim()) contentError.value = '';
});

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

async function loadDraft() {
  try {
    const resp = await axios.get(`/blogs/draft/${store.state.user.id}`);
    if (resp.data.data != null) {
      text.value = resp.data.data;
      hasDraft.value = true;
      ElMessage.info('已恢复上次保存的草稿正文');
    } else {
      hasDraft.value = false;
    }
  } catch (error) {
    pageError.value = apiErrorMessage(error, '草稿读取失败，请重试。');
    console.error(error);
  }
}

async function initializePage() {
  pageLoading.value = true;
  pageError.value = '';
  const verified = await verifyUser();
  if (verified) await loadDraft();
  pageLoading.value = false;
}

function codeSave() {
  ElMessage.info('当前内容仍保留在编辑器中，如需持久保存请点击“保存草稿”');
}

function validatePublish() {
  titleError.value = title.value.trim() ? '' : '请输入文章标题。';
  languageError.value = langList.value.length > 0 ? '' : '请至少选择一种文章语言。';
  contentError.value = text.value.trim() ? '' : '请输入文章正文。';
  return !titleError.value && !languageError.value && !contentError.value;
}

async function publish() {
  if (!validatePublish() || publishSubmitting.value || uploading.value) return;
  publishSubmitting.value = true;
  try {
    const resp = await axios.post('/blogs/submit', {
      writerId: store.state.user.id,
      title: title.value.trim(),
      languageList: langList.value,
      content: text.value,
    });
    if (!resp.data.data) {
      ElMessage.error(resp.data.msg || '博客发布失败');
      return;
    }
    ElMessage.success('博客已提交审核');
    title.value = '';
    langList.value = [];
    text.value = '';
    hasDraft.value = false;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '博客发布失败，请稍后重试'));
    console.error(error);
  } finally {
    publishSubmitting.value = false;
  }
}

async function draft() {
  contentError.value = '';
  if (!text.value.trim()) {
    contentError.value = '请输入正文后再保存草稿。';
    return;
  }
  if (draftSaving.value || uploading.value) return;
  draftSaving.value = true;
  try {
    const resp = await axios.put('/blogs/draft', {
      userId: store.state.user.id,
      content: text.value,
    });
    if (resp.data.data !== true) {
      ElMessage.error(resp.data.msg || '草稿保存失败');
      return;
    }
    hasDraft.value = true;
    ElMessage.success('已保存草稿');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '草稿保存失败，请稍后重试'));
    console.error(error);
  } finally {
    draftSaving.value = false;
  }
}

async function deleteDraft() {
  if (!hasDraft.value || draftSaving.value || publishSubmitting.value || uploading.value) return;
  draftSaving.value = true;
  try {
    const resp = await axios.delete(`/blogs/draft/${store.state.user.id}`);
    if (!resp.data.data) {
      ElMessage.error(resp.data.msg || '草稿删除失败');
      return;
    }
    text.value = '';
    hasDraft.value = false;
    ElMessage.success('草稿已删除');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '草稿删除失败，请稍后重试'));
  } finally {
    draftSaving.value = false;
  }
}

async function onUploadImg(files, callback) {
  if (!files?.length) return;
  uploading.value = true;
  try {
    const responses = await Promise.all(files.map(async (file) => {
      const form = new FormData();
      form.append('file', file);
      const resp = await axios.post('/blogs/uploadImg', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (!resp.data?.url) {
        throw new Error(resp.data?.MSG || '图片上传失败');
      }
      return resp.data.url;
    }));
    callback(responses);
    ElMessage.success(files.length > 1 ? `${files.length} 张图片上传成功` : '图片上传成功');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, error.message || '图片上传失败，请检查格式后重试'));
    console.error(error);
  } finally {
    uploading.value = false;
  }
}

initializePage();
</script>

<style scoped>
.blog-write { min-width: 0; padding: clamp(16px, 3vw, 36px); }
.blog-write__content { width: min(100%, 1180px); margin: 0 auto; }
.page-heading { margin-bottom: 24px; }
.page-heading__eyebrow { margin: 0 0 6px; color: var(--cc4c-primary); font-size: .8125rem; font-weight: 800; letter-spacing: .07em; text-transform: uppercase; }
.page-heading h1 { margin: 0; color: var(--cc4c-text); font-size: clamp(2rem, 4vw, 3rem); }
.page-heading p:not(.page-heading__eyebrow) { margin: 8px 0 0; color: var(--cc4c-muted); }
.editor-card { overflow: hidden; border: 1px solid var(--cc4c-border); border-radius: 16px; background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.editor-section { padding: clamp(20px, 3.5vw, 36px); border-bottom: 1px solid var(--cc4c-border); }
.editor-section__heading { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; margin-bottom: 22px; }
.editor-section__heading > div:first-child { display: flex; gap: 10px; align-items: center; }
.editor-section__heading > div:first-child > span { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 9px; background: #eff6ff; color: var(--cc4c-primary); font-size: .75rem; font-weight: 800; }
.editor-section__heading h2 { margin: 0; color: var(--cc4c-text); font-size: 1.25rem; }
.editor-section__heading small, .field-help { color: var(--cc4c-muted); }
.editor-section__heading b, .form-field b { color: var(--el-color-danger); }
.basic-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 24px; }
.form-field { min-width: 0; padding: 0; margin: 0; border: 0; }
.form-field label, .form-field legend { display: block; padding: 0; margin-bottom: 9px; color: var(--cc4c-text); font-weight: 700; }
.language-options { display: flex; min-height: 40px; flex-wrap: wrap; gap: 2px 12px; align-items: center; }
.field-help, .field-error { margin: 7px 0 0; font-size: .8125rem; }
.field-error { color: var(--el-color-danger); }
.editor-status { display: inline-flex; gap: 6px; align-items: center; color: var(--cc4c-muted); font-size: .8125rem; }
.markdown-editor { min-width: 0; overflow: hidden; border: 1px solid transparent; border-radius: 10px; }
.markdown-editor--error { border-color: var(--el-color-danger); }
.markdown-editor :deep(.md-editor) { height: clamp(480px, 65vh, 700px); }
.editor-actions { display: flex; flex-wrap: wrap; gap: 18px; align-items: center; justify-content: space-between; padding: 22px clamp(20px, 3.5vw, 36px); background: #f8fafc; }
.editor-actions p { max-width: 620px; margin: 0; color: var(--cc4c-muted); font-size: .875rem; }
.editor-actions > div { display: flex; flex-wrap: wrap; gap: 10px; }
.editor-actions :deep(.el-button) { margin: 0; }
@media (max-width: 768px) { .basic-grid { grid-template-columns: 1fr; } }
@media (max-width: 480px) { .blog-write { padding: 12px; } .editor-section { padding: 20px 16px; } .markdown-editor { overflow-x: auto; } .markdown-editor :deep(.md-editor) { min-width: 0; height: 540px; } .editor-actions { align-items: stretch; flex-direction: column; padding: 18px 16px; } .editor-actions > div { display: grid; grid-template-columns: 1fr; } .editor-actions :deep(.el-button) { width: 100%; } }
</style>
