<script setup>
/** 异步消息页面，固定展示消息链路观测仪表板。 */
import DashboardPage from '@/components/DashboardPage.vue';
</script>

<template>
  <DashboardPage
    dashboard-id="messaging"
    title="异步消息"
    description="查看 Outbox、Inbox、发布消费延迟、RabbitMQ 队列及异常消息趋势。"
  />
</template>
