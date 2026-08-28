import { createRouter, createWebHistory } from 'vue-router'

/* Layout */
import Layout from '../layout/index.vue'
import store from '../store'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/home',
      component: Layout,
      redirect: '/home',
      children: [{
        path: '/home',
        name: 'home',
        component: () => import('../views/HomeView.vue'),
      },
      {
        path: '/course',
        name: 'course',
        component: () => import('../views/course/CourseView.vue'),
      },
      {
        path: '/blog',
        name: 'blog',
        component: () => import('../views/blog/BlogView.vue')
      },

      {
        path: '/blogsdetail',
        name: 'blogsdetail',
        component: () => import('../views/blog/BlogDetailView.vue')
      },
      {
        path: '/userinfo',
        name: 'userinfo',
        meta: { requiresUser: true },
        component: () => import('../views/UserinfoView.vue')
      },
      {
        path: '/favorite',
        name: 'favorite',
        meta: { requiresUser: true },
        component: () => import('../views/FavoriteView.vue')
      },
      {
        path: '/blogWrite',
        name: 'blogWrite',
        meta: { requiresUser: true },
        component: () => import('../views/blog/BlogWriteView.vue')
      },
      {
        path: '/blogmanage',
        name: 'blogmanage',
        meta: { requiresUser: true },
        component: () => import('../views/blog/BlogManageView.vue')
      },
      {
        path: '/blogDetail',
        name: 'blogDetail',
        component: () => import('../views/blog/BlogDetailView.vue')
      },
      {
        path: '/courseDetail',
        name: 'courseDetail',
        component: () => import('../views/course/CourseDetailView.vue')
      },
      {
        path: '/allCourses',
        name: 'allCourses',
        component: () => import('../views/course/AllCoursesView.vue')
      },
      {
        path: '/allBlogs',
        name: 'allBlogs',
        component: () => import('../views/blog/AllBlogsView.vue')
      },
      ]
    },
    {
      path: '/admin',
      redirect: '/adminlogin',
      meta: { requiresAdmin: true },
      component: () => import('@/views/admin/index.vue'),
      children: [
        {
          path: 'CoursesAndBlogs',
          name: 'CoursesAndBlogs',
          component: () => import('@/views/admin/CoursesAndBlogsView.vue'),
        },
        {
          path: 'addCourse',
          name: 'addCourse',
          component: () => import('@/views/admin/AddCourseView.vue'),
        },
        {
          path: 'checkBlog',
          name: 'checkBlog',
          component: () => import('@/views/admin/CheckBlogView.vue'),
        },
        {
          path: 'messaging',
          name: 'adminMessaging',
          component: () => import('@/views/admin/MessagingView.vue'),
        }
      ]
    },


    // {
    //   path: '/login',
    //   name: 'login',
    //   component: () => import('../views/login/LoginRegister.vue')
    // },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/login/Login.vue')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/login/Register.vue')
    },
    {
      path: '/adminLogin',
      name: 'adminLogin',
      component: () => import('@/views/admin/AdminLoginView.vue'),
    },
    {
      path: '/',
      redirect: '/login'
    },

  ]
})

router.beforeEach(async (to) => {
  try {
    await store.dispatch('hydrateSession')
  } catch (error) {
    store.commit('RESET_STATE')
    console.error(error)
  }

  const user = store.state.user
  if (to.matched.some((record) => record.meta.requiresAdmin) && user.role !== 'ADMIN') {
    return { path: '/adminLogin', query: { redirect: to.fullPath } }
  }
  if (to.matched.some((record) => record.meta.requiresUser) && user.role !== 'USER') {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && user.role === 'USER') return '/home'
  if (to.path === '/adminLogin' && user.role === 'ADMIN') return '/admin/CoursesAndBlogs'
  return true
})

export default router
