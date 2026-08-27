<template>
  <main class="favorite-page">
    <div class="favorite-page__inner">
      <UserInfo :active-index="2" />

      <section class="favorite-center" aria-labelledby="favorite-title">
        <header class="favorite-heading">
          <div class="favorite-heading__title">
            <span class="favorite-heading__mark" aria-hidden="true">★</span>
            <div>
              <p>Learning collection</p>
              <h2 id="favorite-title">我的收藏</h2>
            </div>
          </div>
          <span class="favorite-heading__hint">集中管理稍后继续学习和阅读的内容</span>
        </header>

        <div class="favorite-tabs" role="tablist" aria-label="收藏内容类型">
          <button
            v-for="tab in tabs"
            :id="`favorite-tab-${tab.key}`"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="activeTab === tab.key"
            :aria-controls="`favorite-panel-${tab.key}`"
            :class="{ 'favorite-tabs__button--active': activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            <span>{{ tab.label }}</span>
            <b>{{ tab.count }}</b>
          </button>
        </div>

        <PageFeedback v-if="loading || errorMessage" :loading="loading" :error="errorMessage" @retry="loadFavorites" />

        <div
          v-else
          :id="`favorite-panel-${activeTab}`"
          class="favorite-panel"
          role="tabpanel"
          :aria-labelledby="`favorite-tab-${activeTab}`"
        >
            <template v-if="activeTab === 'courses'">
              <div v-if="favoriteCourses.length" class="course-grid">
                <button
                  v-for="course in favoriteCourses"
                  :key="course.courseName"
                  type="button"
                  class="course-card"
                  @click="openCourse(course.courseName)"
                >
                  <span class="course-card__visual">
                    <img :src="courseImage(course.languageName)" :alt="`${course.languageName || '编程'}课程封面`" />
                    <small>{{ course.languageName || '课程' }}</small>
                  </span>
                  <span class="course-card__body">
                    <b>{{ course.courseName }}</b>
                    <span>继续学习 <i aria-hidden="true">→</i></span>
                  </span>
                </button>
              </div>
              <el-empty v-else description="还没有收藏课程">
                <el-button type="primary" @click="router.push('/allCourses')">浏览课程</el-button>
              </el-empty>
            </template>

            <template v-else>
              <div v-if="favoriteBlogs.length" class="blog-list">
                <button
                  v-for="blog in favoriteBlogs"
                  :key="blog.blogId"
                  type="button"
                  class="blog-card"
                  @click="openBlog(blog.blogId)"
                >
                  <span class="blog-card__marker" aria-hidden="true">BLOG</span>
                  <span class="blog-card__content">
                    <small>{{ formatDate(blog.publishTime) }}</small>
                    <b>{{ blog.title }}</b>
                    <span>{{ Number(blog.click) || 0 }} 次阅读</span>
                  </span>
                  <i aria-hidden="true">→</i>
                </button>
              </div>
              <el-empty v-else description="还没有收藏博客">
                <el-button type="primary" @click="router.push('/allBlogs')">浏览博客</el-button>
              </el-empty>
            </template>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue';
import axios from '@/plugins/axiosInstance';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import UserInfo from '@/components/UserInfo.vue';
import PageFeedback from '@/components/common/PageFeedback.vue';
import store from '@/store';
import { assets } from '@/assets';


const router = useRouter();
const activeTab = ref('courses');
const favoriteCourses = ref([]);
const favoriteBlogs = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const tabs = computed(() => [
  { key: 'courses', label: '收藏课程', count: favoriteCourses.value.length },
  { key: 'blogs', label: '收藏博客', count: favoriteBlogs.value.length },
]);

function backendMessage(error, fallback) {
  return error?.response?.data?.msg || error?.response?.data?.MSG || fallback;
}

function courseImage(languageName) {
  return assets.languageCards[languageName] || assets.languageCards[String(languageName || '').toLowerCase()] || assets.defaultAvatar;
}

function formatDate(value) {
  if (!value) return '发布时间未知';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

async function verifyUser() {
  const response = await axios.get('/users/verify');
  if (response.data.data === false) {
    ElMessage.warning(response.data.msg || '请先登录');
    await router.push('/login');
    return false;
  }
  return true;
}

async function loadFavorites() {
  loading.value = true;
  errorMessage.value = '';
  try {
    if (!await verifyUser()) return;
    const [courseResponse, blogResponse] = await Promise.all([
      axios.get(`/courses/favorList/${store.state.user.id}`),
      axios.get(`/blogs/collectList/${store.state.user.id}`),
    ]);
    favoriteCourses.value = Array.isArray(courseResponse.data.data) ? courseResponse.data.data : [];
    favoriteBlogs.value = Array.isArray(blogResponse.data.data) ? blogResponse.data.data : [];
  } catch (error) {
    errorMessage.value = backendMessage(error, '收藏内容加载失败，请检查服务状态后重试。');
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function openCourse(courseName) {
  router.push({ path: '/courseDetail', query: { courseName } });
}

function openBlog(blogId) {
  axios.put(`/blogs/click/${blogId}`).catch((error) => console.error(error));
  router.push({ path: '/blogDetail', query: { blogId } });
}

loadFavorites();
</script>

<style>
.favorite-page { min-height: 100%; padding: clamp(16px, 2.5vw, 32px); background: var(--cc4c-bg); }
.favorite-page__inner { display: grid; gap: 22px; width: min(100%, 1180px); margin: 0 auto; }
.favorite-center { position: relative; min-width: 0; overflow: hidden; padding: clamp(22px, 3vw, 36px); border: 1px solid var(--cc4c-border); border-radius: 20px; background: linear-gradient(150deg, #fff 0%, #fbfdff 64%, #f3f7ff 100%); box-shadow: 0 18px 42px rgba(15, 23, 42, .08); }
.favorite-center::before { position: absolute; top: 0; right: 0; width: 240px; height: 240px; border-radius: 50%; background: radial-gradient(circle, rgba(96, 165, 250, .16) 0%, rgba(96, 165, 250, 0) 70%); content: ''; transform: translate(34%, -44%); pointer-events: none; }
.favorite-heading { position: relative; z-index: 1; display: flex; gap: 24px; align-items: center; justify-content: space-between; padding-bottom: 24px; border-bottom: 1px solid var(--cc4c-border); }
.favorite-heading__title { display: flex; gap: 14px; align-items: center; }
.favorite-heading__mark { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 13px; background: linear-gradient(135deg, #2563eb, #60a5fa); color: #fff; font-size: 1rem; box-shadow: 0 9px 20px rgba(37, 99, 235, .22); }
.favorite-heading p { margin: 0 0 4px; color: var(--cc4c-primary); font-size: .7rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.favorite-heading h2 { margin: 0; color: var(--cc4c-text); font-size: clamp(1.3rem, 2vw, 1.6rem); }
.favorite-heading__hint { max-width: 260px; color: var(--cc4c-muted); font-size: .82rem; line-height: 1.55; text-align: right; }
.favorite-tabs { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 24px 0; padding: 6px; border: 1px solid #e6ebf3; border-radius: 14px; background: #f1f5f9; }
.favorite-tabs button { display: flex; gap: 10px; align-items: center; justify-content: center; min-height: 48px; padding: 9px 14px; border: 1px solid transparent; border-radius: 10px; background: transparent; color: var(--cc4c-muted); font: inherit; font-weight: 700; cursor: pointer; transition: color var(--cc4c-transition), background var(--cc4c-transition), box-shadow var(--cc4c-transition), transform var(--cc4c-transition); }
.favorite-tabs button:hover { color: var(--cc4c-primary); }
.favorite-tabs button b { min-width: 25px; padding: 2px 7px; border-radius: 999px; background: #e2e8f0; font-size: .75rem; }
.favorite-tabs .favorite-tabs__button--active { border-color: #cad9f3; background: #fff; color: var(--cc4c-primary); box-shadow: 0 7px 16px rgba(37, 99, 235, .1); transform: translateY(-1px); }
.favorite-tabs .favorite-tabs__button--active b { background: #dbeafe; }
.favorite-center .page-feedback { position: relative; z-index: 1; }
.favorite-panel { position: relative; z-index: 1; min-width: 0; }
.favorite-center .course-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.favorite-center .course-card { min-width: 0; padding: 0; overflow: hidden; border: 1px solid var(--cc4c-border); border-radius: 16px; background: #fff; color: inherit; text-align: left; cursor: pointer; box-shadow: 0 7px 20px rgba(15, 23, 42, .05); transition: transform var(--cc4c-transition), border-color var(--cc4c-transition), box-shadow var(--cc4c-transition); }
.favorite-center .course-card:hover { border-color: #93b4ef; transform: translateY(-4px); box-shadow: 0 15px 28px rgba(37, 99, 235, .12); }
.favorite-center .course-card__visual { position: relative; display: block; height: 154px; overflow: hidden; background: linear-gradient(145deg, #edf4ff, #f8fbff); }
.favorite-center .course-card__visual img { box-sizing: border-box; width: 100%; height: 100%; padding: 17px; object-fit: contain; transition: transform 260ms ease; }
.favorite-center .course-card:hover .course-card__visual img { transform: scale(1.045); }
.favorite-center .course-card__visual small { position: absolute; top: 12px; left: 12px; padding: 4px 9px; border-radius: 999px; background: rgba(15, 23, 42, .78); color: #fff; font-size: .7rem; font-weight: 800; text-transform: uppercase; }
.favorite-center .course-card__body { display: grid; gap: 13px; padding: 18px; border-top: 1px solid #edf1f7; }
.favorite-center .course-card__body b { min-height: 2.8em; color: var(--cc4c-text); line-height: 1.4; overflow-wrap: anywhere; }
.favorite-center .course-card__body span { color: var(--cc4c-primary); font-size: .86rem; font-weight: 700; }
.favorite-center .course-card__body i { font-style: normal; transition: transform var(--cc4c-transition); }
.favorite-center .course-card:hover .course-card__body i { display: inline-block; transform: translateX(4px); }
.favorite-center .blog-list { display: grid; gap: 12px; }
.favorite-center .blog-card { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 18px; align-items: center; width: 100%; padding: 20px; border: 1px solid var(--cc4c-border); border-radius: 15px; background: linear-gradient(135deg, #fff 0%, #f8fbff 100%); color: inherit; text-align: left; cursor: pointer; transition: transform var(--cc4c-transition), border-color var(--cc4c-transition), box-shadow var(--cc4c-transition); }
.favorite-center .blog-card:hover { border-color: #93b4ef; transform: translateY(-2px); box-shadow: 0 12px 26px rgba(37, 99, 235, .1); }
.favorite-center .blog-card__marker { display: grid; place-items: center; width: 54px; height: 54px; border-radius: 14px; background: linear-gradient(135deg, #eaf1ff, #dbeafe); color: var(--cc4c-primary); font-size: .68rem; font-weight: 900; letter-spacing: .08em; }
.favorite-center .blog-card__content { display: grid; gap: 5px; min-width: 0; }
.favorite-center .blog-card__content small, .favorite-center .blog-card__content > span { color: var(--cc4c-muted); font-size: .78rem; }
.favorite-center .blog-card__content b { color: var(--cc4c-text); font-size: 1rem; overflow-wrap: anywhere; }
.favorite-center .blog-card > i { color: var(--cc4c-primary); font-size: 1.25rem; font-style: normal; }

@media (max-width: 900px) {
  .favorite-center .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .favorite-page { padding: 12px; }
  .favorite-heading { align-items: flex-start; flex-direction: column; }
  .favorite-heading__hint { max-width: none; text-align: left; }
  .favorite-center .course-grid { grid-template-columns: 1fr; }
  .favorite-center .blog-card { grid-template-columns: auto minmax(0, 1fr); gap: 12px; padding: 16px; }
  .favorite-center .blog-card > i { display: none; }
}

@media (max-width: 390px) {
  .favorite-center { padding: 18px 14px; }
  .favorite-tabs button { flex-direction: column; gap: 4px; padding-inline: 6px; }
  .favorite-center .blog-card { grid-template-columns: 1fr; }
  .favorite-center .blog-card__marker { width: 46px; height: 46px; }
}
</style>
