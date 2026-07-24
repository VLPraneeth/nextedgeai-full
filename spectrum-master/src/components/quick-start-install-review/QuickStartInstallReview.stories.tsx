//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import { installReviewItems } from './QuickStartInstall.fixtures';
import QuickStartInstallReview, { QuickStartInstallReviewProps } from './QuickStartInstallReview';

import './QuickStartInstallReview.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<QuickStartInstallReviewProps> = (args) => <QuickStartInstallReview {...args} />;

export const BaseText = Template.bind({});
BaseText.args = {
  items: installReviewItems,
};
BaseText.storyName = 'QuickStartInstallReview';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/QuickStartInstallReview',
  component: Template,
} as Meta;
