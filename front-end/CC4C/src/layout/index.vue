<template>
  <div class="user-layout">
    <header class="user-layout__header">
      <Header />
    </header>

    <div class="user-layout__body">
      <aside class="user-layout__sidebar" aria-label="用户功能导航">
        <Sliber />
      </aside>

      <main class="user-layout__main" tabindex="-1">
        <div class="user-layout__content">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import Sliber from './components/sliber.vue';
import Header from './components/header.vue';
</script>

<style scoped>
.user-layout {
  display: flex;
  min-width: 0;
  min-height: 100vh;
  flex-direction: column;
  background: var(--cc4c-bg);
}

.user-layout__header {
  position: sticky;
  top: 0;
  z-index: 20;
  flex: 0 0 auto;
}

.user-layout__body {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
}

.user-layout__sidebar {
  position: sticky;
  top: var(--cc4c-header-height);
  z-index: 10;
  align-self: flex-start;
  height: calc(100vh - var(--cc4c-header-height));
  max-width: var(--cc4c-sidebar-width);
  flex: 0 0 auto;
}

.user-layout__main {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow-x: clip;
  overflow-y: auto;
}

.user-layout__content {
  min-width: 0;
  min-height: 100%;
}

@media (max-width: 768px) {
  .user-layout__sidebar {
    max-width: var(--cc4c-sidebar-collapsed-width);
  }
}
</style>
