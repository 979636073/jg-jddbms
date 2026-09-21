module.exports = {
  root: true,
  parserOptions: {
    parser: 'babel-eslint',
    sourceType: 'module'
  },
  env: {
    browser: true,
    node: true,
    es6: true
  },
  extends: ['plugin:vue/essential', 'eslint:recommended'],
  rules: {
    'camelcase': 'off',
    'eqeqeq': ['warn', 'always', { null: 'ignore' }],
    'no-empty': 'warn',
    'no-extra-semi': 'off',
    'no-mixed-spaces-and-tabs': 'off',
    'no-prototype-builtins': 'warn',
    'no-unused-labels': 'warn',
    'no-console': 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    'no-useless-escape': 'off',
    'no-unused-vars': ['warn', { vars: 'all', args: 'none' }],
    'vue/no-mutating-props': 'warn',
    'vue/no-side-effects-in-computed-properties': 'warn',
    'vue/no-unused-components': 'warn',
    'vue/no-unused-vars': 'warn',
    'vue/no-v-html': 'off',
    'vue/require-valid-default-prop': 'warn',
    'vue/return-in-computed-property': 'warn'
  }
}
