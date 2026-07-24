//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import AutoSuggest, { AutoSuggestProps } from './AutoSuggest';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<AutoSuggestProps> = (args) => <AutoSuggest {...args} />;

export const BaseAutoSuggest = Template.bind({});
BaseAutoSuggest.args = {
  data: [{ value: 'value', text: 'text' }],
};
BaseAutoSuggest.storyName = 'AutoSuggest';
BaseAutoSuggest.parameters = BaseDesignParameters;

export default {
  title: 'General/Auto Suggest',
  component: AutoSuggest,
} as Meta;
