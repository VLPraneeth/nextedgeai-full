//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import { skullColumnsFixture } from './SkullColumns.fixtures';

import { SkullColumns, SkullColumnsProps } from './index';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<SkullColumnsProps> = (args) => <SkullColumns {...args} />;

export const BaseTranslatedText = Template.bind({});
BaseTranslatedText.args = {
  columns: skullColumnsFixture,
};
BaseTranslatedText.storyName = 'Skull Columns';
BaseTranslatedText.parameters = BaseDesignParameters;

export default {
  title: 'skull/SkullColumns',
  component: SkullColumns,
} as Meta;
