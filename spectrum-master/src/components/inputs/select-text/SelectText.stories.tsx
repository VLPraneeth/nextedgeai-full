//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import SelectText, { SelectTextProps } from './SelectText';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/LGrSxvh0Sgwrg9iakPLMk7/Quick-Starts---Author-Flow?node-id=0%3A1',
  },
};

const Template: StoryFn<SelectTextProps> = (args) => <SelectText {...args} />;

export const BaseSelectText = Template.bind({});
BaseSelectText.args = {
  className: 'test',
  selectPicklistValues: [
    {
      label: 'GET',
      value: 'GET',
    },
    {
      label: 'POST',
      value: 'POST',
    },
    {
      label: 'DELETE',
      value: 'DELETE',
    },
    {
      label: 'PUT',
      value: 'PUT',
    },
    {
      label: 'PATCH',
      value: 'PATCH',
    },
  ],
};

BaseSelectText.storyName = 'SelectText';
BaseSelectText.parameters = BaseDesignParameters;

export default {
  title: 'General/SelectText',
  component: SelectText,
} as Meta;
