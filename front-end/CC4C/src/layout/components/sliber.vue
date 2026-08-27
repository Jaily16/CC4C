<template>
  <div class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <div class="sidebar__top">
      <span v-if="!collapsed" class="sidebar__title">
        <strong>CC4C</strong>
        <small>学习空间</small>
      </span>
      <el-tooltip :content="collapsed ? '展开导航' : '收起导航'" placement="right">
        <el-button
          class="sidebar__collapse"
          :aria-label="collapsed ? '展开侧边导航' : '收起侧边导航'"
          circle
          :icon="collapsed ? ArrowRightBold : ArrowLeftBold"
          text
          @click="toggleCollapse"
        />
      </el-tooltip>
    </div>

    <div v-if="!collapsed" class="sidebar__profile">
      <div class="sidebar__avatar-wrap">
        <el-avatar v-if="avatar" :size="64" :src="avatar" @error="handleAvatarError" />
        <span v-else class="sidebar__avatar-fallback" aria-hidden="true">{{ avatarInitial }}</span>
      </div>
      <div class="sidebar__profile-copy">
        <p>{{ displayName }}</p>
        <span>个人学习空间</span>
      </div>
    </div>

    <el-menu
      class="sidebar__menu"
      :default-openeds="['resources', 'blog']"
      :default-active="route.path"
      :collapse="collapsed"
      :collapse-transition="false"
      router
    >
      <el-menu-item index="/home">
        <el-icon><HomeFilled /></el-icon>
        <template #title>主页</template>
      </el-menu-item>
      <el-menu-item index="/userinfo">
        <el-icon><UserFilled /></el-icon>
        <template #title>个人空间</template>
      </el-menu-item>
      <el-menu-item index="/favorite">
        <el-icon><StarFilled /></el-icon>
        <template #title>收藏</template>
      </el-menu-item>

      <el-sub-menu index="resources">
        <template #title>
          <el-icon><Reading /></el-icon>
          <span>学习资源</span>
        </template>
        <el-menu-item index="/course">浏览课程</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="blog">
        <template #title>
          <el-icon><Notebook /></el-icon>
          <span>博客广场</span>
        </template>
        <el-menu-item index="/blog">浏览博客</el-menu-item>
        <el-menu-item index="/blogWrite">撰写博客</el-menu-item>
        <el-menu-item index="/blogmanage">管理博客</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeftBold, ArrowRightBold, HomeFilled, Notebook, Reading, StarFilled, UserFilled } from '@element-plus/icons-vue';
import store from '@/store';

const route = useRoute();
const isCollapse = ref(false);
const isCompact = ref(false);
const avatarLoadFailed = ref(false);
const user = computed(() => store.state.user);
const collapsed = computed(() => isCollapse.value || isCompact.value);
const displayName = computed(() => user.value.role === 'USER' ? user.value.name : '游客');
const avatar = computed(() => avatarLoadFailed.value ? '' : user.value.avatar);
const avatarInitial = computed(() => (displayName.value || '游').trim().slice(0, 1).toUpperCase());

watch(() => user.value.avatar, () => {
  avatarLoadFailed.value = false;
});

function updateCompactMode() {
  isCompact.value = window.innerWidth <= 768;
}

function toggleCollapse() {
  if (!isCompact.value) {
    isCollapse.value = !isCollapse.value;
  }
}

function handleAvatarError() {
  avatarLoadFailed.value = true;
  return true;
}

onMounted(() => {
  updateCompactMode();
  window.addEventListener('resize', updateCompactMode);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCompactMode);
});
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  width: var(--cc4c-sidebar-width);
  height: 100%;
  border-right: 1px solid var(--cc4c-border);
  background: linear-gradient(180deg, #f8fbff 0%, var(--cc4c-surface) 36%);
  box-shadow: 8px 0 24px rgba(15, 23, 42, .035);
  transition: width var(--cc4c-transition);
}

.sidebar--collapsed {
  width: var(--cc4c-sidebar-collapsed-width);
}

.sidebar__top {
  display: flex;
  min-height: 58px;
  padding: 0 12px 0 16px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(203, 213, 225, .72);
}

.sidebar--collapsed .sidebar__top {
  padding: 0;
  justify-content: center;
}

.sidebar__title {
  display: grid;
  gap: 1px;
  color: var(--cc4c-text);
  line-height: 1.15;
}

.sidebar__title strong {
  color: var(--cc4c-primary);
  font-size: .86rem;
  letter-spacing: .04em;
}

.sidebar__title small {
  color: var(--cc4c-muted);
  font-size: .7rem;
  font-weight: 600;
}

.sidebar__collapse {
  width: 32px;
  height: 32px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #eff6ff;
  color: var(--cc4c-primary);
}

.sidebar__collapse:hover,
.sidebar__collapse:focus-visible {
  border-color: var(--cc4c-primary);
  background: var(--cc4c-primary);
  color: white;
  outline: none;
}

.sidebar__profile {
  display: flex;
  gap: 11px;
  align-items: center;
  margin: 14px 10px 8px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: rgba(255, 255, 255, .78);
  box-shadow: 0 8px 18px rgba(15, 23, 42, .045);
  color: var(--cc4c-text);
}

.sidebar__avatar-wrap {
  display: grid;
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  border: 3px solid white;
  box-shadow: 0 7px 18px rgba(37, 99, 235, .15);
}

.sidebar__avatar-wrap :deep(.el-avatar) {
  width: 100%;
  height: 100%;
}

.sidebar__avatar-fallback {
  color: #1d4ed8;
  font-size: 1.25rem;
  font-weight: 800;
}

.sidebar__profile-copy {
  min-width: 0;
}

.sidebar__profile p {
  max-width: 100%;
  margin: 0;
  overflow: hidden;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar__profile-copy span {
  display: block;
  margin-top: 4px;
  color: var(--cc4c-muted);
  font-size: .7rem;
  white-space: nowrap;
}

.sidebar__menu {
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px;
  border-right: 0;
  --el-menu-active-color: var(--cc4c-primary);
  --el-menu-hover-bg-color: #eaf2ff;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--cc4c-text);
}

.sidebar__menu:not(.el-menu--collapse) {
  width: var(--cc4c-sidebar-width);
}

.sidebar--collapsed .sidebar__menu {
  width: 100%;
  padding-right: 0;
  padding-left: 0;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  min-height: 46px;
  margin: 4px 0;
  border-radius: 10px;
  line-height: 46px;
}

:deep(.el-sub-menu .el-menu) {
  background: transparent;
}

:deep(.el-menu-item.is-active) {
  border-right: 0;
  background: linear-gradient(90deg, #dbeafe, #eff6ff);
  font-weight: 700;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  color: var(--cc4c-primary);
}

:deep(.el-menu--collapse) {
  width: var(--cc4c-sidebar-collapsed-width);
}

:deep(.el-menu--collapse .el-menu-item),
:deep(.el-menu--collapse .el-sub-menu__title) {
  margin-right: 0;
  margin-left: 0;
}

:deep(.el-menu--collapse > .el-menu-item .el-icon),
:deep(.el-menu--collapse > .el-sub-menu > .el-sub-menu__title .el-icon) {
  margin: 0 !important;
}

@media (max-width: 768px) {
  .sidebar {
    width: var(--cc4c-sidebar-collapsed-width);
  }

  .sidebar__top {
    display: none;
  }

  .sidebar__menu {
    padding: 10px 6px;
  }
}
</style>
