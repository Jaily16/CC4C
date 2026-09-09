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
/** Markdown 编辑器适配组件，传入净化与标题编号函数，转发正文更新、保存和图片上传事件。 */
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

defineProps({
  modelValue: { type: String, default: '' },
  sanitize: { type: Function, required: true },
  markedHeadingId: { type: Function, required: true },
  toolbarsExclude: { type: Array, default: () => ['link', 'mermaid', 'katex', 'github'] },
});

const emit = defineEmits(['update:modelValue', 'save', 'upload-img']);

/** 将编辑器的文件列表和回调原样转交父页面，由父页面执行上传。 */
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
