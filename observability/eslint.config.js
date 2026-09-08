import eslint from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier/flat';
import eslintPluginVue from 'eslint-plugin-vue';
import globals from 'globals';
import functionalComments from '../infrastructure/quality/eslint-functional-comments.mjs';

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  eslint.configs.recommended,
  ...eslintPluginVue.configs['flat/recommended'],
  {
    plugins: { cc4c: functionalComments },
  },
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      globals: { ...globals.browser, ...globals.node },
      sourceType: 'module',
    },
    rules: {
      'no-console': 'error',
      'no-debugger': 'error',
      'cc4c/require-chinese-functional-comment': 'error',
      'vue/multi-word-component-names': ['error', { ignores: ['App'] }],
    },
  },
  eslintConfigPrettier,
];
