//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import { Steps, StepsProps, Step } from 'components';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/LGrSxvh0Sgwrg9iakPLMk7/Quick-Starts-Author-Flow?node-id=4%3A2',
  },
};

const Template: StoryFn<StepsProps> = (args) => <Steps direction="vertical" {...args} />;

export const BaseSteps = Template.bind({});
BaseSteps.args = {
  children: [
    <Step title="Basic Settings" key="basicSettings" />,
    <Step title="Pipeline Settings" key="pipelineSettings" />,
    <Step title="Review" key="review" />,
    <Step title="Publish" key="publish" />,
  ],
};
BaseSteps.storyName = 'Steps';
BaseSteps.parameters = BaseDesignParameters;

export default {
  title: 'General/Steps',
  component: Steps,
} as Meta;
