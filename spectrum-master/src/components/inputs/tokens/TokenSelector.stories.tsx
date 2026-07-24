//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import TokenSelector, { TokenSelectorProps } from './TokenSelector';
import { tokenSelectorFixture } from './TokenSelector.fixtures';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<TokenSelectorProps> = (args) => <TokenSelector {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  tokens: tokenSelectorFixture,
  onTokenSelect: (token) => {
    console.log(token);
  },
  label: 'hello',
};
BaseInlineStory.storyName = 'Token Selector';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'Input/Token Selector',
  component: TokenSelector,
} as Meta;
