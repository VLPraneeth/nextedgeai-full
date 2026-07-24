//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import { TextTag } from 'components/text-tag';

import ListItem, { ListItemProps } from './ListItem';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: '',
  },
};

const Template: StoryFn<ListItemProps> = (args) => <ListItem {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  title: 'A great list item',
  description: 'Descriptive text.',
  tags: (
    <div>
      <TextTag text="hello" color="green" />
      <TextTag text="goodbye" color="blue" />
    </div>
  ),
};
BaseInlineStory.storyName = 'ListItem';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'General/ListItem',
  component: Template,
} as Meta;
