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
  children: 'Instance Switcher Menu Item',
};
BaseInlineStory.storyName = 'Instance Switcher Menu Item';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'General/Instance Switcher Menu Item',
  component: InlineMessage,
} as Meta;
