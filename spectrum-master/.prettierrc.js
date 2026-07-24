'use strict';

module.exports = {
  printWidth: 120,
  tabWidth: 2,
  useTabs: false,
  semi: true,
  singleQuote: true,
  jsxBracketSameLine: true,
  bracketSpacing: true,
  trailingComma: 'es5',
  arrowParens: 'always',
  overrides: [
    {
      files: ['*.tsx', '*.ts'],
      options: {
        parser: 'typescript',
      },
    },
    {
      files: '*.less',
      options: {
        parser: 'css',
      },
    },
  ],
};
