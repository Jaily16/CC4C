<script setup>
/** 数据库、缓存与安全页面，固定展示对应观测仪表板。 */
import DashboardPage from '@/components/DashboardPage.vue';
</script>

<template>
  <DashboardPage
    dashboard-id="data-cache-security"
    title="数据库、缓存与安全"
    description="查看 MyBatis、Hikari、缓存、认证、限流和授权拒绝的固定指标。"
  />
</template>
