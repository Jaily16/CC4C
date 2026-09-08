<template>
  <div class="markdown-preview">
    <MdEditor
      :model-value="modelValue"
      :sanitize="sanitize"
      :marked-heading-id="markedHeadingId"
      :toolbars-exclude="toolbarsExclude"
      @update:model-value="emit('update:modelValue', $event)"
      @on-save="emit('save', $event)"
      @on-upload-img="handleUploadImg"
    />
  </div>
</template>

<script setup>
/** Markdown 预览组件，渲染受控正文并转发编辑器图片上传请求。 */
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

defineProps({
  modelValue: { type: String, default: '' },
  sanitize: { type: Function, required: true },
  markedHeadingId: { type: Function, required: true },
  toolbarsExclude: { type: Array, default: () => ['link', 'mermaid', 'katex', 'github'] },
});

const emit = defineEmits(['update:modelValue', 'save', 'upload-img']);

/** handleUploadImg 封装当前组件的一项语义操作，并保持既有状态与错误处理边界。 */
function handleUploadImg(...args) {
  emit('upload-img', ...args);
}
</script>

<style scoped>
.markdown-preview {
  min-width: 0;
}
.markdown-preview :deep(.md-editor) {
  width: 100%;
}
</style>
