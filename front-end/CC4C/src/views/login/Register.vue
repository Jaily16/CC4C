<template>
  <main class="auth-page">
    <section class="auth-layout" aria-labelledby="register-title">
      <div class="auth-brand">
        <el-image class="auth-brand__logo" :src="assets.logoPart1" alt="CC4C" fit="contain" />
        <h1>从一门课程开始，持续构建你的技术世界。</h1>
        <p>创建账户后，可以收藏学习资源、发布博客并参与社区讨论。</p>
        <div class="auth-brand__links">
          <router-link to="/login">已有账户？去登录</router-link>
          <router-link to="/adminLogin">管理员登录</router-link>
        </div>
      </div>

      <section class="auth-card">
        <div class="auth-card__heading">
          <p class="auth-card__eyebrow">创建账户</p>
          <h2 id="register-title">注册 CC4C</h2>
          <p>带 <span aria-label="必填项">*</span> 的字段为必填项。</p>
        </div>

        <el-form class="auth-form" label-position="top" @submit.prevent="register">
          <el-form-item label="用户名 *" :error="fieldErrors.userName">
            <el-input
              v-model.trim="user.userName"
              autocomplete="username"
              placeholder="请输入用户名"
              clearable
              @blur="validateField('userName')"
            />
          </el-form-item>

          <el-form-item label="邮箱 *" :error="fieldErrors.email">
            <el-input
              v-model.trim="user.email"
              type="email"
              autocomplete="email"
              placeholder="name@example.com"
              clearable
              @blur="validateField('email')"
            />
          </el-form-item>

          <el-form-item label="密码 *" :error="fieldErrors.password">
            <el-input
              v-model="user.password"
              type="password"
              autocomplete="new-password"
              placeholder="至少 4 个字符"
              show-password
              @blur="validateField('password')"
            />
          </el-form-item>

          <el-form-item label="邮箱验证码 *" :error="fieldErrors.code">
            <div class="verification-row">
              <el-input v-model.trim="iCode" inputmode="numeric" autocomplete="one-time-code" placeholder="请输入验证码" @blur="validateField('code')" />
              <el-button type="primary" plain :loading="sendingCode" @click="getVCode">
                {{ sendingCode ? '发送中…' : '获取验证码' }}
              </el-button>
            </div>
            <p class="field-hint">验证码会发送至当前填写的邮箱；修改邮箱后需要重新获取。</p>
          </el-form-item>

          <div class="auth-form__choices">
            <el-form-item label="专业">
              <el-select v-model="user.major" aria-label="专业">
                <el-option v-for="item in majorList" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="订阅语言">
              <el-select v-model="user.lang" aria-label="订阅语言">
                <el-option v-for="item in langList" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </div>

          <p v-if="formError" class="auth-form__error" role="alert">{{ formError }}</p>
          <el-button class="auth-form__submit" native-type="submit" type="primary" :loading="registering">
            {{ registering ? '注册中…' : '注册' }}
          </el-button>
        </el-form>
      </section>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import axios from '@/plugins/axiosInstance';
import { assets } from '@/assets';
import { apiErrorMessage } from '@/utils/apiError';

const router = useRouter();
const majorList = [
  { label: '非计算机专业', value: -1 },
  { label: '不愿透露', value: 0 },
  { label: '计算机专业', value: 1 },
];
const langList = [
  { label: 'java', value: 1 },
  { label: 'c++', value: 2 },
  { label: 'python', value: 3 },
  { label: 'c', value: 4 },
];

const user = reactive({ userName: '', password: '', email: '', major: 0, lang: 1 });
const fieldErrors = reactive({ userName: '', email: '', password: '', code: '' });
const formError = ref('');
const iCode = ref('');
const sendingCode = ref(false);
const registering = ref(false);
let vCode = '';
let rEmail = '';

function isEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function validateField(field) {
  if (field === 'userName') {
    fieldErrors.userName = user.userName ? '' : '请输入用户名';
  }
  if (field === 'email') {
    fieldErrors.email = !user.email ? '请输入邮箱' : (!isEmail(user.email) ? '请输入正确的邮箱地址' : '');
  }
  if (field === 'password') {
    fieldErrors.password = !user.password ? '请输入密码' : (user.password.length < 4 ? '密码至少需要 4 个字符' : '');
  }
  if (field === 'code') {
    fieldErrors.code = iCode.value ? '' : '请输入邮箱验证码';
  }
}

function validateRegisterForm() {
  ['userName', 'email', 'password', 'code'].forEach(validateField);
  return Object.values(fieldErrors).every((error) => !error);
}

async function getVCode() {
  formError.value = '';
  validateField('email');
  if (fieldErrors.email) return;

  sendingCode.value = true;
  try {
    const resp = await axios.post(`/users/email/${encodeURIComponent(user.email)}`);
    if (!resp.data.data) {
      formError.value = resp.data.msg || '未能成功获取邮箱验证码';
      ElMessage.error(formError.value);
      return;
    }
    rEmail = user.email;
    vCode = resp.data.data;
    ElMessage.success('验证码已发送');
  } catch (error) {
    formError.value = apiErrorMessage(error, '验证码发送失败，请稍后重试');
    ElMessage.error(formError.value);
    console.error(error);
  } finally {
    sendingCode.value = false;
  }
}

async function register() {
  formError.value = '';
  if (!validateRegisterForm()) {
    formError.value = '请完善必填信息后再注册';
    return;
  }
  if (iCode.value !== vCode || !vCode) {
    fieldErrors.code = '邮箱验证失败，请重新验证';
    return;
  }
  if (user.email !== rEmail) {
    fieldErrors.code = '邮箱已修改，请重新获取验证码';
    return;
  }

  registering.value = true;
  try {
    const response = await axios.post('/users/register', {
      name: user.userName,
      email: user.email,
      password: user.password,
      major: user.major,
      language: user.lang,
    });
    if (response.data.data !== true) {
      formError.value = response.data.msg || '注册失败';
      ElMessage.error(formError.value);
      return;
    }

    ElMessage.success(response.data.msg || '注册成功');
    await router.push({ path: '/login' });
  } catch (error) {
    formError.value = apiErrorMessage(error, '注册失败，请稍后重试');
    ElMessage.error(formError.value);
    console.error(error);
  } finally {
    registering.value = false;
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
  width: min(100%, 980px);
  grid-template-columns: minmax(0, 1fr) minmax(320px, 470px);
  gap: clamp(32px, 7vw, 92px);
  align-items: center;
}

.auth-brand { color: var(--cc4c-text); }
.auth-brand__logo { width: 112px; height: 112px; margin-bottom: 18px; }
.auth-brand h1 { margin: 0; font-size: clamp(2rem, 4vw, 3.25rem); line-height: 1.18; }
.auth-brand > p { max-width: 460px; color: var(--cc4c-muted); font-size: 1.05rem; }
.auth-brand__links { display: flex; flex-wrap: wrap; gap: 12px 18px; margin-top: 24px; }
.auth-brand__links a { color: var(--cc4c-primary); font-weight: 600; text-decoration: none; }
.auth-brand__links a:hover { text-decoration: underline; }

.auth-card {
  padding: clamp(24px, 4vw, 42px);
  border: 1px solid var(--cc4c-border);
  border-radius: calc(var(--cc4c-radius) + 4px);
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}

.auth-card__heading { margin-bottom: 22px; }
.auth-card__eyebrow { margin: 0 0 4px; color: var(--cc4c-primary); font-size: 0.875rem; font-weight: 700; }
.auth-card__heading h2 { margin: 0; color: var(--cc4c-text); }
.auth-card__heading > p:last-child { margin: 8px 0 0; color: var(--cc4c-muted); }
.auth-card__heading span { color: var(--el-color-danger); }
.auth-form__choices { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.auth-form__choices :deep(.el-select) { width: 100%; }
.verification-row { display: flex; width: 100%; gap: 10px; }
.verification-row .el-input { min-width: 0; }
.verification-row .el-button { flex: 0 0 auto; min-height: 40px; }
.field-hint { width: 100%; margin: 7px 0 0; color: var(--cc4c-muted); font-size: 0.8125rem; line-height: 1.45; }
.auth-form__submit { width: 100%; min-height: 42px; margin-top: 4px; font-weight: 700; }
.auth-form__error { margin: -4px 0 12px; color: var(--el-color-danger); font-size: 0.875rem; }

@media (max-width: 760px) {
  .auth-page { align-items: start; }
  .auth-layout { grid-template-columns: 1fr; gap: 28px; }
  .auth-brand { text-align: center; }
  .auth-brand > p { margin-inline: auto; }
  .auth-brand__links { justify-content: center; }
}

@media (max-width: 420px) {
  .auth-page { padding: 20px 14px; }
  .auth-card { padding: 22px 18px; }
  .auth-form__choices { grid-template-columns: 1fr; gap: 0; }
  .verification-row { flex-wrap: wrap; }
  .verification-row .el-button { width: 100%; }
}
</style>
