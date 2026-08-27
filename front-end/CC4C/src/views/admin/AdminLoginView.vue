<template>
  <main class="auth-page">
    <section class="auth-layout" aria-labelledby="admin-login-title">
      <div class="auth-brand">
        <el-image class="auth-brand__logo" :src="assets.logoPart1" alt="CC4C" fit="contain" />
        <p class="auth-brand__eyebrow">管理端</p>
        <h1>专注审核、课程与社区内容管理。</h1>
        <p>此入口仅适用于已授权的 CC4C 管理员。</p>
        <router-link class="auth-brand__link" to="/login">返回用户登录</router-link>
      </div>

      <section class="auth-card">
        <div class="auth-card__heading">
          <p class="auth-card__eyebrow">管理员身份验证</p>
          <h2 id="admin-login-title">登录管理端</h2>
          <p>请输入管理员 ID 和密码。</p>
        </div>

        <el-form class="auth-form" label-position="top" @submit.prevent="login">
          <el-form-item label="管理员 ID" :error="fieldErrors.id">
            <el-input
              v-model.trim="form.id"
              autocomplete="username"
              placeholder="请输入管理员 ID"
              clearable
              @blur="validateField('id')"
            />
          </el-form-item>

          <el-form-item label="密码" :error="fieldErrors.password">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              @blur="validateField('password')"
            />
          </el-form-item>

          <p v-if="formError" class="auth-form__error" role="alert">{{ formError }}</p>
          <el-button class="auth-form__submit" native-type="submit" type="primary" :loading="loggingIn">
            {{ loggingIn ? '登录中…' : '管理员登录' }}
          </el-button>
        </el-form>
      </section>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import axios from '@/plugins/axiosInstance';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { assets } from '@/assets';


const router = useRouter();
const form = reactive({ id: '', password: '' });
const fieldErrors = reactive({ id: '', password: '' });
const formError = ref('');
const loggingIn = ref(false);

function validateField(field) {
  if (field === 'id') fieldErrors.id = form.id ? '' : '请输入管理员 ID';
  if (field === 'password') fieldErrors.password = form.password ? '' : '请输入密码';
}

function validateForm() {
  validateField('id');
  validateField('password');
  return !fieldErrors.id && !fieldErrors.password;
}

async function login() {
  formError.value = '';
  if (!validateForm()) {
    formError.value = '请填写管理员 ID 和密码';
    return;
  }

  loggingIn.value = true;
  try {
    const resp = await axios.post('/admin/login', {
      adminId: form.id,
      adminPassword: form.password,
    });
    if (!resp.data.data) {
      formError.value = resp.data.msg || '管理员登录失败';
      ElMessage.error(formError.value);
      return;
    }

    ElMessage.success(resp.data.msg || '登录成功');
    await router.push({ path: '/admin/CoursesAndBlogs' });
  } catch (error) {
    formError.value = '管理员登录服务暂时不可用，请稍后重试';
    ElMessage.error(formError.value);
    console.error(error);
  } finally {
    loggingIn.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: clamp(24px, 5vw, 72px) 20px;
  background: linear-gradient(135deg, #eff6ff 0%, var(--cc4c-bg) 48%, #f8fafc 100%);
}

.auth-layout {
  display: grid;
  width: min(100%, 900px);
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: clamp(32px, 7vw, 92px);
  align-items: center;
}

.auth-brand { color: var(--cc4c-text); }
.auth-brand__logo { width: 112px; height: 112px; margin-bottom: 18px; }
.auth-brand__eyebrow, .auth-card__eyebrow { margin: 0 0 4px; color: var(--cc4c-primary); font-size: 0.875rem; font-weight: 700; }
.auth-brand h1 { margin: 0; font-size: clamp(2rem, 4vw, 3.25rem); line-height: 1.18; }
.auth-brand > p:not(.auth-brand__eyebrow) { max-width: 460px; color: var(--cc4c-muted); font-size: 1.05rem; }
.auth-brand__link { display: inline-block; margin-top: 18px; color: var(--cc4c-primary); font-weight: 600; text-decoration: none; }
.auth-brand__link:hover { text-decoration: underline; }

.auth-card {
  padding: clamp(24px, 4vw, 42px);
  border: 1px solid var(--cc4c-border);
  border-radius: calc(var(--cc4c-radius) + 4px);
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}

.auth-card__heading { margin-bottom: 24px; }
.auth-card__heading h2 { margin: 0; color: var(--cc4c-text); }
.auth-card__heading > p:last-child { margin: 8px 0 0; color: var(--cc4c-muted); }
.auth-form__submit { width: 100%; min-height: 42px; margin-top: 4px; font-weight: 700; }
.auth-form__error { margin: -4px 0 12px; color: var(--el-color-danger); font-size: 0.875rem; }

@media (max-width: 760px) {
  .auth-page { align-items: start; }
  .auth-layout { grid-template-columns: 1fr; gap: 28px; }
  .auth-brand { text-align: center; }
  .auth-brand > p:not(.auth-brand__eyebrow) { margin-inline: auto; }
}

@media (max-width: 420px) {
  .auth-page { padding: 20px 14px; }
  .auth-card { padding: 22px 18px; }
}
</style>
