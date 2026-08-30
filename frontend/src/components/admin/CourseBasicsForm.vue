<template>
  <section class="publisher-card" aria-labelledby="course-basic-title">
    <header class="step-heading">
      <span>01</span>
      <div>
        <h2 id="course-basic-title">基础信息</h2>
        <p>设置用户在课程目录中首先看到的内容。</p>
      </div>
    </header>
    <div class="basic-grid">
      <el-form-item label="课程标题 *" :error="errors.courseName">
        <el-input
          :model-value="form.courseName"
          maxlength="200"
          show-word-limit
          clearable
          placeholder="请输入唯一且清晰的课程标题"
          @update:model-value="updateField('courseName', $event)"
        />
      </el-form-item>
      <el-form-item label="课程难度 *">
        <el-select
          :model-value="form.level"
          placeholder="请选择课程难度"
          @update:model-value="updateField('level', $event)"
        >
          <el-option v-for="level in courseLevels" :key="level.value" :label="level.label" :value="level.value" />
        </el-select>
      </el-form-item>
    </div>
  </section>
</template>

<script setup>
defineProps({
  form: { type: Object, required: true },
  errors: { type: Object, required: true },
  courseLevels: { type: Array, required: true },
});

const emit = defineEmits(['clear-error', 'update-field']);

function updateField(field, value) {
  emit('update-field', field, value);
  if (field === 'courseName') emit('clear-error', field);
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
.step-heading > span {
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
.basic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.publisher-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.publisher-card :deep(.el-select) {
  width: 100%;
}

@media (max-width: 700px) {
  .basic-grid {
    grid-template-columns: 1fr;
  }
}
</style>
