import js from '@eslint/js'
import globals from 'globals'

export default [
  { ignores: ['node_modules'] },
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.node, chrome: 'readonly' },
    },
    rules: js.configs.recommended.rules,
  },
  {
    // Content scripts are classic scripts injected into the page
    files: ['content/**/*.js'],
    languageOptions: { sourceType: 'script' },
  },
]
