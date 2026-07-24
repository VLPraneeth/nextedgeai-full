//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import { TranslatedPipelinePicker as PipelinePicker, PipelinePickerProps } from './PipelinePicker';
import { pipelinePickerEntityValue, pipelineSchemas } from './PipelinePicker.fixtures';

import './PipelinePicker.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<PipelinePickerProps> = (args) => <PipelinePicker {...args} />;

export const BaseText = Template.bind({});
BaseText.args = {
  entities: pipelineSchemas,
  value: pipelinePickerEntityValue,
  hasChanges: true,
};
BaseText.storyName = 'PipelinePicker';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/PipelinePicker',
  component: Template,
} as Meta;
