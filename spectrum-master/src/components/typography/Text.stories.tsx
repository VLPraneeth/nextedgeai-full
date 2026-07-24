//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import Text, { TextProps } from './Text';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<TextProps> = (args) => <Text {...args} />;

export const BaseText = Template.bind({});
BaseText.args = {
  children: 'Hello',
};
BaseText.storyName = 'Text';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'typography/Text',
  component: Text,
} as Meta;
