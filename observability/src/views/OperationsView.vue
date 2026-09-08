<script setup>
/** 告警与依赖页面，组合告警、依赖、存活、就绪和请求关联状态。 */
import { ElTable, ElTableColumn } from 'element-plus';

import StatusBadge from '@/components/StatusBadge.vue';
import TimeRangeToolbar from '@/components/TimeRangeToolbar.vue';
import { usePolling } from '@/composables/usePolling.js';
import { useOperationsStore } from '@/stores/operations.js';

const store = useOperationsStore();
const polling = usePolling((signal) => store.load(signal));

/** 把现有数据转换为 formatTime 所需展示结构，不产生外部副作用。 */
function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN') : '未提供';
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>告警与依赖</h1>
        <p>核对二十条固定告警、应用可用性和依赖状态。这里不会显示凭据、原始异常或任意 PromQL。</p>
      </div>
      <StatusBadge v-if="store.dependencies" :status="store.dependencies.overall" />
    </div>
    <TimeRangeToolbar
      model-value="1h"
      :show-range="false"
      :paused="polling.paused.value"
      :refreshing="polling.refreshing.value"
      :last-updated-at="polling.lastUpdatedAt.value"
      @refresh="polling.refresh()"
      @toggle-pause="polling.togglePause"
    />
    <div v-if="store.error" class="feedback error surface-card" role="alert">{{ store.error }}</div>
    <template v-else-if="store.alerts && store.dependencies">
      <div class="availability-grid">
        <article class="surface-card availability-card">
          <span>存活状态</span>
          <StatusBadge :status="store.dependencies.liveness" />
        </article>
        <article class="surface-card availability-card">
          <span>就绪状态</span>
          <StatusBadge :status="store.dependencies.readiness" />
        </article>
        <article class="surface-card availability-card request-id">
          <span>本次请求关联 ID</span>
          <code>{{ store.dependencies.requestId }}</code>
        </article>
      </div>

      <section class="table-card surface-card" aria-labelledby="dependency-title">
        <header>
          <div>
            <h2 id="dependency-title">运行依赖</h2>
            <p>检查时间：{{ formatTime(store.dependencies.checkedAt) }}</p>
          </div>
        </header>
        <ElTable :data="store.dependencies.dependencies" stripe>
          <ElTableColumn prop="title" label="依赖" min-width="180" />
          <ElTableColumn label="状态" width="130">
            <template #default="scope"><StatusBadge :status="scope.row.status" /></template>
          </ElTableColumn>
          <ElTableColumn prop="message" label="说明" min-width="220" />
        </ElTable>
      </section>

      <section class="table-card surface-card" aria-labelledby="alerts-title">
        <header>
          <div>
            <h2 id="alerts-title">Prometheus 告警规则</h2>
            <p>
              已载入 {{ store.alerts.loaded }}/{{ store.alerts.expected }}，触发 {{ store.alerts.firing }}，等待
              {{ store.alerts.pending }}。
            </p>
          </div>
          <StatusBadge :status="store.alerts.sourceStatus" />
        </header>
        <ElTable :data="store.alerts.rules" stripe>
          <ElTableColumn prop="title" label="告警" min-width="180" />
          <ElTableColumn prop="description" label="含义" min-width="300" />
          <ElTableColumn prop="severity" label="级别" width="100" />
          <ElTableColumn label="状态" width="130">
            <template #default="scope"><StatusBadge :status="scope.row.state" /></template>
          </ElTableColumn>
          <ElTableColumn label="规则健康" width="130">
            <template #default="scope"><StatusBadge :status="scope.row.health" /></template>
          </ElTableColumn>
          <ElTableColumn label="最近评估" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.lastEvaluationAt) }}</template>
          </ElTableColumn>
        </ElTable>
      </section>
    </template>
    <div v-else class="feedback surface-card">正在读取告警与依赖状态…</div>
  </section>
</template>

<style scoped>
.availability-grid {
  display: grid;
  grid-template-columns: 180px 180px minmax(260px, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.availability-card {
  display: flex;
  min-height: 82px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
}

.availability-card span,
.table-card p {
  color: var(--cc4c-muted);
}

.request-id {
  align-items: flex-start;
  flex-direction: column;
}

code {
  max-width: 100%;
  overflow-wrap: anywhere;
  color: var(--cc4c-primary);
}

.table-card {
  padding: 20px;
  margin-bottom: 20px;
  overflow: hidden;
}

.table-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

h2,
p {
  margin: 0;
}

.table-card p {
  margin-top: 7px;
  font-size: 13px;
}

@media (max-width: 800px) {
  .availability-grid {
    grid-template-columns: 1fr;
  }
}
</style>
