//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import ImageUpload, { ImageUploadProps } from './ImageUpload';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/LGrSxvh0Sgwrg9iakPLMk7/Quick-Starts---Author-Flow?node-id=0%3A1',
  },
};

const Template: StoryFn<ImageUploadProps> = (args) => <ImageUpload {...args} />;

export const BaseImageUpload = Template.bind({});
BaseImageUpload.args = {
  defaultValue: ' https://app.syncari.com/assets/icons/favicon.png',
};
BaseImageUpload.storyName = 'ImageUpload';
BaseImageUpload.parameters = BaseDesignParameters;

export default {
  title: 'General/ImageUpload',
  component: ImageUpload,
} as Meta;
