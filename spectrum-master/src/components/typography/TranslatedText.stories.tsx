//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import I18nProvider from 'components/I18nProvider';

import { TranslatedText, TranslatedTextProps } from './Text';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<TranslatedTextProps> = (args) => (
  <I18nProvider namespace="TableFilters">
    <TranslatedText {...args} />
  </I18nProvider>
);

export const BaseTranslatedText = Template.bind({});
BaseTranslatedText.args = {
  text: 'filters',
  args: { count: 1 },
};
BaseTranslatedText.storyName = 'Translated Text';
BaseTranslatedText.parameters = BaseDesignParameters;

export default {
  title: 'typography/TranslatedText',
  component: TranslatedText,
} as Meta;
