<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑个人资料"
    width="min(560px, calc(100vw - 32px))"
    :close-on-click-modal="!saving"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <el-form label-position="top" class="account-form" @submit.prevent>
      <el-form-item label="头像" :error="errors.avatar">
        <el-upload
          class="avatar-uploader"
          action="#"
          accept="image/jpeg,image/png"
          :show-file-list="false"
          :disabled="avatarUploading || saving"
          :http-request="(request) => emit('upload-avatar', request)"
        >
          <div class="avatar-preview" :class="{ 'avatar-preview--loading': avatarUploading }">
            <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
            <el-icon v-else-if="avatarUploading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Plus /></el-icon>
            <span>{{ avatarUploading ? '上传中…' : '更换头像' }}</span>
          </div>
        </el-upload>
        <p class="form-help">支持 JPG、PNG，文件大小不超过 2MB；保存资料后头像才会正式更新。</p>
      </el-form-item>

      <el-form-item label="用户名" required :error="errors.name">
        <el-input
          :model-value="form.name"
          maxlength="30"
          show-word-limit
          autocomplete="nickname"
          @update:model-value="updateField('name', $event)"
        />
        <p class="form-help">用于个人空间和社区内容中的身份展示。</p>
      </el-form-item>

      <div class="account-form__grid">
        <el-form-item label="所学专业">
          <el-select
            :model-value="form.major"
            placeholder="请选择专业"
            @update:model-value="updateField('major', $event)"
          >
            <el-option v-for="item in majorList" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="订阅语言">
          <el-select
            :model-value="form.language"
            placeholder="请选择语言"
            @update:model-value="updateField('language', $event)"
          >
            <el-option v-for="item in languageList" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <div class="dialog-actions">
        <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="avatarUploading" @click="emit('save')"
          >保存资料</el-button
        >
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { Loading, Plus } from '@element-plus/icons-vue';

defineProps({
  modelValue: { type: Boolean, default: false },
  form: { type: Object, required: true },
  errors: { type: Object, required: true },
  majorList: { type: Array, required: true },
  languageList: { type: Array, required: true },
  avatarPreview: { type: String, default: '' },
  avatarUploading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
});

const emit = defineEmits(['update:modelValue', 'closed', 'upload-avatar', 'clear-error', 'update-field', 'save']);

function updateField(field, value) {
  emit('update-field', field, value);
  if (field === 'name') emit('clear-error', field);
}
</script>

<style scoped>
.account-form :deep(.el-form-item) {
  margin-bottom: 22px;
}
.account-form :deep(.el-select) {
  width: 100%;
}
.account-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.form-help {
  width: 100%;
  margin: 6px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}
.avatar-uploader {
  width: 100%;
}
.avatar-uploader :deep(.el-upload) {
  width: 100%;
  border: 0;
}
.avatar-preview {
  display: flex;
  gap: 14px;
  align-items: center;
  width: 100%;
  min-height: 92px;
  padding: 14px;
  border: 1px dashed #b9c8dc;
  border-radius: 14px;
  background: #f8fbff;
  color: var(--cc4c-primary);
  font-weight: 700;
  transition:
    border-color var(--cc4c-transition),
    background var(--cc4c-transition);
}
.avatar-preview:hover {
  border-color: var(--cc4c-primary);
  background: #f1f6ff;
}
.avatar-preview img {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.12);
}
.avatar-preview .el-icon {
  margin-left: 16px;
  font-size: 1.7rem;
}
.avatar-preview--loading {
  cursor: wait;
  opacity: 0.75;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 640px) {
  .account-form__grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .dialog-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dialog-actions .el-button {
    width: 100%;
    margin: 0;
  }
}
</style>
