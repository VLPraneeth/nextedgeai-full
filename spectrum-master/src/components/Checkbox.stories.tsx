//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import Checkbox, { CheckboxProps } from './Checkbox';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<CheckboxProps> = (args) => <Checkbox {...args} />;

export const BaseButton = Template.bind({});
BaseButton.storyName = 'Checkbox';
BaseButton.parameters = BaseDesignParameters;

export default {
  title: 'General/Checkbox',
  component: Checkbox,
} as Meta;
