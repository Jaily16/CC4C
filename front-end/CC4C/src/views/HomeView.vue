<template>
  <main class="home-page">
    <div class="home-page__content">
      <section class="welcome-panel" aria-labelledby="welcome-title">
        <div>
          <p class="section-eyebrow">CC4C 学习社区</p>
          <h1 id="welcome-title">发现适合你的课程与技术分享</h1>
          <p>从精选课程开始学习，在社区博客中获取实战经验。</p>
        </div>
        <div class="welcome-panel__actions">
          <router-link class="primary-link" to="/allCourses">浏览所有课程</router-link>
          <router-link class="secondary-link" to="/allBlogs">浏览全部博客</router-link>
        </div>
      </section>

      <section class="content-section partner-section" aria-labelledby="partner-title">
        <div class="section-heading">
          <div>
            <p class="section-eyebrow">合作伙伴</p>
            <h2 id="partner-title">技术学习资源</h2>
          </div>
        </div>
        <el-carousel class="partner-carousel" type="card" height="clamp(180px, 35vw, 350px)">
          <el-carousel-item v-for="item in langDisplay" :key="item">
            <el-image :src="item" fit="cover" alt="技术学习资源" />
          </el-carousel-item>
        </el-carousel>
      </section>

      <section class="content-section" aria-labelledby="course-title">
        <div class="section-heading">
          <div>
            <p class="section-eyebrow">继续学习</p>
            <h2 id="course-title">课程推荐</h2>
          </div>
          <router-link class="section-link" to="/allCourses">查看全部课程</router-link>
        </div>

        <PageFeedback
          :loading="coursesLoading"
          :empty="!coursesLoading && !coursesError && courses.length === 0"
          :error="coursesError"
          empty-title="暂无推荐课程"
          empty-description="稍后再来看看，或直接浏览全部课程。"
          @retry="loadCourses"
        >
          <div class="course-grid">
            <article
              v-for="course in courses.slice(0, 6)"
              :key="course.courseId"
              class="course-card"
              role="link"
              tabindex="0"
              @click="openCourse(course.courseName)"
              @keydown.enter="openCourse(course.courseName)"
            >
              <el-image class="course-card__image" :src="assets.languageCards[course.languageName]" :alt="`${course.languageName || '课程'}课程`" fit="cover" />
              <div class="course-card__body">
                <span class="course-card__language">{{ course.languageName || '编程课程' }}</span>
                <h3>{{ course.courseName }}</h3>
                <p>{{ courseDifficulty(course) }}</p>
                <span class="course-card__action">查看课程 <span aria-hidden="true">→</span></span>
              </div>
            </article>
          </div>
        </PageFeedback>
      </section>

      <section class="content-section" aria-labelledby="blog-title">
        <div class="section-heading">
          <div>
            <p class="section-eyebrow">社区精选</p>
            <h2 id="blog-title">博客推荐</h2>
          </div>
          <router-link class="section-link" to="/allBlogs">查看全部博客</router-link>
        </div>

        <PageFeedback
          :loading="blogsLoading"
          :empty="!blogsLoading && !blogsError && blogs.length === 0"
          :error="blogsError"
          empty-title="暂无推荐博客"
          empty-description="稍后再来看看，或直接浏览全部博客。"
          @retry="loadBlogs"
        >
          <div class="blog-list">
            <article
              v-for="blog in blogs.slice(0, 4)"
              :key="blog.blogId"
              class="blog-row"
              role="link"
              tabindex="0"
              @click="openBlog(blog.blogId)"
              @keydown.enter="openBlog(blog.blogId)"
            >
              <div class="blog-row__body">
                <p class="blog-row__meta">
                  <span v-if="blog.poster">{{ blog.poster }}</span>
                  <span v-if="blog.publishTime">{{ blog.publishTime }}</span>
                </p>
                <h3>{{ blog.title }}</h3>
                <p v-if="blogSummary(blog)" class="blog-row__summary">{{ blogSummary(blog) }}</p>
              </div>
              <span class="blog-row__action" aria-hidden="true">→</span>
            </article>
          </div>
        </PageFeedback>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from '@/plugins/axiosInstance';
import { assets } from '@/assets';
import PageFeedback from '@/components/common/PageFeedback.vue';


const router = useRouter();
const langDisplay = assets.languageDisplays;
const courses = ref([]);
const blogs = ref([]);
const coursesLoading = ref(false);
const blogsLoading = ref(false);
const coursesError = ref('');
const blogsError = ref('');

function blogSummary(blog) {
  return blog.summary || blog.abstract || blog.description || '';
}

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

async function loadCourses() {
  coursesLoading.value = true;
  coursesError.value = '';
  try {
    const resp = await axios.get('/courses/home');
    courses.value = Array.isArray(resp.data.data) ? resp.data.data : [];
  } catch (error) {
    courses.value = [];
    coursesError.value = '课程推荐加载失败，请检查网络后重试。';
    console.error(error);
  } finally {
    coursesLoading.value = false;
  }
}

async function loadBlogs() {
  blogsLoading.value = true;
  blogsError.value = '';
  try {
    const resp = await axios.get('/blogs/home');
    blogs.value = Array.isArray(resp.data.data) ? resp.data.data : [];
  } catch (error) {
    blogs.value = [];
    blogsError.value = '博客推荐加载失败，请检查网络后重试。';
    console.error(error);
  } finally {
    blogsLoading.value = false;
  }
}

function openCourse(courseName) {
  router.push({ path: '/courseDetail', query: { courseName } });
}

async function openBlog(blogId) {
  try {
    await axios.put(`/blogs/click/${blogId}`);
  } catch (error) {
    console.error(error);
  }
  router.push({ path: '/blogDetail', query: { blogId } });
}

loadCourses();
loadBlogs();
</script>

<style scoped>
.home-page { min-width: 0; padding: clamp(16px, 3vw, 32px); }
.home-page__content { display: grid; width: min(100%, var(--cc4c-content-max-width)); gap: 24px; margin: 0 auto; }
.welcome-panel { display: flex; gap: 24px; align-items: end; justify-content: space-between; padding: clamp(24px, 5vw, 52px); border-radius: calc(var(--cc4c-radius) + 6px); background: linear-gradient(135deg, #1d4ed8, #2563eb 55%, #60a5fa); color: white; box-shadow: var(--cc4c-shadow); }
.section-eyebrow { margin: 0 0 6px; color: var(--cc4c-primary); font-size: .8125rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
.welcome-panel .section-eyebrow { color: #bfdbfe; }
.welcome-panel h1 { max-width: 680px; margin: 0; font-size: clamp(2rem, 4vw, 3.4rem); line-height: 1.15; }
.welcome-panel p:not(.section-eyebrow) { max-width: 600px; margin: 14px 0 0; color: #dbeafe; }
.welcome-panel__actions { display: flex; flex-wrap: wrap; gap: 10px; flex: 0 0 auto; }
.primary-link, .secondary-link, .section-link { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; border-radius: 8px; font-weight: 700; text-decoration: none; }
.primary-link { padding: 9px 14px; background: white; color: var(--cc4c-primary); }
.secondary-link { padding: 8px 13px; border: 1px solid #bfdbfe; color: white; }
.content-section { min-width: 0; padding: clamp(20px, 3vw, 32px); border: 1px solid var(--cc4c-border); border-radius: var(--cc4c-radius); background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.section-heading { display: flex; gap: 16px; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.section-heading h2 { margin: 0; color: var(--cc4c-text); font-size: clamp(1.35rem, 2vw, 1.75rem); }
.section-link { padding: 8px 10px; color: var(--cc4c-primary); }
.section-link:hover, .secondary-link:hover { background: color-mix(in srgb, var(--cc4c-primary) 10%, transparent); }
.partner-carousel { overflow: hidden; border-radius: 10px; }
.partner-carousel :deep(.el-carousel__container) { height: clamp(180px, 35vw, 350px) !important; }
.partner-carousel :deep(.el-image) { width: 100%; height: 100%; }
.course-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; padding: 4px; }
.course-card, .blog-row { cursor: pointer; transition: transform var(--cc4c-transition), box-shadow var(--cc4c-transition), border-color var(--cc4c-transition), background-color var(--cc4c-transition); }
.course-card { overflow: hidden; border: 1px solid var(--cc4c-border); border-radius: 14px; background: var(--cc4c-surface); }
.course-card:hover, .course-card:focus-visible { border-color: var(--cc4c-primary); background: #f8fbff; box-shadow: 0 16px 32px rgba(37, 99, 235, .16); outline: none; transform: translateY(-6px); }
.blog-row:hover, .blog-row:focus-visible { border-color: var(--cc4c-primary); box-shadow: var(--cc4c-shadow); transform: translateY(-2px); outline: none; }
.course-card__image { width: 100%; height: 156px; background: #eff6ff; transition: transform 240ms ease, filter 240ms ease; }
.course-card:hover .course-card__image, .course-card:focus-visible .course-card__image { filter: saturate(1.08); transform: scale(1.035); }
.course-card__body { display: grid; min-height: 164px; gap: 10px; padding: 20px; }
.course-card__language { color: var(--cc4c-primary); font-size: .8125rem; font-weight: 700; transition: color var(--cc4c-transition); }
.course-card h3, .blog-row h3 { margin: 0; color: var(--cc4c-text); font-size: 1.05rem; line-height: 1.5; transition: color var(--cc4c-transition); }
.course-card p { min-height: 1.3em; margin: 0; color: var(--cc4c-muted); font-size: .875rem; }
.course-card__action { color: var(--cc4c-primary); font-size: .875rem; font-weight: 700; transition: color var(--cc4c-transition), transform var(--cc4c-transition); }
.course-card:hover h3, .course-card:focus-visible h3 { color: var(--cc4c-primary-hover); }
.course-card:hover .course-card__language, .course-card:focus-visible .course-card__language { color: #1e40af; }
.course-card:hover .course-card__action, .course-card:focus-visible .course-card__action { color: var(--cc4c-primary-hover); transform: translateX(4px); }
.blog-list { display: grid; gap: 12px; }
.blog-row { display: flex; min-width: 0; gap: 16px; align-items: center; justify-content: space-between; padding: 18px; border: 1px solid var(--cc4c-border); border-radius: 10px; }
.blog-row__body { min-width: 0; }
.blog-row__meta { display: flex; flex-wrap: wrap; gap: 8px 14px; margin: 0 0 5px; color: var(--cc4c-muted); font-size: .8125rem; }
.blog-row__summary { display: -webkit-box; margin: 8px 0 0; overflow: hidden; color: var(--cc4c-muted); font-size: .9rem; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.blog-row__action { color: var(--cc4c-primary); font-size: 1.35rem; }

@media (max-width: 768px) { .welcome-panel { align-items: start; flex-direction: column; } .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; } }
@media (max-width: 480px) { .home-page { padding: 12px; } .content-section { padding: 18px; } .section-heading { align-items: start; flex-direction: column; } .course-grid { grid-template-columns: 1fr; gap: 16px; } .course-card__image { height: 170px; } .course-card__body { min-height: 0; padding: 18px; } .blog-row { padding: 14px; } .welcome-panel__actions { width: 100%; } .primary-link, .secondary-link { flex: 1; } }
</style>
