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

    <el-dialog
      v-model="editDialogOpen"
      title="编辑个人资料"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="!profileSaving"
      @closed="resetEditDialog"
    >
      <el-form label-position="top" class="account-form" @submit.prevent>
        <el-form-item label="头像" :error="editErrors.avatar">
          <el-upload
            class="avatar-uploader"
            action="#"
            accept="image/jpeg,image/png"
            :show-file-list="false"
            :disabled="avatarUploading || profileSaving"
            :http-request="uploadAvatar"
          >
            <div class="avatar-preview" :class="{ 'avatar-preview--loading': avatarUploading }">
              <img v-if="editAvatarPreview" :src="editAvatarPreview" alt="头像预览" />
              <el-icon v-else-if="avatarUploading" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Plus /></el-icon>
              <span>{{ avatarUploading ? '上传中…' : '更换头像' }}</span>
            </div>
          </el-upload>
          <p class="form-help">支持 JPG、PNG，文件大小不超过 2MB；保存资料后头像才会正式更新。</p>
        </el-form-item>

        <el-form-item label="用户名" required :error="editErrors.name">
          <el-input v-model="infoForm.name" maxlength="30" show-word-limit autocomplete="nickname" @input="editErrors.name = ''" />
          <p class="form-help">用于个人空间和社区内容中的身份展示。</p>
        </el-form-item>

        <div class="account-form__grid">
          <el-form-item label="所学专业">
            <el-select v-model="infoForm.major" placeholder="请选择专业">
              <el-option v-for="item in majorList" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="订阅语言">
            <el-select v-model="infoForm.language" placeholder="请选择语言">
              <el-option v-for="item in languageList" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-actions">
          <el-button :disabled="profileSaving" @click="editDialogOpen = false">取消</el-button>
          <el-button type="primary" :loading="profileSaving" :disabled="avatarUploading" @click="saveProfile">保存资料</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogOpen"
      title="修改密码"
      width="min(500px, calc(100vw - 32px))"
      :close-on-click-modal="!passwordSaving"
      @closed="resetPasswordDialog"
    >
      <el-form label-position="top" class="account-form" @submit.prevent>
        <el-form-item label="原密码" required :error="passwordErrors.password">
          <el-input
            v-model="passwordForm.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入当前密码"
            @input="passwordErrors.password = ''"
          />
          <p class="form-help">用于确认本次操作由你本人发起。</p>
        </el-form-item>
        <el-form-item label="新密码" required :error="passwordErrors.newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请输入新密码"
            @input="passwordErrors.newPassword = ''"
            @keyup.enter="changePassword"
          />
          <p class="form-help">请使用不易被猜到且不同于其他网站的密码。</p>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-actions">
          <el-button :disabled="passwordSaving" @click="passwordDialogOpen = false">取消</el-button>
          <el-button type="primary" :loading="passwordSaving" @click="changePassword">确认修改</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import axios from '@/plugins/axiosInstance';
import { ElMessage } from 'element-plus';
import { EditPen, Loading, Lock, Notebook, Plus, StarFilled, UserFilled } from '@element-plus/icons-vue';
import store from '@/store';
import { apiErrorMessage } from '@/utils/apiError';


const props = defineProps({
  activeIndex: { type: [String, Number], default: 1 },
});
const emit = defineEmits(['user-updated']);

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

const currentUser = computed(() => store.state.user);
const activeKey = computed(() => String(props.activeIndex));
const displayName = computed(() => currentUser.value.name || 'CC4C 用户');
const userInitial = computed(() => displayName.value.trim().slice(0, 1).toUpperCase());
const majorLabel = computed(() => majorList.find((item) => item.value === Number(currentUser.value.major))?.label || '未设置');
const languageLabel = computed(() => languageList.find((item) => item.value === Number(currentUser.value.language))?.label || '未设置');
const avatarLoadFailed = ref(false);
const profileAvatar = computed(() => (avatarLoadFailed.value ? '' : currentUser.value.avatar || ''));

const editDialogOpen = ref(false);
const passwordDialogOpen = ref(false);
const avatarUploading = ref(false);
const profileSaving = ref(false);
const passwordSaving = ref(false);
const uploadedAvatar = ref('');
const infoForm = reactive({ id: '', name: '', major: 0, language: 1, avatar: '' });
const passwordForm = reactive({ id: '', password: '', newPassword: '' });
const editErrors = reactive({ name: '', avatar: '' });
const passwordErrors = reactive({ password: '', newPassword: '' });
const editAvatarPreview = computed(() => uploadedAvatar.value || infoForm.avatar || '');

watch(() => currentUser.value.avatar, () => {
  avatarLoadFailed.value = false;
});

function handleAvatarError() {
  avatarLoadFailed.value = true;
  return true;
}

function fillProfileForm() {
  infoForm.id = currentUser.value.id;
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
  passwordForm.id = currentUser.value.id;
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
  const response = await axios.get('/users/info');
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
    console.error(error);
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
    const response = await axios.post('/users/uploadAvatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const requestPath = response.data.data?.requestPath;
    if (!requestPath) {
      editErrors.avatar = response.data.msg || '头像上传失败，请重新选择。';
      return;
    }
    uploadedAvatar.value = requestPath;
    ElMessage.success('头像已上传，请保存资料完成更新');
  } catch (error) {
    editErrors.avatar = apiErrorMessage(error, '头像上传失败，请稍后重试。');
    console.error(error);
  } finally {
    avatarUploading.value = false;
  }
}

async function saveProfile() {
  editErrors.name = infoForm.name.trim() ? '' : '请输入用户名。';
  if (editErrors.name || profileSaving.value || avatarUploading.value) return;

  profileSaving.value = true;
  try {
    const response = await axios.put('/users/update', {
      id: infoForm.id,
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
    console.error(error);
  } finally {
    profileSaving.value = false;
  }
}

async function changePassword() {
  passwordErrors.password = passwordForm.password ? '' : '请输入原密码。';
  passwordErrors.newPassword = passwordForm.newPassword ? '' : '请输入新密码。';
  if (passwordErrors.password || passwordErrors.newPassword || passwordSaving.value) return;

  passwordSaving.value = true;
  try {
    const response = await axios.put('/users/password/change', {
      id: currentUser.value.id,
      password: passwordForm.password,
      newPassword: passwordForm.newPassword,
    });
    if (response.data.data !== true) {
      ElMessage.error(response.data.msg || '密码修改失败');
      return;
    }
    clearPasswords();
    const refreshed = await refreshCurrentUser();
    passwordDialogOpen.value = false;
    if (refreshed) ElMessage.success('密码修改成功');
    else ElMessage.warning('密码已修改，但个人信息自动刷新失败');
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '密码修改失败，请稍后重试'));
    console.error(error);
  } finally {
    passwordSaving.value = false;
  }
}
</script>

<style scoped>
.account-hub { display: grid; gap: 18px; width: 100%; }
.profile-summary { display: grid; grid-template-columns: minmax(260px, 1.5fr) minmax(240px, 1fr) auto; gap: 28px; align-items: center; padding: 26px; border: 1px solid var(--cc4c-border); border-radius: 18px; background: linear-gradient(135deg, #fff 0%, #f8fbff 100%); box-shadow: var(--cc4c-shadow); }
.profile-summary__identity { display: flex; gap: 18px; align-items: center; min-width: 0; }
.profile-summary__identity :deep(.el-avatar) { flex: 0 0 auto; border: 4px solid #fff; background: #dbeafe; color: var(--cc4c-primary); font-size: 1.7rem; font-weight: 800; box-shadow: 0 8px 24px rgba(37, 99, 235, .16); }
.profile-summary__copy { min-width: 0; }
.profile-summary__copy p { margin: 0 0 4px; color: var(--cc4c-primary); font-size: .78rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.profile-summary__copy h1 { margin: 0; color: var(--cc4c-text); font-size: clamp(1.5rem, 2vw, 2rem); overflow-wrap: anywhere; }
.profile-summary__copy span { display: block; margin-top: 5px; color: var(--cc4c-muted); overflow-wrap: anywhere; }
.profile-summary__facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.profile-summary__facts span { min-width: 0; padding: 12px 14px; border: 1px solid var(--cc4c-border); border-radius: 12px; background: rgba(255, 255, 255, .8); color: var(--cc4c-text); overflow-wrap: anywhere; }
.profile-summary__facts b { display: block; margin-bottom: 3px; color: var(--cc4c-muted); font-size: .72rem; font-weight: 700; }
.profile-summary__actions { display: flex; flex-direction: column; gap: 10px; }
.profile-summary__actions .el-button { width: 100%; margin: 0; }
.account-nav { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 7px; border: 1px solid var(--cc4c-border); border-radius: 15px; background: var(--cc4c-surface); }
.account-nav__item { display: flex; gap: 8px; align-items: center; justify-content: center; min-height: 46px; padding: 10px 14px; border-radius: 10px; color: var(--cc4c-muted); font-weight: 700; text-decoration: none; transition: color var(--cc4c-transition), background var(--cc4c-transition), transform var(--cc4c-transition); }
.account-nav__item:hover { color: var(--cc4c-primary); background: #f3f7ff; transform: translateY(-1px); }
.account-nav__item--active { color: var(--cc4c-primary); background: #eaf1ff; box-shadow: inset 0 0 0 1px rgba(37, 99, 235, .12); }
.account-form :deep(.el-form-item) { margin-bottom: 22px; }
.account-form :deep(.el-select) { width: 100%; }
.account-form__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.form-help { width: 100%; margin: 6px 0 0; color: var(--cc4c-muted); font-size: .78rem; line-height: 1.5; }
.avatar-uploader { width: 100%; }
.avatar-uploader :deep(.el-upload) { width: 100%; border: 0; }
.avatar-preview { display: flex; gap: 14px; align-items: center; width: 100%; min-height: 92px; padding: 14px; border: 1px dashed #b9c8dc; border-radius: 14px; background: #f8fbff; color: var(--cc4c-primary); font-weight: 700; transition: border-color var(--cc4c-transition), background var(--cc4c-transition); }
.avatar-preview:hover { border-color: var(--cc4c-primary); background: #f1f6ff; }
.avatar-preview img { width: 64px; height: 64px; border-radius: 50%; object-fit: cover; box-shadow: 0 6px 16px rgba(15, 23, 42, .12); }
.avatar-preview .el-icon { margin-left: 16px; font-size: 1.7rem; }
.avatar-preview--loading { cursor: wait; opacity: .75; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; }

@media (max-width: 1024px) {
  .profile-summary { grid-template-columns: 1fr auto; }
  .profile-summary__facts { grid-column: 1 / -1; grid-row: 2; }
}

@media (max-width: 640px) {
  .profile-summary { grid-template-columns: 1fr; gap: 20px; padding: 20px; }
  .profile-summary__identity { align-items: flex-start; }
  .profile-summary__identity :deep(.el-avatar) { width: 68px !important; height: 68px !important; }
  .profile-summary__facts { grid-column: auto; grid-row: auto; }
  .profile-summary__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .account-nav { grid-template-columns: 1fr; }
  .account-nav__item { justify-content: flex-start; }
  .account-form__grid { grid-template-columns: 1fr; gap: 0; }
  .dialog-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dialog-actions .el-button { width: 100%; margin: 0; }
}

@media (max-width: 390px) {
  .profile-summary__identity { flex-direction: column; }
  .profile-summary__facts, .profile-summary__actions { grid-template-columns: 1fr; }
}
</style>
