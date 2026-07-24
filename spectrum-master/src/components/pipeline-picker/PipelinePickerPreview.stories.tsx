//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import { pipelineSchemas } from './PipelinePicker.fixtures';
import PipelinePickerPreview, { PipelinePickerPreviewProps } from './PipelinePickerPreview';

import './PipelinePicker.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<PipelinePickerPreviewProps> = (args) => <PipelinePickerPreview {...args} />;

export const BaseText = Template.bind({});
BaseText.args = {
  entities: pipelineSchemas,
};
BaseText.storyName = 'PipelinePickerPreview';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/PipelinePickerPreview',
  component: Template,
} as Meta;
