import eslint from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier/flat';
import eslintPluginVue from 'eslint-plugin-vue';
import globals from 'globals';

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'public/avatar/**',
      'public/blogImg/**',
    ],
  },
  eslint.configs.recommended,
  ...eslintPluginVue.configs['flat/recommended'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
      sourceType: 'module',
    },
    rules: {
      'no-console': 'error',
      'no-debugger': 'error',
      'vue/multi-word-component-names': [
        'error',
        {
          ignores: ['index', 'Header', 'Sidebar', 'Login', 'Register'],
        },
      ],
    },
  },
  eslintConfigPrettier,
];
