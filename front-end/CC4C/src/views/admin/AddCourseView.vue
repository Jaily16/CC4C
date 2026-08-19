<template>
  <section class="course-publisher" aria-labelledby="course-publisher-title">
    <header class="publisher-heading">
      <div>
        <p>Course publisher</p>
        <h1 id="course-publisher-title">发布新课程</h1>
        <span>依次完善课程信息、归属模块与课程正文。</span>
      </div>
      <aside class="required-note" role="note" aria-label="发布填写提示">
        <span aria-hidden="true">*</span>
        <div>
          <strong>发布检查</strong>
          <small>标有 * 的内容需要完整填写</small>
        </div>
      </aside>
    </header>

    <form class="publisher-form" @submit.prevent="publishCourse">
      <section class="publisher-card" aria-labelledby="course-basic-title">
        <header class="step-heading">
          <span>01</span>
          <div><h2 id="course-basic-title">基础信息</h2><p>设置用户在课程目录中首先看到的内容。</p></div>
        </header>
        <div class="basic-grid">
          <el-form-item label="课程标题 *" :error="formErrors.courseName">
            <el-input v-model="courseForm.courseName" maxlength="100" show-word-limit clearable placeholder="请输入唯一且清晰的课程标题" @input="formErrors.courseName = ''" />
          </el-form-item>
          <el-form-item label="课程难度 *">
            <el-select v-model="courseForm.level" placeholder="请选择课程难度">
              <el-option v-for="level in courseLevels" :key="level.value" :label="level.label" :value="level.value" />
            </el-select>
          </el-form-item>
        </div>
      </section>

      <section class="publisher-card" aria-labelledby="course-module-title">
        <header class="step-heading step-heading--action">
          <div class="step-heading__copy">
            <span>02</span>
            <div><h2 id="course-module-title">语言与课程模块</h2><p>课程必须归属一个已经存在的语言模块。</p></div>
          </div>
          <el-button :icon="Plus" @click="openModuleDialog">添加语言模块</el-button>
        </header>

        <div v-if="moduleLoading" class="module-feedback" aria-live="polite">
          <el-icon class="is-loading"><Loading /></el-icon><span>正在加载语言模块…</span>
        </div>
        <el-alert v-else-if="moduleError" type="error" :closable="false" show-icon :title="moduleError">
          <el-button type="primary" plain @click="loadModules">重新加载</el-button>
        </el-alert>
        <el-alert v-else-if="!moduleAvailable" type="warning" :closable="false" show-icon title="当前没有可用的语言模块，请先添加模块后再发布课程。" />

        <el-form-item class="module-select" label="课程语言模块 *" :error="formErrors.module">
          <el-cascader
            v-model="selectedModule"
            :options="modules"
            :disabled="moduleLoading || Boolean(moduleError) || !moduleAvailable"
            placeholder="先选择语言，再选择课程模块"
            clearable
            @change="formErrors.module = ''"
          />
          <p class="field-help">已加载 {{ moduleCount }} 个可用模块；模块选择决定课程在学习目录中的位置。</p>
        </el-form-item>
      </section>

      <section class="publisher-card" aria-labelledby="course-content-title">
        <header class="step-heading">
          <span>03</span>
          <div><h2 id="course-content-title">课程正文 *</h2><p>支持 Markdown 和图片，请在提交前检查内容结构。</p></div>
          <small><el-icon v-if="uploading" class="is-loading"><Loading /></el-icon>{{ editorStatus }}</small>
        </header>
        <div class="course-editor" :class="{ 'course-editor--error': formErrors.description }">
          <md-editor
            v-model="courseForm.description"
            :toolbars-exclude="['link', 'mermaid', 'katex', 'github']"
            @on-save="codeSave"
            @on-upload-img="onUploadImg"
          />
        </div>
        <p v-if="formErrors.description" class="field-error" role="alert">{{ formErrors.description }}</p>
      </section>

      <footer class="publisher-actions">
        <div>
          <strong>发布前检查</strong>
          <span>{{ publishDisabledReason || '课程信息完整，可以提交发布。' }}</span>
        </div>
        <el-button native-type="submit" type="primary" size="large" :loading="publishing" :disabled="publishDisabled">发布新课程</el-button>
      </footer>
    </form>

    <el-dialog v-model="moduleDialogOpen" title="添加语言模块" width="min(520px, calc(100vw - 32px))" @closed="resetModuleForm">
      <el-form label-position="top">
        <div class="dialog-grid">
          <el-form-item label="所属语言 *">
            <el-select v-model="moduleForm.languageId">
              <el-option v-for="language in languages" :key="language.value" :label="language.label" :value="language.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="模块名称 *" :error="moduleNameError">
            <el-input v-model="moduleForm.moduleName" maxlength="30" placeholder="例如：Java 基础" @input="moduleNameError = ''" />
          </el-form-item>
          <el-form-item label="模块难度 *">
            <el-select v-model="moduleForm.level">
              <el-option v-for="level in moduleLevels" :key="level.value" :label="level.label" :value="level.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="模块优先级 *">
            <el-select v-model="moduleForm.priority">
              <el-option v-for="priority in 11" :key="priority - 1" :label="priority - 1" :value="priority - 1" />
            </el-select>
          </el-form-item>
        </div>
        <p class="dialog-help">同一语言下的模块优先级不能重复。</p>
      </el-form>
      <template #footer>
        <el-button :disabled="moduleSubmitting" @click="moduleDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="moduleSubmitting" @click="addModule">添加模块</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Loading, Plus } from '@element-plus/icons-vue';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

axios.defaults.withCredentials = true;

const languages = [
  { value: 1, name: 'java', label: 'Java' },
  { value: 2, name: 'c++', label: 'C++' },
  { value: 3, name: 'python', label: 'Python' },
  { value: 4, name: 'c', label: 'C' },
];
const courseLevels = [
  { value: -2, label: '简单' },
  { value: -1, label: '简单且默认' },
  { value: 0, label: '默认' },
  { value: 1, label: '困难且默认' },
  { value: 2, label: '困难' },
  { value: 66, label: '必须展示' },
];
const moduleLevels = [
  { value: -1, label: '简单' },
  { value: 0, label: '默认' },
  { value: 1, label: '困难' },
];

const modules = ref(languages.map((language) => ({ label: language.label, value: language.name, disabled: true, children: [] })));
const selectedModule = ref([]);
const moduleLoading = ref(false);
const moduleError = ref('');
const moduleDialogOpen = ref(false);
const moduleSubmitting = ref(false);
const moduleNameError = ref('');
const publishing = ref(false);
const uploading = ref(false);
const courseForm = reactive({ courseName: '', description: '', level: 0 });
const moduleForm = reactive({ languageId: 1, moduleName: '', level: 0, priority: 1 });
const formErrors = reactive({ courseName: '', module: '', description: '' });

const moduleCount = computed(() => modules.value.reduce((total, language) => total + language.children.length, 0));
const moduleAvailable = computed(() => moduleCount.value > 0);
const editorStatus = computed(() => uploading.value ? '正在上传图片…' : `约 ${courseForm.description.trim().length} 个字符`);
const publishDisabledReason = computed(() => {
  if (moduleLoading.value) return '语言模块仍在加载中。';
  if (moduleError.value) return '语言模块加载失败，请重试。';
  if (!moduleAvailable.value) return '没有可用语言模块，请先添加模块。';
  if (uploading.value) return '图片仍在上传中。';
  return '';
});
const publishDisabled = computed(() => Boolean(publishDisabledReason.value) || publishing.value);

watch(() => courseForm.description, (value) => {
  if (value.trim()) formErrors.description = '';
});

function backendMessage(error, fallback) {
  return error?.response?.data?.msg || error?.response?.data?.MSG || fallback;
}

async function loadModules() {
  moduleLoading.value = true;
  moduleError.value = '';
  try {
    const responses = await Promise.all(languages.map((language) => axios.get(`http://localhost:4080/courses/module/${language.value}`)));
    modules.value = languages.map((language, index) => {
      const children = (responses[index].data.data || []).map((module) => ({
        label: module.moduleName,
        value: module.priority,
        languageId: module.languageId,
      }));
      return { label: language.label, value: language.name, disabled: children.length === 0, children };
    });
  } catch (error) {
    moduleError.value = backendMessage(error, '课程模块加载失败，请稍后重试。');
    modules.value = languages.map((language) => ({ label: language.label, value: language.name, disabled: true, children: [] }));
    console.error(error);
  } finally {
    moduleLoading.value = false;
  }
}

function openModuleDialog() {
  moduleDialogOpen.value = true;
}

function resetModuleForm() {
  moduleForm.languageId = 1;
  moduleForm.moduleName = '';
  moduleForm.level = 0;
  moduleForm.priority = 1;
  moduleNameError.value = '';
}

async function addModule() {
  moduleNameError.value = moduleForm.moduleName.trim() ? '' : '请输入模块名称。';
  if (moduleNameError.value || moduleSubmitting.value) return;
  moduleSubmitting.value = true;
  try {
    const response = await axios.post('http://localhost:4080/courses/module', {
      languageId: moduleForm.languageId,
      moduleName: moduleForm.moduleName.trim(),
      level: moduleForm.level,
      priority: moduleForm.priority,
    });
    if (response.data.data !== true) {
      ElMessage.error(response.data.msg || '课程模块添加失败');
      return;
    }
    moduleDialogOpen.value = false;
    await loadModules();
    ElMessage.success(response.data.msg || '课程模块添加成功');
  } catch (error) {
    ElMessage.error(backendMessage(error, '课程模块添加失败，请稍后重试'));
    console.error(error);
  } finally {
    moduleSubmitting.value = false;
  }
}

function validateCourse() {
  formErrors.courseName = courseForm.courseName.trim() ? '' : '请输入课程标题。';
  formErrors.module = selectedModule.value.length === 2 ? '' : '请选择课程语言模块。';
  formErrors.description = courseForm.description.trim() ? '' : '请输入课程正文。';
  return !formErrors.courseName && !formErrors.module && !formErrors.description;
}

async function publishCourse() {
  if (!validateCourse() || publishDisabled.value) return;
  const language = languages.find((item) => item.name === selectedModule.value[0]);
  if (!language) {
    formErrors.module = '所选语言无效，请重新选择。';
    return;
  }
  publishing.value = true;
  try {
    const response = await axios.post('http://localhost:4080/courses/add', {
      languageName: language.name,
      languageId: language.value,
      courseName: courseForm.courseName.trim(),
      description: courseForm.description,
      level: courseForm.level,
      priority: selectedModule.value[1],
    });
    if (response.data.data !== true) {
      ElMessage.error(response.data.msg || '课程发布失败');
      return;
    }
    ElMessage.success(response.data.msg || '课程发布成功');
    courseForm.courseName = '';
    courseForm.description = '';
    courseForm.level = 0;
    selectedModule.value = [];
  } catch (error) {
    ElMessage.error(backendMessage(error, '课程发布失败，请稍后重试'));
    console.error(error);
  } finally {
    publishing.value = false;
  }
}

function codeSave() {
  ElMessage.info('内容保留在当前编辑器中，发布后才会保存为课程');
}

async function onUploadImg(files, callback) {
  uploading.value = true;
  try {
    const responses = await Promise.all(files.map(async (file) => {
      const formData = new FormData();
      formData.append('file', file);
      const response = await axios.post('http://localhost:4080/blogs/uploadImg', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (!response.data.url) throw new Error(response.data.MSG || '图片上传失败');
      return response.data.url;
    }));
    callback(responses);
    ElMessage.success('图片上传成功');
  } catch (error) {
    ElMessage.error(backendMessage(error, error.message || '图片上传失败，请稍后重试'));
    console.error(error);
  } finally {
    uploading.value = false;
  }
}

loadModules();
</script>

<style scoped>
.course-publisher { display: grid; gap: 24px; width: min(100%, 1180px); margin: 0 auto; }
.publisher-heading { display: flex; gap: 24px; align-items: flex-end; justify-content: space-between; }
.publisher-heading p { margin: 0 0 5px; color: var(--cc4c-primary); font-size: .72rem; font-weight: 900; letter-spacing: .12em; text-transform: uppercase; }
.publisher-heading h1 { margin: 0; font-size: clamp(1.65rem, 3vw, 2.25rem); }
.publisher-heading span { display: block; margin-top: 8px; color: var(--cc4c-muted); }
.required-note { display: flex; flex: 0 0 auto; gap: 11px; align-items: center; min-width: 210px; padding: 11px 14px; border: 1px solid #d8e5f8; border-radius: 14px; background: linear-gradient(135deg, rgba(255, 255, 255, .96), #f1f6ff); box-shadow: 0 8px 20px rgba(37, 99, 235, .08); }
.required-note > span { display: grid; width: 34px; height: 34px; margin: 0; place-items: center; border-radius: 10px; background: linear-gradient(135deg, #2563eb, #60a5fa); color: #fff; font-size: 1.05rem; font-weight: 900; box-shadow: 0 6px 14px rgba(37, 99, 235, .2); }
.required-note div { display: grid; gap: 2px; }
.required-note strong { color: var(--cc4c-text); font-size: .8rem; }
.required-note small { color: var(--cc4c-muted); font-size: .7rem; white-space: nowrap; }
.publisher-form { display: grid; gap: 18px; }
.publisher-card { min-width: 0; padding: clamp(20px, 3vw, 30px); border: 1px solid var(--cc4c-border); border-radius: 17px; background: #fff; box-shadow: 0 9px 26px rgba(15, 23, 42, .05); }
.step-heading { display: flex; gap: 14px; align-items: center; margin-bottom: 22px; }
.step-heading > span, .step-heading__copy > span { display: grid; flex: 0 0 auto; width: 42px; height: 42px; place-items: center; border-radius: 12px; background: #eaf1ff; color: var(--cc4c-primary); font-size: .72rem; font-weight: 900; }
.step-heading h2 { margin: 0; font-size: 1.08rem; }
.step-heading p { margin: 4px 0 0; color: var(--cc4c-muted); font-size: .8rem; }
.step-heading small { display: inline-flex; gap: 5px; align-items: center; margin-left: auto; color: var(--cc4c-muted); }
.step-heading--action { justify-content: space-between; }
.step-heading__copy { display: flex; gap: 14px; align-items: center; }
.basic-grid, .dialog-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.publisher-card :deep(.el-form-item) { margin-bottom: 0; }
.publisher-card :deep(.el-select), .publisher-card :deep(.el-cascader), .dialog-grid :deep(.el-select) { width: 100%; }
.module-select { margin-top: 20px !important; }
.field-help { width: 100%; margin: 7px 0 0; color: var(--cc4c-muted); font-size: .75rem; }
.module-feedback { display: flex; gap: 9px; align-items: center; min-height: 66px; padding: 16px; border-radius: 12px; background: #f4f8ff; color: var(--cc4c-primary); }
.course-editor { min-width: 0; overflow: hidden; border: 1px solid var(--cc4c-border); border-radius: 13px; }
.course-editor--error { border-color: var(--el-color-danger); }
.course-editor :deep(.md-editor) { height: min(620px, 68vh); min-height: 420px; }
.field-error { margin: 8px 0 0; color: var(--el-color-danger); font-size: .78rem; }
.publisher-actions { display: flex; gap: 22px; align-items: center; justify-content: space-between; padding: 20px 24px; border: 1px solid #cfe0fb; border-radius: 15px; background: #f3f7ff; }
.publisher-actions div { display: grid; gap: 4px; }
.publisher-actions strong { color: var(--cc4c-text); }
.publisher-actions span { color: var(--cc4c-muted); font-size: .78rem; }
.dialog-help { margin: 0; color: var(--cc4c-muted); font-size: .78rem; }

@media (max-width: 700px) {
  .publisher-heading, .publisher-actions { align-items: flex-start; flex-direction: column; }
  .required-note { box-sizing: border-box; width: 100%; min-width: 0; }
  .publisher-actions .el-button { width: 100%; }
  .basic-grid, .dialog-grid { grid-template-columns: 1fr; }
  .step-heading--action { align-items: flex-start; flex-direction: column; }
  .step-heading--action .el-button { width: 100%; }
  .course-editor :deep(.md-editor) { min-height: 360px; }
}
</style>
