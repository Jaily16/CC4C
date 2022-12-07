import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/course',
      name: 'course',
      component: () => import('../views/CourseView.vue')
    },
    {
      path: '/blog',
      name: 'blog',
      component: () => import('../views/BlogView.vue')
    },
    {
      path: '/userinfo',
      name: 'userinfo',
      component: () => import('../views/UserinfoView.vue')
    },
    {
      path: '/favorite',
      name: 'favorite',
      component: () => import('../views/FavoriteView.vue')
    },
    {
      path: '/write',
      name: 'write',
      component: () => import('../views/WriteView.vue')
    },
    {
      path: '/blogmanage',
      name: 'blogmanage',
      component: () => import('../views/BlogManageView.vue')
    }
  ]
})

export default router
