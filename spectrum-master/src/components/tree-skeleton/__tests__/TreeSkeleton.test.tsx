//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, userEvent } from 'tests/helpers';

import TreeSkeleton from '../TreeSkeleton';
import treeSkeletonItems from '../TreeSkeleton.fixtures';

test('TreeSkeleton renders items without their content visible', async () => {
  const { findByText, queryByText } = render(<TreeSkeleton items={treeSkeletonItems} />);

  const firstItem = await findByText(treeSkeletonItems[0].label as string);
  expect(firstItem).toBeInTheDocument();

  // Body of the first item is not visible
  expect(queryByText(treeSkeletonItems[0].children as string)).not.toBeInTheDocument();
});

test('TreeSkeleton renders items child after clicking expand', async () => {
  const { findByText, getAllByLabelText } = render(<TreeSkeleton items={treeSkeletonItems} />);

  const firstItem = await findByText(treeSkeletonItems[0].label as string);
  expect(firstItem).toBeInTheDocument();

  const expandButtons = getAllByLabelText('expand-branch');
  await userEvent.click(expandButtons[0]);

  const firstItemBody = await findByText(treeSkeletonItems[0].children as string);
  expect(firstItemBody).toBeInTheDocument();
});
