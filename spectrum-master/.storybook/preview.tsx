//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import type { Preview } from '@storybook/react';
import { Decorator } from '@storybook/react';
import React from 'react';
import { Provider } from 'react-redux';
import 'antd/dist/antd.css';

import { init as initI18n } from '../src/utils/i18nUtil';
import configureAppStore from '../src/store/configureStore';

// Initialize i18n for Storybook
initI18n();

// Create a Redux store for Storybook
const store = configureAppStore();

// Redux Provider decorator
const withRedux: Decorator = (Story) => (
  <Provider store={store}>
    <Story />
  </Provider>
);

const preview: Preview = {
  decorators: [withRedux],
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    // options: {
    //   storySort: (a, b) => (a[1].kind === b[1].kind ? 0 : a[1].id.localeCompare(b[1].id, undefined, { numeric: true })),
    // },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/,
      },
    },
  },
};

export default preview;
