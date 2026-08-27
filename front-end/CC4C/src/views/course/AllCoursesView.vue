<template>
  <main class="discovery-page">
    <section class="discovery-page__content" aria-labelledby="courses-title">
      <header class="discovery-hero">
        <div>
          <p class="discovery-hero__eyebrow">课程发现</p>
          <h1 id="courses-title">找到适合你的下一门课</h1>
          <p>按语言浏览，或搜索你想学习的主题。每门课程都从清晰的学习路径开始。</p>
        </div>
        <div class="discovery-hero__stat" aria-label="当前结果数量">
          <strong>{{ allCourses.length }}</strong>
          <span>{{ isSearching ? '个搜索结果' : '门可学课程' }}</span>
        </div>
      </header>

      <section class="discovery-toolbar" aria-label="课程筛选与搜索">
        <div class="toolbar-section">
          <span class="toolbar-label">选择语言</span>
          <el-radio-group v-model="mainLang" class="language-picker" @change="loadLanguageCourses">
            <el-radio-button v-for="lang in langs" :key="lang.name" :label="lang.name">
              <span class="language-option">
                <img class="language-option__icon" :src="lang.icon" :alt="lang.name" />
                <span>{{ lang.name }}</span>
              </span>
            </el-radio-button>
          </el-radio-group>
        </div>

        <form class="discovery-search" @submit.prevent="searchCourses">
          <label class="sr-only" for="course-search">搜索课程</label>
          <el-input id="course-search" v-model.trim="searchInfo" :prefix-icon="Search" clearable placeholder="搜索课程名称或关键词" @clear="clearSearch" />
          <el-button native-type="submit" type="primary">搜索课程</el-button>
        </form>
      </section>

      <div class="collection-heading">
        <div>
          <p class="collection-heading__eyebrow">课程目录</p>
          <h2>{{ resultLabel }}</h2>
        </div>
        <el-button v-if="isSearching" link type="primary" @click="clearSearch">清除搜索，恢复语言课程</el-button>
      </div>

      <PageFeedback
        :loading="loading"
        :empty="!loading && !errorMessage && allCourses.length === 0"
        :error="errorMessage"
        empty-title="没有匹配的课程"
        empty-description="可以切换语言或调整搜索关键词。"
        @retry="retryLoad"
      >
        <div class="course-grid">
          <article v-for="course in allCourses" :key="course.courseId" class="course-card" role="link" tabindex="0" @click="openCourse(course.courseName)" @keydown.enter="openCourse(course.courseName)">
            <div class="course-card__visual">
              <img class="course-card__image" :src="assets.languageCards[course.languageName]" :alt="`${course.languageName || '课程'}课程`" />
              <span class="course-card__language">{{ course.languageName || mainLang }}</span>
            </div>
            <div class="course-card__body">
              <h3>{{ course.courseName }}</h3>
              <p>{{ courseDifficulty(course) }}</p>
              <span class="course-card__action">查看课程 <span aria-hidden="true">→</span></span>
            </div>
          </article>
        </div>
      </PageFeedback>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue';
import axios from '@/plugins/axiosInstance';
import { Search } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { assets } from '@/assets';
import PageFeedback from '@/components/common/PageFeedback.vue';

const router = useRouter();
const mainLang = ref('java');
const searchInfo = ref('');
const allCourses = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const isSearching = ref(false);
const langs = [
  { name: 'java', icon: assets.languageIcons.java },
  { name: 'c++', icon: assets.languageIcons['c++'] },
  { name: 'python', icon: assets.languageIcons.python },
  { name: 'c', icon: assets.languageIcons.c },
];

const resultLabel = computed(() => {
  if (loading.value) return '正在加载课程…';
  if (isSearching.value) return `“${searchInfo.value}” 的搜索结果`;
  return `${mainLang.value} 课程推荐`;
});

const courseLevelLabels = {
  '-2': '难度：简单',
  '-1': '难度：简单且默认',
  '0': '难度：默认',
  '1': '难度：困难且默认',
  '2': '难度：困难',
  '66': '难度：必须展示',
};

function courseDifficulty(course) {
  const level = course.level ?? course.difficulty;
  if (level === null || level === undefined || level === '') {
    return '系统化学习路径';
  }
  return courseLevelLabels[String(level)] || '系统化学习路径';
}

function openCourse(courseName) {
  router.push({ path: '/courseDetail', query: { courseName } });
}

async function requestCourses(url) {
  loading.value = true;
  errorMessage.value = '';
  try {
    const resp = await axios.get(url);
    allCourses.value = Array.isArray(resp.data.data) ? resp.data.data : [];
  } catch (error) {
    allCourses.value = [];
    errorMessage.value = '课程加载失败，请检查网络后重试。';
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function loadLanguageCourses() {
  isSearching.value = false;
  searchInfo.value = '';
  return requestCourses(`/courses/language/${encodeURIComponent(mainLang.value)}`);
}

function searchCourses() {
  if (!searchInfo.value) return loadLanguageCourses();
  isSearching.value = true;
  return requestCourses(`/courses/search/${encodeURIComponent(searchInfo.value)}`);
}

function clearSearch() {
  searchInfo.value = '';
  return loadLanguageCourses();
}

function retryLoad() {
  return isSearching.value ? searchCourses() : loadLanguageCourses();
}

loadLanguageCourses();
</script>

<style scoped>
.discovery-page { min-width: 0; padding: clamp(16px, 3vw, 36px); }
.discovery-page__content { width: min(100%, 1180px); margin: 0 auto; }
.discovery-hero { display: flex; gap: 28px; align-items: end; justify-content: space-between; padding: clamp(26px, 5vw, 50px); border-radius: 20px; background: var(--cc4c-discovery-hero); color: white; box-shadow: 0 18px 36px rgba(30, 64, 175, .2); }
.discovery-hero__eyebrow, .collection-heading__eyebrow { margin: 0 0 8px; font-size: .78rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.discovery-hero__eyebrow { color: #bfdbfe; }
.discovery-hero h1 { max-width: 740px; margin: 0; font-size: clamp(2rem, 4vw, 3.35rem); line-height: 1.14; }
.discovery-hero p:not(.discovery-hero__eyebrow) { max-width: 660px; margin: 14px 0 0; color: #dbeafe; line-height: 1.65; }
.discovery-hero__stat { display: grid; min-width: 118px; padding: 16px 18px; border: 1px solid rgba(255,255,255,.3); border-radius: 14px; background: rgba(255,255,255,.14); backdrop-filter: blur(8px); text-align: center; }
.discovery-hero__stat strong { font-size: 2rem; line-height: 1; }
.discovery-hero__stat span { margin-top: 6px; color: #dbeafe; font-size: .8125rem; }
.discovery-toolbar { display: flex; flex-wrap: wrap; gap: 20px; align-items: end; justify-content: space-between; padding: 20px; margin-top: 24px; border: 1px solid var(--cc4c-border); border-radius: 16px; background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.toolbar-section { min-width: 0; }
.toolbar-label { display: block; margin-bottom: 9px; color: var(--cc4c-text); font-size: .875rem; font-weight: 700; }
.language-picker { display: flex; flex-wrap: wrap; gap: 6px; }
.language-picker :deep(.el-radio-button__inner) { display: flex; align-items: center; padding: 8px 11px; border: 1px solid var(--cc4c-border) !important; border-radius: 9px !important; box-shadow: none !important; }
.language-picker :deep(.el-radio-button:first-child .el-radio-button__inner), .language-picker :deep(.el-radio-button:last-child .el-radio-button__inner) { border-radius: 9px !important; }
.language-option { display: inline-flex; min-height: 24px; gap: 6px; align-items: center; }
.language-option__icon { display: block; width: 22px; height: 22px; object-fit: contain; }
.discovery-search { display: flex; min-width: min(100%, 390px); gap: 9px; }
.discovery-search :deep(.el-input) { min-width: 0; flex: 1; }
.collection-heading { display: flex; gap: 16px; align-items: center; justify-content: space-between; margin: 30px 0 16px; }
.collection-heading__eyebrow { color: var(--cc4c-primary); }
.collection-heading h2 { margin: 0; color: var(--cc4c-text); font-size: 1.3rem; }
.course-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; }
.course-card { overflow: hidden; border: 1px solid var(--cc4c-border); border-radius: 16px; background: var(--cc4c-surface); box-shadow: 0 5px 15px rgba(15,23,42,.05); cursor: pointer; transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease; }
.course-card:hover, .course-card:focus-visible { border-color: var(--cc4c-primary); box-shadow: 0 18px 32px rgba(37,99,235,.16); outline: none; transform: translateY(-6px); }
.course-card__visual { position: relative; overflow: hidden; height: 172px; background: #eff6ff; }
.course-card__image { display: block; width: 100%; height: 100%; object-fit: cover; transition: transform 260ms ease, filter 260ms ease; }
.course-card:hover .course-card__image, .course-card:focus-visible .course-card__image { filter: saturate(1.08); transform: scale(1.06); }
.course-card__language { position: absolute; top: 14px; left: 14px; padding: 5px 9px; border-radius: 99px; background: rgba(15,32,51,.75); color: white; font-size: .75rem; font-weight: 700; text-transform: uppercase; }
.course-card__body { display: grid; min-height: 165px; gap: 10px; padding: 20px; }
.course-card h3 { margin: 0; color: var(--cc4c-text); font-size: 1.05rem; line-height: 1.5; transition: color 180ms ease; }
.course-card p { margin: 0; color: var(--cc4c-muted); font-size: .875rem; }
.course-card__action { align-self: end; color: var(--cc4c-primary); font-size: .875rem; font-weight: 700; transition: color 180ms ease, transform 180ms ease; }
.course-card:hover h3, .course-card:focus-visible h3, .course-card:hover .course-card__action, .course-card:focus-visible .course-card__action { color: var(--cc4c-primary-hover); }
.course-card:hover .course-card__action, .course-card:focus-visible .course-card__action { transform: translateX(5px); }
@media (max-width: 1024px) { .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 768px) { .discovery-hero { align-items: start; flex-direction: column; } .discovery-toolbar { align-items: stretch; } .discovery-search { width: 100%; max-width: none; } }
@media (max-width: 480px) { .discovery-page { padding: 12px; } .discovery-hero { padding: 24px 20px; border-radius: 16px; } .discovery-hero__stat { width: 100%; grid-template-columns: auto auto; align-items: center; justify-content: center; gap: 8px; } .discovery-hero__stat span { margin: 0; } .discovery-toolbar { padding: 16px; } .language-picker { width: 100%; } .language-picker :deep(.el-radio-button__inner) { padding: 7px 8px; } .discovery-search { flex-wrap: wrap; } .discovery-search .el-button { width: 100%; } .collection-heading { align-items: start; flex-direction: column; } .course-grid { grid-template-columns: 1fr; gap: 18px; } .course-card__visual { height: 190px; } }
</style>
