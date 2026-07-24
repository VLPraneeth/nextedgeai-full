//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import TextTag, { TextTagProps } from './TextTag';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/LGrSxvh0Sgwrg9iakPLMk7/Quick-Starts---Author-Flow?node-id=0%3A1',
  },
};

const Template: StoryFn<{ tags: TextTagProps[] }> = (args) => (
  <div>
    {args.tags.map((tag, index) => (
      <TextTag key={index} {...tag} />
    ))}
  </div>
);

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  tags: [
    { text: 'Library', color: 'green' },
    { text: 'Shared', color: 'blue' },
    { text: 'Draft', color: 'orange' },
  ],
};
BaseInlineStory.storyName = 'TextTag';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'General/TextTag',
  component: Template,
} as Meta;
