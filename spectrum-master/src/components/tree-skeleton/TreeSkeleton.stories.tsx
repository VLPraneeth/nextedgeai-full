//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import TreeSkeleton, { TreeSkeletonProps } from './TreeSkeleton';
import treeSkeletonItems from './TreeSkeleton.fixtures';

import './TreeSkeleton.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<TreeSkeletonProps> = (args) => <TreeSkeleton {...args} />;

export const BaseText = Template.bind({});

BaseText.args = {
  items: [
    ...treeSkeletonItems,
    { key: 'four', label: 'Sub-tree', children: <TreeSkeleton items={treeSkeletonItems} /> },
  ],
};
BaseText.storyName = 'Tree Skeleton';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/TreeSkeleton',
  component: Template,
} as Meta;
