<template>
  <section class="messaging-page" aria-labelledby="messaging-title">
    <header class="page-heading">
      <div>
        <p>Reliability</p>
        <h1 id="messaging-title">异步消息恢复</h1>
        <span>查看待发送、发布失败与消费死信；页面不会展示邮件地址或消息载荷。</span>
      </div>
      <el-button :loading="loading" @click="loadMessages">刷新</el-button>
    </header>

    <div class="filters">
      <el-select v-model="status" aria-label="消息状态" @change="resetAndLoad">
        <el-option label="待发送与失败" value="" />
        <el-option label="待发送" value="PENDING" />
        <el-option label="发布中" value="PUBLISHING" />
        <el-option label="Broker 已接收" value="PUBLISHED" />
        <el-option label="发布失败" value="PUBLISH_FAILED" />
        <el-option label="消费死信" value="DEAD" />
        <el-option label="已过期" value="EXPIRED" />
        <el-option label="已忽略" value="IGNORED" />
      </el-select>
      <el-select v-model="eventType" aria-label="事件类型" @change="resetAndLoad">
        <el-option label="全部事件" value="" />
        <el-option label="验证码邮件" value="identity.verification-email.requested.v1" />
        <el-option label="博客待审核" value="community.blog.submitted.v1" />
        <el-option label="博客审核结果" value="community.blog.reviewed.v1" />
      </el-select>
    </div>

    <PageFeedback v-if="loading || errorMessage" :loading="loading" :error="errorMessage" @retry="loadMessages" />

    <section v-else class="message-panel">
      <el-table v-if="messages.length" :data="messages" stripe>
        <el-table-column prop="eventType" label="事件" min-width="250" show-overflow-tooltip />
        <el-table-column prop="aggregateId" label="业务对象" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="130">
          <template #default="scope"
            ><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="尝试" width="100">
          <template #default="scope">{{ scope.row.publishAttempts }}/{{ scope.row.consumeAttempts }}</template>
        </el-table-column>
        <el-table-column prop="errorCode" label="错误码" min-width="170">
          <template #default="scope">{{ scope.row.errorCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="scope">{{ formatDate(scope.row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :disabled="!scope.row.recoverable" @click="retryMessage(scope.row)"
              >重试</el-button
            >
            <el-button link type="danger" :disabled="!scope.row.recoverable" @click="ignoreMessage(scope.row)"
              >忽略</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="当前筛选条件下没有异步消息" />
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="loadMessages"
      />
    </section>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  ignoreMessage as ignoreMessageRequest,
  listMessages,
  retryMessage as retryMessageRequest,
} from '@/api/messaging';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';

const messages = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const status = ref('');
const eventType = ref('');
const page = ref(1);
const size = 20;
const total = ref(0);

async function loadMessages() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const response = await listMessages({
      page: page.value,
      size,
      ...(status.value ? { status: status.value } : {}),
      ...(eventType.value ? { eventType: eventType.value } : {}),
    });
    messages.value = response.data.data?.items || [];
    total.value = response.data.data?.total || 0;
    if (!messages.value.length && page.value > 1) {
      page.value -= 1;
      await loadMessages();
    }
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '异步消息加载失败，请检查服务状态后重试。');
  } finally {
    loading.value = false;
  }
}

async function retryMessage(row) {
  await ElMessageBox.confirm('确认重新投递这条消息？外部邮件服务处于不确定状态时可能收到内容相同的邮件。', '重试消息', {
    type: 'warning',
  });
  try {
    await retryMessageRequest(row.eventId);
    ElMessage.success('消息已重新进入待发送队列');
    await loadMessages();
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '消息重试失败'));
  }
}

async function ignoreMessage(row) {
  await ElMessageBox.confirm('忽略后不会再自动投递，是否继续？', '忽略消息', { type: 'warning' });
  try {
    await ignoreMessageRequest(row.eventId);
    ElMessage.success('消息已标记为忽略');
    await loadMessages();
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '消息忽略失败'));
  }
}

function resetAndLoad() {
  page.value = 1;
  loadMessages();
}

function statusType(value) {
  if (value === 'PUBLISH_FAILED' || value === 'DEAD') return 'danger';
  if (value === 'PENDING' || value === 'PUBLISHING' || value === 'PUBLISHED') return 'warning';
  if (value === 'DELIVERED') return 'success';
  return 'info';
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN');
}

loadMessages();
</script>

<style scoped>
.messaging-page {
  display: grid;
  gap: 22px;
  width: min(100%, 1380px);
  margin: 0 auto;
}
.page-heading {
  display: flex;
  gap: 20px;
  align-items: flex-end;
  justify-content: space-between;
}
.page-heading p {
  margin: 0 0 5px;
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.page-heading h1 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(1.65rem, 3vw, 2.25rem);
}
.page-heading span {
  display: block;
  margin-top: 8px;
  color: var(--cc4c-muted);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.filters .el-select {
  width: min(100%, 290px);
}
.message-panel {
  display: grid;
  gap: 18px;
  padding: 20px;
  border: 1px solid var(--cc4c-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}
.message-panel .el-pagination {
  justify-self: end;
}
@media (max-width: 640px) {
  .page-heading {
    align-items: start;
    flex-direction: column;
  }
  .message-panel {
    padding: 12px;
    overflow-x: auto;
  }
}
</style>
