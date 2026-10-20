// {
//   "printWidth": 80,
//   "singleQuote": true,
//   "trailingComma": "all",
//   "proseWrap": "never",
//   "overrides": [{ "files": ".prettierrc", "options": { "parser": "json" } }],
//   "plugins": ["prettier-plugin-organize-imports", "prettier-plugin-packagejson"]
// }

module.exports = {
  semi: true, //在语句末尾添加分号
  trailingComma: 'all', //在对象或数组最后一个元素后面添加逗号
  singleQuote: true, //使用单引号
  printWidth: 80, //一行的最大长度是80个字符
  tabWidth: 2, //使用2个空格作为缩进
  bracketSpacing: true, //打印对象文字中括号之间的空格{ foo: bar }
  arrowParens: 'always', //始终包含括号。例子：(x) => x
  // useTabs: true, // 是否使用 tab 进行缩进
};
