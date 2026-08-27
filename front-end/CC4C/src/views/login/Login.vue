<template>
  <main class="auth-page">
    <section class="auth-layout" aria-labelledby="login-title">
      <div class="auth-brand">
        <el-image class="auth-brand__logo" :src="assets.logoPart1" alt="CC4C" fit="contain" />
        <el-image class="auth-brand__tagline" :src="assets.logoPart3" alt="CC4C 学习交流平台" fit="contain" />
        <h1>让学习与交流持续发生</h1>
        <p>登录后即可浏览课程、参与讨论，并管理自己的学习内容。</p>
      </div>

      <section class="auth-card">
        <div class="auth-card__heading">
          <p class="auth-card__eyebrow">欢迎回来</p>
          <h2 id="login-title">登录 CC4C</h2>
          <p>使用你的邮箱继续。</p>
        </div>

        <el-form class="auth-form" label-position="top" @submit.prevent="login">
          <el-form-item label="邮箱" :error="fieldErrors.email">
            <el-input
              v-model.trim="form.email"
              type="email"
              autocomplete="email"
              placeholder="name@example.com"
              clearable
              @blur="validateLoginField('email')"
            />
          </el-form-item>

          <el-form-item label="密码" :error="fieldErrors.password">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              @blur="validateLoginField('password')"
            />
          </el-form-item>

          <p v-if="formError" class="auth-form__error" role="alert">{{ formError }}</p>

          <el-button class="auth-form__submit" native-type="submit" type="primary" :loading="loggingIn">
            {{ loggingIn ? '登录中…' : '登录' }}
          </el-button>
        </el-form>

        <div class="auth-card__links" aria-label="账户帮助">
          <router-link to="/register">注册新账户</router-link>
          <el-button link type="primary" @click="openFindPassword">找回密码</el-button>
          <router-link to="/adminLogin">管理员登录</router-link>
        </div>
      </section>
    </section>

    <el-dialog v-model="findPwdDialog" class="recovery-dialog" title="找回密码" width="min(92vw, 460px)">
      <p class="recovery-dialog__hint">我们会向邮箱发送验证码，用于确认本次密码重置。</p>
      <el-form label-position="top" @submit.prevent="findPassword">
        <el-form-item label="邮箱" :error="recoveryErrors.email">
          <el-input
            v-model.trim="findForm.email"
            type="email"
            autocomplete="email"
            placeholder="name@example.com"
            clearable
            @blur="validateRecoveryField('email')"
          />
        </el-form-item>
        <el-form-item label="新密码" :error="recoveryErrors.password">
          <el-input
            v-model="findForm.password"
            type="password"
            autocomplete="new-password"
            placeholder="8–64 个字符"
            show-password
            @blur="validateRecoveryField('password')"
          />
        </el-form-item>
        <el-form-item label="邮箱验证码" :error="recoveryErrors.code">
          <div class="verification-row">
            <el-input v-model.trim="iCode" inputmode="numeric" autocomplete="one-time-code" placeholder="请输入验证码" />
            <el-button
              type="primary"
              plain
              :loading="sendingRecoveryCode"
              :disabled="recoveryCountdown > 0"
              @click="getVCode"
            >
              {{ sendingRecoveryCode ? '发送中…' : (recoveryCountdown > 0 ? `${recoveryCountdown} 秒后重试` : '获取验证码') }}
            </el-button>
          </div>
        </el-form-item>
        <p v-if="recoveryError" class="auth-form__error" role="alert">{{ recoveryError }}</p>
        <div class="dialog-actions">
          <el-button @click="findPwdDialog = false">取消</el-button>
          <el-button native-type="submit" type="primary" :loading="findingPassword">
            {{ findingPassword ? '提交中…' : '重置密码' }}
          </el-button>
        </div>
      </el-form>
    </el-dialog>
  </main>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import axios from '@/plugins/axiosInstance';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import store from '@/store';
import { assets } from '@/assets';
import { apiErrorMessage } from '@/utils/apiError';


const router = useRouter();
const route = useRoute();
const form = reactive({ email: '', password: '' });
const fieldErrors = reactive({ email: '', password: '' });
const formError = ref('');
const loggingIn = ref(false);

const findPwdDialog = ref(false);
const findForm = reactive({ email: '', password: '' });
const recoveryErrors = reactive({ email: '', password: '', code: '' });
const recoveryError = ref('');
const iCode = ref('');
const sendingRecoveryCode = ref(false);
const findingPassword = ref(false);
const recoveryCountdown = ref(0);
let recoveryTimer = null;

function isEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function validateLoginField(field) {
  if (field === 'email') {
    fieldErrors.email = !form.email ? '请输入邮箱' : (!isEmail(form.email) ? '请输入正确的邮箱地址' : '');
  }
  if (field === 'password') {
    fieldErrors.password = form.password ? '' : '请输入密码';
  }
}

function validateLoginForm() {
  validateLoginField('email');
  validateLoginField('password');
  return !fieldErrors.email && !fieldErrors.password;
}

async function login() {
  formError.value = '';
  if (!validateLoginForm()) {
    formError.value = '请检查邮箱和密码后重试';
    return;
  }

  loggingIn.value = true;
  try {
    const loginResp = await axios.post('/users/login', {
      email: form.email,
      password: form.password,
    });

    if (loginResp.data.data !== true) {
      formError.value = loginResp.data.msg || '登录失败';
      ElMessage.error(formError.value);
      return;
    }

    await store.dispatch('hydrateSession', { force: true });
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home';
    await router.push(redirect);
  } catch (error) {
    formError.value = apiErrorMessage(error, '登录服务暂时不可用，请稍后重试');
    ElMessage.error(formError.value);
    console.error(error);
  } finally {
    loggingIn.value = false;
  }
}

function openFindPassword() {
  recoveryError.value = '';
  findPwdDialog.value = true;
}

function validateRecoveryField(field) {
  if (field === 'email') {
    recoveryErrors.email = !findForm.email ? '请输入邮箱' : (!isEmail(findForm.email) ? '请输入正确的邮箱地址' : '');
  }
  if (field === 'password') {
    const bytes = new TextEncoder().encode(findForm.password).length;
    recoveryErrors.password = !findForm.password
      ? '请输入新密码'
      : (findForm.password.length < 8 || findForm.password.length > 64 || bytes > 72
        ? '密码需为 8–64 个字符且编码后不超过 72 字节'
        : '');
  }
}

async function getVCode() {
  recoveryError.value = '';
  validateRecoveryField('email');
  if (recoveryErrors.email) return;

  sendingRecoveryCode.value = true;
  try {
    const resp = await axios.post('/users/email', {
      email: findForm.email,
      purpose: 'PASSWORD_RESET',
    });
    if (!resp.data.data) {
      recoveryError.value = resp.data.msg || '未能成功获取邮箱验证码';
      ElMessage.error(recoveryError.value);
      return;
    }
    startRecoveryCountdown();
    ElMessage.success('验证码已发送');
  } catch (error) {
    recoveryError.value = apiErrorMessage(error, '验证码发送失败，请稍后重试');
    ElMessage.error(recoveryError.value);
    console.error(error);
  } finally {
    sendingRecoveryCode.value = false;
  }
}

async function findPassword() {
  recoveryError.value = '';
  validateRecoveryField('email');
  validateRecoveryField('password');
  recoveryErrors.code = /^\d{6}$/.test(iCode.value) ? '' : '请输入六位邮箱验证码';

  if (recoveryErrors.email || recoveryErrors.password || recoveryErrors.code) return;

  findingPassword.value = true;
  try {
    const resp = await axios.put('/users/password/forget', {
      email: findForm.email,
      verificationCode: iCode.value,
      newPassword: findForm.password,
    });
    if (resp.data.data !== true) {
      recoveryError.value = resp.data.msg || '密码修改失败';
      ElMessage.error(recoveryError.value);
      return;
    }

    ElMessage.success('密码修改成功');
    findForm.password = '';
    iCode.value = '';
    findPwdDialog.value = false;
  } catch (error) {
    recoveryError.value = apiErrorMessage(error, '密码修改失败，请稍后重试');
    ElMessage.error(recoveryError.value);
    console.error(error);
  } finally {
    findingPassword.value = false;
  }
}

function startRecoveryCountdown() {
  recoveryCountdown.value = 60;
  if (recoveryTimer) window.clearInterval(recoveryTimer);
  recoveryTimer = window.setInterval(() => {
    recoveryCountdown.value -= 1;
    if (recoveryCountdown.value <= 0) {
      window.clearInterval(recoveryTimer);
      recoveryTimer = null;
    }
  }, 1000);
}

onBeforeUnmount(() => {
  if (recoveryTimer) window.clearInterval(recoveryTimer);
});
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
  grid-template-columns: minmax(0, 1fr) minmax(320px, 430px);
  gap: clamp(32px, 7vw, 92px);
  align-items: center;
}

.auth-brand { color: var(--cc4c-text); }
.auth-brand__logo { width: 112px; height: 112px; }
.auth-brand__tagline { width: min(100%, 360px); height: 54px; margin: 12px 0 20px; }
.auth-brand h1 { margin: 0; font-size: clamp(2rem, 4vw, 3.25rem); line-height: 1.18; }
.auth-brand p { max-width: 460px; color: var(--cc4c-muted); font-size: 1.05rem; }

.auth-card {
  padding: clamp(24px, 4vw, 42px);
  border: 1px solid var(--cc4c-border);
  border-radius: calc(var(--cc4c-radius) + 4px);
  background: var(--cc4c-surface);
  box-shadow: var(--cc4c-shadow);
}

.auth-card__heading { margin-bottom: 24px; }
.auth-card__eyebrow { margin: 0 0 4px; color: var(--cc4c-primary); font-size: 0.875rem; font-weight: 700; }
.auth-card__heading h2 { margin: 0; color: var(--cc4c-text); }
.auth-card__heading > p:last-child { margin: 8px 0 0; color: var(--cc4c-muted); }
.auth-form__submit { width: 100%; min-height: 42px; margin-top: 4px; font-weight: 700; }
.auth-form__error { margin: -4px 0 12px; color: var(--el-color-danger); font-size: 0.875rem; }
.auth-card__links { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 22px; font-size: 0.9rem; }
.auth-card__links a { color: var(--cc4c-primary); text-decoration: none; }
.auth-card__links a:hover { text-decoration: underline; }

.recovery-dialog__hint { margin: 0 0 18px; color: var(--cc4c-muted); }
.verification-row { display: flex; width: 100%; gap: 10px; }
.verification-row .el-input { min-width: 0; }
.verification-row .el-button { flex: 0 0 auto; min-height: 40px; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; }

@media (max-width: 760px) {
  .auth-page { align-items: start; }
  .auth-layout { grid-template-columns: 1fr; gap: 28px; }
  .auth-brand { text-align: center; }
  .auth-brand p { margin-inline: auto; }
}

@media (max-width: 420px) {
  .auth-page { padding: 20px 14px; }
  .auth-card { padding: 22px 18px; }
  .verification-row { flex-wrap: wrap; }
  .verification-row .el-button { width: 100%; }
}
</style>
