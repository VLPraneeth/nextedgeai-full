//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import Button from 'components/button-component';

import Center, { CenterProps } from './Center';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://syncari.atlassian.net/wiki/spaces/UX/pages/1139736592/Layouts',
  },
};
const Template: StoryFn<CenterProps> = (args) => (
  <Center {...args}>
    <Button>Center Me</Button>
  </Center>
);

Template.storyName = 'Center';
Template.parameters = BaseDesignParameters;

export { Template };

export default {
  title: 'Layout/Center',
  component: Center,
} as Meta;
