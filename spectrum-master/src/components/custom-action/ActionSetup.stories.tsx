//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import ActionSetup, { ActionSetupProps } from './ActionSetup';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/LGrSxvh0Sgwrg9iakPLMk7/Quick-Starts---Author-Flow?node-id=0%3A1',
  },
};

const Template: StoryFn<ActionSetupProps> = (args) => <ActionSetup {...args} />;

export const BaseActionSetup = Template.bind({});
BaseActionSetup.args = {
  className: 'test',
};
BaseActionSetup.storyName = 'ActionSetup';
BaseActionSetup.parameters = BaseDesignParameters;

export default {
  title: 'General/ActionSetup',
  component: ActionSetup,
} as Meta;
