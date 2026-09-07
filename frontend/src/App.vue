<!-- 应用根组件，承载路由出口与全局页面容器，不直接处理业务请求。 -->
<template>
  <div class="app-shell">
    <router-view />
  </div>
</template>

<script setup></script>

<style>
.app-shell {
  min-width: 0;
  min-height: 100vh;
}
</style>
