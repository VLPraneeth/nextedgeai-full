// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, screen } from '@testing-library/react';

import EmptyGraphContent from 'components/EmptyGraphContent';

it('EmptyGraphContent renders action text', async () => {
  render(<EmptyGraphContent actionText="Create a draft pipeline" />);
  expect((await screen.findByText('Create a draft pipeline')).textContent).toMatch(/Create a draft pipeline/);
});

it('EmptyGraphContent renders empty text', async () => {
  render(
    <EmptyGraphContent actionText="Create a draft pipeline">
      <span>empty content</span>
    </EmptyGraphContent>
  );
  expect((await screen.findByText('empty content')).textContent).toMatch(/empty content/);
});
