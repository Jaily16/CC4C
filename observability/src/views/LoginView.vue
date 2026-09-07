<script setup>
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus';
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth.js';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const formRef = ref(null);
const form = reactive({ username: '', password: '' });
const rules = {
  username: [{ required: true, message: '请输入观测账户', trigger: 'blur' }],
  password: [{ required: true, message: '请输入观测密码', trigger: 'blur' }],
};

async function submit() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  try {
    await auth.login({ username: form.username, password: form.password });
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview';
    await router.replace(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/overview');
  } catch {
    form.password = '';
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card surface-card" aria-labelledby="login-title">
      <div class="brand-mark">C4</div>
      <p class="eyebrow">CC4C 独立运行身份</p>
      <h1 id="login-title">中文观测后台</h1>
      <p class="description">使用专用 OBSERVABILITY 账户登录。业务用户和管理员身份不会被复用。</p>
      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <ElFormItem label="观测账户" prop="username">
          <ElInput v-model="form.username" autocomplete="username" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="观测密码" prop="password">
          <ElInput
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            maxlength="64"
            show-password
          />
        </ElFormItem>
        <p v-if="auth.error" class="login-error" role="alert">{{ auth.error }}</p>
        <ElButton native-type="submit" type="primary" size="large" :loading="auth.loading">安全登录</ElButton>
      </ElForm>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background:
    radial-gradient(circle at 15% 20%, rgba(37, 99, 235, 0.16), transparent 32%),
    linear-gradient(145deg, #eff6ff 0%, #f8fafc 58%, #eef2ff 100%);
}

.login-card {
  width: min(100%, 440px);
  padding: 38px;
}

.brand-mark {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 28px;
  place-items: center;
  color: white;
  background: var(--cc4c-primary);
  border-radius: 13px;
  font-size: 20px;
  font-weight: 800;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--cc4c-primary);
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 30px;
}

.description {
  margin: 12px 0 28px;
  color: var(--cc4c-muted);
  line-height: 1.7;
}

.login-error {
  margin: 0 0 16px;
  color: var(--cc4c-danger);
}

.el-button {
  width: 100%;
}
</style>
