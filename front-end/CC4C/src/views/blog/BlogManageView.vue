<template>
  <main class="blog-manage">
    <div class="blog-manage__content">
      <section class="profile-nav" aria-label="个人中心导航">
        <UserInfo active-index="4" />
      </section>

      <section class="manage-card" aria-labelledby="my-blogs-title">
        <header class="manage-heading">
          <div>
            <p class="manage-heading__eyebrow">内容管理</p>
            <h1 id="my-blogs-title">我的博客</h1>
            <p>查看文章审核状态，并继续你的下一次创作。</p>
          </div>
          <el-button type="primary" @click="writeBlog">
            <el-icon><EditPen /></el-icon>
            写博客
          </el-button>
        </header>

        <div v-if="blogList.length" class="status-summary" aria-label="博客状态统计">
          <span><strong>{{ total }}</strong> 全部</span>
          <span><strong>{{ stateCounts.published }}</strong> 已发布</span>
          <span><strong>{{ stateCounts.pending }}</strong> 待审核</span>
          <span><strong>{{ stateCounts.rejected }}</strong> 未通过</span>
        </div>

        <PageFeedback :loading="loading" :error="errorMessage" @retry="loadBlogs">
          <el-empty v-if="blogList.length === 0" description="还没有创作博客">
            <el-button type="primary" @click="writeBlog">开始写第一篇博客</el-button>
          </el-empty>

          <div v-else class="blog-list">
            <article
              v-for="blog in blogList"
              :key="blog.blogId"
              class="blog-item"
              role="link"
              tabindex="0"
              @click="openBlog(blog.blogId)"
              @keydown.enter="openBlog(blog.blogId)"
            >
              <div class="blog-item__marker" :class="`blog-item__marker--${statusInfo(blog.state).key}`" aria-hidden="true"></div>
              <div class="blog-item__body">
                <div class="blog-item__topline">
                  <el-tag :type="statusInfo(blog.state).type" effect="light">{{ statusInfo(blog.state).label }}</el-tag>
                  <time v-if="blog.publishTime" :datetime="String(blog.publishTime)">{{ formatDate(blog.publishTime) }}</time>
                </div>
                <h2>{{ blog.title || '未命名博客' }}</h2>
                <p>{{ statusInfo(blog.state).description }}</p>
                <div class="blog-item__meta">
                  <span v-if="blog.click !== undefined"><el-icon><View /></el-icon>{{ blog.click }} 次阅读</span>
                  <span><el-icon><Document /></el-icon>博客文章</span>
                </div>
              </div>
              <div class="blog-item__actions">
                <el-button type="primary" plain @click.stop="openBlog(blog.blogId)">查看内容</el-button>
                <el-button v-if="blog.state === -1" @click.stop="writeBlog">重新撰写</el-button>
              </div>
            </article>
          </div>
        </PageFeedback>
        <el-pagination
          v-if="total > pageSize"
          class="manage-pagination"
          background
          layout="prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          @current-change="changePage"
        />
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue';
import { Document, EditPen, View } from '@element-plus/icons-vue';
import axios from '@/plugins/axiosInstance';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import UserInfo from '@/components/UserInfo.vue';
import PageFeedback from '@/components/common/PageFeedback.vue';
import store from '@/store';
import { apiErrorMessage } from '@/utils/apiError';


const router = useRouter();
const blogList = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const currentPage = ref(1);
const pageSize = 10;
const total = ref(0);

const stateCounts = computed(() => blogList.value.reduce((counts, blog) => {
  if (blog.state === 1) counts.published += 1;
  else if (blog.state === 0) counts.pending += 1;
  else if (blog.state === -1) counts.rejected += 1;
  return counts;
}, { published: 0, pending: 0, rejected: 0 }));

function statusInfo(state) {
  return ({
    '-1': { key: 'rejected', label: '审核未通过', type: 'danger', description: '文章未通过审核，可以调整内容后重新撰写并提交。' },
    '0': { key: 'pending', label: '待审核', type: 'warning', description: '文章已提交，正在等待管理员审核。' },
    '1': { key: 'published', label: '已发布', type: 'success', description: '文章已公开发布，社区用户可以阅读和评论。' },
  }[String(state)] || { key: 'unknown', label: '状态未知', type: 'info', description: '暂时无法识别当前文章状态。' });
}

function formatDate(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date);
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

async function loadBlogs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const verified = await verifyUser();
    if (!verified) return;
    const resp = await axios.get(`/blogs/myBlogs/${store.state.user.id}`, { params: { page: currentPage.value, size: pageSize } });
    blogList.value = resp.data.data?.items || [];
    total.value = resp.data.data?.total || 0;
  } catch (error) {
    blogList.value = [];
    total.value = 0;
    errorMessage.value = apiErrorMessage(error, '个人博客加载失败，请检查网络后重试。');
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function changePage(page) {
  currentPage.value = page;
  return loadBlogs();
}

function openBlog(blogId) {
  router.push({ path: '/blogDetail', query: { blogId } });
}

function writeBlog() {
  router.push('/blogWrite');
}

loadBlogs();
</script>

<style scoped>
.blog-manage { min-width: 0; padding: clamp(16px, 3vw, 36px); }
.blog-manage__content { display: grid; width: min(100%, 1180px); gap: 22px; margin: 0 auto; }
.profile-nav, .manage-card { min-width: 0; border: 1px solid var(--cc4c-border); border-radius: 16px; background: var(--cc4c-surface); box-shadow: var(--cc4c-shadow); }
.profile-nav { overflow: hidden; padding: 16px 20px; }
.manage-card { padding: clamp(20px, 3.5vw, 36px); }
.manage-heading { display: flex; flex-wrap: wrap; gap: 18px; align-items: start; justify-content: space-between; }
.manage-heading__eyebrow { margin: 0 0 6px; color: var(--cc4c-primary); font-size: .8rem; font-weight: 800; letter-spacing: .07em; text-transform: uppercase; }
.manage-heading h1 { margin: 0; color: var(--cc4c-text); font-size: clamp(1.8rem, 4vw, 2.6rem); }
.manage-heading p:not(.manage-heading__eyebrow) { margin: 8px 0 0; color: var(--cc4c-muted); }
.status-summary { display: flex; flex-wrap: wrap; gap: 10px; margin: 24px 0 18px; }
.status-summary span { display: inline-flex; min-height: 38px; gap: 6px; align-items: center; padding: 7px 12px; border: 1px solid var(--cc4c-border); border-radius: 10px; color: var(--cc4c-muted); font-size: .875rem; }
.status-summary strong { color: var(--cc4c-text); font-size: 1rem; }
.blog-list { display: grid; gap: 14px; margin-top: 18px; }
.blog-item { position: relative; display: grid; min-width: 0; grid-template-columns: 5px minmax(0, 1fr) auto; gap: 18px; align-items: center; overflow: hidden; padding: 20px; border: 1px solid var(--cc4c-border); border-radius: 14px; background: linear-gradient(145deg, #fff, #f8fbff); cursor: pointer; transition: border-color var(--cc4c-transition), box-shadow var(--cc4c-transition), transform var(--cc4c-transition); }
.blog-item:hover, .blog-item:focus-visible { border-color: var(--cc4c-primary); box-shadow: 0 12px 28px rgba(37, 99, 235, .12); outline: none; transform: translateY(-2px); }
.blog-item__marker { align-self: stretch; border-radius: 99px; background: #94a3b8; }
.blog-item__marker--published { background: #22c55e; }
.blog-item__marker--pending { background: #f59e0b; }
.blog-item__marker--rejected { background: #ef4444; }
.blog-item__body { min-width: 0; }
.blog-item__topline { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; }
.blog-item__topline time { color: var(--cc4c-muted); font-size: .8rem; }
.blog-item h2 { margin: 10px 0 0; color: var(--cc4c-text); font-size: 1.15rem; line-height: 1.45; overflow-wrap: anywhere; }
.blog-item__body > p { margin: 7px 0 0; color: var(--cc4c-muted); font-size: .875rem; }
.blog-item__meta { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 11px; color: var(--cc4c-muted); font-size: .8rem; }
.blog-item__meta span { display: inline-flex; gap: 5px; align-items: center; }
.blog-item__actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: end; }
.blog-item__actions :deep(.el-button) { margin: 0; }
.manage-pagination { justify-content: center; margin-top: 24px; }
@media (max-width: 768px) { .blog-item { grid-template-columns: 5px minmax(0, 1fr); } .blog-item__actions { grid-column: 2; justify-content: start; } }
@media (max-width: 480px) { .blog-manage { padding: 12px; } .profile-nav { padding: 12px; } .manage-card { padding: 20px 16px; } .manage-heading { align-items: stretch; flex-direction: column; } .manage-heading :deep(.el-button) { width: 100%; } .blog-item { gap: 12px; padding: 16px 13px; } .blog-item__actions { display: grid; grid-template-columns: 1fr; } .blog-item__actions :deep(.el-button) { width: 100%; } }
</style>
