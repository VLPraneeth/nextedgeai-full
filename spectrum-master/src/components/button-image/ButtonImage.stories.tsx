//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import ButtonImage, { ButtonImageProps } from './ButtonImage';

const Template: StoryFn<ButtonImageProps> = (args) => <ButtonImage {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {
  onClick: () => {},
  imageSrc: 'http://localhost:3000/assets/icons/syncari-logo-story-book.png',
  imageAlt: 'Syncari logo',
};
BaseInlineStory.storyName = 'ButtonImage';

export default {
  title: 'General/ButtonImage',
  component: ButtonImage,
} as Meta;
