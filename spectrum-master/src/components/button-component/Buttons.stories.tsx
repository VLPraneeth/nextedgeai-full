//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import Button, { ButtonProps } from './Button';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<ButtonProps> = (args) => <Button {...args} />;

export const BaseButton = Template.bind({});
BaseButton.args = {
  type: 'default',
  children: 'Button',
};
BaseButton.storyName = 'Button';
BaseButton.parameters = BaseDesignParameters;

export const Danger = Template.bind({});
Danger.args = {
  type: 'danger',
  children: 'Button',
};
Danger.storyName = 'with danger type';
Danger.parameters = BaseDesignParameters;

export const Link = Template.bind({});
Link.args = {
  type: 'link',
  children: 'Button',
};
Link.storyName = 'with link type';
Link.parameters = BaseDesignParameters;

export const Primary = Template.bind({});
Primary.args = {
  type: 'primary',
  children: 'Button',
};
Primary.storyName = 'with primary type';
Primary.parameters = BaseDesignParameters;

export default {
  title: 'General/Button',
  component: Button,
} as Meta;
