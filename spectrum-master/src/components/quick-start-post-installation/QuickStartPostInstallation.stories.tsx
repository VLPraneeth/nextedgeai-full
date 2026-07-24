//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import QuickStartPostInstallation, { QuickStartPostInstallationProps } from './QuickStartPostInstallation';
import './QuickStartPostInstallation.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<QuickStartPostInstallationProps> = (args) => <QuickStartPostInstallation {...args} />;

export const BaseText = Template.bind({});
BaseText.args = {
  postInstallMessage: `Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur id placerat purus, et gravida elit.
  Morbi et magna ac nisi euismod laoreet. Morbi sed dignissim justo. Pellentesque imperdiet tincidunt ante, nec imperdiet diam maximus id. Aliquam erat volutpat. Phasellus venenatis nec purus sollicitudin dictum. Donec porttitor leo vel mauris gravida hendrerit semper a urna. Maecenas non sem massa.

  Aenean ut maximus quam. Mauris eget justo est. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam gravida est id interdum sodales.`,
};
BaseText.storyName = 'QuickStartPostInstallation';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/QuickStartPostInstallation',
  component: Template,
} as Meta;
