//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import QuickStartIssueResolution, { QuickStartIssueResolutionProps } from './QuickStartIssueResolution';

import './QuickStartIssueResolution.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<QuickStartIssueResolutionProps> = (args) => <QuickStartIssueResolution {...args} />;

export const BaseText = Template.bind({});
BaseText.args = {
  successTitle: 'ClearBit credentials successfully verified',
  successMessage: 'We successfully verified ClearBit credentials, and you can now continue to the next step.',
};
BaseText.storyName = 'QuickStartIssueResolution';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/QuickStartIssueResolution',
  component: Template,
} as Meta;
