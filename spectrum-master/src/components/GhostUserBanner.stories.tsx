//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import GhostUserBanner from './GhostUserBanner';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<{}> = (args) => <GhostUserBanner {...args} />;

export const BaseInlineStory = Template.bind({});
BaseInlineStory.args = {};
BaseInlineStory.storyName = 'Ghost User Banner';
BaseInlineStory.parameters = BaseDesignParameters;

export default {
  title: 'General/Ghost User Banner',
  component: GhostUserBanner,
} as Meta;
