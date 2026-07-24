//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';
import { Provider } from 'react-redux';

import I18nProvider from 'components/I18nProvider';
import configureAppStore from 'store/configureStore';
import { init } from 'utils/i18nUtil';

import QuickStartInstallSchemaMatcher, { QuickStartInstallSchemaMatcherProps } from './QuickStartInstallSchemaMatcher';
import { installSchemaMatcherDefaultValue, installSchemaMatcherItems } from './QuickStartInstallSchemaMatcher.fixtures';

import './QuickStartInstallSchemaMatcher.less';

init();

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/31WfiIoKm3VHhJcvFsokoN/Quick-Starts-Install-Flow?node-id=82%3A240',
  },
};

const store = configureAppStore();

const Template: StoryFn<QuickStartInstallSchemaMatcherProps> = (args) => (
  <Provider store={store}>
    <I18nProvider namespace="QuickStart">
      <QuickStartInstallSchemaMatcher {...args} />
    </I18nProvider>
  </Provider>
);

export const BaseText = Template.bind({});
BaseText.args = {
  items: installSchemaMatcherItems,
  synapseName: 'Hubspot-1',
  onChange: () => {},
  defaultValue: installSchemaMatcherDefaultValue,
};
BaseText.storyName = 'QuickStartInstallSchemaMatcher';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/QuickStartInstallSchemaMatcher',
  component: Template,
} as Meta;
