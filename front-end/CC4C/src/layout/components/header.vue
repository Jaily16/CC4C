<template>
  <nav class="top-header" aria-label="主导航">
    <router-link class="brand" to="/home" aria-label="CC4C 主页">
      <el-image class="brand__mark" :src="assets.logoPart1" alt="CC4C 图标" fit="contain" />
      <el-image class="brand__name" :src="assets.logoPart2" alt="CC4C" fit="contain" />
      <el-image class="brand__tagline" :src="assets.logoPart3" alt="学习与交流平台" fit="contain" />
    </router-link>

    <div class="top-header__links">
      <router-link v-for="item in navItems" :key="item.to" v-slot="{ isExactActive, navigate }" :to="item.to" custom>
        <button
          type="button"
          class="nav-link"
          :class="{ 'nav-link--active': isExactActive }"
          @click="navigate"
        >
          {{ item.label }}
        </button>
      </router-link>
    </div>

    <div class="top-header__actions">
      <el-button
        v-if="isLoggedIn"
        class="session-action"
        size="small"
        :icon="SwitchButton"
        @click="logout"
      >
        退出登录
      </el-button>
      <el-button
        v-else
        class="session-action"
        size="small"
        type="primary"
        :icon="Position"
        @click="flyToLogin"
      >
        登录
      </el-button>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue';
import axios from '@/plugins/axiosInstance';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Position, SwitchButton } from '@element-plus/icons-vue';
import store from '@/store';
import { assets } from '@/assets';

const router = useRouter();
const user = computed(() => store.state.user);
const isLoggedIn = computed(() => user.value.id !== '');
const navItems = [
  { label: '主页', to: '/home' },
  { label: '所有课程', to: '/allCourses' },
  { label: '所有博客', to: '/allBlogs' },
];

async function logout() {
  try {
    const resp = await axios.get('/users/logout');

    if (resp.data.data === true) {
      store.commit('RESET_STATE');
      ElMessage.success('已退出登录');
      await router.push('/login');
      return;
    }

    ElMessage.error(resp.data.msg || '退出登录失败');
  } catch (error) {
    ElMessage.error('退出登录失败，请稍后重试');
    console.error(error);
  }
}

function flyToLogin() {
  router.push('/login');
}
</script>

<style scoped>
.top-header {
  display: flex;
  min-width: 0;
  min-height: var(--cc4c-header-height);
  align-items: center;
  gap: 12px;
  padding: 7px clamp(12px, 2vw, 28px);
  border-bottom: 1px solid var(--cc4c-border);
  background: var(--cc4c-surface);
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.04);
}

.brand {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  color: var(--cc4c-text);
  text-decoration: none;
}

.brand__mark {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
}

.brand__name {
  width: 82px;
  height: 36px;
  flex: 0 0 82px;
}

.brand__tagline {
  width: min(230px, 18vw);
  height: 36px;
}

.top-header__links {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}

.nav-link {
  min-height: 36px;
  padding: 7px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--cc4c-muted);
  cursor: pointer;
  transition: color var(--cc4c-transition), background-color var(--cc4c-transition);
}

.nav-link:hover,
.nav-link--active {
  background: color-mix(in srgb, var(--cc4c-primary) 10%, transparent);
  color: var(--cc4c-primary);
}

.nav-link--active {
  font-weight: 700;
}

.top-header__actions {
  flex: 0 0 auto;
}

.session-action {
  min-height: 36px;
}

@media (max-width: 900px) {
  .brand__tagline {
    display: none;
  }
}

@media (max-width: 600px) {
  .top-header {
    flex-wrap: wrap;
    gap: 6px 10px;
  }

  .top-header__links {
    width: 100%;
    order: 3;
    justify-content: space-between;
    margin-left: 0;
  }

  .nav-link {
    flex: 1;
    padding-inline: 6px;
  }
}

@media (max-width: 390px) {
  .brand__name {
    width: 72px;
  }

  .nav-link {
    font-size: 0.875rem;
  }
}
</style>
