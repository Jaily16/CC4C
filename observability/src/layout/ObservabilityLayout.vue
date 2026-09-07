<script setup>
/** 观测后台布局，维护中文导航、当前路径和安全退出入口。 */
import { ElButton } from 'element-plus';
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth.js';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const navigation = [
  { path: '/overview', label: '运行总览' },
  { path: '/api-jvm', label: 'API 与 JVM' },
  { path: '/data-cache-security', label: '数据库、缓存与安全' },
  { path: '/messaging', label: '异步消息' },
  { path: '/operations', label: '告警与依赖' },
];

/** 从现有响应式状态派生 currentPath，不发起请求或写入外部数据。 */
const currentPath = computed(() => route.path);

/** 处理 signOut 清理操作，仅影响当前功能明确指向的状态或资源。 */
async function signOut() {
  try {
    await auth.logout();
  } finally {
    await router.replace('/login');
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">C4</span>
        <div>
          <strong>CC4C 中文观测后台</strong>
          <small>独立运行身份</small>
        </div>
      </div>
      <div class="operator">
        <span>{{ auth.username }}</span>
        <ElButton plain size="small" @click="signOut">安全退出</ElButton>
      </div>
    </header>
    <aside class="sidebar" aria-label="观测导航">
      <RouterLink
        v-for="item in navigation"
        :key="item.path"
        :to="item.path"
        class="nav-link"
        :class="{ active: currentPath === item.path }"
      >
        {{ item.label }}
      </RouterLink>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding-top: var(--cc4c-header-height);
  padding-left: var(--cc4c-sidebar-width);
}

.topbar {
  position: fixed;
  z-index: 20;
  top: 0;
  right: 0;
  left: 0;
  display: flex;
  height: var(--cc4c-header-height);
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--cc4c-surface);
  border-bottom: 1px solid var(--cc4c-border);
}

.brand,
.operator {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: white;
  background: var(--cc4c-primary);
  border-radius: 10px;
  font-weight: 800;
}

.brand strong,
.brand small {
  display: block;
}

.brand small {
  margin-top: 2px;
  color: var(--cc4c-muted);
}

.sidebar {
  position: fixed;
  z-index: 10;
  top: var(--cc4c-header-height);
  bottom: 0;
  left: 0;
  width: var(--cc4c-sidebar-width);
  padding: 22px 14px;
  overflow-y: auto;
  background: #0f172a;
}

.nav-link {
  display: block;
  padding: 12px 14px;
  margin-bottom: 6px;
  color: #cbd5e1;
  border-radius: 9px;
  transition: background var(--cc4c-transition);
}

.nav-link:hover,
.nav-link:focus-visible,
.nav-link.active {
  color: white;
  background: #1e40af;
  outline: none;
}

.content {
  width: min(100%, calc(var(--cc4c-content-max-width) + 48px));
  min-height: calc(100vh - var(--cc4c-header-height));
  padding: 28px 24px 48px;
  margin: 0 auto;
}

@media (max-width: 900px) {
  .app-shell {
    padding-left: 0;
    padding-top: calc(var(--cc4c-header-height) + 54px);
  }

  .sidebar {
    top: var(--cc4c-header-height);
    right: 0;
    bottom: auto;
    display: flex;
    width: auto;
    padding: 8px 12px;
    overflow-x: auto;
  }

  .nav-link {
    flex: 0 0 auto;
    padding: 8px 11px;
    margin: 0 4px 0 0;
    font-size: 13px;
  }
}

@media (max-width: 600px) {
  .brand small,
  .operator span {
    display: none;
  }

  .topbar {
    padding: 0 14px;
  }

  .content {
    padding: 22px 14px 36px;
  }
}
</style>
