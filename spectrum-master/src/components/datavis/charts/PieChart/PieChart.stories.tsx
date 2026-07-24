//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import { InlineMessage, InlineMessageProps } from 'components';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<InlineMessageProps> = (args) => <InlineMessage {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  type: 'success',
  children: 'Pie Chart',
};
BaseInlineStory.storyName = 'Pie Chart';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'Chart/Pie Chart',
  component: InlineMessage,
} as Meta;
