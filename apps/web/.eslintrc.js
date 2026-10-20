module.exports = {
  parser: '@typescript-eslint/parser',
  plugins: ['@typescript-eslint', 'prettier', 'react-hooks'],
  extends: ['eslint:recommended', 'plugin:@typescript-eslint/recommended', 'prettier', 'plugin:prettier/recommended'],
  extends: require.resolve('@umijs/max/eslint'),

  rules: {
    'react-hooks/rules-of-hooks': 'error', //Recoil
    'react-hooks/exhaustive-deps': [
      'warn',
      {
        additionalHooks: 'useRecoilCallback',
      },
    ], //Recoil
    'array-callback-return': 'error', //在数组方法的回调中强制执行 return 语句
    'no-const-assign': 'error', //禁止重新分配 const 变量
    'no-dupe-args': 'error', //禁止 function 定义中的重复参数
    'no-dupe-else-if': 'error', //禁止 if-else-if 链中的重复条件
    'no-dupe-keys': 'error', //不允许对象字面量中的重复键。
    'no-duplicate-case': 'error', //允许在 switch 语句的 case 子句中出现重复的测试表达式
    'no-duplicate-imports': 'error', //要求来自单个模块的所有可以合并的导入都存在于单个 import 语句中
    'no-fallthrough': 'error', //每个 case 语句都应以 throw、return、break 或注释结尾
    'no-func-assign': 'error', //不允许重新分配 function 声明


    'prettier/prettier': 'off',
    '@typescript-eslint/explicit-module-boundary-types': 'off',
    '@typescript-eslint/no-explicit-any': 'off',
    '@typescript-eslint/no-unused-vars': 'warn', //未使用的变量，改成警告
  },
  parserOptions: {
    ecmaVersion: 2020,
    sourceType: 'module',
  },
  env: {
    browser: true,
    node: true,
    es6: true,
  },
};
