import { createRouter, createWebHistory } from 'vue-router';

import { useAuthStore } from '@/stores/auth.js';

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/ObservabilityLayout.vue'),
    children: [
      { path: '', redirect: '/overview' },
      { path: 'overview', name: 'overview', component: () => import('@/views/OverviewView.vue') },
      { path: 'api-jvm', name: 'api-jvm', component: () => import('@/views/ApiJvmView.vue') },
      {
        path: 'data-cache-security',
        name: 'data-cache-security',
        component: () => import('@/views/DataCacheSecurityView.vue'),
      },
      { path: 'messaging', name: 'messaging', component: () => import('@/views/MessagingView.vue') },
      { path: 'operations', name: 'operations', component: () => import('@/views/OperationsView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

/** 在路由进入前恢复观测 Session，并按认证状态限制登录页与受保护页面。 */
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.hydrate();
  if (to.meta.public) {
    return auth.authenticated ? { name: 'overview' } : true;
  }
  if (!auth.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
