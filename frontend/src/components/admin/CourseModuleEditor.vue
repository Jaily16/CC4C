<template>
  <section class="publisher-card" aria-labelledby="course-module-title">
    <header class="step-heading step-heading--action">
      <div class="step-heading__copy">
        <span>02</span>
        <div>
          <h2 id="course-module-title">语言与课程模块</h2>
          <p>课程必须归属一个已经存在的语言模块。</p>
        </div>
      </div>
      <el-button :icon="Plus" @click="emit('open')">添加语言模块</el-button>
    </header>

    <div v-if="moduleLoading" class="module-feedback" aria-live="polite">
      <el-icon class="is-loading"><Loading /></el-icon><span>正在加载语言模块…</span>
    </div>
    <el-alert v-else-if="moduleError" type="error" :closable="false" show-icon :title="moduleError">
      <el-button type="primary" plain @click="emit('load')">重新加载</el-button>
    </el-alert>
    <el-alert
      v-else-if="!moduleAvailable"
      type="warning"
      :closable="false"
      show-icon
      title="当前没有可用的语言模块，请先添加模块后再发布课程。"
    />

    <el-form-item class="module-select" label="课程语言模块 *" :error="errors.module">
      <el-cascader
        :model-value="modelValue"
        :options="modules"
        :disabled="moduleLoading || Boolean(moduleError) || !moduleAvailable"
        placeholder="先选择语言，再选择课程模块"
        clearable
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('clear-error', 'module')"
      />
      <p class="field-help">已加载 {{ moduleCount }} 个可用模块；模块选择决定课程在学习目录中的位置。</p>
    </el-form-item>

    <el-dialog
      :model-value="dialogOpen"
      title="添加语言模块"
      width="min(520px, calc(100vw - 32px))"
      @update:model-value="emit('update:dialogOpen', $event)"
      @closed="emit('reset')"
    >
      <el-form label-position="top">
        <div class="dialog-grid">
          <el-form-item label="所属语言 *">
            <el-select
              :model-value="moduleForm.languageId"
              @update:model-value="updateModuleField('languageId', $event)"
            >
              <el-option
                v-for="language in languages"
                :key="language.value"
                :label="language.label"
                :value="language.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模块名称 *" :error="moduleNameError">
            <el-input
              :model-value="moduleForm.moduleName"
              maxlength="50"
              placeholder="例如：Java 基础"
              @update:model-value="updateModuleField('moduleName', $event)"
            />
          </el-form-item>
          <el-form-item label="模块难度 *">
            <el-select :model-value="moduleForm.level" @update:model-value="updateModuleField('level', $event)">
              <el-option v-for="level in moduleLevels" :key="level.value" :label="level.label" :value="level.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="模块优先级 *">
            <el-select :model-value="moduleForm.priority" @update:model-value="updateModuleField('priority', $event)">
              <el-option v-for="priority in 11" :key="priority - 1" :label="priority - 1" :value="priority - 1" />
            </el-select>
          </el-form-item>
        </div>
        <p class="dialog-help">同一语言下的模块优先级不能重复。</p>
      </el-form>
      <template #footer>
        <el-button :disabled="moduleSubmitting" @click="emit('update:dialogOpen', false)">取消</el-button>
        <el-button type="primary" :loading="moduleSubmitting" @click="emit('add')">添加模块</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { Loading, Plus } from '@element-plus/icons-vue';

defineProps({
  modelValue: { type: Array, default: () => [] },
  modules: { type: Array, required: true },
  errors: { type: Object, required: true },
  moduleLoading: { type: Boolean, default: false },
  moduleError: { type: String, default: '' },
  moduleAvailable: { type: Boolean, default: false },
  moduleCount: { type: Number, default: 0 },
  languages: { type: Array, required: true },
  dialogOpen: { type: Boolean, default: false },
  moduleForm: { type: Object, required: true },
  moduleNameError: { type: String, default: '' },
  moduleLevels: { type: Array, required: true },
  moduleSubmitting: { type: Boolean, default: false },
});

const emit = defineEmits([
  'update:modelValue',
  'update:dialogOpen',
  'open',
  'load',
  'reset',
  'add',
  'clear-error',
  'clear-module-error',
  'update-module-field',
]);

function updateModuleField(field, value) {
  emit('update-module-field', field, value);
  if (field === 'moduleName') emit('clear-module-error');
}
</script>

<style scoped>
.publisher-card {
  min-width: 0;
  padding: clamp(20px, 3vw, 30px);
  border: 1px solid var(--cc4c-border);
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 9px 26px rgba(15, 23, 42, 0.05);
}
.step-heading {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 22px;
}
.step-heading > span,
.step-heading__copy > span {
  display: grid;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 12px;
  background: #eaf1ff;
  color: var(--cc4c-primary);
  font-size: 0.72rem;
  font-weight: 900;
}
.step-heading h2 {
  margin: 0;
  font-size: 1.08rem;
}
.step-heading p {
  margin: 4px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.8rem;
}
.step-heading--action {
  justify-content: space-between;
}
.step-heading__copy {
  display: flex;
  gap: 14px;
  align-items: center;
}
.publisher-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.publisher-card :deep(.el-select),
.publisher-card :deep(.el-cascader),
.dialog-grid :deep(.el-select) {
  width: 100%;
}
.module-select {
  margin-top: 20px !important;
}
.field-help {
  width: 100%;
  margin: 7px 0 0;
  color: var(--cc4c-muted);
  font-size: 0.75rem;
}
.module-feedback {
  display: flex;
  gap: 9px;
  align-items: center;
  min-height: 66px;
  padding: 16px;
  border-radius: 12px;
  background: #f4f8ff;
  color: var(--cc4c-primary);
}
.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.dialog-help {
  margin: 0;
  color: var(--cc4c-muted);
  font-size: 0.78rem;
}

@media (max-width: 700px) {
  .step-heading--action {
    align-items: flex-start;
    flex-direction: column;
  }
  .step-heading--action .el-button {
    width: 100%;
  }
  .dialog-grid {
    grid-template-columns: 1fr;
  }
}
</style>
