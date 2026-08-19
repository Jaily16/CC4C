<template>
  <div class="admin-shell">
    <header class="admin-header">
      <router-link class="admin-brand" to="/admin/CoursesAndBlogs" aria-label="CC4C 管理后台首页">
        <span>CC4C</span>
        <div>
          <strong>内容管理后台</strong>
          <small>ADMIN CONSOLE</small>
        </div>
      </router-link>
      <div class="admin-header__status">
        <span><i aria-hidden="true"></i>管理员会话</span>
        <el-button plain @click="logout">退出管理端</el-button>
      </div>
    </header>

    <aside class="admin-sidebar" aria-label="管理端主导航">
      <p>工作台</p>
      <el-menu :default-active="activePath" router>
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <div class="admin-sidebar__help">
        <strong>操作提示</strong>
        <span>审核与发布操作会直接影响用户端内容，请确认后执行。</span>
      </div>
    </aside>

    <nav class="admin-mobile-nav" aria-label="管理端主导航">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        :class="{ 'admin-mobile-nav__active': activePath === item.path }"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.shortLabel }}</span>
      </router-link>
    </nav>

    <main class="admin-main">
      <PageFeedback v-if="verifying" :loading="true" />
      <router-view v-else-if="verified" />
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Checked, DataAnalysis, Plus } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import PageFeedback from '@/components/common/PageFeedback.vue';

axios.defaults.withCredentials = true;

const route = useRoute();
const router = useRouter();
const verifying = ref(true);
const verified = ref(false);
const menuItems = [
  { path: '/admin/CoursesAndBlogs', label: '内容数据概览', shortLabel: '概览', icon: DataAnalysis },
  { path: '/admin/addCourse', label: '发布新课程', shortLabel: '课程', icon: Plus },
  { path: '/admin/checkBlog', label: '审核社区博客', shortLabel: '审核', icon: Checked },
];
const activePath = computed(() => route.path);

async function verifyAdmin() {
  verifying.value = true;
  try {
    const response = await axios.get('http://localhost:4080/admin/verify');
    if (response.data.data === false) {
      ElMessage.warning(response.data.msg || '请先登录管理端');
      await router.replace('/adminLogin');
      return;
    }
    verified.value = true;
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '管理员身份验证失败，请重新登录');
    await router.replace('/adminLogin');
    console.error(error);
  } finally {
    verifying.value = false;
  }
}

async function logout() {
  try {
    await axios.get('http://localhost:4080/admin/logout');
  } catch (error) {
    console.error(error);
  } finally {
    ElMessage.success('已退出管理端');
    await router.replace('/adminLogin');
  }
}

verifyAdmin();
</script>

<style scoped>
.admin-shell { min-height: 100vh; background: #f3f6fb; color: var(--cc4c-text); }
.admin-header { position: fixed; z-index: 50; top: 0; right: 0; left: 0; display: flex; height: 68px; align-items: center; justify-content: space-between; padding: 0 24px; border-bottom: 1px solid #dfe6f0; background: rgba(255, 255, 255, .96); box-shadow: 0 3px 14px rgba(15, 23, 42, .05); backdrop-filter: blur(12px); }
.admin-brand { display: flex; gap: 12px; align-items: center; color: inherit; text-decoration: none; }
.admin-brand > span { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; background: var(--cc4c-discovery-hero); color: #fff; font-size: .78rem; font-weight: 900; box-shadow: 0 8px 18px rgba(37, 99, 235, .22); }
.admin-brand div { display: grid; gap: 1px; }
.admin-brand strong { font-size: .96rem; }
.admin-brand small { color: var(--cc4c-muted); font-size: .62rem; font-weight: 800; letter-spacing: .12em; }
.admin-header__status { display: flex; gap: 16px; align-items: center; }
.admin-header__status > span { display: inline-flex; gap: 7px; align-items: center; color: var(--cc4c-muted); font-size: .8rem; }
.admin-header__status i { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 4px rgba(34, 197, 94, .12); }
.admin-sidebar { position: fixed; z-index: 40; top: 68px; bottom: 0; left: 0; display: flex; width: 230px; flex-direction: column; padding: 24px 14px; border-right: 1px solid #dfe6f0; background: #fff; }
.admin-sidebar > p { margin: 0 12px 12px; color: #94a3b8; font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.admin-sidebar :deep(.el-menu) { border-right: 0; }
.admin-sidebar :deep(.el-menu-item) { height: 48px; margin-bottom: 6px; border-radius: 11px; color: #526079; font-weight: 700; }
.admin-sidebar :deep(.el-menu-item:hover) { background: #f3f7ff; color: var(--cc4c-primary); }
.admin-sidebar :deep(.el-menu-item.is-active) { background: #eaf1ff; color: var(--cc4c-primary); box-shadow: inset 3px 0 var(--cc4c-primary); }
.admin-sidebar__help { display: grid; gap: 7px; margin-top: auto; padding: 15px; border: 1px solid #dce7f8; border-radius: 13px; background: #f5f9ff; }
.admin-sidebar__help strong { color: var(--cc4c-primary); font-size: .78rem; }
.admin-sidebar__help span { color: var(--cc4c-muted); font-size: .72rem; line-height: 1.55; }
.admin-main { min-width: 0; min-height: 100vh; padding: 96px 30px 34px 260px; }
.admin-mobile-nav { display: none; }

@media (max-width: 1024px) {
  .admin-sidebar { display: none; }
  .admin-mobile-nav { position: fixed; z-index: 45; top: 68px; right: 0; left: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border-bottom: 1px solid #dfe6f0; background: #fff; }
  .admin-mobile-nav a { display: flex; gap: 7px; align-items: center; justify-content: center; min-height: 52px; color: var(--cc4c-muted); font-size: .82rem; font-weight: 700; text-decoration: none; }
  .admin-mobile-nav__active { color: var(--cc4c-primary) !important; background: #eef4ff; box-shadow: inset 0 -3px var(--cc4c-primary); }
  .admin-main { padding: 142px 22px 28px; }
}

@media (max-width: 520px) {
  .admin-header { padding: 0 14px; }
  .admin-brand div { display: none; }
  .admin-header__status > span { display: none; }
  .admin-main { padding-inline: 12px; }
}
</style>
