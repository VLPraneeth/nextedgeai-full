//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import type { StorybookConfig } from '@storybook/react-webpack5';
import { merge } from 'lodash';
import webpackConfig from './webpack.config';

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.mdx', '../src/**/*.stories.@(ts|tsx)'],
  addons: ['@storybook/addon-links', '@storybook/addon-a11y', '@storybook/addon-docs'],
  framework: {
    name: '@storybook/react-webpack5',
    options: {},
  },
  webpackFinal: (config: any) => {
    const result = merge(config, webpackConfig);

    // Add babel-loader for tsx/ts files to handle JSX
    result.module.rules.push({
      test: /\.(ts|tsx)$/,
      exclude: /node_modules/,
      use: [
        {
          loader: require.resolve('babel-loader'),
          options: {
            presets: [
              [require.resolve('@babel/preset-react'), { runtime: 'automatic' }],
              require.resolve('@babel/preset-typescript'),
            ],
            plugins: [
              require.resolve('@babel/plugin-proposal-optional-chaining'),
              require.resolve('@babel/plugin-proposal-export-namespace-from'),
            ],
          },
        },
      ],
    });

    result.module.rules.push({
      test: /\.less$/,
      use: [
        {
          loader: 'style-loader',
        },
        {
          loader: 'css-loader',
        },
        {
          loader: 'less-loader',
          options: {
            lessOptions: {
              javascriptEnabled: true,
            },
          },
        },
      ],
    });

    result.module.rules.push({
      test: /\.s[ac]ss$/i,
      use: [
        // Creates `style` nodes from JS strings
        'style-loader',
        // Translates CSS into CommonJS
        'css-loader',
        // Compiles Sass to CSS
        'sass-loader',
      ],
    });

    // remove svg from existing rule - https://github.com/storybookjs/storybook/issues/6188#issuecomment-487705465
    result.module.rules = result.module.rules.map((rule: any) => {
      if (
        String(rule.test) ===
        String(/\.(svg|ico|jpg|jpeg|png|apng|gif|eot|otf|webp|ttf|woff|woff2|cur|ani|pdf)(\?.*)?$/)
      ) {
        return {
          test: /\.svg$/,
          use: ['@svgr/webpack', 'url-loader'],
        };
      }

      return rule;
    });

    return result;
  },
};

export default config;
