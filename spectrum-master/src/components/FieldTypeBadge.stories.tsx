//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import FieldTypeBadge, { FieldTypeBadgeProps } from './FieldTypeBadge';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<FieldTypeBadgeProps> = (args) => <FieldTypeBadge {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  dataType: 'string',
};
BaseInlineStory.storyName = 'FieldTypeBadge';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'General/FieldTypeBadge',
  component: FieldTypeBadge,
} as Meta;
