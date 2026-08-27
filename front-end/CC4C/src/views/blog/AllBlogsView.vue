<template>
  <main class="discovery-page">
    <section class="discovery-page__content" aria-labelledby="all-blogs-title">
      <header class="discovery-hero">
        <div>
          <p class="discovery-hero__eyebrow">社区内容</p>
          <h1 id="all-blogs-title">从真实经验中获得灵感</h1>
          <p>浏览社区作者分享的学习心得、技术实践和成长记录，找到值得深入阅读的下一篇文章。</p>
        </div>
        <div class="discovery-hero__stat" aria-label="公开博客数量">
          <strong>{{ total }}</strong>
          <span>篇公开文章</span>
        </div>
      </header>

      <div class="collection-heading">
        <div>
          <p class="collection-heading__eyebrow">精选内容</p>
          <h2>全部博客</h2>
        </div>
        <span class="collection-heading__hint">点击卡片阅读全文</span>
      </div>

      <PageFeedback
        :loading="loading"
        :empty="!loading && !errorMessage && blogList.length === 0"
        :error="errorMessage"
        empty-title="还没有公开博客"
        empty-description="稍后再来看看新的技术分享。"
        @retry="loadBlogs"
      >
        <div class="blog-grid">
          <article v-for="blog in blogList" :key="blog.blogId" class="blog-card" role="link" tabindex="0" @click="openBlog(blog.blogId)" @keydown.enter="openBlog(blog.blogId)">
            <div class="blog-card__accent" aria-hidden="true"></div>
            <div class="blog-card__body">
              <p class="blog-card__meta">
                <span v-if="blog.poster">作者 · {{ blog.poster }}</span>
                <span v-if="blog.publishTime">{{ blog.publishTime }}</span>
              </p>
              <h3>{{ blog.title }}</h3>
              <p v-if="blogSummary(blog)" class="blog-card__summary">{{ blogSummary(blog) }}</p>
              <div class="blog-card__footer">
                <span v-if="blog.click !== undefined" class="blog-card__views"><el-icon><View /></el-icon>{{ blog.click }} 次阅读</span>
                <span class="blog-card__action">阅读全文 <span aria-hidden="true">→</span></span>
              </div>
            </div>
          </article>
        </div>
      </PageFeedback>
      <el-pagination
        v-if="total > pageSize"
        class="collection-pagination"
        background
        layout="prev, pager, next"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        @current-change="changePage"
      />
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { View } from '@element-plus/icons-vue';
import axios from '@/plugins/axiosInstance';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';

const router = useRouter();
const blogList = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const currentPage = ref(1);
const pageSize = 12;
const total = ref(0);

function blogSummary(blog) {
  return blog.summary || blog.abstract || blog.description || '';
}

async function openBlog(blogId) {
  try {
    await axios.put(`/blogs/click/${blogId}`);
  } catch (error) {
    console.error(error);
  }
  router.push({ path: '/blogDetail', query: { blogId } });
}

async function loadBlogs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const resp = await axios.get('/blogs/all', { params: { page: currentPage.value, size: pageSize } });
    blogList.value = resp.data.data?.items || [];
    total.value = resp.data.data?.total || 0;
  } catch (error) {
    blogList.value = [];
    total.value = 0;
    errorMessage.value = apiErrorMessage(error, '博客加载失败，请检查网络后重试。');
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function changePage(page) {
  currentPage.value = page;
  return loadBlogs();
}

loadBlogs();
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
.collection-heading { display: flex; gap: 16px; align-items: center; justify-content: space-between; margin: 30px 0 16px; }
.collection-heading__eyebrow { color: var(--cc4c-primary); }
.collection-heading h2 { margin: 0; color: var(--cc4c-text); font-size: 1.3rem; }
.collection-heading__hint { color: var(--cc4c-muted); font-size: .875rem; }
.blog-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }
.blog-card { position: relative; overflow: hidden; min-width: 0; border: 1px solid var(--cc4c-border); border-radius: 16px; background: var(--cc4c-surface); box-shadow: 0 5px 15px rgba(15,23,42,.05); cursor: pointer; transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease; }
.blog-card:hover, .blog-card:focus-visible { border-color: var(--cc4c-primary); box-shadow: 0 18px 32px rgba(37,99,235,.16); outline: none; transform: translateY(-6px); }
.blog-card__accent { height: 6px; background: linear-gradient(90deg, #2563eb, #60a5fa); transition: height 200ms ease; }
.blog-card:hover .blog-card__accent, .blog-card:focus-visible .blog-card__accent { height: 9px; }
.blog-card__body { display: grid; min-height: 218px; gap: 12px; padding: 22px; }
.blog-card__meta { display: flex; flex-wrap: wrap; gap: 7px 14px; margin: 0; color: var(--cc4c-muted); font-size: .8125rem; }
.blog-card h3 { margin: 0; color: var(--cc4c-text); font-size: clamp(1.1rem, 2vw, 1.35rem); line-height: 1.45; transition: color 180ms ease; }
.blog-card__summary { display: -webkit-box; margin: 0; overflow: hidden; color: var(--cc4c-muted); line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.blog-card__footer { display: flex; gap: 12px; align-items: center; justify-content: space-between; align-self: end; }
.blog-card__views { display: inline-flex; gap: 5px; align-items: center; color: var(--cc4c-muted); font-size: .8125rem; }
.blog-card__action { color: var(--cc4c-primary); font-size: .875rem; font-weight: 700; transition: color 180ms ease, transform 180ms ease; }
.blog-card:hover h3, .blog-card:focus-visible h3, .blog-card:hover .blog-card__action, .blog-card:focus-visible .blog-card__action { color: var(--cc4c-primary-hover); }
.blog-card:hover .blog-card__action, .blog-card:focus-visible .blog-card__action { transform: translateX(5px); }
.collection-pagination { justify-content: center; margin-top: 26px; }
@media (max-width: 768px) { .discovery-hero { align-items: start; flex-direction: column; } }
@media (max-width: 480px) { .discovery-page { padding: 12px; } .discovery-hero { padding: 24px 20px; border-radius: 16px; } .discovery-hero__stat { width: 100%; grid-template-columns: auto auto; align-items: center; justify-content: center; gap: 8px; } .discovery-hero__stat span { margin: 0; } .collection-heading { align-items: start; flex-direction: column; } .blog-grid { grid-template-columns: 1fr; gap: 18px; } .blog-card__body { min-height: 200px; padding: 20px; } }
</style>
