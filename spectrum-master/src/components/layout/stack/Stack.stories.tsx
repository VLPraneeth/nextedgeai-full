//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import Button from '../../button-component';
import { Stack, StackProps } from './Stack';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://syncari.atlassian.net/wiki/spaces/UX/pages/1139736592/Layouts',
  },
};

const Template: StoryFn<StackProps> = (args) => (
  <Stack {...args}>
    <Button>Button One</Button>
    <Button>Button Two</Button>
    <Button>Button Three</Button>
  </Stack>
);

Template.storyName = 'Stack';
Template.parameters = BaseDesignParameters;
export { Template };

export const Medium = () => <Template spacing="lg" />;
Medium.storyName = 'with large spacing';

export const Large = () => <Template spacing="xl" />;
Large.storyName = 'with extra large spacing';

export default {
  title: 'Layout/Stack',
  component: Stack,
} as Meta;
