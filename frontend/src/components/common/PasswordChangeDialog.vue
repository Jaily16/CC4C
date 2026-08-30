<template>
  <el-dialog
    :model-value="modelValue"
    title="修改密码"
    width="min(500px, calc(100vw - 32px))"
    :close-on-click-modal="!saving"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <el-form label-position="top" class="account-form" @submit.prevent>
      <el-form-item label="原密码" required :error="errors.password">
        <el-input
          :model-value="form.password"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入当前密码"
          @update:model-value="updateField('password', $event)"
        />
        <p class="form-help">用于确认本次操作由你本人发起。</p>
      </el-form-item>
      <el-form-item label="新密码" required :error="errors.newPassword">
        <el-input
          :model-value="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="请输入新密码"
          @update:model-value="updateField('newPassword', $event)"
          @keyup.enter="emit('save')"
        />
        <p class="form-help">请使用不易被猜到且不同于其他网站的密码。</p>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-actions">
        <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="saving" @click="emit('save')">确认修改</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  form: { type: Object, required: true },
  errors: { type: Object, required: true },
  saving: { type: Boolean, default: false },
});

const emit = defineEmits(['update:modelValue', 'closed', 'clear-error', 'update-field', 'save']);

function updateField(field, value) {
  emit('update-field', field, value);
  emit('clear-error', field);
}
</script>

<style scoped>
.account-form :deep(.el-form-item) {
  margin-bottom: 22px;
}
.form-help {
  width: 100%;
  margin: 6px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 640px) {
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
