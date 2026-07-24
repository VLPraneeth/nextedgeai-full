//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import Fieldset, { FieldSetProps } from 'components/Fieldset';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<FieldSetProps> = (args) => <Fieldset {...args} />;

export const BaseFieldSet = Template.bind({});
BaseFieldSet.args = {};
BaseFieldSet.storyName = 'Fieldset';
BaseFieldSet.parameters = BaseDesignParameters;

export default {
  title: 'General/Fieldset',
  component: Fieldset,
} as Meta;
