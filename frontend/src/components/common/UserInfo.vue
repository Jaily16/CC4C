<template>
  <section class="account-hub" aria-labelledby="account-user-name">
    <div class="profile-summary">
      <div class="profile-summary__identity">
        <el-avatar v-if="profileAvatar" :size="84" :src="profileAvatar" @error="handleAvatarError" />
        <el-avatar v-else :size="84" class="profile-summary__fallback">{{ userInitial }}</el-avatar>
        <div class="profile-summary__copy">
          <p>CC4C 个人学习空间</p>
          <h1 id="account-user-name">{{ displayName }}</h1>
          <span>{{ currentUser.email || '尚未绑定邮箱' }}</span>
        </div>
      </div>

      <div class="profile-summary__facts" aria-label="学习偏好">
        <span><b>专业</b>{{ majorLabel }}</span>
        <span><b>订阅语言</b>{{ languageLabel }}</span>
      </div>

      <div class="profile-summary__actions">
        <el-button :icon="EditPen" @click="openEditDialog">编辑资料</el-button>
        <el-button :icon="Lock" @click="openPasswordDialog">修改密码</el-button>
      </div>
    </div>

    <nav class="account-nav" aria-label="个人中心导航">
      <RouterLink
        v-for="item in navItems"
        :key="item.key"
        :to="item.path"
        class="account-nav__item"
        :class="{ 'account-nav__item--active': activeKey === item.key }"
        :aria-current="activeKey === item.key ? 'page' : undefined"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <ProfileEditDialog
      v-model="editDialogOpen"
      :form="infoForm"
      :errors="editErrors"
      :major-list="majorList"
      :language-list="languageList"
      :avatar-preview="editAvatarPreview"
      :avatar-uploading="avatarUploading"
      :saving="profileSaving"
      @upload-avatar="uploadAvatar"
      @update-field="updateInfoFormField"
      @clear-error="
        (field) => {
          editErrors[field] = '';
        }
      "
      @save="saveProfile"
      @closed="resetEditDialog"
    />

    <PasswordChangeDialog
      v-model="passwordDialogOpen"
      :form="passwordForm"
      :errors="passwordErrors"
      :saving="passwordSaving"
      @update-field="updatePasswordFormField"
      @clear-error="
        (field) => {
          passwordErrors[field] = '';
        }
      "
      @save="changePassword"
      @closed="resetPasswordDialog"
    />
  </section>
</template>

<script setup>
import { reportClientError } from '@/utils/reportClientError.js';
import { computed, reactive, ref, watch } from 'vue';
import { resetCsrfToken } from '@/api/client';
import {
  changePassword as changePasswordRequest,
  updateProfile,
  uploadAvatar as uploadAvatarRequest,
  getCurrentUser,
} from '@/api/profile';
import { ElMessage } from 'element-plus';
import { EditPen, Lock, Notebook, StarFilled, UserFilled } from '@element-plus/icons-vue';
import store from '@/store';
import { apiErrorMessage } from '@/utils/apiError';
import { useRouter } from 'vue-router';
import { useCurrentUser } from '@/composables/useCurrentUser';
import ProfileEditDialog from '@/components/common/ProfileEditDialog.vue';
import PasswordChangeDialog from '@/components/common/PasswordChangeDialog.vue';

const props = defineProps({
  activeIndex: { type: [String, Number], default: 1 },
});
const emit = defineEmits(['user-updated']);
const router = useRouter();

const majorList = [
  { label: '非计算机专业', value: -1 },
  { label: '不愿透露', value: 0 },
  { label: '计算机专业', value: 1 },
];
const languageList = [
  { label: 'Java', value: 1 },
  { label: 'C++', value: 2 },
  { label: 'Python', value: 3 },
  { label: 'C', value: 4 },
];
const navItems = [
  { key: '1', label: '个人信息', path: '/userinfo', icon: UserFilled },
  { key: '2', label: '我的收藏', path: '/favorite', icon: StarFilled },
  { key: '4', label: '我的文章', path: '/blogmanage', icon: Notebook },
];

const { user: currentUser } = useCurrentUser();
const activeKey = computed(() => String(props.activeIndex));
const displayName = computed(() => currentUser.value.name || 'CC4C 用户');
const userInitial = computed(() => displayName.value.trim().slice(0, 1).toUpperCase());
const majorLabel = computed(
  () => majorList.find((item) => item.value === Number(currentUser.value.major))?.label || '未设置',
);
const languageLabel = computed(
  () => languageList.find((item) => item.value === Number(currentUser.value.language))?.label || '未设置',
);
const avatarLoadFailed = ref(false);
const profileAvatar = computed(() => (avatarLoadFailed.value ? '' : currentUser.value.avatar || ''));

const editDialogOpen = ref(false);
const passwordDialogOpen = ref(false);
const avatarUploading = ref(false);
const profileSaving = ref(false);
const passwordSaving = ref(false);
const uploadedAvatar = ref('');
const infoForm = reactive({ name: '', major: 0, language: 1, avatar: '' });
const passwordForm = reactive({ password: '', newPassword: '' });
const editErrors = reactive({ name: '', avatar: '' });
const passwordErrors = reactive({ password: '', newPassword: '' });
const editAvatarPreview = computed(() => uploadedAvatar.value || infoForm.avatar || '');

function updateInfoFormField(field, value) {
  if (Object.prototype.hasOwnProperty.call(infoForm, field)) infoForm[field] = value;
}

function updatePasswordFormField(field, value) {
  if (Object.prototype.hasOwnProperty.call(passwordForm, field)) passwordForm[field] = value;
}

watch(
  () => currentUser.value.avatar,
  () => {
    avatarLoadFailed.value = false;
  },
);

function handleAvatarError() {
  avatarLoadFailed.value = true;
  return true;
}

function fillProfileForm() {
  infoForm.name = currentUser.value.name || '';
  infoForm.major = Number(currentUser.value.major);
  infoForm.language = Number(currentUser.value.language);
  infoForm.avatar = currentUser.value.avatar || '';
}

function openEditDialog() {
  fillProfileForm();
  uploadedAvatar.value = '';
  editErrors.name = '';
  editErrors.avatar = '';
  editDialogOpen.value = true;
}

function openPasswordDialog() {
  passwordErrors.password = '';
  passwordErrors.newPassword = '';
  passwordDialogOpen.value = true;
}

function resetEditDialog() {
  if (profileSaving.value) return;
  uploadedAvatar.value = '';
  editErrors.name = '';
  editErrors.avatar = '';
  fillProfileForm();
}

function clearPasswords() {
  passwordForm.password = '';
  passwordForm.newPassword = '';
}

function resetPasswordDialog() {
  if (passwordSaving.value) return;
  clearPasswords();
  passwordErrors.password = '';
  passwordErrors.newPassword = '';
}

async function syncCurrentUser() {
  const response = await getCurrentUser();
  const user = response.data.data;
  if (!user || !user.id) throw new Error(response.data.msg || '用户信息刷新失败');
  store.commit('SET_ID', user.id);
  store.commit('SET_NAME', user.name);
  store.commit('SET_EMAIL', user.email);
  store.commit('SET_MAJOR', user.major);
  store.commit('SET_LANGUAGE', user.language);
  store.commit('SET_AVATAR', user.avatar);
  emit('user-updated', user);
  return user;
}

async function refreshCurrentUser() {
  try {
    await syncCurrentUser();
    return true;
  } catch (error) {
    reportClientError(error, 'frontend/src/components/common/UserInfo.vue');
    return false;
  }
}

async function uploadAvatar({ file }) {
  editErrors.avatar = '';
  const isSupported = file.type === 'image/jpeg' || file.type === 'image/png';
  const isWithinLimit = file.size / 1024 / 1024 < 2;
  if (!isSupported) {
    editErrors.avatar = '请选择 JPG 或 PNG 格式的图片。';
    return;
  }
  if (!isWithinLimit) {
    editErrors.avatar = '图片大小不能超过 2MB。';
    return;
  }

  avatarUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file);
    const response = await uploadAvatarRequest(formData);
    const requestPath = response.data.data?.requestPath;
    if (!requestPath) {
      editErrors.avatar = response.data.msg || '头像上传失败，请重新选择。';
      return;
    }
    uploadedAvatar.value = requestPath;
    ElMessage.success('头像已上传，请保存资料完成更新');
  } catch (error) {
    editErrors.avatar = apiErrorMessage(error, '头像上传失败，请稍后重试。');
    reportClientError(error, 'frontend/src/components/common/UserInfo.vue');
  } finally {
    avatarUploading.value = false;
  }
}

async function saveProfile() {
  editErrors.name = infoForm.name.trim() ? '' : '请输入用户名。';
  if (editErrors.name || profileSaving.value || avatarUploading.value) return;

  profileSaving.value = true;
  try {
    const response = await updateProfile({
      name: infoForm.name.trim(),
      major: infoForm.major,
      language: infoForm.language,
      avatar: uploadedAvatar.value || infoForm.avatar,
    });
    if (response.data.data !== true) {
      ElMessage.error(response.data.msg || '用户信息修改失败');
      return;
    }
    const refreshed = await refreshCurrentUser();
    if (!refreshed) {
      store.commit('SET_NAME', infoForm.name.trim());
      store.commit('SET_MAJOR', infoForm.major);
      store.commit('SET_LANGUAGE', infoForm.language);
      store.commit('SET_AVATAR', uploadedAvatar.value || infoForm.avatar);
    }
    editDialogOpen.value = false;
    if (refreshed) ElMessage.success('个人资料已更新');
    else ElMessage.warning('资料已保存，但最新信息自动刷新失败');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '用户信息修改失败，请稍后重试'));
    reportClientError(error, 'frontend/src/components/common/UserInfo.vue');
  } finally {
    profileSaving.value = false;
  }
}

async function changePassword() {
  passwordErrors.password = passwordForm.password ? '' : '请输入原密码。';
  const bytes = new TextEncoder().encode(passwordForm.newPassword).length;
  passwordErrors.newPassword = !passwordForm.newPassword
    ? '请输入新密码。'
    : passwordForm.newPassword.length < 8 || passwordForm.newPassword.length > 64 || bytes > 72
      ? '新密码需为 8–64 个字符且编码后不超过 72 字节。'
      : '';
  if (passwordErrors.password || passwordErrors.newPassword || passwordSaving.value) return;

  passwordSaving.value = true;
  try {
    const response = await changePasswordRequest({
      password: passwordForm.password,
      newPassword: passwordForm.newPassword,
    });
    if (response.data.data !== true) {
      ElMessage.error(response.data.msg || '密码修改失败');
      return;
    }
    clearPasswords();
    passwordDialogOpen.value = false;
    resetCsrfToken();
    store.commit('RESET_STATE');
    ElMessage.success('密码修改成功，请重新登录');
    await router.replace('/login');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '密码修改失败，请稍后重试'));
    reportClientError(error, 'frontend/src/components/common/UserInfo.vue');
  } finally {
    passwordSaving.value = false;
  }
}
</script>

<style scoped>
.account-hub {
  display: grid;
  gap: 18px;
  width: 100%;
}
.profile-summary {
  display: grid;
  grid-template-columns: minmax(260px, 1.5fr) minmax(240px, 1fr) auto;
  gap: 28px;
  align-items: center;
  padding: 26px;
  border: 1px solid var(--cc4c-border);
  border-radius: 18px;
  background: linear-gradient(135deg, #fff 0%, #f8fbff 100%);
  box-shadow: var(--cc4c-shadow);
}
.profile-summary__identity {
  display: flex;
  gap: 18px;
  align-items: center;
  min-width: 0;
}
.profile-summary__identity :deep(.el-avatar) {
  flex: 0 0 auto;
  border: 4px solid #fff;
  background: #dbeafe;
  color: var(--cc4c-primary);
  font-size: 1.7rem;
  font-weight: 800;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.16);
}
.profile-summary__copy {
  min-width: 0;
}
.profile-summary__copy p {
  margin: 0 0 4px;
  color: var(--cc4c-primary);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.profile-summary__copy h1 {
  margin: 0;
  color: var(--cc4c-text);
  font-size: clamp(1.5rem, 2vw, 2rem);
  overflow-wrap: anywhere;
}
.profile-summary__copy span {
  display: block;
  margin-top: 5px;
  color: var(--cc4c-muted);
  overflow-wrap: anywhere;
}
.profile-summary__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.profile-summary__facts span {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--cc4c-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--cc4c-text);
  overflow-wrap: anywhere;
}
.profile-summary__facts b {
  display: block;
  margin-bottom: 3px;
  color: var(--cc4c-muted);
  font-size: 0.72rem;
  font-weight: 700;
}
.profile-summary__actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.profile-summary__actions .el-button {
  width: 100%;
  margin: 0;
}
.account-nav {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 7px;
  border: 1px solid var(--cc4c-border);
  border-radius: 15px;
  background: var(--cc4c-surface);
}
.account-nav__item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 46px;
  padding: 10px 14px;
  border-radius: 10px;
  color: var(--cc4c-muted);
  font-weight: 700;
  text-decoration: none;
  transition:
    color var(--cc4c-transition),
    background var(--cc4c-transition),
    transform var(--cc4c-transition);
}
.account-nav__item:hover {
  color: var(--cc4c-primary);
  background: #f3f7ff;
  transform: translateY(-1px);
}
.account-nav__item--active {
  color: var(--cc4c-primary);
  background: #eaf1ff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.12);
}
@media (max-width: 1024px) {
  .profile-summary {
    grid-template-columns: 1fr auto;
  }
  .profile-summary__facts {
    grid-column: 1 / -1;
    grid-row: 2;
  }
}

@media (max-width: 640px) {
  .profile-summary {
    grid-template-columns: 1fr;
    gap: 20px;
    padding: 20px;
  }
  .profile-summary__identity {
    align-items: flex-start;
  }
  .profile-summary__identity :deep(.el-avatar) {
    width: 68px !important;
    height: 68px !important;
  }
  .profile-summary__facts {
    grid-column: auto;
    grid-row: auto;
  }
  .profile-summary__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .account-nav {
    grid-template-columns: 1fr;
  }
  .account-nav__item {
    justify-content: flex-start;
  }
}

@media (max-width: 390px) {
  .profile-summary__identity {
    flex-direction: column;
  }
  .profile-summary__facts,
  .profile-summary__actions {
    grid-template-columns: 1fr;
  }
}
</style>
