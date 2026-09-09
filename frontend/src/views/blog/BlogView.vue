<template>
  <main class="blogs-page">
    <section class="blogs-page__content" aria-labelledby="blog-square-title">
      <header class="page-heading">
        <div>
          <p class="page-heading__eyebrow">博客广场</p>
          <h1 id="blog-square-title">浏览博客</h1>
          <p>查看已发布的社区技术文章。</p>
        </div>
        <router-link class="write-link" to="/blogWrite">写博客</router-link>
      </header>

      <PageFeedback
        :loading="loading"
        :empty="!loading && !errorMessage && blogList.length === 0"
        :error="errorMessage"
        empty-title="当前语言暂无博客"
        empty-description="可以稍后再来，或去写下第一篇分享。"
        @retry="loadBlogs"
      >
        <div class="blog-list">
          <article
            v-for="blog in blogList"
            :key="blog.blogId"
            class="blog-card"
            role="link"
            tabindex="0"
            @click="openBlog(blog.blogId)"
            @keydown.enter="openBlog(blog.blogId)"
          >
            <div class="blog-card__body">
              <p class="blog-card__meta">
                <span v-if="blog.poster">作者：{{ blog.poster }}</span>
                <span v-if="blog.publishTime">发布于 {{ blog.publishTime }}</span>
                <span v-if="blog.click !== undefined"
                  ><el-icon><View /></el-icon>{{ blog.click }}</span
                >
              </p>
              <h2>{{ blog.title }}</h2>
              <p v-if="blogSummary(blog)" class="blog-card__summary">{{ blogSummary(blog) }}</p>
            </div>
            <span class="blog-card__action" aria-hidden="true">阅读 <span>→</span></span>
          </article>
        </div>
      </PageFeedback>
      <el-pagination
        v-if="total > pageSize"
        class="blog-pagination"
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
/** 博客广场在确认 USER 会话后分页读取语言编号 1 的公开博客，并提供写博客入口。 */
import { reportClientError } from '@/utils/reportClientError.js';
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { getSession } from '@/api/auth';
import { incrementBlogClick, listPublicBlogs } from '@/api/community';
import { ElMessage } from 'element-plus';
import { View } from '@element-plus/icons-vue';
import PageFeedback from '@/components/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';

const router = useRouter();
const blogList = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const currentPage = ref(1);
const pageSize = 12;
const total = ref(0);

/** 依次读取摘要候选字段，均为空时不显示摘要。 */
function blogSummary(blog) {
  return blog.summary || blog.abstract || blog.description || '';
}

/** 确认 USER 会话；未登录或查询失败时提示并进入登录页。 */
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

/** 尝试更新阅读计数后进入博客详情，计数失败不阻断导航。 */
async function openBlog(blogId) {
  try {
    await incrementBlogClick(blogId);
  } catch (error) {
    reportClientError(error, 'frontend/src/views/blog/BlogView.vue');
  }
  router.push({ path: '/blogDetail', query: { blogId } });
}

/** 分页读取固定语言编号 1 的公开博客，维护列表、总数和加载错误。 */
async function loadBlogs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const resp = await listPublicBlogs({ page: currentPage.value, size: pageSize });
    blogList.value = resp.data.data?.items || [];
    total.value = resp.data.data?.total || 0;
  } catch (error) {
    blogList.value = [];
    total.value = 0;
    errorMessage.value = apiErrorMessage(error, '博客加载失败，请检查网络后重试。');
    reportClientError(error, 'frontend/src/views/blog/BlogView.vue');
  } finally {
    loading.value = false;
  }
}

/** 更新当前页码并重新查询公开博客。 */
function changePage(page) {
  currentPage.value = page;
  return loadBlogs();
}

verifyUser().then((verified) => {
  if (verified) loadBlogs();
});
</script>

<style scoped>
.blogs-page {
  min-width: 0;
  padding: clamp(16px, 3vw, 32px);
}
.blogs-page__content {
  width: min(100%, 980px);
  margin: 0 auto;
}
.page-heading {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
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
.write-link {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--cc4c-primary);
  color: white;
  font-weight: 700;
  text-decoration: none;
}
.write-link:hover {
  background: var(--cc4c-primary-hover);
}
.blog-list {
  display: grid;
  gap: 14px;
}
.blog-card {
  display: flex;
  min-width: 0;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: clamp(16px, 3vw, 24px);
  border: 1px solid var(--cc4c-border);
  border-radius: var(--cc4c-radius);
  background: var(--cc4c-surface);
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  transition:
    transform var(--cc4c-transition),
    box-shadow var(--cc4c-transition),
    border-color var(--cc4c-transition);
}
.blog-card:hover,
.blog-card:focus-visible {
  border-color: var(--cc4c-primary);
  box-shadow: var(--cc4c-shadow);
  outline: none;
  transform: translateY(-2px);
}
.blog-card__body {
  min-width: 0;
}
.blog-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 14px;
  margin: 0 0 7px;
  color: var(--cc4c-muted);
  font-size: 0.8125rem;
}
.blog-card__meta span {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.blog-card h2 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(1.05rem, 2vw, 1.3rem);
  line-height: 1.45;
}
.blog-card__summary {
  display: -webkit-box;
  margin: 9px 0 0;
  overflow: hidden;
  color: var(--cc4c-muted);
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.blog-card__action {
  flex: 0 0 auto;
  color: var(--cc4c-primary);
  font-size: 0.875rem;
  font-weight: 700;
  white-space: nowrap;
}
.blog-pagination {
  justify-content: center;
  margin-top: 24px;
}
@media (max-width: 480px) {
  .blogs-page {
    padding: 12px;
  }
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .blog-card {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
  .blog-card__action {
    display: inline-flex;
    min-height: 36px;
    align-items: center;
  }
}
</style>
