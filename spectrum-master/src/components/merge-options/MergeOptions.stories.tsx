//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import { MergeOptionsProps } from './MergeOptions';

import { MergeOptions } from '.';

import './MergeOptions.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<MergeOptionsProps> = (args) => <MergeOptions {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  onChange: () => {},
};
BaseInlineStory.storyName = 'Merge Options';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'Merge Options',
  component: Template,
} as Meta;
