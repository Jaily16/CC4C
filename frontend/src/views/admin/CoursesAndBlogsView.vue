<template>
  <section class="admin-page" aria-labelledby="overview-title">
    <header class="admin-page__heading">
      <div>
        <p>Dashboard</p>
        <h1 id="overview-title">内容数据概览</h1>
        <span>查看当前已发布课程和公开博客的基础数据。</span>
      </div>
      <el-button :loading="loading" @click="loadOverview">刷新数据</el-button>
    </header>

    <div class="overview-stats" aria-label="内容统计">
      <article>
        <span>课程总数</span>
        <strong>{{ courseTotal }}</strong>
        <small>已进入学习目录</small>
      </article>
      <article>
        <span>公开博客</span>
        <strong>{{ blogTotal }}</strong>
        <small>已通过内容审核</small>
      </article>
      <article>
        <span>待处理事项</span>
        <strong>{{ pendingCount }}</strong>
        <small>篇博客等待审核决定</small>
      </article>
    </div>

    <PageFeedback v-if="loading || errorMessage" :loading="loading" :error="errorMessage" @retry="loadOverview" />

    <div v-else class="overview-grid">
      <section class="data-panel" aria-labelledby="courses-title">
        <header class="data-panel__heading">
          <div>
            <h2 id="courses-title">已发布课程</h2>
            <p>共 {{ courseTotal }} 门课程</p>
          </div>
          <el-tag type="success" effect="light">公开中</el-tag>
        </header>
        <div v-if="courseList.length" class="data-table-scroll" tabindex="0" aria-label="课程数据表，可横向滚动">
          <el-table :data="courseList" stripe>
            <el-table-column prop="courseId" label="ID" width="70" />
            <el-table-column prop="courseName" label="课程名称" min-width="240" show-overflow-tooltip />
            <el-table-column prop="languageName" label="语言" width="100">
              <template #default="scope"
                ><el-tag effect="plain">{{ scope.row.languageName || '未分类' }}</el-tag></template
              >
            </el-table-column>
            <el-table-column label="难度" width="120">
              <template #default="scope">{{ levelLabel(scope.row.level) }}</template>
            </el-table-column>
            <el-table-column prop="favorsNum" label="收藏数" width="100" />
            <el-table-column label="状态" width="100">
              <template #default
                ><span class="status-dot"><i></i>已发布</span></template
              >
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="暂时没有已发布课程">
          <el-button type="primary" @click="router.push('/admin/addCourse')">发布第一门课程</el-button>
        </el-empty>
      </section>

      <section class="data-panel" aria-labelledby="blogs-title">
        <header class="data-panel__heading">
          <div>
            <h2 id="blogs-title">公开博客</h2>
            <p>共 {{ blogTotal }} 篇文章</p>
          </div>
          <el-tag type="success" effect="light">审核通过</el-tag>
        </header>
        <div v-if="blogList.length" class="data-table-scroll" tabindex="0" aria-label="博客数据表，可横向滚动">
          <el-table :data="blogList" stripe>
            <el-table-column prop="title" label="博客标题" min-width="260" show-overflow-tooltip />
            <el-table-column prop="writerId" label="作者 ID" width="150" show-overflow-tooltip />
            <el-table-column label="发布时间" width="140">
              <template #default="scope">{{ formatDate(scope.row.publishTime) }}</template>
            </el-table-column>
            <el-table-column prop="click" label="阅读量" width="100" />
            <el-table-column label="状态" width="110">
              <template #default
                ><span class="status-dot"><i></i>已公开</span></template
              >
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="暂时没有审核通过的博客">
          <el-button type="primary" @click="router.push('/admin/checkBlog')">前往博客审核</el-button>
        </el-empty>
      </section>
    </div>
  </section>
</template>

<script setup>
import { reportClientError } from '@/utils/reportClientError.js';
import { ref } from 'vue';
import { listHomeCourses } from '@/api/catalog';
import { listAllBlogs, listPendingBlogs } from '@/api/community';
import { useRouter } from 'vue-router';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';

const router = useRouter();
const blogList = ref([]);
const courseList = ref([]);
const pendingCount = ref(0);
const courseTotal = ref(0);
const blogTotal = ref(0);
const loading = ref(false);
const errorMessage = ref('');

const levelMap = {
  '-2': '简单',
  '-1': '简单且默认',
  0: '默认',
  1: '困难且默认',
  2: '困难',
  66: '重点展示',
};

function levelLabel(level) {
  return levelMap[String(level)] || '未设置';
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

async function loadOverview() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [coursesResponse, blogsResponse, pendingResponse] = await Promise.all([
      listHomeCourses({ page: 1, size: 10 }),
      listAllBlogs({ page: 1, size: 10 }),
      listPendingBlogs({ page: 1, size: 10 }),
    ]);
    courseList.value = coursesResponse.data.data?.items || [];
    blogList.value = blogsResponse.data.data?.items || [];
    courseTotal.value = coursesResponse.data.data?.total || 0;
    blogTotal.value = blogsResponse.data.data?.total || 0;
    pendingCount.value = pendingResponse.data.data?.total || 0;
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '内容数据加载失败，请检查服务状态后重试。');
    reportClientError(error, 'frontend/src/views/admin/CoursesAndBlogsView.vue');
  } finally {
    loading.value = false;
  }
}

loadOverview();
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 24px;
  width: min(100%, 1380px);
  margin: 0 auto;
}
.admin-page__heading {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
}
.admin-page__heading p {
  margin: 0 0 5px;
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.admin-page__heading h1 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(1.65rem, 3vw, 2.25rem);
}
.admin-page__heading span {
  display: block;
  margin-top: 8px;
  color: var(--cc4c-muted);
}
.overview-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.overview-stats article {
  display: grid;
  gap: 5px;
  padding: 22px;
  border: 1px solid var(--cc4c-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}
.overview-stats span {
  color: var(--cc4c-muted);
  font-size: 0.78rem;
  font-weight: 700;
}
.overview-stats strong {
  color: var(--cc4c-text);
  font-size: 2rem;
  line-height: 1.2;
}
.overview-stats article:first-child strong {
  color: var(--cc4c-primary);
}
.overview-stats small {
  color: #94a3b8;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: start;
}
.data-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--cc4c-border);
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.055);
}
.data-panel__heading {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 20px 22px;
  border-bottom: 1px solid var(--cc4c-border);
}
.data-panel__heading h2 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: 1.08rem;
}
.data-panel__heading p {
  margin: 4px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.78rem;
}
.data-table-scroll {
  width: 100%;
  overflow-x: auto;
}
.data-table-scroll :deep(.el-table) {
  min-width: 720px;
}
.data-table-scroll :deep(.el-table__header th) {
  background: #f8fafc;
  color: #526079;
}
.status-dot {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  color: #15803d;
  font-size: 0.78rem;
  font-weight: 700;
}
.status-dot i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
}

@media (max-width: 1180px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 700px) {
  .admin-page__heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .overview-stats {
    grid-template-columns: 1fr;
  }
  .admin-page__heading .el-button {
    width: 100%;
  }
}
</style>
