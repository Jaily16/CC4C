<template>
  <main class="profile-page">
    <div class="profile-page__inner">
      <UserInfo :active-index="1" />

      <section class="profile-details" aria-labelledby="profile-details-title">
        <header class="section-heading">
          <div class="section-heading__title">
            <span class="section-heading__mark" aria-hidden="true">ID</span>
            <div>
              <p>Account overview</p>
              <h2 id="profile-details-title">个人信息</h2>
            </div>
          </div>
          <span class="section-heading__hint">通过上方“编辑资料”维护账户信息</span>
        </header>

        <PageFeedback v-if="loading || errorMessage" :loading="loading" :error="errorMessage" @retry="loadProfile" />

        <template v-else>
          <dl class="profile-details__grid">
            <div class="profile-field profile-field--primary">
              <span class="profile-field__index" aria-hidden="true">01</span>
              <div>
                <dt>用户名</dt>
                <dd>{{ user.name || '未设置' }}</dd>
              </div>
            </div>
            <div class="profile-field">
              <span class="profile-field__index" aria-hidden="true">02</span>
              <div>
                <dt>邮箱地址</dt>
                <dd>{{ user.email || '未设置' }}</dd>
              </div>
            </div>
            <div class="profile-field">
              <span class="profile-field__index" aria-hidden="true">03</span>
              <div>
                <dt>所学专业</dt>
                <dd>{{ majorLabel }}</dd>
              </div>
            </div>
            <div class="profile-field profile-field--language">
              <span class="profile-field__index" aria-hidden="true">04</span>
              <div>
                <dt>订阅语言</dt>
                <dd>{{ languageLabel }}</dd>
              </div>
            </div>
          </dl>

          <aside class="profile-tip">
            <span class="profile-tip__label">推荐依据</span>
            <div>
              <strong>让学习内容更贴近你的方向</strong>
              <p>课程推荐会参考专业背景和订阅语言。资料越准确，首页提供的学习内容就越符合你的当前目标。</p>
            </div>
          </aside>
        </template>
      </section>
    </div>
  </main>
</template>

<script setup>
import { reportClientError } from '@/utils/reportClientError.js';
import { computed, ref } from 'vue';
import { getCurrentUser } from '@/api/profile';
import UserInfo from '@/components/common/UserInfo.vue';
import PageFeedback from '@/components/common/PageFeedback.vue';
import { apiErrorMessage } from '@/utils/apiError';
import store from '@/store';
import { useCurrentUser } from '@/composables/useCurrentUser';

const loading = ref(false);
const errorMessage = ref('');
const { user } = useCurrentUser();
const majors = {
  '-1': '非计算机专业',
  0: '不愿透露',
  1: '计算机专业',
};
const languages = {
  1: 'Java',
  2: 'C++',
  3: 'Python',
  4: 'C',
};
const majorLabel = computed(() => majors[String(user.value.major)] || '未设置');
const languageLabel = computed(() => languages[Number(user.value.language)] || '未设置');

function syncStore(currentUser) {
  store.commit('SET_ID', currentUser.id);
  store.commit('SET_NAME', currentUser.name);
  store.commit('SET_EMAIL', currentUser.email);
  store.commit('SET_MAJOR', currentUser.major);
  store.commit('SET_LANGUAGE', currentUser.language);
  store.commit('SET_AVATAR', currentUser.avatar);
}

async function loadProfile() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const infoResponse = await getCurrentUser();
    if (!infoResponse.data.data?.id) {
      errorMessage.value = infoResponse.data.msg || '个人信息加载失败，请重试。';
      return;
    }
    syncStore(infoResponse.data.data);
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '个人信息加载失败，请检查服务状态后重试。');
    reportClientError(error, 'frontend/src/views/UserInfoView.vue');
  } finally {
    loading.value = false;
  }
}

loadProfile();
</script>

<style scoped>
.profile-page {
  min-height: 100%;
  padding: clamp(16px, 2.5vw, 32px);
  background: var(--cc4c-bg);
}
.profile-page__inner {
  display: grid;
  gap: 22px;
  width: min(100%, 1180px);
  margin: 0 auto;
}
.profile-details {
  position: relative;
  overflow: hidden;
  padding: clamp(22px, 3vw, 36px);
  border: 1px solid var(--cc4c-border);
  border-radius: 20px;
  background: linear-gradient(150deg, #fff 0%, #fbfdff 64%, #f3f7ff 100%);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}
.profile-details::before {
  position: absolute;
  top: 0;
  right: 0;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(96, 165, 250, 0.16) 0%, rgba(96, 165, 250, 0) 70%);
  content: '';
  transform: translate(34%, -44%);
  pointer-events: none;
}
.section-heading {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--cc4c-border);
}
.section-heading__title {
  display: flex;
  gap: 14px;
  align-items: center;
}
.section-heading__mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 900;
  letter-spacing: 0.08em;
  box-shadow: 0 9px 20px rgba(37, 99, 235, 0.22);
}
.section-heading p {
  margin: 0 0 4px;
  color: var(--cc4c-primary);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.section-heading h2 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(1.3rem, 2vw, 1.6rem);
}
.section-heading__hint {
  max-width: 260px;
  color: var(--cc4c-muted);
  font-size: 0.82rem;
  line-height: 1.55;
  text-align: right;
}
.profile-details :deep(.page-feedback) {
  margin-top: 24px;
}
.profile-details__grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin: 24px 0;
}
.profile-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  min-width: 0;
  min-height: 92px;
  padding: 18px 20px;
  border: 1px solid #e1e8f2;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 7px 20px rgba(15, 23, 42, 0.045);
  transition:
    border-color var(--cc4c-transition),
    box-shadow var(--cc4c-transition),
    transform var(--cc4c-transition);
}
.profile-field:hover {
  border-color: #b7cdf3;
  box-shadow: 0 12px 25px rgba(37, 99, 235, 0.09);
  transform: translateY(-2px);
}
.profile-field__index {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #edf3ff;
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 900;
}
.profile-field--primary .profile-field__index,
.profile-field--language .profile-field__index {
  background: var(--cc4c-primary);
  color: #fff;
}
.profile-field dt {
  margin-bottom: 5px;
  color: var(--cc4c-muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.profile-field dd {
  margin: 0;
  color: var(--cc4c-text);
  font-size: 1rem;
  font-weight: 750;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.profile-tip {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 18px;
  align-items: center;
  padding: 19px 22px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 16px;
  background: linear-gradient(120deg, #edf4ff 0%, #f7faff 100%);
  color: var(--cc4c-text);
}
.profile-tip__label {
  padding: 6px 10px;
  border-radius: 999px;
  background: #fff;
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 800;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
}
.profile-tip strong {
  color: var(--cc4c-text);
}
.profile-tip p {
  margin: 5px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.88rem;
  line-height: 1.65;
}

@media (max-width: 640px) {
  .profile-page {
    padding: 12px;
  }
  .profile-details {
    padding: 20px 16px;
    border-radius: 17px;
  }
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .section-heading__hint {
    max-width: none;
    text-align: left;
  }
  .profile-details__grid {
    grid-template-columns: 1fr;
  }
  .profile-tip {
    grid-template-columns: 1fr;
  }
  .profile-tip__label {
    width: fit-content;
  }
}
</style>
